package com.example.netafim.atendimento.ui.lista

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.netafim.atendimento.dominio.FiltroDaRegiao
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

// Estado da lista da região do distribuidor: chip, busca e a lista pronta do repositório. Não há filtro por
// técnico aqui: o que o distribuidor enxerga (a região dele) é decidido pelo repositório a partir do usuário
// logado, e esta tela só escolhe o recorte por status.
class AtendimentosDaRegiaoViewModel(
    repositorio: AtendimentoRepository,
    sessao: SessaoDoUsuario,
) : ViewModel() {

    private val _filtro = MutableStateFlow(FiltroDaRegiao.TODOS)
    val filtro: StateFlow<FiltroDaRegiao> = _filtro.asStateFlow()

    private val _busca = MutableStateFlow("")
    val busca: StateFlow<String> = _busca.asStateFlow()

    val itens: StateFlow<List<ItemDeAtendimento>> = observarListaDoUsuario(
        sessao = sessao,
        repositorio = repositorio,
        pedidos = combine(_filtro, _busca) { filtro, busca -> filtro.comoFiltroDeAtendimentos(busca) },
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(ESPERA_SEM_TELA_MS), emptyList())

    fun trocarFiltro(novo: FiltroDaRegiao) {
        _filtro.value = novo
    }

    fun buscar(texto: String) {
        _busca.value = texto
    }

    companion object {
        fun fabrica(repositorio: AtendimentoRepository, sessao: SessaoDoUsuario): ViewModelProvider.Factory =
            viewModelFactory { initializer { AtendimentosDaRegiaoViewModel(repositorio, sessao) } }
    }
}
