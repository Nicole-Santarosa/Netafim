package com.netafim.posvenda.acesso.ui.privacidade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netafim.posvenda.acesso.dominio.AcoesDoPedido
import com.netafim.posvenda.acesso.dominio.ErroAcesso
import com.netafim.posvenda.acesso.dominio.PedidoAdmin
import com.netafim.posvenda.acesso.dominio.RepositorioAcesso
import com.netafim.posvenda.acesso.dominio.RepositorioFilaPrivacidade
import com.netafim.posvenda.acesso.dominio.Resultado
import com.netafim.posvenda.acesso.dominio.TipoPedido
import com.netafim.posvenda.acesso.dominio.acoesDoPedido
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Mensagem de sucesso que a tela mostra uma vez. */
enum class AvisoPedido { EM_ANALISE, ATENDIDO, NEGADO }

/** Limite do motivo da negativa aceito pela API. */
const val LIMITE_JUSTIFICATIVA = 2000

data class PedidoAdminEstado(
    val pedido: PedidoAdmin? = null,
    val acoes: AcoesDoPedido = AcoesDoPedido(),
    val carregando: Boolean = true,
    /** Falha ao carregar o pedido (a tela oferece tentar de novo). */
    val erro: String? = null,
    /** Uma ação está em andamento: os botões ficam desabilitados. */
    val processando: Boolean = false,
    /** Falha de uma ação (atender, negar...), mostrada acima dos botões. */
    val erroAcao: String? = null,
    val aviso: AvisoPedido? = null,
    /** Confirmação de atender aberta. */
    val confirmandoAtender: Boolean = false,
    /** Diálogo do motivo da negativa aberto. */
    val negando: Boolean = false,
    val justificativa: String = "",
    val erroJustificativa: String? = null,
    val baixando: Boolean = false,
    /** Cópia dos dados do titular pronta para a tela entregar (folha de compartilhar do sistema). */
    val copia: String? = null,
) {
    /** Atender este pedido anonimiza a conta do titular, sem volta. */
    val atenderAnonimiza: Boolean get() = TipoPedido.deCodigo(pedido?.tipo.orEmpty())?.irreversivel == true
}

/**
 * Um pedido da fila, para o Netafim Admin: marcar em análise, atender, negar (com o motivo) e baixar a cópia dos dados
 * do titular. A API não tem rota de um pedido só; o pedido vem da lista e, depois de cada ação, da resposta da ação.
 * Os botões seguem [acoesDoPedido]; a API continua sendo quem decide e as recusas dela aparecem como mensagem.
 */
class PedidoAdminViewModel(
    private val repositorio: RepositorioFilaPrivacidade,
    private val acesso: RepositorioAcesso,
    private val idPedido: Int,
) : ViewModel() {

    private val _estado = MutableStateFlow(PedidoAdminEstado())
    val estado: StateFlow<PedidoAdminEstado> = _estado.asStateFlow()

    init {
        carregar()
    }

    fun carregar() {
        _estado.update { it.copy(carregando = true, erro = null) }
        viewModelScope.launch {
            when (val r = repositorio.fila()) {
                is Resultado.Sucesso -> {
                    val pedido = r.valor.firstOrNull { it.id == idPedido }
                    if (pedido == null) {
                        _estado.update { it.copy(carregando = false, erro = "Pedido não encontrado.") }
                    } else {
                        mostrar(pedido)
                    }
                }
                is Resultado.Falha -> _estado.update { it.copy(carregando = false, erro = r.erro.mensagem) }
            }
        }
    }

    private fun mostrar(pedido: PedidoAdmin) {
        val ator = acesso.sessao.value?.usuario
        _estado.update {
            it.copy(
                pedido = pedido,
                acoes = if (ator != null) acoesDoPedido(ator, pedido) else AcoesDoPedido(),
                carregando = false,
                erro = null,
                processando = false,
            )
        }
    }

    // ------------------------------------------------------------------ ações

    fun analisar() = executar(AvisoPedido.EM_ANALISE) { repositorio.analisar(idPedido) }

    fun pedirAtender() = _estado.update { it.copy(confirmandoAtender = true, erroAcao = null) }

    fun cancelarAtender() = _estado.update { if (it.processando) it else it.copy(confirmandoAtender = false) }

    fun confirmarAtender() {
        if (!_estado.value.confirmandoAtender) return
        executar(AvisoPedido.ATENDIDO) { repositorio.atender(idPedido) }
    }

    fun pedirNegar() = _estado.update { it.copy(negando = true, justificativa = "", erroJustificativa = null, erroAcao = null) }

    fun cancelarNegar() = _estado.update { if (it.processando) it else it.copy(negando = false, erroJustificativa = null) }

    fun mudarJustificativa(valor: String) = _estado.update {
        if (it.processando) it else it.copy(justificativa = valor, erroJustificativa = null)
    }

    fun confirmarNegar() {
        val atual = _estado.value
        if (!atual.negando || atual.processando) return
        val motivo = atual.justificativa.trim()
        val erro = when {
            motivo.isEmpty() -> "Informe o motivo: é o que o titular vai ler."
            motivo.length > LIMITE_JUSTIFICATIVA -> "Use no máximo $LIMITE_JUSTIFICATIVA caracteres."
            else -> null
        }
        if (erro != null) {
            _estado.update { it.copy(erroJustificativa = erro) }
            return
        }
        executar(AvisoPedido.NEGADO) { repositorio.negar(idPedido, motivo) }
    }

    private fun executar(aviso: AvisoPedido, chamada: suspend () -> Resultado<PedidoAdmin>) {
        if (_estado.value.processando) return
        _estado.update { it.copy(processando = true, erroAcao = null, aviso = null) }
        viewModelScope.launch {
            when (val r = chamada()) {
                is Resultado.Sucesso -> {
                    mostrar(r.valor)
                    _estado.update { it.copy(confirmandoAtender = false, negando = false, aviso = aviso) }
                }
                is Resultado.Falha -> falhaDeAcao(r.erro)
            }
        }
    }

    private fun falhaDeAcao(erro: ErroAcesso) = _estado.update {
        // A recusa (ex.: o próprio pedido de anonimização) aparece acima dos botões; os diálogos fecham.
        it.copy(processando = false, confirmandoAtender = false, negando = false, erroAcao = erro.mensagem)
    }

    // ------------------------------------------------------------------ cópia dos dados

    fun baixarDados() {
        if (_estado.value.baixando) return
        _estado.update { it.copy(baixando = true, erroAcao = null, copia = null) }
        viewModelScope.launch {
            when (val r = repositorio.dadosDoTitular(idPedido)) {
                is Resultado.Sucesso -> _estado.update { it.copy(baixando = false, copia = r.valor) }
                is Resultado.Falha -> _estado.update { it.copy(baixando = false, erroAcao = r.erro.mensagem) }
            }
        }
    }

    /** A tela já entregou a cópia: ela sai da memória. */
    fun copiaEntregue() = _estado.update { it.copy(copia = null) }

    fun avisoMostrado() = _estado.update { it.copy(aviso = null) }
}
