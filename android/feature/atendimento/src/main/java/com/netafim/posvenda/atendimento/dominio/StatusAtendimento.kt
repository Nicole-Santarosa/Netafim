package com.netafim.posvenda.atendimento.dominio

// Status de negócio do atendimento. O código de texto é o mesmo que a API devolve no campo
// status (contrato docs/api/atendimentos-openapi.yaml, schema Status). Rótulos e cores não ficam
// aqui: o domínio só carrega código e regra.
enum class StatusAtendimento(val codigo: String) {
    ABERTO("aberto"),
    DESTINADO("destinado"),
    AGENDADO("agendado"),
    EM_ATENDIMENTO("em_atendimento"),
    RESOLVIDO("resolvido"),
    ENCERRADO("encerrado"),
    ENCERRADO_SEM_AVALIACAO("encerrado_sem_avaliacao"),
    CANCELADO("cancelado");

    // Estados finais: o banco não aceita mais alteração neles (só do Netafim Admin ou do processo
    // automático), então a tela não deve oferecer nenhuma ação.
    val ehFinal: Boolean
        get() = this == ENCERRADO || this == ENCERRADO_SEM_AVALIACAO || this == CANCELADO

    // Agrupa os oito status nos quatro selos que as listas mostram.
    val selo: SeloStatus
        get() = when (this) {
            ABERTO, DESTINADO, AGENDADO -> SeloStatus.ABERTO
            EM_ATENDIMENTO -> SeloStatus.EM_ATENDIMENTO
            RESOLVIDO, ENCERRADO, ENCERRADO_SEM_AVALIACAO -> SeloStatus.CONCLUIDO
            CANCELADO -> SeloStatus.CANCELADO
        }

    companion object {
        // Devolve null para um código que o app não conhece (por exemplo, um status novo criado
        // no servidor depois desta versão do app), para a tela decidir o que fazer em vez de quebrar.
        fun deCodigo(codigo: String): StatusAtendimento? = entries.firstOrNull { it.codigo == codigo }
    }
}

// Selo visual de um status. Cores e rótulos ficam na camada de interface.
enum class SeloStatus {
    ABERTO,
    EM_ATENDIMENTO,
    CONCLUIDO,
    CANCELADO,
}
