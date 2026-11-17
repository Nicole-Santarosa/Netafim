package com.example.netafim.atendimento.ui.comum

import com.example.netafim.atendimento.dominio.IndicadorDeSincronizacao

// Cor do ícone de sincronização, em ARGB (0xAARRGGBB), no mesmo formato das cores dos selos: Long, sem Compose,
// para o contraste poder ser conferido em teste unitário. O ícone fica sobre o fundo do cartão, que é a cor
// surface (branco no tema do app e nos previews), e por ser elemento gráfico precisa de pelo menos 3 para 1.
// - Pendente: cinza escuro neutro, porque esperar o envio é normal e não deve assustar.
// - Atenção: vermelho escuro, o mesmo tom usado para erro no Material 3.
fun corDoIndicador(indicador: IndicadorDeSincronizacao): Long = when (indicador) {
    IndicadorDeSincronizacao.PENDENTE -> 0xFF5E5E5A
    IndicadorDeSincronizacao.ATENCAO -> 0xFFB3261E
}

// Fundo do cartão sobre o qual o ícone é desenhado, usado só para conferir o contraste.
const val FUNDO_DO_CARTAO: Long = 0xFFFFFFFF
