package com.example.netafim.core.modelo

// Funções puras sobre a lista de ItemDeAtendimento. O repositório as aplica antes de entregar a lista à
// tela, conforme a OrdemDaLista e a busca do FiltroDeAtendimentos.

// Ordem da fila do técnico: primeiro o que está em atendimento (trabalho em curso); depois os demais, do
// mais antigo para o mais novo; no empate, pelo número do protocolo. Protocolo nulo vem por último e só
// entra como critério final.
fun ordenarPelaFilaDoTecnico(itens: List<ItemDeAtendimento>): List<ItemDeAtendimento> =
    itens.sortedWith(
        compareBy<ItemDeAtendimento>(
            { if (it.status == StatusAtendimento.EM_ATENDIMENTO) 0 else 1 },
            { it.abertoEm },
        ).thenBy(ComparadorDeProtocolo) { it.protocolo },
    )

// Do mais recente para o mais antigo pela data de abertura; no empate, pelo id em ordem crescente, que é a
// mesma ordem em que a API devolve a lista.
fun ordenarPelosMaisRecentes(itens: List<ItemDeAtendimento>): List<ItemDeAtendimento> =
    itens.sortedWith(compareByDescending<ItemDeAtendimento> { it.abertoEm }.thenBy { it.id })

// Aplica a ordem escolhida pela tela.
fun ordenar(itens: List<ItemDeAtendimento>, ordem: OrdemDaLista): List<ItemDeAtendimento> = when (ordem) {
    OrdemDaLista.FILA_DO_TECNICO -> ordenarPelaFilaDoTecnico(itens)
    OrdemDaLista.MAIS_RECENTE_PRIMEIRO -> ordenarPelosMaisRecentes(itens)
}

// Busca no protocolo, no nome do cliente e no local, que são os campos que a busca das telas já usa e que o
// item possui. Apara os espaços das pontas e ignora maiúsculas e acentos. Busca vazia ou só de espaços não
// filtra nada. Os campos nulos do item simplesmente não entram na comparação.
fun buscarNosItens(itens: List<ItemDeAtendimento>, busca: String): List<ItemDeAtendimento> {
    val termo = normalizarParaBusca(busca)
    if (termo.isEmpty()) return itens
    return itens.filter { item ->
        listOfNotNull(item.protocolo, item.nomeCliente, item.local)
            .any { normalizarParaBusca(it).contains(termo) }
    }
}
