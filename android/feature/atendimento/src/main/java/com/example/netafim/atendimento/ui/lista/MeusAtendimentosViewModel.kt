package com.example.netafim.atendimento.ui.lista

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.netafim.core.modelo.AtendimentoRepository
import com.example.netafim.core.modelo.FiltroDeAtendimentos
import com.example.netafim.core.modelo.sessao.SessaoDoUsuario
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

// Estado de Meus atendimentos (cliente). A tela não tem chip nem busca, então o ViewModel só expõe o estado da lista:
// todos os atendimentos que o repositório entrega ao usuário logado (o cliente só enxerga os da empresa dele),
// do mais recente para o mais antigo, que é a ordem padrão do pedido sem filtro.
class MeusAtendimentosViewModel(
    repositorio: AtendimentoRepository,
    sessao: SessaoDoUsuario,
) : ViewModel() {

    // Começa em Carregando até o repositório responder pela primeira vez (ver observarListaDoUsuario).
    val estado: StateFlow<EstadoDaLista> = observarListaDoUsuario(
        sessao = sessao,
        repositorio = repositorio,
        pedidos = flowOf(FiltroDeAtendimentos()),
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(ESPERA_SEM_TELA_MS), EstadoDaLista.Carregando)

    companion object {
        fun fabrica(repositorio: AtendimentoRepository, sessao: SessaoDoUsuario): ViewModelProvider.Factory =
            viewModelFactory { initializer { MeusAtendimentosViewModel(repositorio, sessao) } }
    }
}
