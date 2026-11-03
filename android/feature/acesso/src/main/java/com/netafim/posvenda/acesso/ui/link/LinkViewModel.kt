package com.netafim.posvenda.acesso.ui.link

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netafim.posvenda.acesso.dominio.AcaoLink
import com.netafim.posvenda.acesso.dominio.ErroAcesso
import com.netafim.posvenda.acesso.dominio.LinkAcesso
import com.netafim.posvenda.acesso.dominio.RepositorioAcesso
import com.netafim.posvenda.acesso.dominio.Resultado
import com.netafim.posvenda.acesso.dominio.Validacao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CampoLink { SENHA, CONFIRMACAO, TERMO }

data class LinkEstado(
    /** null enquanto o link não foi lido; o token é apagado assim que o link é usado. */
    val link: LinkAcesso? = null,
    val acao: AcaoLink? = null,
    val linkInvalido: Boolean = false,
    val senha: String = "",
    val confirmacao: String = "",
    val aceiteTermo: Boolean = false,
    val versaoTermo: String? = null,
    val erros: Map<CampoLink, String> = emptyMap(),
    val erroGeral: String? = null,
    val carregando: Boolean = false,
    /** Mensagem de sucesso da API; quando preenchida, a tela mostra a confirmação. */
    val concluido: String? = null,
)

/**
 * Tela aberta pelo link do e-mail: define a senha de um convite (com o aceite do aviso de privacidade)
 * ou redefine uma senha esquecida.
 */
class LinkViewModel(private val repositorio: RepositorioAcesso) : ViewModel() {

    private val _estado = MutableStateFlow(LinkEstado())
    val estado: StateFlow<LinkEstado> = _estado.asStateFlow()

    /** Lê o link recebido. Chamado de novo com os mesmos valores (recomposição), não faz nada. */
    fun definirLink(acao: String?, token: String?) {
        val atual = _estado.value
        if (atual.link != null || atual.linkInvalido || atual.concluido != null) return
        val link = LinkAcesso.de(acao, token)
        if (link == null) {
            _estado.update { it.copy(linkInvalido = true, acao = AcaoLink.de(acao)) }
            return
        }
        _estado.update { it.copy(link = link, acao = link.acao) }
        if (link.acao == AcaoLink.CONVITE) carregarTermo()
    }

    fun carregarTermo() {
        viewModelScope.launch {
            when (val r = repositorio.versaoTermoVigente()) {
                is Resultado.Sucesso -> _estado.update { it.copy(versaoTermo = r.valor) }
                is Resultado.Falha -> _estado.update { it.copy(erroGeral = r.erro.mensagem) }
            }
        }
    }

    fun aoMudar(campo: CampoLink, valor: String) = _estado.update {
        val novo = when (campo) {
            CampoLink.SENHA -> it.copy(senha = valor)
            CampoLink.CONFIRMACAO -> it.copy(confirmacao = valor)
            CampoLink.TERMO -> it
        }
        novo.copy(erros = it.erros - campo, erroGeral = null)
    }

    fun aoMudarAceite(aceito: Boolean) = _estado.update {
        it.copy(aceiteTermo = aceito, erros = it.erros - CampoLink.TERMO)
    }

    fun enviar() {
        val atual = _estado.value
        val link = atual.link ?: return
        if (atual.carregando) return
        val erros = validar(atual, link.acao)
        if (erros.isNotEmpty()) {
            _estado.update { it.copy(erros = erros) }
            return
        }
        val versao = atual.versaoTermo
        if (link.acao == AcaoLink.CONVITE && versao == null) {
            carregarTermo()
            return
        }
        _estado.update { it.copy(carregando = true, erroGeral = null) }
        viewModelScope.launch {
            val resultado = when (link.acao) {
                AcaoLink.CONVITE -> repositorio.aceitarConvite(link.token, atual.senha, versao.orEmpty())
                AcaoLink.REDEFINIR_SENHA -> repositorio.redefinirSenha(link.token, atual.senha)
            }
            when (resultado) {
                is Resultado.Sucesso -> _estado.update {
                    // O token é de uso único: depois de usado, não fica guardado no estado.
                    it.copy(
                        carregando = false,
                        concluido = resultado.valor,
                        link = null,
                        senha = "",
                        confirmacao = "",
                    )
                }
                is Resultado.Falha -> {
                    _estado.update { aplicarErro(it, resultado.erro) }
                    val erro = resultado.erro
                    if (erro is ErroAcesso.Api && erro.codigo == TERMO_DESATUALIZADO) carregarTermo()
                }
            }
        }
    }

    private fun validar(e: LinkEstado, acao: AcaoLink): Map<CampoLink, String> = buildMap {
        Validacao.novaSenha(e.senha)?.let { put(CampoLink.SENHA, it) }
        Validacao.confirmacao(e.senha, e.confirmacao)?.let { put(CampoLink.CONFIRMACAO, it) }
        if (acao == AcaoLink.CONVITE && !e.aceiteTermo) {
            put(CampoLink.TERMO, "Aceite o aviso de privacidade para continuar.")
        }
    }

    private fun aplicarErro(e: LinkEstado, erro: ErroAcesso): LinkEstado {
        val base = e.copy(carregando = false)
        if (erro is ErroAcesso.Campos) {
            val porCampo = erro.campos.mapNotNull { (campo, msg) ->
                CAMPOS_API[campo]?.let { it to msg }
            }.toMap()
            // Erro no próprio token (campo "token") ou em campo desconhecido vira mensagem geral.
            return base.copy(
                erros = porCampo,
                erroGeral = if (porCampo.isEmpty()) MENSAGEM_LINK_INVALIDO else null,
            )
        }
        if (erro is ErroAcesso.Api) {
            if (erro.codigo == TOKEN_INVALIDO) return base.copy(erroGeral = MENSAGEM_LINK_INVALIDO)
            if (erro.codigo == TERMO_DESATUALIZADO) {
                return base.copy(aceiteTermo = false, erroGeral = erro.mensagem)
            }
        }
        return base.copy(erroGeral = erro.mensagem)
    }

    companion object {
        const val TOKEN_INVALIDO = "token_invalido"
        const val TERMO_DESATUALIZADO = "termo_desatualizado"
        const val MENSAGEM_LINK_INVALIDO =
            "Este link é inválido ou já expirou. Peça um novo ao administrador ou use " +
                "\"Esqueci minha senha\"."
        private val CAMPOS_API = mapOf(
            "senha" to CampoLink.SENHA,
            "nova_senha" to CampoLink.SENHA,
            "aceite_termo" to CampoLink.TERMO,
        )
    }
}
