package com.example.netafim.atendimento.ui.comum

// Cores do aviso de erro das telas de ação, em ARGB (0xAARRGGBB), no mesmo formato das cores dos selos: Long,
// sem Compose, para o contraste poder ser conferido em teste unitário.
//
// O texto não usa a cor error do tema porque o tema de verdade vem do :app e este módulo não tem como garantir
// o contraste dele. Com a cor fixa aqui o teste confere exatamente o que é desenhado: o vermelho escuro do
// Material 3 sobre o fundo branco das telas dá cerca de 6,5 para 1, acima dos 4,5 exigidos para texto normal.
const val COR_DO_AVISO_DE_ERRO: Long = 0xFFB3261E

// Fundo das telas de ação (background branco do protótipo), usado só para conferir o contraste.
const val FUNDO_DA_TELA: Long = 0xFFFFFFFF
