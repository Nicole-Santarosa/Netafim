package com.example.netafim.acesso.dominio

/**
 * Tipos de pedido do titular (LGPD, art. 18), com o código que a API aceita em `POST me/solicitacoes-lgpd`.
 * [irreversivel]: atender o pedido anonimiza a conta e a pessoa deixa de conseguir entrar, sem volta.
 */
enum class TipoPedido(val codigo: String, val irreversivel: Boolean = false) {
    CONFIRMACAO("confirmacao"),
    ACESSO("acesso"),
    CORRECAO("correcao"),
    ANONIMIZACAO("anonimizacao", irreversivel = true),
    ELIMINACAO("eliminacao", irreversivel = true),
    PORTABILIDADE("portabilidade"),
    COMPARTILHAMENTO("compartilhamento"),
    REVOGACAO_CONSENTIMENTO("revogacao_consentimento"),
    ;

    companion object {
        fun deCodigo(codigo: String): TipoPedido? = entries.firstOrNull { it.codigo == codigo }
    }
}

/** Andamento de um pedido: `recebida` → `em_analise` → `atendida` ou `negada`. */
object StatusPedido {
    const val RECEBIDA = "recebida"
    const val EM_ANALISE = "em_analise"
    const val ATENDIDA = "atendida"
    const val NEGADA = "negada"
}

/**
 * Um pedido do próprio titular. As datas vêm da API em ISO 8601 (UTC). [tipo] e [status] ficam como
 * texto para que um valor novo da API não derrube a tela; [justificativaNegativa] só existe quando negado.
 */
data class PedidoTitular(
    val id: Int,
    val tipo: String,
    val status: String,
    val observacao: String?,
    val dataSolicitacao: String,
    val dataPrazo: String,
    val dataConclusao: String?,
    val justificativaNegativa: String?,
) {
    /** Ainda sem resposta: não dá para pedir o mesmo tipo de novo (a API recusa com `solicitacao_em_aberto`). */
    val aberto: Boolean get() = status == StatusPedido.RECEBIDA || status == StatusPedido.EM_ANALISE
}

/** Direitos do titular sobre os próprios dados, para qualquer usuário logado. */
interface RepositorioPrivacidade {
    /** Os pedidos da própria pessoa, do mais novo ao mais antigo (`GET me/solicitacoes-lgpd`). */
    suspend fun meusPedidos(): Resultado<List<PedidoTitular>>

    /** Abre um pedido sempre em nome de quem está logado. [observacao] em branco não é enviada. */
    suspend fun abrirPedido(tipo: TipoPedido, observacao: String): Resultado<PedidoTitular>

    /**
     * Cópia dos dados pessoais que o módulo de acesso guarda (`GET me/dados`), como JSON legível. Não traz
     * relatórios nem evidências dos atendimentos.
     */
    suspend fun copiaDosDados(): Resultado<String>
}

/** Tamanho máximo do campo de observação aceito pela API. */
const val LIMITE_OBSERVACAO_PEDIDO = 1000
