package com.netafim.posvenda.acesso.ui.perfil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netafim.posvenda.acesso.dominio.ConfiguracaoMfa
import com.netafim.posvenda.acesso.dominio.ErroAcesso
import com.netafim.posvenda.acesso.dominio.EstadoMfa
import com.netafim.posvenda.acesso.dominio.RepositorioAcesso
import com.netafim.posvenda.acesso.dominio.Resultado
import com.netafim.posvenda.acesso.dominio.Validacao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class EtapaMfa {
    /** Só mostra o estado (ativo ou não). */
    Nenhuma,

    /** Segredo gerado: a pessoa cadastra no aplicativo e informa o primeiro código. */
    Ativando,

    /** MFA ligado: mostra os códigos de recuperação, que a API só devolve esta vez. */
    CodigosDeRecuperacao,

    /** Pede senha e código para desligar. */
    Desativando,
}

data class MfaEstadoTela(
    val estado: EstadoMfa? = null,
    val etapa: EtapaMfa = EtapaMfa.Nenhuma,
    val configuracao: ConfiguracaoMfa? = null,
    val codigosRecuperacao: List<String> = emptyList(),
    val codigo: String = "",
    val senha: String = "",
    val senhaVisivel: Boolean = false,
    val erro: String? = null,
    val erroCodigo: String? = null,
    val carregando: Boolean = false,
)

class MfaViewModel(private val repositorio: RepositorioAcesso) : ViewModel() {

    private val _estado = MutableStateFlow(MfaEstadoTela())
    val estado: StateFlow<MfaEstadoTela> = _estado.asStateFlow()

    init {
        carregar()
    }

    fun carregar() {
        viewModelScope.launch {
            // Sem rede ou sem MFA disponível no servidor, o cartão simplesmente não aparece.
            val resultado = repositorio.estadoMfa()
            if (resultado is Resultado.Sucesso) _estado.update { it.copy(estado = resultado.valor) }
        }
    }

    fun comecarAtivacao() {
        if (_estado.value.carregando) return
        _estado.update { it.copy(carregando = true, erro = null) }
        viewModelScope.launch {
            when (val r = repositorio.iniciarMfa()) {
                is Resultado.Sucesso -> _estado.update {
                    it.copy(
                        carregando = false,
                        etapa = EtapaMfa.Ativando,
                        configuracao = r.valor,
                        codigo = "",
                        erroCodigo = null,
                    )
                }
                is Resultado.Falha -> _estado.update { it.copy(carregando = false, erro = r.erro.mensagem) }
            }
        }
    }

    fun aoMudarCodigo(valor: String) =
        _estado.update { it.copy(codigo = valor.take(CODIGO_MAXIMO), erroCodigo = null, erro = null) }

    fun aoMudarSenha(valor: String) = _estado.update { it.copy(senha = valor, erro = null) }

    fun alternarVisibilidadeSenha() = _estado.update { it.copy(senhaVisivel = !it.senhaVisivel) }

    fun confirmarAtivacao() {
        val atual = _estado.value
        if (atual.carregando) return
        val erroCodigo = Validacao.codigoMfa(atual.codigo)
        if (erroCodigo != null) {
            _estado.update { it.copy(erroCodigo = erroCodigo) }
            return
        }
        _estado.update { it.copy(carregando = true, erro = null) }
        viewModelScope.launch {
            when (val r = repositorio.confirmarMfa(atual.codigo)) {
                is Resultado.Sucesso -> _estado.update {
                    it.copy(
                        carregando = false,
                        etapa = EtapaMfa.CodigosDeRecuperacao,
                        codigosRecuperacao = r.valor,
                        estado = EstadoMfa(ativo = true, codigosRecuperacaoRestantes = r.valor.size),
                        // O segredo não precisa mais ficar na memória da tela.
                        configuracao = null,
                        codigo = "",
                    )
                }
                is Resultado.Falha -> _estado.update {
                    it.copy(carregando = false, codigo = "", erro = r.erro.mensagem)
                }
            }
        }
    }

    fun comecarDesativacao() =
        _estado.update {
            it.copy(etapa = EtapaMfa.Desativando, codigo = "", senha = "", erro = null, erroCodigo = null)
        }

    fun confirmarDesativacao() {
        val atual = _estado.value
        if (atual.carregando) return
        val erroCodigo = Validacao.codigoMfa(atual.codigo)
        if (atual.senha.isEmpty() || erroCodigo != null) {
            _estado.update {
                it.copy(
                    erroCodigo = erroCodigo,
                    erro = if (atual.senha.isEmpty()) "Informe a senha." else null,
                )
            }
            return
        }
        _estado.update { it.copy(carregando = true, erro = null) }
        viewModelScope.launch {
            when (val r = repositorio.desativarMfa(atual.senha, atual.codigo)) {
                is Resultado.Sucesso -> _estado.update {
                    it.copy(
                        carregando = false,
                        etapa = EtapaMfa.Nenhuma,
                        estado = EstadoMfa(ativo = false, codigosRecuperacaoRestantes = 0),
                        codigo = "",
                        senha = "",
                    )
                }
                is Resultado.Falha -> _estado.update {
                    it.copy(carregando = false, codigo = "", erro = mensagem(r.erro))
                }
            }
        }
    }

    /** Fecha qualquer etapa aberta. Os códigos de recuperação só saem da memória aqui. */
    fun fechar() = _estado.update {
        it.copy(
            etapa = EtapaMfa.Nenhuma,
            configuracao = null,
            codigosRecuperacao = emptyList(),
            codigo = "",
            senha = "",
            erro = null,
            erroCodigo = null,
        )
    }

    private fun mensagem(erro: ErroAcesso): String = when (erro) {
        is ErroAcesso.Campos -> erro.campos["senha"] ?: erro.campos["codigo"] ?: erro.mensagem
        else -> erro.mensagem
    }

    private companion object {
        const val CODIGO_MAXIMO = 16
    }
}
