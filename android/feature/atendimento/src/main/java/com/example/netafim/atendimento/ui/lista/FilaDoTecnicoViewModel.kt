package com.example.netafim.atendimento.ui.lista

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.netafim.atendimento.dominio.FiltroDaFila
import com.example.netafim.atendimento.dominio.comoFiltroDeAtendimentos
import com.example.netafim.core.modelo.AtendimentoRepository
import com.example.netafim.core.modelo.ItemDeAtendimento
import com.example.netafim.core.modelo.sessao.SessaoDoUsuario
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

// Estado da fila do técnico: o chip escolhido, o texto da busca e a lista que o repositório entrega para esse
// pedido. A lista só traz o que está destinado ao usuário logado (somenteMeusComoTecnico), já filtrada,
// buscada e na ordem da fila, então a tela só desenha.
class FilaDoTecnicoViewModel(
    repositorio: AtendimentoRepository,
    sessao: SessaoDoUsuario,
) : ViewModel() {

    private val _filtro = MutableStateFlow(FiltroDaFila.TODOS)
    val filtro: StateFlow<FiltroDaFila> = _filtro.asStateFlow()

    private val _busca = MutableStateFlow("")
    val busca: StateFlow<String> = _busca.asStateFlow()

    // Chip e busca juntos viram um pedido novo a cada mudança de qualquer um dos dois.
    val itens: StateFlow<List<ItemDeAtendimento>> = observarListaDoUsuario(
        sessao = sessao,
        repositorio = repositorio,
        pedidos = combine(_filtro, _busca) { filtro, busca -> filtro.comoFiltroDeAtendimentos(busca) },
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(ESPERA_SEM_TELA_MS), emptyList())

    fun trocarFiltro(novo: FiltroDaFila) {
        _filtro.value = novo
    }

    fun buscar(texto: String) {
        _busca.value = texto
    }

    companion object {
        // Fábrica para quem cria o ViewModel (a navegação), já que ele recebe dependências no construtor.
        fun fabrica(repositorio: AtendimentoRepository, sessao: SessaoDoUsuario): ViewModelProvider.Factory =
            viewModelFactory { initializer { FilaDoTecnicoViewModel(repositorio, sessao) } }
    }
}
