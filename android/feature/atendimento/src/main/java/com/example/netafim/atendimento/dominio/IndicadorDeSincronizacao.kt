package com.example.netafim.atendimento.dominio

import com.example.netafim.core.modelo.SituacaoDeSincronizacao

// O que o cartão da lista mostra sobre a sincronização do registro. Só existem dois desenhos: um para "ainda
// vai ser enviado" e outro para "algo deu errado e precisa de atenção". Registro sincronizado não mostra nada,
// porque é o caso normal e um ícone em todo cartão viraria ruído.
enum class IndicadorDeSincronizacao {
    PENDENTE,
    ATENCAO,
}

// Escolhe o indicador de cada situação. O when cobre todos os valores sem else de propósito: se alguém criar
// uma situação nova no core, o compilador acusa aqui e obriga a decidir como ela aparece na lista.
fun SituacaoDeSincronizacao.comoIndicador(): IndicadorDeSincronizacao? = when (this) {
    SituacaoDeSincronizacao.SINCRONIZADO -> null
    SituacaoDeSincronizacao.PENDENTE_DE_ENVIO -> IndicadorDeSincronizacao.PENDENTE
    // Conflito e erro têm o mesmo desenho; a diferença fica no texto que o leitor de tela lê.
    SituacaoDeSincronizacao.CONFLITO -> IndicadorDeSincronizacao.ATENCAO
    SituacaoDeSincronizacao.ERRO -> IndicadorDeSincronizacao.ATENCAO
}
