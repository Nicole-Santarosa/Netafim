package com.netafim.posvenda.atendimento.dominio

// Lista de atendimentos da região do distribuidor. Reaproveita o ItemDaFila da fila do técnico: o mesmo
// item (protocolo, cliente, local, motivo, status e data de abertura) serve às duas listas.

// Filtros da lista da região. Diferente da fila do técnico, aqui aparecem atendimentos de qualquer
// status, inclusive os concluídos e os cancelados.
enum class FiltroDaRegiao {
    TODOS,
    EM_ANDAMENTO,
    CONCLUIDOS,
}

// Diz se um status passa pelo filtro, decidindo pelo selo. Em andamento reúne os selos Aberto e Em
// atendimento; Concluídos reúne o selo Concluído. Cancelado só aparece em Todos.
fun FiltroDaRegiao.aceita(status: StatusAtendimento): Boolean = when (this) {
    FiltroDaRegiao.TODOS -> true
    FiltroDaRegiao.EM_ANDAMENTO -> status.selo == SeloStatus.ABERTO || status.selo == SeloStatus.EM_ATENDIMENTO
    FiltroDaRegiao.CONCLUIDOS -> status.selo == SeloStatus.CONCLUIDO
}

// Aplica o filtro e a busca e devolve o resultado já ordenado por ordenarRegiao. A busca tem as mesmas
// regras da fila do técnico: apara os espaços, ignora maiúsculas e acentos e procura no protocolo, no
// nome do cliente e no local. Busca vazia ou só de espaços não filtra nada.
fun filtrarRegiao(itens: List<ItemDaFila>, filtro: FiltroDaRegiao, busca: String): List<ItemDaFila> {
    val termo = normalizarParaBusca(busca)
    val encontrados = itens.filter { item ->
        filtro.aceita(item.status) && (termo.isEmpty() || combinaComABusca(item, termo))
    }
    return ordenarRegiao(encontrados)
}

// Ordem da lista da região: do mais recente para o mais antigo pela data de abertura; no empate, pelo
// protocolo em ordem alfabética. É uma decisão do Rafael para a lista da região (a região acompanha o
// que acabou de chegar, diferente da fila do técnico) e ainda não foi confirmada com o grupo.
fun ordenarRegiao(itens: List<ItemDaFila>): List<ItemDaFila> =
    itens.sortedWith(
        compareByDescending<ItemDaFila> { it.abertoEm }.thenBy { it.protocolo },
    )
