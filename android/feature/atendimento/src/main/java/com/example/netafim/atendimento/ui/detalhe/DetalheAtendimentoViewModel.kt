package com.example.netafim.atendimento.ui.detalhe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.netafim.atendimento.dominio.ErroAoSalvar
import com.example.netafim.atendimento.dominio.erroDoResultado
import com.example.netafim.atendimento.ui.lista.ESPERA_SEM_TELA_MS
import com.example.netafim.core.modelo.AtendimentoRepository
import com.example.netafim.core.modelo.sessao.SessaoDoUsuario
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Estado da tela de detalhe de um atendimento (cliente, técnico e distribuidor usam o mesmo). A única escrita
// que o detalhe dispara é o botão Iniciar atendimento do técnico.
class DetalheAtendimentoViewModel(
    private val id: String,
    private val repositorio: AtendimentoRepository,
    private val sessao: SessaoDoUsuario,
) : ViewModel() {

    val estado: StateFlow<EstadoDoDetalhe> = observarEstadoDoDetalhe(id, sessao, repositorio)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(ESPERA_SEM_TELA_MS), EstadoDoDetalhe.Carregando)

    private val _iniciando = MutableStateFlow(false)

    // Verdadeiro enquanto o pedido de iniciar está rodando; a tela mostra o progresso no botão.
    val iniciando: StateFlow<Boolean> = _iniciando.asStateFlow()

    private val _erro = MutableStateFlow<ErroAoSalvar?>(null)

    // Erro do último pedido de iniciar, ou nulo. Fica até a tela dispensar ou até um novo pedido.
    val erro: StateFlow<ErroAoSalvar?> = _erro.asStateFlow()

    // Pede ao repositório para iniciar o atendimento (agendado para em atendimento). O repositório grava
    // primeiro no aparelho, então um Sucesso aqui quer dizer "salvo no aparelho"; a tela vê o novo status
    // pelo próprio estado, que emite de novo quando o registro muda.
    fun iniciar() {
        // Sem usuário logado não há quem inicie.
        if (sessao.usuario.value == null) return
        // Toque duplo: se já está iniciando, o segundo toque é ignorado. compareAndSet liga a trava e
        // confere a anterior num passo só.
        if (!_iniciando.compareAndSet(false, true)) return
        _erro.value = null
        viewModelScope.launch {
            val resultado = repositorio.iniciarAtendimento(id)
            _erro.value = erroDoResultado(resultado)
            _iniciando.value = false
        }
    }

    // A tela chama quando o aviso de erro já foi lido.
    fun dispensarErro() {
        _erro.value = null
    }

    companion object {
        // Fábrica para a navegação, que conhece o id do atendimento e as dependências.
        fun fabrica(
            id: String,
            repositorio: AtendimentoRepository,
            sessao: SessaoDoUsuario,
        ): ViewModelProvider.Factory =
            viewModelFactory { initializer { DetalheAtendimentoViewModel(id, repositorio, sessao) } }
    }
}
