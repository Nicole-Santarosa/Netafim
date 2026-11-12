package com.netafim.posvenda.atendimento.dominio

import java.text.Normalizer
import java.time.Instant

// Um atendimento na fila do técnico: o que o cartão da fila precisa mostrar.
// O local é opcional: o atendimento pode ter sido aberto sem cidade nem fazenda.
data class ItemDaFila(
    val id: String,
    val protocolo: String,
    val nomeCliente: String,
    val local: String?,
    val motivo: MotivoAtendimento,
    val status: StatusAtendimento,
    val abertoEm: Instant,
)

// A fila do técnico traz só o que está destinado a ele e ainda não foi resolvido: destinado, agendado
// e em atendimento. Aberto (ainda sem destino) e os status de conclusão ou cancelamento ficam de fora.
fun estaNaFila(status: StatusAtendimento): Boolean =
    status == StatusAtendimento.DESTINADO ||
        status == StatusAtendimento.AGENDADO ||
        status == StatusAtendimento.EM_ATENDIMENTO

// Filtros da linha de chips da fila.
enum class FiltroDaFila {
    TODOS,
    NOVO,
    EM_ATENDIMENTO,
}

// Diz se um status passa pelo filtro. Fora da fila, nenhum filtro aceita.
// Novo reúne o que ainda não foi iniciado (destinado e agendado).
fun FiltroDaFila.aceita(status: StatusAtendimento): Boolean {
    if (!estaNaFila(status)) return false
    return when (this) {
        FiltroDaFila.TODOS -> true
        FiltroDaFila.NOVO -> status == StatusAtendimento.DESTINADO || status == StatusAtendimento.AGENDADO
        FiltroDaFila.EM_ATENDIMENTO -> status == StatusAtendimento.EM_ATENDIMENTO
    }
}

// Aplica o filtro e a busca e devolve o resultado já ordenado por ordenarFila. O item serve também à
// lista da região do distribuidor (ver RegiaoDoDistribuidor.kt).
// A busca apara os espaços das pontas, ignora maiúsculas e acentos e procura no protocolo, no nome do
// cliente e no local. Busca vazia ou só de espaços não filtra nada. Itens fora da fila nunca entram.
fun filtrarFila(itens: List<ItemDaFila>, filtro: FiltroDaFila, busca: String): List<ItemDaFila> {
    val termo = normalizarParaBusca(busca)
    val encontrados = itens.filter { item ->
        filtro.aceita(item.status) && (termo.isEmpty() || combinaComABusca(item, termo))
    }
    return ordenarFila(encontrados)
}

internal fun combinaComABusca(item: ItemDaFila, termo: String): Boolean =
    listOfNotNull(item.protocolo, item.nomeCliente, item.local)
        .any { normalizarParaBusca(it).contains(termo) }

// Minúsculas, sem acentos e sem espaços nas pontas: "Ribeirão" e "ribeirao" ficam iguais.
// A forma NFD separa a letra do acento, e o replace tira só as marcas de acento.
internal fun normalizarParaBusca(texto: String): String =
    Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()

// Ordem da fila: primeiro o que está em atendimento (trabalho em curso); depois os demais, do mais
// antigo para o mais novo; no empate, pelo protocolo em ordem alfabética.
// Essa ordem é uma decisão do Rafael para a fila, ainda não confirmada com o grupo.
fun ordenarFila(itens: List<ItemDaFila>): List<ItemDaFila> =
    itens.sortedWith(
        compareBy<ItemDaFila>(
            { if (it.status == StatusAtendimento.EM_ATENDIMENTO) 0 else 1 },
            { it.abertoEm },
            { it.protocolo },
        ),
    )
