package com.example.netafim.data.repository

// Importa o DAO responsável pelas operações no banco local.
import com.example.netafim.data.local.AtendimentoDao

// Importa a entidade que representa um atendimento.
import com.example.netafim.data.local.AtendimentoEntity

// Permite observar alterações nos dados do banco continuamente.
import kotlinx.coroutines.flow.Flow

// Repository: camada intermediária entre o aplicativo e o banco de dados.
class AtendimentoRepository(
    // Recebe o DAO por meio do construtor.
    // Assim, o Repository não precisa criar o banco diretamente.
    private val atendimentoDao: AtendimentoDao
) {

    // Salva um atendimento no banco de dados local.
    suspend fun salvarAtendimento(atendimento: AtendimentoEntity) {

        // Encaminha a operação de gravação para o DAO.
        atendimentoDao.salvar(atendimento)
    }

    // Observa todos os atendimentos armazenados localmente.
    fun observarAtendimentos(): Flow<List<AtendimentoEntity>> {

        // Delega ao DAO a consulta que acompanha alterações no banco.
        return atendimentoDao.listarTodos()
    }

    // Busca um atendimento específico utilizando seu UUID.
    suspend fun buscarAtendimento(id: String): AtendimentoEntity? {

        // Solicita ao DAO o atendimento correspondente ao identificador.
        return atendimentoDao.buscarPorId(id)
    }
}