package com.example.netafim.acesso.dominio

/**
 * Um pedido de titular visto pelo Netafim Admin (`GET solicitacoes-lgpd`). [idUsuario] e [nomeTitular] ficam
 * nulos depois que a conta do titular é anonimizada; [atrasada] vem calculada pela API (prazo vencido e
 * pedido ainda aberto).
 */
data class PedidoAdmin(
    val id: Int,
    val idUsuario: Int?,
    val nomeTitular: String?,
    val emailContato: String,
    val tipo: String,
    val status: String,
    val observacao: String?,
    val dataSolicitacao: String,
    val dataPrazo: String,
    val dataConclusao: String?,
    val idResponsavel: Int?,
    val justificativaNegativa: String?,
    val atrasada: Boolean,
) {
    val aberto: Boolean get() = status == StatusPedido.RECEBIDA || status == StatusPedido.EM_ANALISE
}

/** Fila de pedidos de titulares: só o Netafim Admin lê e responde (`solicitacoes-lgpd`). */
interface RepositorioFilaPrivacidade {
    /** Todos os pedidos, ordenados pelo prazo (sem paginação na API). */
    suspend fun fila(): Resultado<List<PedidoAdmin>>

    /** Marca como `em_analise` e registra o responsável. */
    suspend fun analisar(idPedido: Int): Resultado<PedidoAdmin>

    /**
     * Conclui o pedido. Para `anonimizacao` e `eliminacao` anonimiza a conta do titular de verdade (sem
     * volta); o administrador não atende o próprio pedido desses tipos (`403 proprio_pedido`).
     */
    suspend fun atender(idPedido: Int): Resultado<PedidoAdmin>

    /** Nega o pedido; a [justificativa] é obrigatória e é o que o titular vê. */
    suspend fun negar(idPedido: Int, justificativa: String): Resultado<PedidoAdmin>

    /** Cópia dos dados do titular (`GET solicitacoes-lgpd/{id}/dados`), só para pedidos de acesso e portabilidade. */
    suspend fun dadosDoTitular(idPedido: Int): Resultado<String>
}

/** Quem atende os pedidos de titulares: o Netafim Admin. */
fun Usuario.podeAtenderPedidosLgpd(): Boolean =
    perfil?.let { it.tipoOrganizacao == TiposOrganizacao.NETAFIM && it.ehAdmin } == true

/** O que o administrador logado pode fazer com um pedido. */
data class AcoesDoPedido(
    val analisar: Boolean = false,
    val atender: Boolean = false,
    val negar: Boolean = false,
    val verDados: Boolean = false,
) {
    val alguma: Boolean get() = analisar || atender || negar || verDados
}

private val TIPOS_COM_COPIA = setOf(TipoPedido.ACESSO.codigo, TipoPedido.PORTABILIDADE.codigo)

/**
 * Regras que a tela usa para mostrar só os botões que a API aceitaria (a API continua decidindo):
 * só o Netafim Admin age; pedido concluído não muda; o administrador não atende o próprio pedido de
 * anonimização ou eliminação (separação de funções); a cópia dos dados só existe para acesso e
 * portabilidade, de pedido não negado e de titular que ainda tem conta.
 */
fun acoesDoPedido(ator: Usuario, pedido: PedidoAdmin): AcoesDoPedido {
    if (!ator.podeAtenderPedidosLgpd()) return AcoesDoPedido()
    val proprioIrreversivel = pedido.idUsuario == ator.id && TipoPedido.deCodigo(pedido.tipo)?.irreversivel == true
    return AcoesDoPedido(
        analisar = pedido.status == StatusPedido.RECEBIDA,
        atender = pedido.aberto && !proprioIrreversivel,
        negar = pedido.aberto,
        verDados = pedido.tipo in TIPOS_COM_COPIA && pedido.status != StatusPedido.NEGADA && pedido.idUsuario != null,
    )
}
