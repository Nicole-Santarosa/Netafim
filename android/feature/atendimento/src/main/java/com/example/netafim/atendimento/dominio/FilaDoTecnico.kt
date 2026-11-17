package com.example.netafim.atendimento.dominio

import com.example.netafim.core.modelo.FiltroDeAtendimentos
import com.example.netafim.core.modelo.OrdemDaLista
import com.example.netafim.core.modelo.StatusAtendimento

// Filtros da linha de chips da fila do técnico.
enum class FiltroDaFila {
    TODOS,
    NOVO,
    EM_ATENDIMENTO,
}

// Traduz o chip e o texto da busca no pedido que o repositório entende. A fila do técnico traz só o que está
// destinado a ele e ainda não foi resolvido: destinado, agendado e em atendimento. Aberto (ainda sem destino)
// e os status de conclusão ou cancelamento ficam de fora em todos os chips. Novo reúne o que ainda não foi
// iniciado (destinado e agendado). O id do técnico não vai no pedido: somenteMeusComoTecnico manda o
// repositório usar o usuário logado, que é quem ele conhece.
// A ordem é a da fila (em atendimento primeiro e depois os mais antigos): decisão do Rafael para a fila, ainda
// não confirmada com o grupo. A busca e a ordem em si são aplicadas pelo repositório.
fun FiltroDaFila.comoFiltroDeAtendimentos(busca: String): FiltroDeAtendimentos = FiltroDeAtendimentos(
    status = when (this) {
        FiltroDaFila.TODOS -> setOf(
            StatusAtendimento.DESTINADO,
            StatusAtendimento.AGENDADO,
            StatusAtendimento.EM_ATENDIMENTO,
        )
        FiltroDaFila.NOVO -> setOf(StatusAtendimento.DESTINADO, StatusAtendimento.AGENDADO)
        FiltroDaFila.EM_ATENDIMENTO -> setOf(StatusAtendimento.EM_ATENDIMENTO)
    },
    somenteMeusComoTecnico = true,
    busca = busca,
    ordem = OrdemDaLista.FILA_DO_TECNICO,
)
