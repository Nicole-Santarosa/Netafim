package com.example.netafim.atendimento.dominio

import com.example.netafim.core.modelo.ItemDeAtendimento
import com.example.netafim.core.modelo.StatusAtendimento
import com.example.netafim.core.modelo.buscarNosItens
import com.example.netafim.core.modelo.ordenarPelaFilaDoTecnico

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

// Aplica o filtro e a busca e devolve o resultado já na ordem da fila do técnico. O item é o mesmo da lista
// da região do distribuidor (ver RegiaoDoDistribuidor.kt).
// A busca e a ordem são as do :core:modelo (buscarNosItens e ordenarPelaFilaDoTecnico): a busca apara os
// espaços das pontas, ignora maiúsculas e acentos e procura no protocolo, no nome do cliente e no local;
// busca vazia ou só de espaços não filtra nada. A ordem põe o que está em atendimento primeiro e depois
// os mais antigos, com desempate pelo número do protocolo; essa ordem é uma decisão do Rafael para a fila,
// ainda não confirmada com o grupo. Itens fora da fila nunca entram.
fun filtrarFila(itens: List<ItemDeAtendimento>, filtro: FiltroDaFila, busca: String): List<ItemDeAtendimento> =
    ordenarPelaFilaDoTecnico(buscarNosItens(itens.filter { filtro.aceita(it.status) }, busca))
