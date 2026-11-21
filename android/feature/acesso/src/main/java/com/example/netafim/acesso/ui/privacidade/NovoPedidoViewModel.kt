package com.example.netafim.acesso.ui.privacidade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.netafim.acesso.dominio.ErroAcesso
import com.example.netafim.acesso.dominio.LIMITE_OBSERVACAO_PEDIDO
import com.example.netafim.acesso.dominio.RepositorioPrivacidade
import com.example.netafim.acesso.dominio.Resultado
import com.example.netafim.acesso.dominio.TipoPedido
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CampoNovoPedido { TIPO, OBSERVACAO }

data class NovoPedidoEstado(
    val tipo: TipoPedido? = null,
    val observacao: String = "",
    val erros: Map<CampoNovoPedido, String> = emptyMap(),
    val erroGeral: String? = null,
    /** Pedido que não tem volta (anonimização, eliminação): a tela mostra a confirmação reforçada. */
    val confirmando: Boolean = false,
    val enviando: Boolean = false,
    val concluido: Boolean = false,
)

/**
 * Fazer um pedido ao titular dos dados (`POST me/solicitacoes-lgpd`). Os tipos que apagam a conta
 * ([TipoPedido.irreversivel]) só seguem depois de a pessoa confirmar que sabe que não há volta.
 */
class NovoPedidoViewModel(private val repositorio: RepositorioPrivacidade) : ViewModel() {

    private val _estado = MutableStateFlow(NovoPedidoEstado())
    val estado: StateFlow<NovoPedidoEstado> = _estado.asStateFlow()

    fun escolherTipo(tipo: TipoPedido) = _estado.update {
        if (it.enviando) it else it.copy(tipo = tipo, erros = it.erros - CampoNovoPedido.TIPO, erroGeral = null)
    }

    fun mudarObservacao(valor: String) = _estado.update {
        if (it.enviando) it else it.copy(observacao = valor, erros = it.erros - CampoNovoPedido.OBSERVACAO, erroGeral = null)
    }

    fun enviar() {
        val atual = _estado.value
        if (atual.enviando || atual.concluido) return
        val erros = buildMap {
            if (atual.tipo == null) put(CampoNovoPedido.TIPO, "Escolha o que você quer pedir.")
            if (atual.observacao.trim().length > LIMITE_OBSERVACAO_PEDIDO) {
                put(CampoNovoPedido.OBSERVACAO, "Use no máximo $LIMITE_OBSERVACAO_PEDIDO caracteres.")
            }
        }
        if (erros.isNotEmpty()) {
            _estado.update { it.copy(erros = erros, erroGeral = null) }
            return
        }
        if (atual.tipo!!.irreversivel) {
            _estado.update { it.copy(confirmando = true, erroGeral = null) }
        } else {
            registrar()
        }
    }

    fun cancelarConfirmacao() = _estado.update { if (it.enviando) it else it.copy(confirmando = false) }

    fun confirmar() {
        val atual = _estado.value
        if (!atual.confirmando || atual.enviando || atual.tipo?.irreversivel != true) return
        registrar()
    }

    private fun registrar() {
        val atual = _estado.value
        val tipo = atual.tipo ?: return
        _estado.update { it.copy(enviando = true, erroGeral = null, erros = emptyMap()) }
        viewModelScope.launch {
            when (val r = repositorio.abrirPedido(tipo, atual.observacao)) {
                is Resultado.Sucesso -> _estado.update { it.copy(enviando = false, confirmando = false, concluido = true) }
                is Resultado.Falha -> _estado.update { aplicarErro(it, r.erro) }
            }
        }
    }

    private fun aplicarErro(e: NovoPedidoEstado, erro: ErroAcesso): NovoPedidoEstado {
        val base = e.copy(enviando = false, confirmando = false)
        return when {
            // Pedido igual já em andamento: o motivo está na escolha do tipo.
            erro is ErroAcesso.Api && erro.codigo == "solicitacao_em_aberto" ->
                base.copy(erros = mapOf(CampoNovoPedido.TIPO to erro.mensagem))
            erro is ErroAcesso.Campos -> {
                val porCampo = erro.campos.mapNotNull { (campo, msg) -> CAMPOS_API[campo]?.let { it to msg } }.toMap()
                base.copy(erros = porCampo, erroGeral = if (porCampo.isEmpty()) erro.mensagem else null)
            }
            else -> base.copy(erroGeral = erro.mensagem)
        }
    }

    private companion object {
        val CAMPOS_API = mapOf("tipo" to CampoNovoPedido.TIPO, "observacao" to CampoNovoPedido.OBSERVACAO)
    }
}
