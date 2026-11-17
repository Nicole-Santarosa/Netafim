package com.example.netafim.atendimento.dominio

import com.example.netafim.core.modelo.StatusAtendimento

// Selo visual de um status. Cores e rótulos ficam na camada de interface.
enum class SeloStatus {
    ABERTO,
    EM_ATENDIMENTO,
    CONCLUIDO,
    CANCELADO,
    // Status que o app ainda não conhece (servidor mais novo que o aplicativo).
    DESCONHECIDO,
}

// Agrupa os oito status reais nos quatro selos das listas; o status desconhecido tem selo neutro próprio.
// Fica aqui e não no StatusAtendimento porque o selo é coisa da tela; o status em si é domínio puro.
val StatusAtendimento.selo: SeloStatus
    get() = when (this) {
        StatusAtendimento.ABERTO, StatusAtendimento.DESTINADO, StatusAtendimento.AGENDADO -> SeloStatus.ABERTO
        StatusAtendimento.EM_ATENDIMENTO -> SeloStatus.EM_ATENDIMENTO
        StatusAtendimento.RESOLVIDO, StatusAtendimento.ENCERRADO, StatusAtendimento.ENCERRADO_SEM_AVALIACAO ->
            SeloStatus.CONCLUIDO
        StatusAtendimento.CANCELADO -> SeloStatus.CANCELADO
        // O app guarda o registro, mas não sabe em que etapa ele está. Por isso não herda o selo de nenhuma
        // etapa conhecida: ter selo neutro evita dizer "Cancelado" ou "Aberto" sobre algo que não sabemos.
        StatusAtendimento.DESCONHECIDO -> SeloStatus.DESCONHECIDO
    }
