package com.netafim.posvenda.atendimento.dominio

import java.time.Instant

// Origem do atendimento: quem abriu a solicitação. Os códigos são os do contrato da API (schema Origem).
enum class OrigemAtendimento(val codigo: String) {
    NETAFIM("netafim"),
    DISTRIBUIDOR("distribuidor"),
    CLIENTE_FINAL("cliente_final");

    companion object {
        // Devolve null para um código que o app não conhece, para a tela decidir o que fazer em vez de quebrar.
        fun deCodigo(codigo: String): OrigemAtendimento? = entries.firstOrNull { it.codigo == codigo }
    }
}

// Um atendimento na lista do Admin da Netafim: o que cada linha precisa mostrar.
data class ItemDaListaAdmin(
    val id: String,
    val protocolo: String,
    val nomeCliente: String,
    val origem: OrigemAtendimento,
    val status: StatusAtendimento,
    val abertoEm: Instant,
)

// Filtros da linha de chips da lista do Admin: todas as origens ou uma só.
enum class FiltroDeOrigem {
    TODAS,
    NETAFIM,
    DISTRIBUIDOR,
    CLIENTE_FINAL,
}

// Diz se uma origem passa pelo filtro. Todas aceita qualquer origem; os outros aceitam só a sua.
fun FiltroDeOrigem.aceita(origem: OrigemAtendimento): Boolean = when (this) {
    FiltroDeOrigem.TODAS -> true
    FiltroDeOrigem.NETAFIM -> origem == OrigemAtendimento.NETAFIM
    FiltroDeOrigem.DISTRIBUIDOR -> origem == OrigemAtendimento.DISTRIBUIDOR
    FiltroDeOrigem.CLIENTE_FINAL -> origem == OrigemAtendimento.CLIENTE_FINAL
}

// Aplica o filtro e devolve o resultado do mais recente para o mais antigo pela data de abertura; no
// empate, pelo número do protocolo. A ordem é uma decisão do Rafael para a lista do Admin e
// ainda não foi confirmada com o grupo.
fun filtrarListaAdmin(itens: List<ItemDaListaAdmin>, filtro: FiltroDeOrigem): List<ItemDaListaAdmin> =
    itens.filter { filtro.aceita(it.origem) }
        .sortedWith(
            compareByDescending<ItemDaListaAdmin> { it.abertoEm }.thenBy(ComparadorDeProtocolo) { it.protocolo },
        )

// Texto da linha: protocolo e nome do cliente separados por " · ". O separador é pontuação, por isso
// fica no Kotlin e não no strings.xml.
fun textoDaLinha(item: ItemDaListaAdmin): String = "${item.protocolo} · ${item.nomeCliente}"
