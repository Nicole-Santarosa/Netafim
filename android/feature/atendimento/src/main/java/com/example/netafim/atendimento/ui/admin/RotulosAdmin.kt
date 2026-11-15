package com.example.netafim.atendimento.ui.admin

import androidx.annotation.StringRes
import com.example.netafim.atendimento.R
import com.example.netafim.atendimento.dominio.FiltroDeOrigem

// Texto de cada chip de filtro por origem da lista do Admin.
@StringRes
fun FiltroDeOrigem.rotuloDoFiltro(): Int = when (this) {
    FiltroDeOrigem.TODAS -> R.string.atendimento_admin_filtro_todos
    FiltroDeOrigem.NETAFIM -> R.string.atendimento_admin_filtro_netafim
    FiltroDeOrigem.DISTRIBUIDOR -> R.string.atendimento_admin_filtro_distribuidor
    FiltroDeOrigem.CLIENTE_FINAL -> R.string.atendimento_admin_filtro_cliente_final
}
