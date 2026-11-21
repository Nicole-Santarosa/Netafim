package com.netafim.posvenda.acesso.ui.perfil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netafim.posvenda.acesso.dominio.ErroAcesso
import com.netafim.posvenda.acesso.dominio.RepositorioAcesso
import com.netafim.posvenda.acesso.dominio.Resultado
import com.netafim.posvenda.acesso.dominio.Validacao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CampoTrocaSenha { ATUAL, NOVA, CONFIRMACAO }

data class TrocarSenhaEstado(
    val senhaAtual: String = "",
    val novaSenha: String = "",
    val confirmacao: String = "",
    val senhaVisivel: Boolean = false,
    val erros: Map<CampoTrocaSenha, String> = emptyMap(),
    val erroGeral: String? = null,
    val carregando: Boolean = false,
    /** A senha foi trocada e a sessão local foi encerrada: a tela leva ao login. */
    val concluida: Boolean = false,
)

/**
 * Troca da própria senha (`POST me/senha`). Valida no aparelho (mínimo de 8 caracteres, não só
 * números, confirmação igual, nova diferente da atual) e deixa a API dar a palavra final. Com a
 * senha trocada, a API encerra as outras sessões; por isso o app também sai e pede o login com a
 * nova senha.
 */
class TrocarSenhaViewModel(private val repositorio: RepositorioAcesso) : ViewModel() {

    private val _estado = MutableStateFlow(TrocarSenhaEstado())
    val estado: StateFlow<TrocarSenhaEstado> = _estado.asStateFlow()

    fun aoMudar(campo: CampoTrocaSenha, valor: String) = _estado.update {
        val novo = when (campo) {
            CampoTrocaSenha.ATUAL -> it.copy(senhaAtual = valor)
            CampoTrocaSenha.NOVA -> it.copy(novaSenha = valor)
            CampoTrocaSenha.CONFIRMACAO -> it.copy(confirmacao = valor)
        }
        novo.copy(erros = it.erros - campo, erroGeral = null)
    }

    fun alternarVisibilidade() = _estado.update { it.copy(senhaVisivel = !it.senhaVisivel) }

    fun salvar() {
        val atual = _estado.value
        if (atual.carregando || atual.concluida) return
        val erros = buildMap {
            Validacao.senhaLogin(atual.senhaAtual)?.let { put(CampoTrocaSenha.ATUAL, it) }
            Validacao.novaSenha(atual.novaSenha)?.let { put(CampoTrocaSenha.NOVA, it) }
            if (CampoTrocaSenha.NOVA !in this && atual.novaSenha == atual.senhaAtual) {
                put(CampoTrocaSenha.NOVA, "A nova senha deve ser diferente da atual.")
            }
            Validacao.confirmacao(atual.novaSenha, atual.confirmacao)?.let { put(CampoTrocaSenha.CONFIRMACAO, it) }
        }
        if (erros.isNotEmpty()) {
            _estado.update { it.copy(erros = erros, erroGeral = null) }
            return
        }
        _estado.update { it.copy(carregando = true, erroGeral = null, erros = emptyMap()) }
        viewModelScope.launch {
            when (val r = repositorio.alterarSenha(atual.senhaAtual, atual.novaSenha)) {
                is Resultado.Sucesso -> {
                    repositorio.sair()
                    _estado.update { it.copy(carregando = false, concluida = true) }
                }
                is Resultado.Falha -> _estado.update { aplicarErro(it, r.erro) }
            }
        }
    }

    private fun aplicarErro(e: TrocarSenhaEstado, erro: ErroAcesso): TrocarSenhaEstado {
        val base = e.copy(carregando = false)
        return when {
            erro is ErroAcesso.Api && erro.codigo == "senha_atual_incorreta" ->
                base.copy(erros = mapOf(CampoTrocaSenha.ATUAL to erro.mensagem))
            erro is ErroAcesso.Api && erro.codigo == "senha_repetida" ->
                base.copy(erros = mapOf(CampoTrocaSenha.NOVA to erro.mensagem))
            erro is ErroAcesso.Campos -> {
                val porCampo = erro.campos.mapNotNull { (campo, msg) -> CAMPOS_API[campo]?.let { it to msg } }.toMap()
                base.copy(erros = porCampo, erroGeral = if (porCampo.isEmpty()) erro.mensagem else null)
            }
            else -> base.copy(erroGeral = erro.mensagem)
        }
    }

    private companion object {
        val CAMPOS_API = mapOf(
            "senha_atual" to CampoTrocaSenha.ATUAL,
            "nova_senha" to CampoTrocaSenha.NOVA,
        )
    }
}
