package com.example.netafim.data.repository

import com.example.netafim.data.sync.SyncConflictDao
import com.example.netafim.data.sync.SyncConflictEntity

class SyncConflictRepository(
    private val dao: SyncConflictDao
) {

    suspend fun registrarConflito(
        conflito: SyncConflictEntity
    ) {
        require(conflito.usuarioDonoId > 0) {
            "O ID do usuário proprietário deve ser válido."
        }

        require(conflito.idConflito.isNotBlank()) {
            "O ID do conflito não pode ser vazio."
        }

        require(conflito.idAtendimento.isNotBlank()) {
            "O ID do atendimento não pode ser vazio."
        }

        require(conflito.versaoLocal >= 0 && conflito.versaoServidor >= 0) {
            "As versões do conflito não podem ser negativas."
        }

        dao.inserir(conflito)
    }

    suspend fun buscarConflito(
        usuarioDonoId: Int,
        idConflito: String
    ): SyncConflictEntity? {
        validarUsuario(usuarioDonoId)

        return dao.buscarPorId(usuarioDonoId, idConflito)
    }

    suspend fun listarConflitosPorAtendimento(
        usuarioDonoId: Int,
        idAtendimento: String
    ): List<SyncConflictEntity> {
        validarUsuario(usuarioDonoId)

        return dao.listarPorAtendimento(
            usuarioDonoId,
            idAtendimento
        )
    }

    suspend fun removerConflito(
        usuarioDonoId: Int,
        idConflito: String
    ): Boolean {
        validarUsuario(usuarioDonoId)

        return dao.removerPorId(
            usuarioDonoId,
            idConflito
        ) > 0
    }

    suspend fun removerConflitosDoAtendimento(
        usuarioDonoId: Int,
        idAtendimento: String
    ): Int {
        validarUsuario(usuarioDonoId)

        return dao.removerPorAtendimento(
            usuarioDonoId,
            idAtendimento
        )
    }

    private fun validarUsuario(usuarioDonoId: Int) {
        require(usuarioDonoId > 0) {
            "O ID do usuário proprietário deve ser válido."
        }
    }
}
