package com.example.netafim.data.repository

class RelatorioRepositorioImpl : RelatorioRepositorio {
    override suspend fun salvarRascunho(
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
    ): Boolean {
        // Ponto de integração com a persistência Room (Pessoa 4 -)
        return true
    }
}