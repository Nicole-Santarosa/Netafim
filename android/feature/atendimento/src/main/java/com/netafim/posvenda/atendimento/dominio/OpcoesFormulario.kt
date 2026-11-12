package com.netafim.posvenda.atendimento.dominio

// Cultura do catálogo (GET /culturas). A lista vem do servidor, por isso a tela a recebe pronta.
data class OpcaoCultura(val id: Int, val nome: String)

// Distribuidor ou revenda que o cliente pode escolher na abertura. A lista também vem do servidor.
data class OpcaoDistribuidor(val id: Int, val nome: String)
