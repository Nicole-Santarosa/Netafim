package com.example.netafim.atendimento.ui.abrir

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.netafim.atendimento.dominio.ErroAoSalvar
import com.example.netafim.atendimento.dominio.ErrosFormulario
import com.example.netafim.atendimento.dominio.FormularioAbertura
import com.example.netafim.atendimento.dominio.OrigemDaAbertura
import com.example.netafim.atendimento.dominio.comoOrigemDaAbertura
import com.example.netafim.atendimento.dominio.comoSolicitacao
import com.example.netafim.atendimento.dominio.erroDoResultado
import com.example.netafim.atendimento.dominio.limparParaOrigem
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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

    // Origem da abertura de quem está logado, para a tela saber que campos desenhar (cada origem tem o seu
    // conjunto). Nula quando ninguém está logado ou quando o usuário não pode abrir (cliente ou distribuidor
    // sem organização). O valor inicial já é o da sessão atual, para a tela não piscar o formulário errado
    // antes da primeira coleta.
    val origem: StateFlow<OrigemDaAbertura?> = sessao.usuario
        .map { it?.comoOrigemDaAbertura() }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(ESPERA_SEM_TELA_MS),
            sessao.usuario.value?.comoOrigemDaAbertura(),
        )

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

    init {
        // Se a origem de quem está logado mudar com o formulário aberto (outro usuário entra na sessão, por
        // exemplo), o que foi preenchido para a origem anterior e não vale na nova é descartado: um
        // distribuidor escolhido como cliente final não pode ficar escondido no estado e valer numa origem que
        // nem o desenha. Os campos comuns são mantidos. Os erros por campo voltam a zero, porque eram de outra
        // origem, e a validação só reaparece depois da próxima tentativa de envio. distinctUntilChanged evita
        // limpar quando a sessão muda sem mudar de origem (a primeira emissão só confirma o estado inicial).
        viewModelScope.launch {
            sessao.usuario
                .map { it?.comoOrigemDaAbertura()?.origem }
                .distinctUntilChanged()
                .collect { novaOrigem ->
                    _formulario.update { it.limparParaOrigem(novaOrigem) }
                    _erros.value = ErrosFormulario()
                    tentouEnviar = false
                }
        }
    }

    fun alterar(novo: FormularioAbertura) {
        _formulario.value = novo
        // Sem origem (ninguém logado ou usuário que não pode abrir) não há contra o que validar.
        val origem = sessao.usuario.value?.comoOrigemDaAbertura() ?: return
        if (tentouEnviar) _erros.value = validar(novo, origem.origem)
    }

    fun enviar() {
        // Sem usuário logado não há origem nem solicitante: não faz nada e não chama o repositório.
        val usuario = sessao.usuario.value ?: return

        tentouEnviar = true
        // Cada tentativa começa sem o aviso da anterior: se esta parar na validação, o que vale são os erros
        // dos campos, e não uma recusa antiga do servidor. Fica antes da trava porque, com um envio em
        // andamento, o erro já foi limpo quando ele começou.
        _erro.value = null
        // Cliente ou distribuidor sem organização não pode abrir (a API recusaria): mostra "sem permissão" sem
        // chamar o repositório. A regra é a do usuário da sessão, a mesma que as listas usam.
        if (usuario.semOrganizacao) {
            _erro.value = ErroAoSalvar.SemPermissao
            return
        }
        // A origem e os ids da sessão saem do usuário, e vêm antes da validação porque é a origem que diz
        // quais campos são obrigatórios. Com organização (ou sendo Netafim) ela sempre existe; o retorno só
        // cobre o tipo, que o compilador não sabe que já foi conferido acima.
        val origem = usuario.comoOrigemDaAbertura() ?: return

        val formulario = _formulario.value
        val erros = validar(formulario, origem.origem)
        _erros.value = erros
        if (!erros.valido) return

        // Envio duplo: liga a trava e confere a anterior num passo só, e por isso fica depois das saídas acima
        // (usuário sem organização ou formulário inválido não podem deixar a trava ligada). Com um envio em
        // andamento o segundo toque é ignorado.
        if (!_enviando.compareAndSet(false, true)) return

        val id = idDoEnvio ?: gerarId().also { idDoEnvio = it }
        val solicitacao = comoSolicitacao(
            formulario = formulario,
            id = id,
            origem = origem,
            abertoEm = relogio(),
        )
        if (solicitacao == null) {
            // Não deveria acontecer (o formulário já foi validado), mas se acontecer a trava é desligada.
            _enviando.value = false
            return
        }

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
