package com.netafim.posvenda.acesso.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netafim.posvenda.acesso.dominio.ErroAcesso
import com.netafim.posvenda.acesso.dominio.RepositorioAcesso
import com.netafim.posvenda.acesso.dominio.Resultado
import com.netafim.posvenda.acesso.dominio.ResultadoEntrada
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
    /** Preenchido quando a senha estava certa e falta o código do MFA (segundo passo). */
    val desafio: String? = null,
    val codigo: String = "",
    val erroCodigo: String? = null,
) {
    val pedindoCodigo: Boolean get() = desafio != null
}

class LoginViewModel(private val repositorio: RepositorioAcesso) : ViewModel() {

    private val _estado = MutableStateFlow(LoginEstado())
    val estado: StateFlow<LoginEstado> = _estado.asStateFlow()

    fun aoMudarEmail(valor: String) =
        _estado.update { it.copy(email = valor, erroEmail = null, erroGeral = null) }

    fun aoMudarSenha(valor: String) =
        _estado.update { it.copy(senha = valor, erroSenha = null, erroGeral = null) }

    fun alternarVisibilidadeSenha() = _estado.update { it.copy(senhaVisivel = !it.senhaVisivel) }

    fun aoMudarCodigo(valor: String) =
        _estado.update { it.copy(codigo = valor.take(CODIGO_MAXIMO), erroCodigo = null, erroGeral = null) }

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
                is Resultado.Sucesso -> when (val entrada = resultado.valor) {
                    is ResultadoEntrada.Entrou -> _estado.update {
                        it.copy(carregando = false, entrou = true, senha = "")
                    }
                    // A senha já foi conferida: não fica na memória da tela durante o segundo passo.
                    is ResultadoEntrada.ExigeSegundoFator -> _estado.update {
                        it.copy(carregando = false, senha = "", desafio = entrada.desafio, codigo = "")
                    }
                }
                is Resultado.Falha -> _estado.update {
                    it.copy(carregando = false, erroGeral = mensagem(resultado.erro), senha = "")
                }
            }
        }
    }

    /** Envia o código do aplicativo autenticador (ou de recuperação) junto com o desafio. */
    fun confirmarCodigo() {
        val atual = _estado.value
        val desafio = atual.desafio ?: return
        if (atual.carregando) return
        val erroCodigo = Validacao.codigoMfa(atual.codigo)
        if (erroCodigo != null) {
            _estado.update { it.copy(erroCodigo = erroCodigo) }
            return
        }
        _estado.update { it.copy(carregando = true, erroGeral = null) }
        viewModelScope.launch {
            when (val resultado = repositorio.concluirLoginMfa(desafio, atual.codigo)) {
                is Resultado.Sucesso -> _estado.update {
                    it.copy(carregando = false, entrou = true, codigo = "", desafio = null)
                }
                is Resultado.Falha -> {
                    val erro = resultado.erro
                    val desafioExpirou = erro is ErroAcesso.Api && erro.codigo == "desafio_mfa_invalido"
                    _estado.update {
                        // Desafio vencido: não adianta tentar outro código, volta para a senha.
                        if (desafioExpirou) {
                            it.copy(carregando = false, desafio = null, codigo = "", erroGeral = erro.mensagem)
                        } else {
                            it.copy(carregando = false, codigo = "", erroGeral = mensagem(erro))
                        }
                    }
                }
            }
        }
    }

    fun voltarParaSenha() =
        _estado.update { it.copy(desafio = null, codigo = "", erroCodigo = null, erroGeral = null) }

    private fun mensagem(erro: ErroAcesso): String = when (erro) {
        is ErroAcesso.Campos ->
            erro.campos["email"] ?: erro.campos["senha"] ?: erro.campos["codigo"] ?: erro.mensagem
        else -> erro.mensagem
    }

    private companion object {
        const val CODIGO_MAXIMO = 16
    }
}
