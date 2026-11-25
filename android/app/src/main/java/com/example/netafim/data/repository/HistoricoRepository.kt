package com.example.netafim.data.repository

import com.example.netafim.data.local.HistoricoDao
import com.example.netafim.data.local.HistoricoEntity

// Repository para acesso aos dados do histórico
class HistoricoRepository(
    private val historicoDao: HistoricoDao // DAO para operações de banco de dados relacionadas ao histórico
) {

    // Salva um evento no banco de dados e retorna o ID gerado
    suspend fun salvarEvento(evento: HistoricoEntity): Long {
        require(evento.usuarioDonoId > 0) {
            "O ID do usuário deve ser válido."
        }

        require(evento.idAtendimento.isNotBlank()) {
            "O ID do atendimento não pode estar vazio."
        }

        require(evento.idHistorico > 0) {
            "O ID do histórico deve ser válido."
        }

        return historicoDao.inserir(evento)
    }

    // Salva uma lista de eventos no banco de dados e retorna uma lista de IDs gerados
    suspend fun salvarEventos(eventos: List<HistoricoEntity>): List<Long> {
        require(eventos.all { it.usuarioDonoId > 0 }) {
            "Todos os eventos devem possuir um usuário válido."
        }

        require(eventos.all { it.idAtendimento.isNotBlank() }) {
            "Todos os eventos devem possuir um ID de atendimento válido."
        }

        require(eventos.all { it.idHistorico > 0 }) {
            "Todos os eventos devem possuir um ID de histórico válido."
        }

        return historicoDao.inserirTodos(eventos)
    }

    // Lista os registros de histórico associados a um atendimento específico
    suspend fun listarHistorico(
        usuarioDonoId: Int,
        idAtendimento: String
    ): List<HistoricoEntity> {
        require(usuarioDonoId > 0) {
            "O ID do usuário deve ser válido."
        }

        require(idAtendimento.isNotBlank()) {
            "O ID do atendimento não pode estar vazio."
        }

        // Retorna a lista de registros de histórico do atendimento específico
        return historicoDao.listarPorAtendimento(
            usuarioDonoId,
            idAtendimento
        )
    }

    // Busca um evento específico no histórico com base no ID do usuário e no ID do histórico
    suspend fun buscarEvento(
        usuarioDonoId: Int,
        idHistorico: Long
    ): HistoricoEntity? {
        require(usuarioDonoId > 0) {
            "O ID do usuário deve ser válido."
        }

        require(idHistorico > 0) {
            "O ID do histórico deve ser válido."
        }

        // Retorna o evento específico do histórico
        return historicoDao.buscarPorId(
            usuarioDonoId,
            idHistorico
        )
    }

    // Exclui todos os registros de histórico associados a um atendimento específico
    suspend fun excluirHistoricoDoAtendimento(
        usuarioDonoId: Int,
        idAtendimento: String
    ): Int {
        require(usuarioDonoId > 0) {
            "O ID do usuário deve ser válido."
        }

        require(idAtendimento.isNotBlank()) {
            "O ID do atendimento não pode estar vazio."
        }

        // Exclui os registros de histórico do atendimento específico e retorna o número de registros excluídos
        return historicoDao.excluirPorAtendimento(
            usuarioDonoId,
            idAtendimento
        )
    }
}
