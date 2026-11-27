package com.example.netafim.feature.relatorio.data

import com.example.netafim.feature.relatorio.model.RelatorioRascunho
import java.io.IOException

/**
 * Implementação pendente de integração com o Room da Pessoa 4 (RELATORIO_LOCAL, EVIDENCIA_LOCAL, ASSINATURA_LOCAL).
 * Não simula sucesso (não retorna Result.success sem gravação real).
 */
class RelatorioRepositorioImpl : RelatorioRepositorio {

    override suspend fun salvarRelatorio(rascunho: RelatorioRascunho): Result<Unit> {
        return Result.failure(
            IOException("Persistência Room ainda não integrada pela Pessoa 4; gravação não confirmada.")
        )
    }

    override suspend fun recuperarRelatorio(idAtendimento: String): Result<RelatorioRascunho?> {
        return Result.failure(
            IOException("Recuperação Room ainda não integrada pela Pessoa 4.")
        )
    }
}