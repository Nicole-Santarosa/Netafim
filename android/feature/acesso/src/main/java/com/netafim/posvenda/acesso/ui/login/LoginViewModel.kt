package com.netafim.posvenda.acesso.ui.login

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

data class LoginEstado(
    val email: String = "",
    val senha: String = "",
    val senhaVisivel: Boolean = false,
    val erroEmail: String? = null,
    val erroSenha: String? = null,
    val erroGeral: String? = null,
    val carregando: Boolean = false,
    val entrou: Boolean = false,
)

class LoginViewModel(private val repositorio: RepositorioAcesso) : ViewModel() {

    private val _estado = MutableStateFlow(LoginEstado())
    val estado: StateFlow<LoginEstado> = _estado.asStateFlow()

    fun aoMudarEmail(valor: String) =
        _estado.update { it.copy(email = valor, erroEmail = null, erroGeral = null) }

    fun aoMudarSenha(valor: String) =
        _estado.update { it.copy(senha = valor, erroSenha = null, erroGeral = null) }

    fun alternarVisibilidadeSenha() = _estado.update { it.copy(senhaVisivel = !it.senhaVisivel) }

    fun entrar() {
        val atual = _estado.value
        if (atual.carregando) return
        val erroEmail = Validacao.email(atual.email)
        val erroSenha = Validacao.senhaLogin(atual.senha)
        if (erroEmail != null || erroSenha != null) {
            _estado.update { it.copy(erroEmail = erroEmail, erroSenha = erroSenha) }
            return
        }
        _estado.update { it.copy(carregando = true, erroGeral = null) }
        viewModelScope.launch {
            when (val resultado = repositorio.entrar(atual.email, atual.senha)) {
                is Resultado.Sucesso -> _estado.update {
                    it.copy(carregando = false, entrou = true, senha = "")
                }
                is Resultado.Falha -> _estado.update {
                    it.copy(carregando = false, erroGeral = mensagem(resultado.erro), senha = "")
                }
            }
        }
    }

    private fun mensagem(erro: ErroAcesso): String = when (erro) {
        is ErroAcesso.Campos -> erro.campos["email"] ?: erro.campos["senha"] ?: erro.mensagem
        else -> erro.mensagem
    }
}
