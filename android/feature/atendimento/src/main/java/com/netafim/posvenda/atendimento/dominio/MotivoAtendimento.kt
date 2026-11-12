package com.netafim.posvenda.atendimento.dominio

// Motivo do atendimento. Os códigos são os do contrato da API (schema Motivo).
enum class MotivoAtendimento(val codigo: String) {
    DIGITAL_FARMING("digital_farming"),
    DUVIDAS("duvidas"),
    ENTREGA_TECNICA("entrega_tecnica"),
    POS_VENDA("pos_venda"),
    PROGRAMACAO_FEIRAS("programacao_feiras"),
    TREINAMENTO("treinamento");

    companion object {
        // Motivos que o cliente pode escolher, na ordem do protótipo. Programação de feiras fica
        // de fora: não aparece na abertura feita pelo cliente.
        val motivosDoCliente: List<MotivoAtendimento> = listOf(
            DIGITAL_FARMING,
            DUVIDAS,
            ENTREGA_TECNICA,
            POS_VENDA,
            TREINAMENTO,
        )

        fun deCodigo(codigo: String): MotivoAtendimento? = entries.firstOrNull { it.codigo == codigo }
    }
}

// Tipo de venda, só usado no motivo Digital Farming. Códigos do contrato (schema TipoVenda).
enum class TipoVenda(val codigo: String) {
    PROJETO("projeto"),
    VENDA_REGULAR("venda_regular"),
}
