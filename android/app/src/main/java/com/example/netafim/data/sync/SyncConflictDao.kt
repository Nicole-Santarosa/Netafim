package com.example.netafim.data.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SyncConflictDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun inserir(conflito: SyncConflictEntity)

    @Query("""
        SELECT * FROM CONFLITO_SYNC
        WHERE usuarioDonoId = :usuarioDonoId
        AND idConflito = :idConflito
        LIMIT 1
    """)
    suspend fun buscarPorId(
        usuarioDonoId: Int,
        idConflito: String
    ): SyncConflictEntity?

    @Query("""
        SELECT * FROM CONFLITO_SYNC
        WHERE usuarioDonoId = :usuarioDonoId
        AND idAtendimento = :idAtendimento
        ORDER BY dataIdentificacao DESC
    """)
    suspend fun listarPorAtendimento(
        usuarioDonoId: Int,
        idAtendimento: String
    ): List<SyncConflictEntity>

    @Query("""
        DELETE FROM CONFLITO_SYNC
        WHERE usuarioDonoId = :usuarioDonoId
        AND idConflito = :idConflito
    """)
    suspend fun removerPorId(
        usuarioDonoId: Int,
        idConflito: String
    ): Int

    @Query("""
        DELETE FROM CONFLITO_SYNC
        WHERE usuarioDonoId = :usuarioDonoId
        AND idAtendimento = :idAtendimento
    """)
    suspend fun removerPorAtendimento(
        usuarioDonoId: Int,
        idAtendimento: String
    ): Int
}
