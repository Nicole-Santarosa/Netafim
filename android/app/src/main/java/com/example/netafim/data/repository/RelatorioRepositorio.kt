package com.example.netafim.data.repository

interface RelatorioRepositorio {
    suspend fun salvarRelatorio(
        motivo: String,
        constatacoes: String,
        atividades: String,
        planoAcao: String,
        conclusao: String,
        fotos: List<String>,
        videos: List<String>,
        anexos: List<String>,
        assinaturaCliente: String?,
        assinaturaEspecialista: String?
    ): Boolean
}