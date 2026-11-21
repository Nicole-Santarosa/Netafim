package com.example.netafim.data.sync

/**
 * Entidades contempladas pela sincronização na versão 1.
 */
object SyncEntityType {
    const val ATENDIMENTO = "atendimento"
    const val EVIDENCIA = "evidencia"
}

/**
 * Tipos de operações que podem ser enviadas ao servidor.
 */
object SyncOperationType {
    const val CRIAR = "criar"
    const val ATUALIZAR = "atualizar"
}

/**
 * Resultados que podem ser retornados pelo servidor no PUSH.
 */
object SyncResult {
    const val APLICADO = "aplicado"
    const val DUPLICADO = "duplicado"
    const val CONFLITO = "conflito"
    const val REJEITADO = "rejeitado"
}
