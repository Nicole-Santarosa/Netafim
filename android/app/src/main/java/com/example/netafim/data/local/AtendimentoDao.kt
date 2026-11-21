package com.example.netafim.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

//O DAO é a camada responsável pelas operações no banco.
@Dao
interface AtendimentoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvar(atendimento: AtendimentoEntity) //Insere ou substitui um registro com a mesma chave primária.

    @Query("SELECT * FROM ATENDIMENTO_LOCAL")
    fun listarTodos(): Flow<List<AtendimentoEntity>> //Observa os atendimentos e emite uma nova lista quando os dados mudam.

    @Query(
        "SELECT * FROM ATENDIMENTO_LOCAL " +
                "WHERE idAtendimento = :id"
    )
    suspend fun buscarPorId(id: String): AtendimentoEntity? //Busca um atendimento específico.

    @Query("DELETE FROM ATENDIMENTO_LOCAL")
    suspend fun apagarTodos() //Remove todos os registros; incluímos para testes, mas não devemos expor essa operação na funcionalidade normal do aplicativo.
}