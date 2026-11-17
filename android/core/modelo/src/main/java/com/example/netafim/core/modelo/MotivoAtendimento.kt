package com.example.netafim.core.modelo

// Motivo do atendimento. Os códigos são os do contrato da API (schema Motivo).
enum class MotivoAtendimento(val codigo: String) {
    DIGITAL_FARMING("digital_farming"),
    DUVIDAS("duvidas"),
    ENTREGA_TECNICA("entrega_tecnica"),
    POS_VENDA("pos_venda"),
    PROGRAMACAO_FEIRAS("programacao_feiras"),
    TREINAMENTO("treinamento"),

    // O servidor mandou um motivo que esta versão do app não conhece. O registro não pode ser descartado
    // por isso: ele é guardado com este valor e o código original continua no dadosJson do Room.
    // O código textual abaixo é só um nome interno e nunca é enviado à API.
    DESCONHECIDO("desconhecido");

    companion object {
        // Motivos que o cliente pode escolher, na ordem do protótipo. Programação de feiras fica
        // de fora: não aparece na abertura feita pelo cliente. DESCONHECIDO também fica de fora.
        val motivosDoCliente: List<MotivoAtendimento> = listOf(
            DIGITAL_FARMING,
            DUVIDAS,
            ENTREGA_TECNICA,
            POS_VENDA,
            TREINAMENTO,
        )

        // Código fora dos seis reais (inclusive o texto vazio) vira DESCONHECIDO.
        fun deCodigo(codigo: String): MotivoAtendimento = entries.firstOrNull { it.codigo == codigo } ?: DESCONHECIDO
    }
}

// Tipo de venda, só usado no motivo Digital Farming. Códigos do contrato (schema TipoVenda).
enum class TipoVenda(val codigo: String) {
    PROJETO("projeto"),
    VENDA_REGULAR("venda_regular"),

    // O servidor mandou um tipo de venda que esta versão do app não conhece. O registro é guardado com este
    // valor e o código original continua no dadosJson do Room. O código textual abaixo é só um nome interno
    // e nunca é enviado à API.
    DESCONHECIDO("desconhecido");

    companion object {
        // Código fora dos dois reais (inclusive o texto vazio) vira DESCONHECIDO. Venda sem tipo tem tipo nulo,
        // e esse nulo é tratado por quem chama, antes de usar este método.
        fun deCodigo(codigo: String): TipoVenda = entries.firstOrNull { it.codigo == codigo } ?: DESCONHECIDO
    }
}
