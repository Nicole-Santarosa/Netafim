package com.example.netafim.atendimento.dominio

import com.example.netafim.core.modelo.FiltroDeAtendimentos
import com.example.netafim.core.modelo.OrdemDaLista
import com.example.netafim.core.modelo.StatusAtendimento

// Lista de atendimentos da região do distribuidor. Usa o mesmo ItemDeAtendimento da fila do técnico.

// Filtros da lista da região. Diferente da fila do técnico, aqui aparecem atendimentos de qualquer
// status, inclusive os concluídos e os cancelados.
enum class FiltroDaRegiao {
    TODOS,
    EM_ANDAMENTO,
    CONCLUIDOS,
}

// Traduz o chip e o texto da busca no pedido que o repositório entende. Em andamento reúne os selos Aberto
// (aberto, destinado e agendado) e Em atendimento; Concluídos reúne o selo Concluído (resolvido e os dois
// encerrados). Cancelado só aparece em Todos, que não limita o status e por isso também deixa passar o status
// que o app ainda não conhece. O que a pessoa enxerga (a região) quem decide é o repositório, pelo usuário.
// A ordem é do mais recente para o mais antigo: decisão do Rafael para a região, ainda não confirmada com o grupo.
fun FiltroDaRegiao.comoFiltroDeAtendimentos(busca: String): FiltroDeAtendimentos = FiltroDeAtendimentos(
    status = when (this) {
        FiltroDaRegiao.TODOS -> emptySet()
        FiltroDaRegiao.EM_ANDAMENTO -> setOf(
            StatusAtendimento.ABERTO,
            StatusAtendimento.DESTINADO,
            StatusAtendimento.AGENDADO,
            StatusAtendimento.EM_ATENDIMENTO,
        )
        FiltroDaRegiao.CONCLUIDOS -> setOf(
            StatusAtendimento.RESOLVIDO,
            StatusAtendimento.ENCERRADO,
            StatusAtendimento.ENCERRADO_SEM_AVALIACAO,
        )
    },
    busca = busca,
    ordem = OrdemDaLista.MAIS_RECENTE_PRIMEIRO,
)
