package com.example.netafim.core.modelo

// Cultura do catálogo (GET /culturas). A lista fica guardada no aparelho para a abertura funcionar offline.
data class Cultura(val id: Int, val nome: String)

// Distribuidor ou revenda que o cliente pode escolher na abertura (GET /distribuidores). O repositório só
// entrega os ativos.
data class Distribuidor(val id: Int, val nome: String)
