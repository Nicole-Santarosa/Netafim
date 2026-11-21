package com.example.netafim.acesso.ui.privacidade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.netafim.acesso.dominio.PedidoAdmin
import com.example.netafim.acesso.dominio.RepositorioFilaPrivacidade
import com.example.netafim.acesso.dominio.Resultado
import com.example.netafim.acesso.dominio.StatusPedido
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Recorte da fila. A API ordena por prazo; o filtro é feito no aparelho com a lista inteira. */
enum class FiltroFila { ABERTOS, ATENDIDOS, NEGADOS, TODOS }

data class FilaPrivacidadeEstado(
    val pedidos: List<PedidoAdmin> = emptyList(),
    val filtro: FiltroFila = FiltroFila.ABERTOS,
    val carregando: Boolean = true,
    val erro: String? = null,
) {
    /** Pedidos do filtro atual, na ordem da API (do prazo mais próximo ao mais distante). */
    val visiveis: List<PedidoAdmin>
        get() = when (filtro) {
            FiltroFila.ABERTOS -> pedidos.filter { it.aberto }
            FiltroFila.ATENDIDOS -> pedidos.filter { it.status == StatusPedido.ATENDIDA }
            FiltroFila.NEGADOS -> pedidos.filter { it.status == StatusPedido.NEGADA }
            FiltroFila.TODOS -> pedidos
        }

    /** Quantos pedidos abertos já passaram do prazo, para destacar no alto da tela. */
    val atrasados: Int get() = pedidos.count { it.atrasada }
}

/** Fila de pedidos de privacidade dos titulares, para o Netafim Admin. */
class FilaPrivacidadeViewModel(private val repositorio: RepositorioFilaPrivacidade) : ViewModel() {

    private val _estado = MutableStateFlow(FilaPrivacidadeEstado())
    val estado: StateFlow<FilaPrivacidadeEstado> = _estado.asStateFlow()

    private var jaCarregou = false

    init {
        carregar()
    }

    fun carregar() {
        _estado.update { it.copy(carregando = true, erro = null) }
        viewModelScope.launch {
            when (val r = repositorio.fila()) {
                is Resultado.Sucesso -> {
                    jaCarregou = true
                    _estado.update { it.copy(pedidos = r.valor, carregando = false) }
                }
                is Resultado.Falha -> _estado.update { it.copy(carregando = false, erro = r.erro.mensagem) }
            }
        }
    }

    /** Ao voltar do detalhe (onde o pedido pode ter mudado), atualiza a lista sem piscar o carregamento. */
    fun aoRetomar() {
        if (!jaCarregou) return
        viewModelScope.launch {
            val r = repositorio.fila()
            if (r is Resultado.Sucesso) _estado.update { it.copy(pedidos = r.valor) }
        }
    }

    fun escolherFiltro(filtro: FiltroFila) = _estado.update { it.copy(filtro = filtro) }
}
