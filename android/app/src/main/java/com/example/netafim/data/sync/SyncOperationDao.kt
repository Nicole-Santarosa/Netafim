package com.example.netafim.data.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SyncOperationDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun inserir(operacao: SyncOperationEntity)

    @Query("""
        SELECT * FROM OPERACAO_SYNC
        WHERE usuarioDonoId = :usuarioDonoId
        AND estado = :estado
        ORDER BY rowid ASC
    """)
    suspend fun listarPorEstado(
        usuarioDonoId: Int,
        estado: String
    ): List<SyncOperationEntity>

    @Query("""
        SELECT * FROM OPERACAO_SYNC
        WHERE usuarioDonoId = :usuarioDonoId
        AND idOperacao = :idOperacao
        LIMIT 1
    """)
    suspend fun buscarPorId(
        usuarioDonoId: Int,
        idOperacao: String
    ): SyncOperationEntity?

    @Query("""
        UPDATE OPERACAO_SYNC
        SET estado = :novoEstado
        WHERE usuarioDonoId = :usuarioDonoId
        AND idOperacao = :idOperacao
    """)
    suspend fun atualizarEstado(
        usuarioDonoId: Int,
        idOperacao: String,
        novoEstado: String
    ): Int

    @Query("""
        UPDATE OPERACAO_SYNC
        SET tentativas = tentativas + 1
        WHERE usuarioDonoId = :usuarioDonoId
        AND idOperacao = :idOperacao
    """)
    suspend fun incrementarTentativas(
        usuarioDonoId: Int,
        idOperacao: String
    ): Int

    @Query("""
        DELETE FROM OPERACAO_SYNC
        WHERE usuarioDonoId = :usuarioDonoId
        AND idOperacao = :idOperacao
    """)
    suspend fun removerPorId(
        usuarioDonoId: Int,
        idOperacao: String
    ): Int

    @Query("""
        SELECT COUNT(*) FROM OPERACAO_SYNC
        WHERE usuarioDonoId = :usuarioDonoId
    """)
    suspend fun contarOperacoes(
        usuarioDonoId: Int
    ): Int

    // Verifica se existe operação de sincronização associada a um atendimento específico.
    @Query("""
        SELECT COUNT(*) FROM OPERACAO_SYNC
        WHERE usuarioDonoId = :usuarioDonoId
        AND entidade = 'atendimento'
        AND id = :idAtendimento
    """)
    suspend fun contarOperacoesDoAtendimento(
        usuarioDonoId: Int,
        idAtendimento: String
    ): Int

    // Conta todas as operações de atendimento do usuário.
    @Query("""
        SELECT COUNT(*) FROM OPERACAO_SYNC
        WHERE usuarioDonoId = :usuarioDonoId
        AND entidade = 'atendimento'
    """)
    suspend fun contarOperacoesDeAtendimentoDoUsuario(
        usuarioDonoId: Int
    ): Int
}
