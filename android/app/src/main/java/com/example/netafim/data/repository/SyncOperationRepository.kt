package com.example.netafim.data.repository

import com.example.netafim.data.sync.SyncOperationDao
import com.example.netafim.data.sync.SyncOperationEntity

class SyncOperationRepository(
    private val dao: SyncOperationDao
) {

    suspend fun registrarOperacao(
        operacao: SyncOperationEntity
    ) {
        require(operacao.usuarioDonoId > 0) {
            "O ID do usuário proprietário deve ser válido."
        }

        require(operacao.idOperacao.isNotBlank()) {
            "O ID da operação não pode ser vazio."
        }

        require(operacao.id.isNotBlank()) {
            "O ID do registro relacionado não pode ser vazio."
        }

        dao.inserir(operacao)
    }

    suspend fun listarOperacoesPorEstado(
        usuarioDonoId: Int,
        estado: String
    ): List<SyncOperationEntity> {
        validarUsuario(usuarioDonoId)

        return dao.listarPorEstado(usuarioDonoId, estado)
    }

    suspend fun buscarOperacao(
        usuarioDonoId: Int,
        idOperacao: String
    ): SyncOperationEntity? {
        validarUsuario(usuarioDonoId)

        return dao.buscarPorId(usuarioDonoId, idOperacao)
    }

    suspend fun atualizarEstado(
        usuarioDonoId: Int,
        idOperacao: String,
        novoEstado: String
    ): Boolean {
        validarUsuario(usuarioDonoId)

        return dao.atualizarEstado(
            usuarioDonoId,
            idOperacao,
            novoEstado
        ) > 0
    }

    suspend fun incrementarTentativas(
        usuarioDonoId: Int,
        idOperacao: String
    ): Boolean {
        validarUsuario(usuarioDonoId)

        return dao.incrementarTentativas(
            usuarioDonoId,
            idOperacao
        ) > 0
    }

    suspend fun removerAposConfirmacaoPersistida(
        usuarioDonoId: Int,
        idOperacao: String
    ): Boolean {
        validarUsuario(usuarioDonoId)

        return dao.removerPorId(
            usuarioDonoId,
            idOperacao
        ) > 0
    }

    suspend fun contarOperacoes(
        usuarioDonoId: Int
    ): Int {
        validarUsuario(usuarioDonoId)

        return dao.contarOperacoes(usuarioDonoId)
    }

    private fun validarUsuario(usuarioDonoId: Int) {
        require(usuarioDonoId > 0) {
            "O ID do usuário proprietário deve ser válido."
        }
    }
}
