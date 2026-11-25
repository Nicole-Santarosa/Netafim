package com.example.netafim.data.sync

/**
 * Centraliza os estados possíveis de uma operação na fila de sincronização.
 *
 * Esses estados representam a situação do envio da operação, não o status de negócio do atendimento.
 */
object SyncOperationStatus {

    // Operação registrada localmente e aguardando envio ao servidor.
    const val PENDENTE = "pendente"

    // Operação que está sendo preparada ou processada para envio.
    const val EM_PROCESSAMENTO = "em_processamento"

    // O servidor identificou divergência de versão.
    // A operação deve ser preservada para tratamento do conflito.
    const val CONFLITO = "conflito"

    // O servidor recusou a operação por alguma regra de negócio.
    // Não deve ser reenviada automaticamente sem edição do usuário.
    const val REJEITADA = "rejeitado"
}
