package com.netafim.posvenda.atendimento.dominio

// O que a tela de detalhe do distribuidor mostra de um atendimento da região. O id e o protocolo não
// aparecem nesta tela (o protótipo não mostra o número ao distribuidor), mas ficam no objeto para o
// chamador identificar o atendimento. O local é opcional: pode ter sido aberto sem cidade nem fazenda.
data class DetalheDoDistribuidor(
    val id: String,
    val protocolo: String,
    val produtor: String,
    val local: String?,
    val motivo: MotivoAtendimento,
    val status: StatusAtendimento,
)
