package com.example.netafim.atendimento.ui.regiao

import androidx.annotation.StringRes
import com.example.netafim.atendimento.R
import com.example.netafim.atendimento.dominio.FiltroDaRegiao

// Texto de cada chip de filtro da lista da região.
@StringRes
fun FiltroDaRegiao.rotuloDoFiltro(): Int = when (this) {
    FiltroDaRegiao.TODOS -> R.string.atendimento_regiao_filtro_todos
    FiltroDaRegiao.EM_ANDAMENTO -> R.string.atendimento_regiao_filtro_em_andamento
    FiltroDaRegiao.CONCLUIDOS -> R.string.atendimento_regiao_filtro_concluidos
}
