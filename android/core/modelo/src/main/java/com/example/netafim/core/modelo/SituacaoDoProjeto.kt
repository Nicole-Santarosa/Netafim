package com.example.netafim.core.modelo

// Situação do projeto do atendimento. Os códigos são os do contrato da API (schema SituacaoProjeto)
// e os mesmos do CHECK do banco. Rótulos ficam na camada de interface.
enum class SituacaoDoProjeto(val codigo: String) {
    GARANTIA("garantia"),
    FORA_GARANTIA("fora_garantia"),
    INSTALACAO("instalacao"),

    // O servidor mandou uma situação que esta versão do app não conhece. O registro não pode ser descartado
    // por isso: ele é guardado com este valor e o código original continua no dadosJson do Room.
    // O código textual abaixo é só um nome interno e nunca é enviado à API.
    DESCONHECIDO("desconhecido");

    companion object {
        // Código fora dos três reais (inclusive o texto vazio) vira DESCONHECIDO. Atendimento sem situação
        // gravada tem situação nula, e esse nulo é tratado por quem chama, antes de usar este método.
        fun deCodigo(codigo: String): SituacaoDoProjeto = entries.firstOrNull { it.codigo == codigo } ?: DESCONHECIDO
    }
}
