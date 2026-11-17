package com.example.netafim.core.modelo

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
    CANCELADO("cancelado"),

    // O servidor mandou um status que esta versão do app não conhece (por exemplo um status criado no
    // servidor depois desta versão). O registro não pode ser descartado por isso: ele é guardado com este
    // valor e o código original continua no dadosJson do Room. O código textual abaixo é só um nome interno
    // e nunca é enviado à API.
    DESCONHECIDO("desconhecido");

    // Estados finais: o banco não aceita mais alteração neles (só do Netafim Admin ou do processo
    // automático), então a tela não deve oferecer nenhuma ação. DESCONHECIDO não é final: não dá para
    // afirmar que um status que o app não conhece encerra o atendimento.
    val ehFinal: Boolean
        get() = this == ENCERRADO || this == ENCERRADO_SEM_AVALIACAO || this == CANCELADO

    companion object {
        // Qualquer código que não seja um dos oito reais (inclusive o texto vazio) vira DESCONHECIDO, para o
        // registro continuar existindo e a tela decidir o que mostrar em vez de quebrar.
        fun deCodigo(codigo: String): StatusAtendimento = entries.firstOrNull { it.codigo == codigo } ?: DESCONHECIDO
    }
}
