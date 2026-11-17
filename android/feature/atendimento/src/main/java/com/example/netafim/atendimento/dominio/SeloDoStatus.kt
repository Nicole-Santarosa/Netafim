package com.example.netafim.atendimento.dominio

import com.example.netafim.core.modelo.StatusAtendimento

// Selo visual de um status. Cores e rótulos ficam na camada de interface.
enum class SeloStatus {
    ABERTO,
    EM_ATENDIMENTO,
    CONCLUIDO,
    CANCELADO,
}

// Agrupa os oito status nos quatro selos que as listas mostram.
// Fica aqui e não no StatusAtendimento porque o selo é coisa da tela; o status em si é domínio puro.
val StatusAtendimento.selo: SeloStatus
    get() = when (this) {
        StatusAtendimento.ABERTO, StatusAtendimento.DESTINADO, StatusAtendimento.AGENDADO -> SeloStatus.ABERTO
        StatusAtendimento.EM_ATENDIMENTO -> SeloStatus.EM_ATENDIMENTO
        StatusAtendimento.RESOLVIDO, StatusAtendimento.ENCERRADO, StatusAtendimento.ENCERRADO_SEM_AVALIACAO ->
            SeloStatus.CONCLUIDO
        StatusAtendimento.CANCELADO -> SeloStatus.CANCELADO
    }
