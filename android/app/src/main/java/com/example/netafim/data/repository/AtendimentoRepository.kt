package com.example.netafim.data.repository

import androidx.room.withTransaction
import com.example.netafim.data.local.NetafimDatabase
import com.example.netafim.data.local.AtendimentoEntity
import com.example.netafim.data.local.SyncStatus
import com.example.netafim.data.local.OrigemRegistro
import com.example.netafim.data.sync.SyncOperationEntity
import com.example.netafim.data.sync.SyncOperationStatus
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

// Camada intermediária entre o aplicativo e o banco de dados local.
class AtendimentoRepository(
    // O banco de dados local do aplicativo.
    private val banco: NetafimDatabase
) {

    private val atendimentoDao = banco.atendimentoDao() // O DAO para operações de atendimento.

    private val operacaoDao = banco.syncOperationDao() // O DAO para operações de sincronização.

    // ---------------------------------------------------------
    // OPERAÇÕES LOCAIS
    // ---------------------------------------------------------

    // Insere o atendimento e sua operação de sincronização dentro de uma única transação Room.
    suspend fun salvarAtendimento(
        usuarioDonoId: Int,
        atendimento: AtendimentoEntity
    ) {
        exigirProprietarioCorreto(usuarioDonoId, atendimento) // Verifica se o atendimento pertence ao usuário.

        // Cria uma cópia do atendimento com status de pendência de envio.
        val atendimentoPendente = atendimento.copy(
            origemRegistro = OrigemRegistro.LOCAL,
            statusLocal = SyncStatus.PENDENTE_ENVIO
        )

        // Cria uma operação de sincronização para o atendimento.
        val operacao = SyncOperationEntity(
            idOperacao = UUID.randomUUID().toString(),
            usuarioDonoId = usuarioDonoId,
            entidade = "atendimento",
            id = atendimento.idAtendimento,
            tipo = "criar",
            versaoBase = null,
            campos = atendimentoPendente.dadosJson,
            feitoEm = dataAtualIso8601(),
            estado = SyncOperationStatus.PENDENTE
        )

        // Salva o atendimento e a operação de sincronização dentro de uma transação para garantir consistência.
        banco.withTransaction {
            atendimentoDao.salvarLocalmente(atendimentoPendente)
            operacaoDao.inserir(operacao)
        }
    }

    // ---------------------------------------------------------
    // CONSULTAS
    // ---------------------------------------------------------

    // Retorna um Flow que emite a lista de atendimentos do usuário proprietário.
    fun observarAtendimentos(
        usuarioDonoId: Int
    ): Flow<List<AtendimentoEntity>> {
        return atendimentoDao.listarTodos(usuarioDonoId)
    }

    // Retorna o atendimento específico do usuário proprietário, se existir.
    suspend fun buscarAtendimento(
        usuarioDonoId: Int,
        idAtendimento: String
    ): AtendimentoEntity? {
        return atendimentoDao.buscarPorId(
            usuarioDonoId = usuarioDonoId,
            id = idAtendimento
        )
    }

    // Retorna o atendimento específico do usuário proprietário, se existir.
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

    // Insere o atendimento recebido do servidor, garantindo que ele pertença ao usuário correto.
    suspend fun inserirAtendimentoDoServidor(
        usuarioDonoId: Int,
        atendimento: AtendimentoEntity
    ): Long {
        exigirProprietarioCorreto(usuarioDonoId, atendimento)

        return atendimentoDao.inserirSeNaoExistir(atendimento) // Insere o atendimento apenas se ele não existir, evitando duplicatas.
    }

    // Atualiza o atendimento recebido do servidor, garantindo que ele pertença ao usuário correto.
    suspend fun atualizarAtendimentoDoServidor(
        usuarioDonoId: Int,
        atendimento: AtendimentoEntity
    ): Int {
        exigirProprietarioCorreto(usuarioDonoId, atendimento)

        // Atualiza o atendimento no banco de dados local com os dados recebidos do servidor.
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
    // EXCLUSÕES CONTROLADAS
    // ---------------------------------------------------------

    // Exclui um atendimento somente quando ele está sincronizado,
    // não possui operação pendente e não possui conflito.
    suspend fun excluirAtendimento(
        usuarioDonoId: Int,
        idAtendimento: String
    ): Int {

        require(usuarioDonoId > 0) {
            "O ID do usuário proprietário deve ser válido."
        }

        return banco.withTransaction {

            val atendimento = atendimentoDao.buscarPorId(
                usuarioDonoId = usuarioDonoId,
                id = idAtendimento
            ) ?: return@withTransaction 0

            check(atendimento.statusLocal == SyncStatus.SINCRONIZADO) {
                "Não é possível excluir: o atendimento ainda não está sincronizado."
            }

            val operacoesPendentes = operacaoDao.contarOperacoesDoAtendimento(
                usuarioDonoId = usuarioDonoId,
                idAtendimento = idAtendimento
            )

            check(operacoesPendentes == 0) {
                "Não é possível excluir: existe uma operação de sincronização associada."
            }

            val conflitos = banco.syncConflictDao()
                .contarConflitosDoAtendimento(
                    usuarioDonoId = usuarioDonoId,
                    idAtendimento = idAtendimento
                )

            check(conflitos == 0) {
                "Não é possível excluir: o atendimento possui conflito de sincronização."
            }

            atendimentoDao.excluirPorId(
                usuarioDonoId = usuarioDonoId,
                idAtendimento = idAtendimento
            )
        }
    }

    // Exclui todos os atendimentos somente quando todos estiverem sincronizados e não houver operações ou conflitos associados.
    suspend fun excluirAtendimentosDoUsuario(
        usuarioDonoId: Int
    ): Int {

        // Valida se o ID do usuário proprietário é válido.
        require(usuarioDonoId > 0) {
            "O ID do usuário proprietário deve ser válido."
        }

        return banco.withTransaction { // Inicia uma transação para garantir consistência durante a exclusão.

            // Verifica se existem atendimentos não sincronizados para o usuário.
            val naoSincronizados =
                atendimentoDao.contarAtendimentosNaoSincronizados(usuarioDonoId)

            // Se houver atendimentos não sincronizados, lança uma exceção com uma mensagem apropriada.
            check(naoSincronizados == 0) {
                "Não é possível excluir todos: existem atendimentos não sincronizados."
            }

            // Verifica se existem operações de sincronização pendentes para o usuário.
            val operacoes =
                operacaoDao.contarOperacoesDeAtendimentoDoUsuario(usuarioDonoId)

            // Se houver operações pendentes, lança uma exceção com uma mensagem apropriada.
            check(operacoes == 0) {
                "Não é possível excluir todos: existem operações de sincronização pendentes."
            }

            // Verifica se existem conflitos de sincronização para o usuário.
            val conflitos = banco.syncConflictDao()
                .contarConflitosDoUsuario(usuarioDonoId)

            // Se houver conflitos, lança uma exceção com uma mensagem apropriada.
            check(conflitos == 0) {
                "Não é possível excluir todos: existem conflitos de sincronização."
            }

            atendimentoDao.excluirTodosDoUsuario(usuarioDonoId) // Exclui todos os atendimentos do usuário, já que todas as condições foram atendidas.
        }
    }

    // ---------------------------------------------------------
    // VALIDAÇÃO DE PROPRIEDADE
    // ---------------------------------------------------------

    // Verifica se o atendimento pertence ao usuário proprietário correto, lançando uma exceção se não for o caso.
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

    // Data e hora em formato ISO 8601, em UTC.
    private fun dataAtualIso8601(): String {
        return SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            Locale.US
        ).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
    }
}
