package com.example.netafim.atendimento.ui.abrir

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.netafim.atendimento.dominio.ErroAoSalvar
import com.example.netafim.atendimento.dominio.ErrosFormulario
import com.example.netafim.atendimento.dominio.FormularioAbertura
import com.example.netafim.atendimento.dominio.comoOrigemDaAbertura
import com.example.netafim.atendimento.dominio.comoSolicitacao
import com.example.netafim.atendimento.dominio.erroDoResultado
import com.example.netafim.atendimento.dominio.validar
import com.example.netafim.atendimento.ui.lista.ESPERA_SEM_TELA_MS
import com.example.netafim.core.modelo.AtendimentoRepository
import com.example.netafim.core.modelo.Cultura
import com.example.netafim.core.modelo.Distribuidor
import com.example.netafim.core.modelo.ResultadoDaAcao
import com.example.netafim.core.modelo.sessao.SessaoDoUsuario
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Estado do formulário de abertura de atendimento.
//
// gerarId e relogio entram pelo construtor, com o valor de produção como padrão (UUID aleatório e a hora do
// aparelho), por dois motivos: o teste passa valores fixos e confere exatamente o que foi enviado, sem
// depender da hora real nem de um UUID sorteado; e a fábrica da navegação não precisa conhecer os dois.
// O UUID é gerado aqui, no aparelho, porque é ele que garante que reenviar o mesmo atendimento não duplica.
// Por isso o id da tentativa em curso é guardado (idDoEnvio) e reaproveitado em todo reenvio.
class AbrirAtendimentoViewModel(
    private val repositorio: AtendimentoRepository,
    private val sessao: SessaoDoUsuario,
    private val gerarId: () -> String = { UUID.randomUUID().toString() },
    private val relogio: () -> Instant = { Instant.now() },
) : ViewModel() {

    // Catálogos guardados no aparelho, para os chips de cultura e o menu de distribuidor.
    val culturas: StateFlow<List<Cultura>> = repositorio.observarCulturas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(ESPERA_SEM_TELA_MS), emptyList())
    val distribuidores: StateFlow<List<Distribuidor>> = repositorio.observarDistribuidores()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(ESPERA_SEM_TELA_MS), emptyList())

    private val _formulario = MutableStateFlow(FormularioAbertura())
    val formulario: StateFlow<FormularioAbertura> = _formulario.asStateFlow()

    // Erros por campo. Começam vazios (ninguém é avisado de erro antes de tentar enviar).
    private val _erros = MutableStateFlow(ErrosFormulario())
    val erros: StateFlow<ErrosFormulario> = _erros.asStateFlow()

    private val _enviando = MutableStateFlow(false)
    val enviando: StateFlow<Boolean> = _enviando.asStateFlow()

    // Erro do repositório ou da sessão no último envio, ou nulo.
    private val _erro = MutableStateFlow<ErroAoSalvar?>(null)
    val erro: StateFlow<ErroAoSalvar?> = _erro.asStateFlow()

    // Id do atendimento criado, quando o envio deu Sucesso (salvo no aparelho). A navegação usa para abrir o
    // detalhe dele. Nulo enquanto nada foi aberto.
    private val _aberto = MutableStateFlow<String?>(null)
    val aberto: StateFlow<String?> = _aberto.asStateFlow()

    // Depois da primeira tentativa de envio, os erros passam a ser recalculados a cada mudança, para sumirem
    // conforme a pessoa corrige os campos.
    private var tentouEnviar = false

    // Id do atendimento que está sendo aberto. Nasce no primeiro envio e é reaproveitado em todo reenvio depois
    // de um erro: se a primeira tentativa chegou a gravar o atendimento e só a resposta se perdeu, reenviar
    // com o mesmo id não cria um segundo atendimento. Não precisa ser limpo depois do Sucesso porque a tela
    // sai (e o próximo atendimento nasce de um ViewModel novo).
    private var idDoEnvio: String? = null

    fun alterar(novo: FormularioAbertura) {
        _formulario.value = novo
        if (tentouEnviar) _erros.value = validar(novo)
    }

    fun enviar() {
        // Sem usuário logado não há origem nem solicitante: não faz nada e não chama o repositório.
        val usuario = sessao.usuario.value ?: return

        val formulario = _formulario.value
        tentouEnviar = true
        val erros = validar(formulario)
        _erros.value = erros
        if (!erros.valido) return

        // A origem e a empresa saem do usuário. Cliente sem organização não pode abrir (a API recusaria com
        // 403): mostra "sem permissão" sem chamar o repositório.
        val origem = usuario.comoOrigemDaAbertura()
        if (origem == null) {
            _erro.value = ErroAoSalvar.SemPermissao
            return
        }

        // Envio duplo: liga a trava e confere a anterior num passo só, e por isso fica depois das saídas acima
        // (formulário inválido ou cliente sem organização não podem deixar a trava ligada). Com um envio em
        // andamento o segundo toque é ignorado.
        if (!_enviando.compareAndSet(false, true)) return

        val id = idDoEnvio ?: gerarId().also { idDoEnvio = it }
        val solicitacao = comoSolicitacao(
            formulario = formulario,
            id = id,
            origem = origem.origem,
            idCliente = origem.idCliente,
            abertoEm = relogio(),
        )
        if (solicitacao == null) {
            // Não deveria acontecer (o formulário já foi validado), mas se acontecer a trava é desligada.
            _enviando.value = false
            return
        }

        _erro.value = null
        viewModelScope.launch {
            val resultado = repositorio.abrirAtendimento(solicitacao)
            _erro.value = erroDoResultado(resultado)
            if (resultado == ResultadoDaAcao.Sucesso) _aberto.value = solicitacao.id
            _enviando.value = false
        }
    }

    // A tela chama quando o aviso de erro já foi lido.
    fun dispensarErro() {
        _erro.value = null
    }

    companion object {
        // Na produção vale o gerador de id e o relógio padrão; só os testes trocam.
        fun fabrica(repositorio: AtendimentoRepository, sessao: SessaoDoUsuario): ViewModelProvider.Factory =
            viewModelFactory { initializer { AbrirAtendimentoViewModel(repositorio, sessao) } }
    }
}
