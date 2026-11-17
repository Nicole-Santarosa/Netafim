package com.example.netafim.atendimento.ui.edicao

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.netafim.atendimento.dominio.ErroAoSalvar
import com.example.netafim.atendimento.dominio.alteracaoDaEscolha
import com.example.netafim.atendimento.dominio.erroDoResultado
import com.example.netafim.atendimento.dominio.podeSalvar
import com.example.netafim.atendimento.ui.detalhe.EstadoDoDetalhe
import com.example.netafim.atendimento.ui.detalhe.observarEstadoDoDetalhe
import com.example.netafim.atendimento.ui.lista.ESPERA_SEM_TELA_MS
import com.example.netafim.core.modelo.Atendimento
import com.example.netafim.core.modelo.AtendimentoRepository
import com.example.netafim.core.modelo.ResultadoDaAcao
import com.example.netafim.core.modelo.SituacaoDoProjeto
import com.example.netafim.core.modelo.sessao.SessaoDoUsuario
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Estado da edição do atendimento pelo Admin: só a situação do projeto pode mudar.
//
// Decisão sobre o "original" (a situação gravada contra a qual a escolha é comparada): é a situação do
// PRIMEIRO atendimento carregado e fica fixa. Se o repositório trouxer uma versão nova do atendimento enquanto
// o Admin edita (uma sincronização que mudou a situação, por exemplo), nem o original nem a escolha dele
// mudam: o botão Salvar não pode acender ou apagar sozinho e a escolha não pode ser trocada sob os dedos.
// Quem quer ver o dado novo toca em Atualizar (recarregar), que reinicia o original e a escolha.
// A tela precisa coletar o estado para o original ser fixado.
class EdicaoDoAtendimentoViewModel(
    private val id: String,
    private val repositorio: AtendimentoRepository,
    private val sessao: SessaoDoUsuario,
) : ViewModel() {

    // Última versão do atendimento que o repositório entregou; serve ao recarregar.
    private var ultimoAtendimento: Atendimento? = null
    private var originalFixado = false

    private val _original = MutableStateFlow<SituacaoDoProjeto?>(null)
    private val _escolhida = MutableStateFlow<SituacaoDoProjeto?>(null)
    private val _salvando = MutableStateFlow(false)
    private val _erro = MutableStateFlow<ErroAoSalvar?>(null)
    private val _salvo = MutableStateFlow(false)

    val estado: StateFlow<EstadoDoDetalhe> = observarEstadoDoDetalhe(id, sessao, repositorio)
        .onEach { estado ->
            if (estado is EstadoDoDetalhe.Pronto) {
                ultimoAtendimento = estado.atendimento
                // Só o primeiro atendimento carregado define o original e a escolha inicial (a situação gravada).
                if (!originalFixado) {
                    originalFixado = true
                    _original.value = estado.atendimento.situacaoProjeto
                    _escolhida.value = estado.atendimento.situacaoProjeto
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(ESPERA_SEM_TELA_MS), EstadoDoDetalhe.Carregando)

    // Situação que o seletor mostra marcada.
    val escolhida: StateFlow<SituacaoDoProjeto?> = _escolhida.asStateFlow()
    val salvando: StateFlow<Boolean> = _salvando.asStateFlow()
    val erro: StateFlow<ErroAoSalvar?> = _erro.asStateFlow()

    // Vira verdadeiro quando o salvamento deu Sucesso (salvo no aparelho): a tela volta ou navega.
    val salvo: StateFlow<Boolean> = _salvo.asStateFlow()

    // Estado do botão Salvar alterações, pela regra do domínio.
    val podeSalvar: StateFlow<Boolean> = combine(_original, _escolhida, _salvando) { original, escolhida, salvando ->
        podeSalvar(original, escolhida, salvando)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(ESPERA_SEM_TELA_MS), false)

    // O Admin tocou em uma das opções. Mudar a escolha tira o erro da tela (ele já foi visto e a situação
    // que o causou mudou).
    fun escolher(situacao: SituacaoDoProjeto) {
        _escolhida.value = situacao
        _erro.value = null
    }

    fun salvar() {
        val escolhida = _escolhida.value
        // Sem alteração válida (nada escolhido, escolha igual à gravada ou valor desconhecido) o repositório
        // nem é chamado.
        if (!podeSalvar(_original.value, escolhida, salvando = false)) return
        val alteracao = alteracaoDaEscolha(escolhida) ?: return
        // Sem usuário logado não há quem altere.
        if (sessao.usuario.value == null) return
        // Toque duplo: com um salvamento em andamento o segundo toque é ignorado.
        if (!_salvando.compareAndSet(false, true)) return
        _erro.value = null
        viewModelScope.launch {
            val resultado = repositorio.alterarAtendimento(id, alteracao)
            _erro.value = erroDoResultado(resultado)
            if (resultado == ResultadoDaAcao.Sucesso) {
                // O que acabou de ser salvo passa a ser a situação gravada: se a tela continuar aberta, o
                // botão não deixa salvar o mesmo valor de novo.
                _original.value = escolhida
                _salvo.value = true
            }
            _salvando.value = false
        }
    }

    // Ação Atualizar do aviso de conflito: pega a versão mais nova que o repositório já entregou, passa a
    // usá-la como original e como escolha, e limpa o erro. Depois disso o Admin refaz a escolha.
    fun recarregar() {
        val atual = ultimoAtendimento ?: return
        _original.value = atual.situacaoProjeto
        _escolhida.value = atual.situacaoProjeto
        _erro.value = null
    }

    // A tela chama quando o aviso de erro já foi lido e o Admin o fechou. A escolha continua como estava, para
    // ele poder tentar salvar de novo sem refazer nada.
    fun dispensarErro() {
        _erro.value = null
    }

    companion object {
        fun fabrica(
            id: String,
            repositorio: AtendimentoRepository,
            sessao: SessaoDoUsuario,
        ): ViewModelProvider.Factory =
            viewModelFactory { initializer { EdicaoDoAtendimentoViewModel(id, repositorio, sessao) } }
    }
}
