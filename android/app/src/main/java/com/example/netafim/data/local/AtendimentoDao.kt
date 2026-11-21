package com.example.netafim.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// Camada responsável pelas operações de acesso aos atendimentos locais.
@Dao
interface AtendimentoDao {

    // ---------------------------------------------------------
    // GRAVAÇÕES REALIZADAS LOCALMENTE
    // ---------------------------------------------------------

    // Salva um atendimento criado ou editado pelo usuário.
    // A chave composta impede afetar a cópia de outro usuário.
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun salvarLocalmente(atendimento: AtendimentoEntity)

    // ---------------------------------------------------------
    // CONSULTAS
    // ---------------------------------------------------------

    // Observa somente os atendimentos pertencentes ao usuário informado.
    @Query("""
        SELECT * FROM ATENDIMENTO_LOCAL
        WHERE usuarioDonoId = :usuarioDonoId
        ORDER BY dataCriacaoLocal DESC
    """)
    fun listarTodos(usuarioDonoId: Int): Flow<List<AtendimentoEntity>>

    // Busca um atendimento específico dentro da cópia do usuário.
    @Query("""
        SELECT * FROM ATENDIMENTO_LOCAL
        WHERE usuarioDonoId = :usuarioDonoId
        AND idAtendimento = :id
        LIMIT 1
    """)
    suspend fun buscarPorId(
        usuarioDonoId: Int,
        id: String
    ): AtendimentoEntity?

    // Observa um atendimento específico do usuário.
    @Query("""
        SELECT * FROM ATENDIMENTO_LOCAL
        WHERE usuarioDonoId = :usuarioDonoId
        AND idAtendimento = :id
        LIMIT 1
    """)
    fun observarPorId(
        usuarioDonoId: Int,
        id: String
    ): Flow<AtendimentoEntity?>

    // ---------------------------------------------------------
    // DADOS RECEBIDOS DO SERVIDOR
    // ---------------------------------------------------------

    // Insere um atendimento recebido do servidor somente se ele
    // ainda não existir para aquele usuário.
    // IGNORE evita substituir uma edição local existente.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun inserirSeNaoExistir(atendimento: AtendimentoEntity): Long

    // Atualiza os dados recebidos do servidor somente quando:
    // 1. O atendimento pertence ao usuário informado.
    // 2. O registro local está sincronizado.
    // 3. A versão recebida é mais recente.
    //
    // Registros pendentes, em conflito ou com erro não são sobrescritos.
    @Query("""
        UPDATE ATENDIMENTO_LOCAL
        SET
            numeroProtocolo = :numeroProtocolo,
            dadosJson = :dadosJson,
            origemRegistro = :origemRegistro,
            statusLocal = 'sincronizado',
            versaoBase = :versaoBase,
            syncSeqServidor = :syncSeqServidor,
            dataCriacaoLocal = :dataCriacaoLocal
        WHERE usuarioDonoId = :usuarioDonoId
        AND idAtendimento = :idAtendimento
        AND statusLocal = 'sincronizado'
        AND versaoBase < :versaoBase
    """)
    suspend fun atualizarSeSincronizado(
        usuarioDonoId: Int,
        idAtendimento: String,
        numeroProtocolo: String?,
        dadosJson: String,
        origemRegistro: String,
        versaoBase: Int,
        syncSeqServidor: Long?,
        dataCriacaoLocal: Long
    ): Int

    // ---------------------------------------------------------
    // EXCLUSÕES CONTROLADAS
    // ---------------------------------------------------------

    // Remove somente um atendimento pertencente ao usuário informado.
    @Query("""
        DELETE FROM ATENDIMENTO_LOCAL
        WHERE usuarioDonoId = :usuarioDonoId
        AND idAtendimento = :idAtendimento
    """)
    suspend fun excluirPorId(
        usuarioDonoId: Int,
        idAtendimento: String
    ): Int

    // Remove somente os atendimentos do usuário informado.
    @Query("""
        DELETE FROM ATENDIMENTO_LOCAL
        WHERE usuarioDonoId = :usuarioDonoId
    """)
    suspend fun excluirTodosDoUsuario(usuarioDonoId: Int): Int
}
