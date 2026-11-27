package com.example.netafim.feature.relatorio.data

import com.example.netafim.feature.relatorio.model.RelatorioRascunho

interface RelatorioRepositorio {
    suspend fun salvarRelatorio(rascunho: RelatorioRascunho): Result<Unit>
    suspend fun recuperarRelatorio(idAtendimento: String): Result<RelatorioRascunho?>
}