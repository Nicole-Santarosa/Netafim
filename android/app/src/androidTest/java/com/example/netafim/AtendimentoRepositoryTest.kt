package com.example.netafim

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.netafim.data.local.AtendimentoEntity
import com.example.netafim.data.local.NetafimDatabase
import com.example.netafim.data.local.OrigemRegistro
import com.example.netafim.data.local.SyncStatus
import com.example.netafim.data.repository.AtendimentoRepository
import com.example.netafim.data.sync.SyncOperationStatus
import com.example.netafim.data.sync.SyncOperationEntity
import com.example.netafim.data.sync.SyncConflictEntity
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows

// Indica que este teste será executado no ambiente Android.
@RunWith(AndroidJUnit4::class)
class AtendimentoRepositoryTest {

    // Contexto utilizado para criar o banco de teste.
    private lateinit var context: Context

    // Banco Room temporário utilizado exclusivamente neste teste.
    private lateinit var database: NetafimDatabase

    // Camada responsável por intermediar o acesso ao banco.
    private lateinit var repository: AtendimentoRepository

    // Prepara o banco e o Repository antes de cada teste.
    @Before
    fun configurar() {
        context = ApplicationProvider.getApplicationContext()

        // Cria um banco em memória, sem persistir dados no dispositivo.
        database = Room.inMemoryDatabaseBuilder(
            context,
            NetafimDatabase::class.java
        ).allowMainThreadQueries().build()

        // Injeta o banco de dados no Repository.
        repository = AtendimentoRepository(
            banco = database
        )
    }

    // Cria um atendimento sincronizado para testes.
    private fun criarAtendimentoSincronizado(
        usuarioDonoId: Int,
        idAtendimento: String = UUID.randomUUID().toString()
    ): AtendimentoEntity {
        return AtendimentoEntity(
            usuarioDonoId = usuarioDonoId,
            idAtendimento = idAtendimento,
            numeroProtocolo = "OS-TESTE",
            dadosJson = """{"descricao":"Atendimento sincronizado"}""",
            origemRegistro = OrigemRegistro.SERVIDOR,
            statusLocal = SyncStatus.SINCRONIZADO,
            versaoBase = 1,
            syncSeqServidor = 1L,
            dataCriacaoLocal = System.currentTimeMillis()
        )
    }

    // Verifica se o Repository consegue salvar e recuperar um atendimento.
    @Test
    fun deveSalvarEBuscarAtendimento() = runBlocking {

        // Gera um identificador único para o atendimento.
        val id = UUID.randomUUID().toString()

        val usuarioDonoId = 1 // ID do usuário proprietário do atendimento.

        // Cria um atendimento fictício para o teste.
        val atendimento = AtendimentoEntity(
            usuarioDonoId = usuarioDonoId,
            idAtendimento = id,
            numeroProtocolo = null,
            dadosJson = """{"descricao":"Teste do Repository"}""",
            origemRegistro = OrigemRegistro.LOCAL, // Atendimento criado no próprio dispositivo.
            statusLocal = SyncStatus.PENDENTE_ENVIO, // Atendimento aguardando sincronização com o servidor.
            versaoBase = 0,
            syncSeqServidor = null,
            dataCriacaoLocal = System
                .currentTimeMillis()
        )

        // Solicita ao Repository que salve o atendimento.
        repository.salvarAtendimento(
            usuarioDonoId = usuarioDonoId,
            atendimento = atendimento
        )

        // Busca as operações pendentes criadas na fila de sincronização.
        val operacoes = database.syncOperationDao().listarPorEstado(
            usuarioDonoId = usuarioDonoId,
            estado = SyncOperationStatus.PENDENTE
        )

        // Verifica se foi criada exatamente uma operação pendente.
        assertEquals(1, operacoes.size)

        // Recupera a operação criada para o atendimento.
        val operacao = operacoes.first()

        // Verifica se o identificador da operação foi gerado.
        assertNotNull(operacao.idOperacao)
        assertEquals(false, operacao.idOperacao.isBlank())

        // Verifica se a operação pertence ao usuário correto.
        assertEquals(usuarioDonoId, operacao.usuarioDonoId)

        // Verifica se a operação está vinculada ao atendimento correto.
        assertEquals("atendimento", operacao.entidade)
        assertEquals(id, operacao.id)

        // Verifica se o tipo da operação é criação.
        assertEquals("criar", operacao.tipo)

        // Verifica se os dados enviados correspondem aos dados do atendimento.
        assertEquals(atendimento.dadosJson, operacao.campos)

        // Busca o atendimento pelo identificador utilizado na gravação.
        val recuperado = repository.buscarAtendimento(
            usuarioDonoId = usuarioDonoId,
            idAtendimento = id
        )

        // Verifica se o atendimento foi encontrado.
        assertNotNull(
            "O atendimento deveria ter sido encontrado.",
            recuperado
        )

        // Confirma se o identificador foi preservado.
        assertEquals(id, recuperado?.idAtendimento)

        // Confirma se os dados JSON foram preservados.
        assertEquals(atendimento.dadosJson, recuperado?.dadosJson)

        // Confirma se o status foi preservado.
        assertEquals(atendimento.statusLocal, recuperado?.statusLocal)

        // Confirma se a origem foi preservada.
        assertEquals(atendimento.origemRegistro, recuperado?.origemRegistro)
    }

