package com.netafim.posvenda.acesso.ui.perfil

/** (16) 99999-8888 para celular e (16) 3333-4444 para fixo; outros formatos ficam como estão. */
internal fun formatarTelefone(valor: String): String {
    val d = valor.filter { it.isDigit() }
    return when (d.length) {
        11 -> "(${d.substring(0, 2)}) ${d.substring(2, 7)}-${d.substring(7)}"
        10 -> "(${d.substring(0, 2)}) ${d.substring(2, 6)}-${d.substring(6)}"
        else -> valor
    }
}
