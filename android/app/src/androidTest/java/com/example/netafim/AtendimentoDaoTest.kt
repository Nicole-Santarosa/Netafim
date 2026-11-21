package com.example.netafim

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.netafim.data.local.AtendimentoEntity
import com.example.netafim.data.local.NetafimDatabase
import com.example.netafim.data.local.OrigemRegistro
import com.example.netafim.data.local.SyncStatus
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import android.database.sqlite.SQLiteConstraintException

@RunWith(AndroidJUnit4::class)
class AtendimentoDaoTest {

    // Contexto do aplicativo utilizado para acessar os arquivos do dispositivo.
    private lateinit var context: Context

    // Nome exclusivo do banco de dados utilizado nos testes do DAO.
    private val nomeBanco = "netafim_dao_teste.db"

    // Instância do banco de dados utilizada durante os testes.
    private lateinit var database: NetafimDatabase

    // Identificadores fictícios de usuários para verificar o isolamento dos dados.
    private val usuarioA = 1
    private val usuarioB = 2

    @Before
    fun configurar() {
        // Obtém o contexto de teste do Android.
        context = ApplicationProvider.getApplicationContext()

        // Apaga um eventual banco de teste de uma execução anterior.
        context.deleteDatabase(nomeBanco)

        // Cria uma nova instância do banco de dados de teste.
        database = abrirBanco()
    }

    // Cria uma instância do banco apontando para o arquivo SQLite de teste.
    private fun abrirBanco(): NetafimDatabase {
        return Room.databaseBuilder(
            context,
            NetafimDatabase::class.java,
            nomeBanco
        ).build()
    }

    // Cria um atendimento fictício para reutilizar nos diferentes testes.
    private fun criarAtendimento(
        usuarioDonoId: Int,
        idAtendimento: String = UUID.randomUUID().toString(),
        descricao: String = "Atendimento de teste"
    ): AtendimentoEntity {
        return AtendimentoEntity(
            usuarioDonoId = usuarioDonoId,
            idAtendimento = idAtendimento,
            numeroProtocolo = null,
            dadosJson = """{"descricao":"$descricao"}""",
            origemRegistro = OrigemRegistro.LOCAL,
            statusLocal = SyncStatus.PENDENTE_ENVIO,
            versaoBase = 0,
            syncSeqServidor = null,
            dataCriacaoLocal = System.currentTimeMillis()
        )
    }

    @Test
    fun usuarioConsegueRecuperarSeuProprioAtendimento() = runBlocking {
        // Cria um atendimento pertencente ao usuário A.
        val atendimento = criarAtendimento(usuarioA)

        // Insere o atendimento localmente no banco de dados.
        database.atendimentoDao().salvarLocalmente(atendimento)

        // Busca o atendimento utilizando o proprietário e seu identificador.
        val recuperado = database.atendimentoDao()
            .buscarPorId(usuarioA, atendimento.idAtendimento)

        // Confirma que o atendimento foi encontrado.
        assertEquals(atendimento.idAtendimento, recuperado?.idAtendimento)

        // Confirma que o atendimento pertence ao usuário A.
        assertEquals(usuarioA, recuperado?.usuarioDonoId)
    }

    @Test
    fun usuarioNaoConsegueRecuperarAtendimentoDeOutroUsuario() = runBlocking {
        // Cria um atendimento pertencente ao usuário A.
        val atendimento = criarAtendimento(usuarioA)

        // Salva o atendimento no banco de dados.
        database.atendimentoDao().salvarLocalmente(atendimento)

        // Tenta buscar o atendimento utilizando o ID do usuário B.
        val recuperado = database.atendimentoDao()
            .buscarPorId(usuarioB, atendimento.idAtendimento)

        // Confirma que a consulta não retorna dados de outro usuário.
        assertNull(recuperado)
    }

    @Test
    fun usuariosDiferentesPodemTerAtendimentosComMesmoId() = runBlocking {
        // Gera um identificador que será compartilhado pelos dois registros.
        val idCompartilhado = UUID.randomUUID().toString()

        // Cria um atendimento para o usuário A.
        val atendimentoA = criarAtendimento(
            usuarioDonoId = usuarioA,
            idAtendimento = idCompartilhado,
            descricao = "Atendimento do usuario A"
        )

        // Cria outro atendimento com o mesmo ID, mas pertencente ao usuário B.
        val atendimentoB = criarAtendimento(
            usuarioDonoId = usuarioB,
            idAtendimento = idCompartilhado,
            descricao = "Atendimento do usuario B"
        )

        // Salva os dois registros no banco de dados.
        database.atendimentoDao().salvarLocalmente(atendimentoA)
        database.atendimentoDao().salvarLocalmente(atendimentoB)

        // Recupera separadamente os registros de cada proprietário.
        val recuperadoA = database.atendimentoDao()
            .buscarPorId(usuarioA, idCompartilhado)

        val recuperadoB = database.atendimentoDao()
            .buscarPorId(usuarioB, idCompartilhado)

        // Confirma que os dois registros foram encontrados.
        assertEquals(usuarioA, recuperadoA?.usuarioDonoId)
        assertEquals(usuarioB, recuperadoB?.usuarioDonoId)

        // Confirma que os dados de cada usuário permanecem independentes.
        assertNotEquals(
            recuperadoA?.dadosJson,
            recuperadoB?.dadosJson
        )
    }

    @Test
    fun inserirRegistroExistenteNaoSubstituiDadosOriginais() = runBlocking {
        // Cria e salva o primeiro atendimento.
        val atendimentoOriginal = criarAtendimento(
            usuarioDonoId = usuarioA,
            descricao = "Dados originais"
        )

        database.atendimentoDao().salvarLocalmente(atendimentoOriginal)

        // Cria outro atendimento com a mesma chave primária,
        // mas com dados diferentes.
        val atendimentoDuplicado = atendimentoOriginal.copy(
            dadosJson = """{"descricao":"Dados substitutos"}"""
        )

        // Tenta inserir novamente o mesmo registro.
        // A estratégia ABORT deve impedir a substituição dos dados existentes.
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking {
                database.atendimentoDao()
                    .salvarLocalmente(atendimentoDuplicado)
            }
        }

        // Recupera o registro original após a tentativa de inserção.
        val recuperado = database.atendimentoDao()
            .buscarPorId(usuarioA, atendimentoOriginal.idAtendimento)

        // Confirma que os dados originais foram preservados.
        assertEquals(
            atendimentoOriginal.dadosJson,
            recuperado?.dadosJson
        )
    }

    @After
    fun finalizar() {
        // Fecha o banco de dados após a execução dos testes.
        database.close()

        // Apaga somente o arquivo utilizado pelos testes do DAO.
        context.deleteDatabase(nomeBanco)
    }
}
