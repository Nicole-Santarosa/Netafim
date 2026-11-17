package com.example.netafim.data.repository

import kotlinx.coroutines.flow.Flow

interface RelatorioRepositorio {
    suspend fun salvarRascunho(
        motivo: String,
        constatacoes: String,
        atividades: String,
        planoAcao: String,
        conclusao: String,
        fotos: List<String>,
        videos: List<String>,
        assinaturaCliente: String?,
        assinaturaEspecialista: String?
    )
}