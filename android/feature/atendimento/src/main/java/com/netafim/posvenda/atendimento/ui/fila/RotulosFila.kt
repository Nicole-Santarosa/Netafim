package com.netafim.posvenda.atendimento.ui.fila

import androidx.annotation.StringRes
import com.netafim.posvenda.atendimento.R
import com.netafim.posvenda.atendimento.dominio.FiltroDaFila
import com.netafim.posvenda.atendimento.dominio.StatusAtendimento
import com.netafim.posvenda.atendimento.ui.comum.rotulo

// Na fila do técnico, destinado e agendado aparecem os dois como "Novo" (ainda não iniciado) e em
// atendimento mantém o nome. Para os outros status, que não entram na fila, vale o rótulo normal do selo.
@StringRes
fun rotuloNaFila(status: StatusAtendimento): Int = when (status) {
    StatusAtendimento.DESTINADO, StatusAtendimento.AGENDADO -> R.string.atendimento_fila_selo_novo
    StatusAtendimento.EM_ATENDIMENTO -> R.string.atendimento_fila_selo_em_atendimento
    else -> status.selo.rotulo()
}

// Texto de cada chip de filtro da fila.
@StringRes
fun FiltroDaFila.rotuloDoFiltro(): Int = when (this) {
    FiltroDaFila.TODOS -> R.string.atendimento_fila_filtro_todos
    FiltroDaFila.NOVO -> R.string.atendimento_fila_filtro_novo
    FiltroDaFila.EM_ATENDIMENTO -> R.string.atendimento_fila_filtro_em_atendimento
}
