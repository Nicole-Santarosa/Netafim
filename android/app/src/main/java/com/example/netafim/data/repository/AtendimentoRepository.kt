package com.example.netafim.data.repository

import com.example.netafim.data.local.AtendimentoDao
import com.example.netafim.data.local.AtendimentoEntity
import kotlinx.coroutines.flow.Flow

// Camada intermediária entre o aplicativo e o banco de dados local.
class AtendimentoRepository(
    private val atendimentoDao: AtendimentoDao
) {

    // ---------------------------------------------------------
    // OPERAÇÕES LOCAIS
    // ---------------------------------------------------------

    // Insere um atendimento criado localmente.
    // O proprietário informado deve corresponder ao da entidade.
    suspend fun salvarAtendimento(
        usuarioDonoId: Int,
        atendimento: AtendimentoEntity
    ) {
        exigirProprietarioCorreto(usuarioDonoId, atendimento)

        atendimentoDao.salvarLocalmente(atendimento)
    }

    // ---------------------------------------------------------
    // CONSULTAS
    // ---------------------------------------------------------

    // Observa os atendimentos pertencentes ao usuário informado.
    fun observarAtendimentos(
        usuarioDonoId: Int
    ): Flow<List<AtendimentoEntity>> {
        return atendimentoDao.listarTodos(usuarioDonoId)
    }

    // Busca um atendimento específico do usuário.
    suspend fun buscarAtendimento(
        usuarioDonoId: Int,
        idAtendimento: String
    ): AtendimentoEntity? {
        return atendimentoDao.buscarPorId(
            usuarioDonoId = usuarioDonoId,
            id = idAtendimento
        )
    }

    // Observa um atendimento específico e suas alterações.
    fun observarAtendimento(
        usuarioDonoId: Int,
        idAtendimento: String
    ): Flow<AtendimentoEntity?> {
        return atendimentoDao.observarPorId(
            usuarioDonoId = usuarioDonoId,
            id = idAtendimento
        )
    }

    // ---------------------------------------------------------
    // DADOS RECEBIDOS DO SERVIDOR
    // ---------------------------------------------------------

    // Insere um atendimento recebido do servidor somente se
    // ainda não existir para aquele usuário.
    suspend fun inserirAtendimentoDoServidor(
        usuarioDonoId: Int,
        atendimento: AtendimentoEntity
    ): Long {
        exigirProprietarioCorreto(usuarioDonoId, atendimento)

        return atendimentoDao.inserirSeNaoExistir(atendimento)
    }

    // Atualiza um atendimento somente se ele estiver sincronizado
    // e a versão recebida for mais recente.
    suspend fun atualizarAtendimentoDoServidor(
        usuarioDonoId: Int,
        atendimento: AtendimentoEntity
    ): Int {
        exigirProprietarioCorreto(usuarioDonoId, atendimento)

        return atendimentoDao.atualizarSeSincronizado(
            usuarioDonoId = usuarioDonoId,
            idAtendimento = atendimento.idAtendimento,
            numeroProtocolo = atendimento.numeroProtocolo,
            dadosJson = atendimento.dadosJson,
            origemRegistro = atendimento.origemRegistro,
            versaoBase = atendimento.versaoBase,
            syncSeqServidor = atendimento.syncSeqServidor,
            dataCriacaoLocal = atendimento.dataCriacaoLocal
        )
    }

    // ---------------------------------------------------------
    // EXCLUSÕES
    // ---------------------------------------------------------

    // Remove somente um atendimento pertencente ao usuário.
    suspend fun excluirAtendimento(
        usuarioDonoId: Int,
        idAtendimento: String
    ): Int {
        return atendimentoDao.excluirPorId(
            usuarioDonoId = usuarioDonoId,
            idAtendimento = idAtendimento
        )
    }

    // Remove todos os atendimentos locais do usuário informado.
    suspend fun excluirAtendimentosDoUsuario(
        usuarioDonoId: Int
    ): Int {
        return atendimentoDao.excluirTodosDoUsuario(usuarioDonoId)
    }

    // ---------------------------------------------------------
    // VALIDAÇÃO DE PROPRIEDADE
    // ---------------------------------------------------------

    // Impede que uma operação seja executada usando o ID
    // de um usuário diferente daquele registrado na entidade.
    private fun exigirProprietarioCorreto(
        usuarioDonoId: Int,
        atendimento: AtendimentoEntity
    ) {
        require(usuarioDonoId > 0) {
            "O ID do usuário proprietário deve ser válido."
        }

        require(atendimento.usuarioDonoId == usuarioDonoId) {
            "O atendimento não pertence ao usuário informado."
        }
    }
}
