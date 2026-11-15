package com.netafim.posvenda.acesso.ui.cadastro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netafim.posvenda.acesso.dominio.DadosCadastro
import com.netafim.posvenda.acesso.dominio.ErroAcesso
import com.netafim.posvenda.acesso.dominio.RepositorioAcesso
import com.netafim.posvenda.acesso.dominio.Resultado
import com.netafim.posvenda.acesso.dominio.Validacao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CampoCadastro { NOME, EMAIL, TELEFONE, SENHA, CONFIRMACAO, TERMO }

data class CadastroEstado(
    val nome: String = "",
    val email: String = "",
    val telefone: String = "",
    val senha: String = "",
    val confirmacao: String = "",
    val aceiteTermo: Boolean = false,
    val versaoTermo: String? = null,
    val erros: Map<CampoCadastro, String> = emptyMap(),
    val erroGeral: String? = null,
    val carregando: Boolean = false,
    /** Mensagem de sucesso da API; quando preenchida, a tela mostra a confirmação. */
    val enviado: String? = null,
)

/** Solicitação de cadastro: o acesso só é liberado depois da validação da Netafim. */
class CadastroViewModel(private val repositorio: RepositorioAcesso) : ViewModel() {

    private val _estado = MutableStateFlow(CadastroEstado())
    val estado: StateFlow<CadastroEstado> = _estado.asStateFlow()

    init {
        carregarTermo()
    }

    fun carregarTermo() {
        viewModelScope.launch {
            when (val r = repositorio.versaoTermoVigente()) {
                is Resultado.Sucesso -> _estado.update { it.copy(versaoTermo = r.valor) }
                is Resultado.Falha -> _estado.update { it.copy(erroGeral = r.erro.mensagem) }
            }
        }
    }

    fun aoMudar(campo: CampoCadastro, valor: String) = _estado.update {
        val novo = when (campo) {
            CampoCadastro.NOME -> it.copy(nome = valor)
            CampoCadastro.EMAIL -> it.copy(email = valor)
            CampoCadastro.TELEFONE -> it.copy(telefone = valor)
            CampoCadastro.SENHA -> it.copy(senha = valor)
            CampoCadastro.CONFIRMACAO -> it.copy(confirmacao = valor)
            CampoCadastro.TERMO -> it
        }
        novo.copy(erros = it.erros - campo, erroGeral = null)
    }

    fun aoMudarAceite(aceito: Boolean) = _estado.update {
        it.copy(aceiteTermo = aceito, erros = it.erros - CampoCadastro.TERMO)
    }

    fun enviar() {
        val atual = _estado.value
        if (atual.carregando) return
        val erros = validar(atual)
        if (erros.isNotEmpty()) {
            _estado.update { it.copy(erros = erros) }
            return
        }
        val versao = atual.versaoTermo ?: run {
            carregarTermo()
            return
        }
        _estado.update { it.copy(carregando = true, erroGeral = null) }
        viewModelScope.launch {
            val dados = DadosCadastro(
                nome = atual.nome,
                email = atual.email,
                telefone = Validacao.somenteDigitos(atual.telefone).ifEmpty { null },
                senha = atual.senha,
                versaoTermo = versao,
            )
            when (val r = repositorio.solicitarCadastro(dados)) {
                is Resultado.Sucesso -> _estado.update {
                    it.copy(carregando = false, enviado = r.valor, senha = "", confirmacao = "")
                }
                is Resultado.Falha -> {
                    _estado.update { aplicarErro(it, r.erro) }
                    if (r.erro is ErroAcesso.Api && r.erro.codigo == TERMO_DESATUALIZADO) {
                        carregarTermo()
                    }
                }
            }
        }
    }

    private fun validar(e: CadastroEstado): Map<CampoCadastro, String> = buildMap {
        Validacao.nome(e.nome)?.let { put(CampoCadastro.NOME, it) }
        Validacao.email(e.email)?.let { put(CampoCadastro.EMAIL, it) }
        Validacao.telefone(e.telefone)?.let { put(CampoCadastro.TELEFONE, it) }
        Validacao.novaSenha(e.senha)?.let { put(CampoCadastro.SENHA, it) }
        Validacao.confirmacao(e.senha, e.confirmacao)?.let { put(CampoCadastro.CONFIRMACAO, it) }
        if (!e.aceiteTermo) put(CampoCadastro.TERMO, "Aceite o aviso de privacidade para continuar.")
    }

    private fun aplicarErro(e: CadastroEstado, erro: ErroAcesso): CadastroEstado {
        val base = e.copy(carregando = false)
        if (erro is ErroAcesso.Campos) {
            val porCampo = erro.campos.mapNotNull { (campo, msg) ->
                CAMPOS_API[campo]?.let { it to msg }
            }.toMap()
            return base.copy(
                erros = porCampo,
                erroGeral = if (porCampo.isEmpty()) erro.mensagem else null,
            )
        }
        if (erro is ErroAcesso.Api && erro.codigo == TERMO_DESATUALIZADO) {
            return base.copy(aceiteTermo = false, erroGeral = erro.mensagem)
        }
        return base.copy(erroGeral = erro.mensagem)
    }

    private companion object {
        const val TERMO_DESATUALIZADO = "termo_desatualizado"
        val CAMPOS_API = mapOf(
            "nome" to CampoCadastro.NOME,
            "email" to CampoCadastro.EMAIL,
            "telefone" to CampoCadastro.TELEFONE,
            "senha" to CampoCadastro.SENHA,
            "aceite_termo" to CampoCadastro.TERMO,
        )
    }
}
