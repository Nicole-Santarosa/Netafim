package com.example.netafim

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.netafim.data.local.NetafimDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

// Teste para verificar a migração do histórico.
@RunWith(AndroidJUnit4::class)
class HistoricoMigrationTest {

    private lateinit var context: Context // Contexto do aplicativo para acessar recursos e serviços do Android.
    private lateinit var database: NetafimDatabase // Base de dados Room para testes.

    private val nomeBanco = "netafim_migracao_teste.db" // Nome do banco de dados usado para os testes, garantindo que não interfira com o banco de dados real do aplicativo.

    @Before
    // Configura o ambiente de teste antes de cada teste unitário, garantindo que o banco de dados esteja limpo e pronto para uso.
    fun configurar() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(nomeBanco)
    }

    // Cria manualmente um banco com a estrutura da versão 2.
    private fun criarBancoVersao2() {
        val arquivo: File = context.getDatabasePath(nomeBanco)
        arquivo.parentFile?.mkdirs()

        // Abre o banco de dados SQLite.
        val db = SQLiteDatabase.openOrCreateDatabase(
            arquivo.absolutePath,
            null
        )

        // Tabela de atendimentos da versão 2.
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `ATENDIMENTO_LOCAL` (
                `usuarioDonoId` INTEGER NOT NULL,
                `idAtendimento` TEXT NOT NULL,
                `numeroProtocolo` TEXT,
                `dadosJson` TEXT NOT NULL,
                `origemRegistro` TEXT NOT NULL,
                `statusLocal` TEXT NOT NULL,
                `versaoBase` INTEGER NOT NULL,
                `syncSeqServidor` INTEGER,
                `dataCriacaoLocal` INTEGER NOT NULL,
                PRIMARY KEY(`usuarioDonoId`, `idAtendimento`)
            )
        """)

        // Tabela da fila de sincronização da versão 2.
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `OPERACAO_SYNC` (
                `idOperacao` TEXT NOT NULL,
                `usuarioDonoId` INTEGER NOT NULL,
                `entidade` TEXT NOT NULL,
                `id` TEXT NOT NULL,
                `tipo` TEXT NOT NULL,
                `versaoBase` INTEGER,
                `campos` TEXT NOT NULL,
                `feitoEm` TEXT NOT NULL,
                `tentativas` INTEGER NOT NULL,
                `estado` TEXT NOT NULL,
                PRIMARY KEY(`idOperacao`)
            )
        """)

        db.execSQL("""
            CREATE INDEX IF NOT EXISTS
            `index_OPERACAO_SYNC_usuarioDonoId_estado`
            ON `OPERACAO_SYNC` (`usuarioDonoId`, `estado`)
        """)

        db.execSQL("""
            CREATE INDEX IF NOT EXISTS
            `index_OPERACAO_SYNC_usuarioDonoId_id`
            ON `OPERACAO_SYNC` (`usuarioDonoId`, `id`)
        """)

        // Tabela de conflitos da versão 2.
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `CONFLITO_SYNC` (
                `idConflito` TEXT NOT NULL,
                `usuarioDonoId` INTEGER NOT NULL,
                `idAtendimento` TEXT NOT NULL,
                `registroLocalJson` TEXT NOT NULL,
                `registroServidorJson` TEXT NOT NULL,
                `versaoLocal` INTEGER NOT NULL,
                `versaoServidor` INTEGER NOT NULL,
                `dataIdentificacao` INTEGER NOT NULL,
                PRIMARY KEY(`idConflito`)
            )
        """)

        db.execSQL("""
            CREATE INDEX IF NOT EXISTS
            `index_CONFLITO_SYNC_usuarioDonoId_idAtendimento`
            ON `CONFLITO_SYNC` (`usuarioDonoId`, `idAtendimento`)
        """)

        db.execSQL("""
            CREATE INDEX IF NOT EXISTS
            `index_CONFLITO_SYNC_usuarioDonoId_dataIdentificacao`
            ON `CONFLITO_SYNC` (`usuarioDonoId`, `dataIdentificacao`)
        """)

        // Insere um atendimento já existente.
        db.execSQL("""
            INSERT INTO ATENDIMENTO_LOCAL (
                usuarioDonoId,
                idAtendimento,
                numeroProtocolo,
                dadosJson,
                origemRegistro,
                statusLocal,
                versaoBase,
                syncSeqServidor,
                dataCriacaoLocal
            ) VALUES (
                1,
                'atendimento-teste',
                'NET-001',
                '{"descricao":"Atendimento preservado"}',
                'SERVIDOR',
                'SINCRONIZADO',
                1,
                100,
                1795000000000
            )
        """)

        // Insere uma operação pendente.
        db.execSQL("""
            INSERT INTO OPERACAO_SYNC (
                idOperacao,
                usuarioDonoId,
                entidade,
                id,
                tipo,
                versaoBase,
                campos,
                feitoEm,
                tentativas,
                estado
            ) VALUES (
                'operacao-teste',
                1,
                'atendimento',
                'atendimento-teste',
                'atualizar',
                1,
                '{"descricao":"Alteração pendente"}',
                '2026-11-24T10:00:00Z',
                2,
                'PENDENTE'
            )
        """)

        // Insere um conflito existente.
        db.execSQL("""
            INSERT INTO CONFLITO_SYNC (
                idConflito,
                usuarioDonoId,
                idAtendimento,
                registroLocalJson,
                registroServidorJson,
                versaoLocal,
                versaoServidor,
                dataIdentificacao
            ) VALUES (
                'conflito-teste',
                1,
                'atendimento-teste',
                '{"descricao":"Versão local"}',
                '{"descricao":"Versão servidor"}',
                2,
                3,
                1795000000000
            )
        """)

        // Marca o banco como versão 2.
        db.version = 2

        db.close()
    }

    // Abre o banco de dados Room com a migração real.
    private fun abrirBancoMigrado() {
        // Cria uma instância do banco de dados Room.
        database = Room.databaseBuilder(
            context,
            NetafimDatabase::class.java,
            nomeBanco
        )
            // Registra as migrações personalizadas.
            .addMigrations(
                NetafimDatabase.MIGRATION_1_2,
                NetafimDatabase.MIGRATION_2_3
            )
            .build()

        // Acessar o banco força sua abertura e execução da migração.
        database.openHelper.writableDatabase
    }

    // Conta o número de registros em uma tabela.
    private fun contarRegistros(tabela: String): Int {
        val cursor = database.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM `$tabela`"
        )

        // Retorna o valor do primeiro campo da primeira linha.
        return cursor.use {
            it.moveToFirst()
            it.getInt(0)
        }
    }

    @Test
    // Testa se o banco de dados é migrado corretamente para a versão 3.
    fun deveMigrarVersao2Para3PreservandoDados() {
        // Arrange: cria um banco antigo, na versão 2.
        criarBancoVersao2()

        // Act: abre o banco utilizando a migração real.
        abrirBancoMigrado()

        // Verifica se o Room atualizou a versão.
        assertEquals(
            3,
            database.openHelper.readableDatabase.version
        )

        // Verifica se os registros antigos foram preservados.
        assertEquals(1, contarRegistros("ATENDIMENTO_LOCAL"))
        assertEquals(1, contarRegistros("OPERACAO_SYNC"))
        assertEquals(1, contarRegistros("CONFLITO_SYNC"))

        // Verifica se a nova tabela foi criada.
        assertEquals(0, contarRegistros("HISTORICO_LOCAL"))

        // Verifica se os índices da nova tabela foram criados.
        val cursor = database.openHelper.readableDatabase.query(
            """
            SELECT name FROM sqlite_master
            WHERE type = 'index'
            AND name IN (
                'index_HISTORICO_LOCAL_usuarioDonoId_idAtendimento',
                'index_HISTORICO_LOCAL_usuarioDonoId_dataEvento'
            )
            """.trimIndent()
        )

        // Verifica se os índices foram criados.
        val quantidadeIndices = cursor.use {
            var quantidade = 0
            while (it.moveToNext()) {
                quantidade++
            }
            quantidade
        }

        assertEquals(2, quantidadeIndices) // Verifica se dois índices foram criados.

        // Verifica que os registros específicos continuam presentes.
        val atendimento = database.openHelper.readableDatabase.query(
            """
            SELECT numeroProtocolo
            FROM ATENDIMENTO_LOCAL
            WHERE usuarioDonoId = 1
            AND idAtendimento = 'atendimento-teste'
            """.trimIndent()
        )

        // Verifica se o registro do atendimento foi preservado.
        assertTrue(atendimento.use {
            it.moveToFirst() &&
                    it.getString(0) == "NET-001"
        })

        // Verifica se a operação pendente foi preservada.
        val operacao = database.openHelper.readableDatabase.query(
            """
            SELECT estado, tentativas
            FROM OPERACAO_SYNC
            WHERE idOperacao = 'operacao-teste'
            """.trimIndent()
        )

        // Verifica se a operação pendente foi preservada.
        assertTrue(operacao.use {
            it.moveToFirst() &&
                    it.getString(0) == "PENDENTE" &&
                    it.getInt(1) == 2
        })

        // Verifica se o conflito foi preservado.
        val conflito = database.openHelper.readableDatabase.query(
            """
            SELECT idConflito
            FROM CONFLITO_SYNC
            WHERE idConflito = 'conflito-teste'
            """.trimIndent()
        )

        // Verifica se o conflito foi preservado.
        assertTrue(conflito.use {
            it.moveToFirst() &&
                    it.getString(0) == "conflito-teste"
        })
    }

    @After
    // Finaliza o banco de dados após os testes.
    fun finalizar() {
        if (::database.isInitialized) {
            database.close()
        }

        context.deleteDatabase(nomeBanco)
    }
}
