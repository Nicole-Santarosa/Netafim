package com.example.netafim.core.modelo

// Situação de sincronização de um registro local. Vem do statusLocal que o Room guarda, mas a tela só
// conhece este tipo: nenhum campo do Room aparece no modelo.
enum class SituacaoDeSincronizacao {
    // O que está no aparelho é igual ao que o servidor confirmou.
    SINCRONIZADO,

    // Foi salvo no aparelho e ainda espera o envio.
    PENDENTE_DE_ENVIO,

    // O servidor tem uma versão mais nova e a alteração local não foi aplicada. Nada é descartado.
    CONFLITO,

    // O servidor recusou o envio; a mensagem fica em Atendimento.erroDeEnvio.
    ERRO,
}

// De onde o registro nasceu: neste aparelho ou no servidor. Não confundir com OrigemAtendimento, que é o
// canal do atendimento (Netafim, distribuidor ou cliente final) e existe nos dois casos.
enum class OrigemDoRegistro {
    LOCAL,
    SERVIDOR,
}
