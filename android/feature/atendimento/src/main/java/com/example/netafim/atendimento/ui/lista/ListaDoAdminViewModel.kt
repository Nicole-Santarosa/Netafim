package com.example.netafim.atendimento.ui.lista

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.netafim.atendimento.dominio.FiltroDeOrigem
import com.example.netafim.atendimento.dominio.comoFiltroDeAtendimentos
import com.example.netafim.core.modelo.AtendimentoRepository
import com.example.netafim.core.modelo.ItemDeAtendimento
import com.example.netafim.core.modelo.sessao.SessaoDoUsuario
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

// Estado da lista do Admin: o chip de origem e a lista pronta do repositório. Esta tela não tem busca, então
// o ViewModel também não guarda texto. Quem pode ver tudo (Netafim Admin) é regra do repositório e do banco.
class ListaDoAdminViewModel(
    repositorio: AtendimentoRepository,
    sessao: SessaoDoUsuario,
) : ViewModel() {

    private val _filtro = MutableStateFlow(FiltroDeOrigem.TODAS)
    val filtro: StateFlow<FiltroDeOrigem> = _filtro.asStateFlow()

    val itens: StateFlow<List<ItemDeAtendimento>> = observarListaDoUsuario(
        sessao = sessao,
        repositorio = repositorio,
        pedidos = _filtro.map { it.comoFiltroDeAtendimentos() },
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(ESPERA_SEM_TELA_MS), emptyList())

    fun trocarFiltro(novo: FiltroDeOrigem) {
        _filtro.value = novo
    }

    companion object {
        fun fabrica(repositorio: AtendimentoRepository, sessao: SessaoDoUsuario): ViewModelProvider.Factory =
            viewModelFactory { initializer { ListaDoAdminViewModel(repositorio, sessao) } }
    }
}
