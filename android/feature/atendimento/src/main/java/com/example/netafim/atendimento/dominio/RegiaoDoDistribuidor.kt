package com.example.netafim.atendimento.dominio

import com.example.netafim.core.modelo.ComparadorDeProtocolo
import com.example.netafim.core.modelo.ItemDeAtendimento
import com.example.netafim.core.modelo.StatusAtendimento
import com.example.netafim.core.modelo.buscarNosItens

// Lista de atendimentos da região do distribuidor. Usa o mesmo ItemDeAtendimento da fila do técnico.

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

// Aplica o filtro e a busca e devolve o resultado já ordenado por ordenarRegiao. A busca é a do
// :core:modelo (buscarNosItens): apara os espaços, ignora maiúsculas e acentos e procura no protocolo, no
// nome do cliente e no local. Busca vazia ou só de espaços não filtra nada.
fun filtrarRegiao(itens: List<ItemDeAtendimento>, filtro: FiltroDaRegiao, busca: String): List<ItemDeAtendimento> =
    ordenarRegiao(buscarNosItens(itens.filter { filtro.aceita(it.status) }, busca))

// Ordem da lista da região: do mais recente para o mais antigo pela data de abertura; no empate, pelo
// número do protocolo. É uma decisão do Rafael para a lista da região (a região acompanha o
// que acabou de chegar, diferente da fila do técnico) e ainda não foi confirmada com o grupo.
// Fica aqui e não usa ordenarPelosMaisRecentes do core porque o desempate do core é pelo id, não pelo
// protocolo: as duas só diferem quando duas aberturas têm exatamente o mesmo instante.
fun ordenarRegiao(itens: List<ItemDeAtendimento>): List<ItemDeAtendimento> =
    itens.sortedWith(
        compareByDescending<ItemDeAtendimento> { it.abertoEm }.thenBy(ComparadorDeProtocolo) { it.protocolo },
    )