    // Verifica se a transação desfaz o atendimento quando a inclusão da operação na fila de sincronização falha.
    @Test
    fun deveDesfazerAtendimentoQuandoFalharInsercaoNaFila() = runBlocking {

        // Gera um identificador único para o atendimento.
        val usuarioDonoId = 1
        val id = UUID.randomUUID().toString()

        // Cria um atendimento fictício para o teste.
        val atendimento = AtendimentoEntity(
            usuarioDonoId = usuarioDonoId,
            idAtendimento = id,
            numeroProtocolo = null,
            dadosJson = """{"descricao":"Teste de rollback"}""",
            origemRegistro = OrigemRegistro.LOCAL,
            statusLocal = SyncStatus.PENDENTE_ENVIO,
            versaoBase = 0,
            syncSeqServidor = null,
            dataCriacaoLocal = System.currentTimeMillis()
        )

        // Cria um gatilho que simula uma falha ao inserir na fila.
        database.openHelper.writableDatabase.execSQL(
            """
        CREATE TRIGGER falhar_insert_operacao_sync
        BEFORE INSERT ON OPERACAO_SYNC
        BEGIN
            SELECT RAISE(ABORT, 'Falha simulada na fila');
        END;
        """.trimIndent()
        )

        // A gravação deve falhar quando tentar inserir a operação.
        assertThrows(Exception::class.java) {
            runBlocking {
                repository.salvarAtendimento(
                    usuarioDonoId = usuarioDonoId,
                    atendimento = atendimento
                )
            }
        }

        // Confirma que o atendimento também foi desfeito.
        val recuperado = repository.buscarAtendimento(
            usuarioDonoId = usuarioDonoId,
            idAtendimento = id
        )

        // Verifica se o atendimento não foi encontrado, indicando que a transação foi revertida.
        assertNull(
            "O atendimento não deveria permanecer no banco após o rollback.",
            recuperado
        )

        // Confirma que nenhuma operação foi mantida na fila.
        val quantidadeOperacoes = database.syncOperationDao()
            .contarOperacoes(usuarioDonoId)

        assertEquals(0, quantidadeOperacoes) // Verifica se não há operações pendentes para o usuário.
    }

    @Test
        fun naoDeveSalvarAtendimentoDeOutroUsuario() = runBlocking {
        val atendimento = AtendimentoEntity(
            idAtendimento = UUID.randomUUID().toString(),
            usuarioDonoId = 1,
            numeroProtocolo = null,
            dadosJson = """{"descricao":"Atendimento de teste"}""",
            origemRegistro = OrigemRegistro.LOCAL,
            statusLocal = SyncStatus.PENDENTE_ENVIO,
            versaoBase = 0,
            syncSeqServidor = null,
            dataCriacaoLocal = System.currentTimeMillis()
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.salvarAtendimento(
                    usuarioDonoId = 2,
                    atendimento = atendimento
                )
            }
        }

        val recuperado = repository.buscarAtendimento(
            usuarioDonoId = 1,
            idAtendimento = atendimento.idAtendimento
        )

