package com.example.netafim.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
// HistoricoDao é uma interface que define as operações de acesso a dados para a entidade HistoricoEntity no banco de dados local. Ela utiliza anotações do Room para mapear métodos para consultas SQL, permitindo inserir, listar, buscar e excluir registros de histórico relacionados a atendimentos específicos.
interface HistoricoDao {

    // O método inserir é responsável por adicionar um novo registro de histórico no banco de dados. Ele utiliza a anotação @Insert com a estratégia de conflito IGNORE, o que significa que, se houver um conflito (por exemplo, uma chave primária duplicada), a operação será ignorada e não causará erro. O método é suspenso, permitindo que seja chamado dentro de uma corrotina, e retorna o ID do registro inserido como Long.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun inserir(evento: HistoricoEntity): Long

    // O método inserirTodos é responsável por adicionar uma lista de registros de histórico no banco de dados. Ele também utiliza a anotação @Insert com a estratégia de conflito IGNORE, permitindo que múltiplos registros sejam inseridos de uma vez. O método é suspenso e retorna uma lista de IDs (Long) correspondentes aos registros inseridos, onde cada ID representa o registro inserido ou -1 se a inserção foi ignorada devido a um conflito.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun inserirTodos(eventos: List<HistoricoEntity>): List<Long>

    // Query para listar todos os registros de histórico associados a um atendimento específico, ordenados por data do evento e ID do histórico. O método listarPorAtendimento recebe o ID do usuário dono e o ID do atendimento como parâmetros, retornando uma lista de objetos HistoricoEntity correspondentes aos registros encontrados.
    @Query("""
        SELECT * FROM HISTORICO_LOCAL
        WHERE usuarioDonoId = :usuarioDonoId
        AND idAtendimento = :idAtendimento
        ORDER BY dataEvento ASC, idHistorico ASC
    """)
    // O método listarPorAtendimento é suspenso, permitindo que seja chamado dentro de uma corrotina, e retorna uma lista de objetos HistoricoEntity que representam os registros de histórico encontrados para o atendimento especificado.
    suspend fun listarPorAtendimento(
        usuarioDonoId: Int,
        idAtendimento: String
    ): List<HistoricoEntity>

    // Query para buscar um registro de histórico específico pelo ID do usuário dono e pelo ID do histórico. O método buscarPorId recebe o ID do usuário dono e o ID do histórico como parâmetros, retornando um objeto HistoricoEntity correspondente ao registro encontrado ou null se não houver correspondência.
    @Query("""
        SELECT * FROM HISTORICO_LOCAL
        WHERE usuarioDonoId = :usuarioDonoId
        AND idHistorico = :idHistorico
        LIMIT 1
    """)
    // O método buscarPorId é suspenso, permitindo que seja chamado dentro de uma corrotina, e retorna um objeto HistoricoEntity correspondente ao registro encontrado ou null se não houver correspondência.
    suspend fun buscarPorId(
        usuarioDonoId: Int,
        idHistorico: Long
    ): HistoricoEntity?

    // Query para excluir registros de histórico associados a um atendimento específico. O método excluirPorAtendimento recebe o ID do usuário dono e o ID do atendimento como parâmetros, removendo todos os registros correspondentes do banco de dados. Ele retorna um inteiro representando o número de registros excluídos.
    @Query("""
        DELETE FROM HISTORICO_LOCAL
        WHERE usuarioDonoId = :usuarioDonoId
        AND idAtendimento = :idAtendimento
    """)
    // O método excluirPorAtendimento é suspenso, permitindo que seja chamado dentro de uma corrotina, e retorna um inteiro representando o número de registros excluídos do banco de dados.
    suspend fun excluirPorAtendimento(
        usuarioDonoId: Int,
        idAtendimento: String
    ): Int
}
