package com.netafim.posvenda.acesso.ui.privacidade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netafim.posvenda.acesso.dominio.PedidoTitular
import com.netafim.posvenda.acesso.dominio.RepositorioPrivacidade
import com.netafim.posvenda.acesso.dominio.Resultado
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PrivacidadeEstado(
    val pedidos: List<PedidoTitular> = emptyList(),
    val carregando: Boolean = true,
    /** Falha ao listar os pedidos (a tela oferece tentar de novo). */
    val erro: String? = null,
    /** A cópia dos dados está sendo buscada. */
    val baixando: Boolean = false,
    /** Cópia pronta para a pessoa guardar ou enviar; a tela a entrega e chama [PrivacidadeViewModel.copiaEntregue]. */
    val copia: String? = null,
    val erroCopia: String? = null,
)

/**
 * Privacidade (LGPD): a lista dos pedidos do titular e o botão de baixar a cópia dos próprios dados. A cópia
 * fica só na memória até a tela entregá-la à pessoa; o app não a grava no aparelho.
 */
class PrivacidadeViewModel(private val repositorio: RepositorioPrivacidade) : ViewModel() {

    private val _estado = MutableStateFlow(PrivacidadeEstado())
    val estado: StateFlow<PrivacidadeEstado> = _estado.asStateFlow()

    private var jaCarregou = false

    init {
        carregar()
    }

    fun carregar() {
        _estado.update { it.copy(carregando = true, erro = null) }
        viewModelScope.launch {
            when (val r = repositorio.meusPedidos()) {
                is Resultado.Sucesso -> {
                    jaCarregou = true
                    _estado.update { it.copy(pedidos = r.valor, carregando = false) }
                }
                is Resultado.Falha -> _estado.update { it.copy(carregando = false, erro = r.erro.mensagem) }
            }
        }
    }

    /** Ao voltar do novo pedido, atualiza a lista sem piscar o carregamento. */
    fun aoRetomar() {
        if (!jaCarregou) return
        viewModelScope.launch {
            val r = repositorio.meusPedidos()
            if (r is Resultado.Sucesso) _estado.update { it.copy(pedidos = r.valor) }
        }
    }

    fun baixarDados() {
        if (_estado.value.baixando) return
        _estado.update { it.copy(baixando = true, erroCopia = null, copia = null) }
        viewModelScope.launch {
            when (val r = repositorio.copiaDosDados()) {
                is Resultado.Sucesso -> _estado.update { it.copy(baixando = false, copia = r.valor) }
                is Resultado.Falha -> _estado.update { it.copy(baixando = false, erroCopia = r.erro.mensagem) }
            }
        }
    }

    /** A tela já entregou a cópia: ela sai da memória. */
    fun copiaEntregue() = _estado.update { it.copy(copia = null) }
}