        assertNull(recuperado)
    }

    // Verifica se a exclusão de atendimentos de um usuário não afeta os atendimentos de outro usuário.
    @Test
    fun excluirAtendimentosDeUmUsuarioNaoAfetaOutro() = runBlocking {
        val atendimentoA = criarAtendimentoSincronizado( // Cria um atendimento sincronizado para testes.
            usuarioDonoId = 1 // ID do usuário proprietário do atendimento.
        )

        // Cria outro atendimento sincronizado para testes.
        val atendimentoB = criarAtendimentoSincronizado(
            usuarioDonoId = 2
        )

        database.atendimentoDao().salvarLocalmente(atendimentoA) // Salva os atendimentos no banco.
        database.atendimentoDao().salvarLocalmente(atendimentoB)

        val removidos = repository.excluirAtendimentosDoUsuario(1) // Tenta excluir os atendimentos do usuário 1.

        assertEquals(1, removidos) // Verifica se apenas um atendimento foi removido.

        // Verifica se o atendimento foi realmente excluído.
        assertNull(
            repository.buscarAtendimento(1, atendimentoA.idAtendimento)
        )

        // Verifica se o outro atendimento não foi excluído.
        assertNotNull(
            repository.buscarAtendimento(2, atendimentoB.idAtendimento)
        )
    }

    @Test
    // Verifica se a exclusão de um atendimento sincronizado não pode ser realizada.
    fun deveExcluirAtendimentoSincronizadoSemPendencias() = runBlocking {
        val usuarioId = 1 // ID do usuário proprietário do atendimento.
        val id = UUID.randomUUID().toString() // Gera um identificador único para o atendimento.

        // Cria um atendimento sincronizado para testes.
        database.atendimentoDao().salvarLocalmente(
            criarAtendimentoSincronizado(usuarioId, id)
        )

        // Tenta excluir o atendimento.
        val removidos = repository.excluirAtendimento(usuarioId, id)

        assertEquals(1, removidos) // Verifica se apenas um atendimento foi removido.

        // Verifica se o atendimento foi realmente excluído.
        assertNull(
            repository.buscarAtendimento(usuarioId, id)
        )
    }

    @Test
    // Verifica se a exclusão de um atendimento pendente não pode ser realizada.
    fun naoDeveExcluirAtendimentoPendente() = runBlocking {
        val usuarioId = 1 // ID do usuário proprietário do atendimento.
        val id = UUID.randomUUID().toString() // Gera um identificador único para o atendimento.

        // Cria um atendimento pendente para testes.
        val atendimento = AtendimentoEntity(
            usuarioDonoId = usuarioId,
            idAtendimento = id,
            numeroProtocolo = null,
            dadosJson = """{"descricao":"Pendente"}""",
            origemRegistro = OrigemRegistro.LOCAL,
            statusLocal = SyncStatus.PENDENTE_ENVIO,
            versaoBase = 0,
            syncSeqServidor = null,
            dataCriacaoLocal = System.currentTimeMillis()
        )

        database.atendimentoDao().salvarLocalmente(atendimento) // Salva o atendimento no banco.

        // Tenta excluir o atendimento.
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                repository.excluirAtendimento(usuarioId, id)
            }
        }

        assertNotNull(repository.buscarAtendimento(usuarioId, id)) // Verifica se o atendimento ainda está no banco.
    }

    @Test
    // Verifica se a exclusão de um atendimento com operação na fila não pode ser realizada.
    fun naoDeveExcluirAtendimentoComOperacaoNaFila() = runBlocking {
        val usuarioId = 1 // ID do usuário proprietário do atendimento.
        val id = UUID.randomUUID().toString() // Gera um identificador único para o atendimento.

        // Cria um atendimento sincronizado para testes.
        database.atendimentoDao().salvarLocalmente(
            criarAtendimentoSincronizado(usuarioId, id)
        )

        // Cria uma operação de sincronização associada ao atendimento.
        database.syncOperationDao().inserir(
            SyncOperationEntity(
                idOperacao = UUID.randomUUID().toString(),
                usuarioDonoId = usuarioId,
                entidade = "atendimento",
                id = id,
                tipo = "atualizar",
                versaoBase = 1,
                campos = """{"descricao":"Atualização"}""",
                feitoEm = "2026-11-23T12:00:00.000Z",
                estado = SyncOperationStatus.PENDENTE
            )
        )

        // Tenta excluir o atendimento.
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                repository.excluirAtendimento(usuarioId, id)
            }
        }

        assertNotNull(repository.buscarAtendimento(usuarioId, id)) // Verifica se o atendimento ainda está no banco.
    }

    @Test
    // Verifica se a exclusão de um atendimento com conflito não pode ser realizada.
    fun naoDeveExcluirAtendimentoComConflito() = runBlocking {
        val usuarioId = 1 // ID do usuário proprietário do atendimento.
        val id = UUID.randomUUID().toString() // Gera um identificador único para o atendimento.

        // Cria um atendimento sincronizado para testes.
        database.atendimentoDao().salvarLocalmente(
            criarAtendimentoSincronizado(usuarioId, id)
        )

        // Cria um conflito associado ao atendimento.
        database.syncConflictDao().inserir(
            SyncConflictEntity(
                idConflito = UUID.randomUUID().toString(),
                usuarioDonoId = usuarioId,
                idAtendimento = id,
                registroLocalJson = """{"origem":"local"}""",
                registroServidorJson = """{"origem":"servidor"}""",
                versaoLocal = 2,
                versaoServidor = 3,
                dataIdentificacao = System.currentTimeMillis()
            )
        )

        // Tenta excluir o atendimento.
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                repository.excluirAtendimento(usuarioId, id)
            }
        }

        assertNotNull(repository.buscarAtendimento(usuarioId, id)) // Verifica se o atendimento ainda está no banco.
    }

    // Fecha o banco após a execução do teste.
    @After
    fun finalizar() {
        database.close()
    }
}
