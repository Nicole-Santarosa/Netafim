package com.example.netafim.data.repository

class RelatorioRepositorioImpl : RelatorioRepositorio {
    override suspend fun salvarRelatorio(
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
        // Implementação temporária local até a integração concreta com Room (Pessoa 4).
        // Relatórios ficam fora da sincronização v1 de atendimentos.
        return true
    }
}