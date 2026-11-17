package com.example.netafim.core.modelo

// Origem do atendimento: o canal pelo qual a solicitação nasceu (quem abriu). Os códigos são os do contrato
// da API (schema Origem). Não confundir com OrigemDoRegistro, que diz se o registro foi criado neste aparelho
// ou veio do servidor.
enum class OrigemAtendimento(val codigo: String) {
    NETAFIM("netafim"),
    DISTRIBUIDOR("distribuidor"),
    CLIENTE_FINAL("cliente_final"),

    // O servidor mandou uma origem que esta versão do app não conhece. O registro não pode ser descartado
    // por isso: ele é guardado com este valor e o código original continua no dadosJson do Room.
    // O código textual abaixo é só um nome interno e nunca é enviado à API.
    DESCONHECIDO("desconhecido");

    companion object {
        // Qualquer código que não seja um dos três acima (inclusive o texto vazio) vira DESCONHECIDO.
        // Quem recebe um valor opcional trata o nulo do seu lado antes de chamar.
        fun deCodigo(codigo: String): OrigemAtendimento = entries.firstOrNull { it.codigo == codigo } ?: DESCONHECIDO
    }
}
