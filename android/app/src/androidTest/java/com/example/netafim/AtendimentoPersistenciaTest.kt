package com.example.netafim

// Permite acessar o contexto do aplicativo Android.
import android.content.Context

// Permite criar bancos Room diretamente nos testes.
import androidx.room.Room

// Fornece o contexto do aplicativo durante os testes instrumentados.
import androidx.test.core.app.ApplicationProvider

// Permite executar testes no ambiente Android.
import androidx.test.ext.junit.runners.AndroidJUnit4

// Importa a entidade que representa um atendimento local.
import com.example.netafim.data.local.AtendimentoEntity

// Importa a configuração do banco de dados Room.
import com.example.netafim.data.local.NetafimDatabase

// Gera identificadores UUID únicos.
import java.util.UUID

// Permite executar funções suspend dentro do teste.
import kotlinx.coroutines.runBlocking

// Anotações e ferramentas do JUnit.
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// Informa que o teste será executado no ambiente Android.
@RunWith(AndroidJUnit4::class)
class AtendimentoPersistenciaTestOffline {

    // Contexto utilizado para acessar os arquivos do aplicativo.
    private lateinit var context: Context

    // Nome do arquivo SQLite utilizado exclusivamente neste teste.
    private val nomeBanco = "netafim_persistencia_teste.db"

    // Instância do banco de dados Room.
    private lateinit var database: NetafimDatabase

    // Executa antes de cada teste.
    @Before
    fun configurar() {

        // Obtém o contexto de teste do aplicativo.
        context = ApplicationProvider.getApplicationContext()

        // Remove um banco de teste anterior, caso exista.
        // Isso evita que dados antigos interfiram no resultado.
        context.deleteDatabase(nomeBanco)

        // Abre uma nova instância do banco SQLite em disco.
        database = abrirBanco()
    }

    // Método responsável por abrir o banco de teste.
    private fun abrirBanco(): NetafimDatabase {

        // Cria ou abre o arquivo SQLite com o Room.
        return Room.databaseBuilder(
            context,
            NetafimDatabase::class.java,
            nomeBanco
        ).build()
    }

    // Verifica se os dados continuam salvos após fechar e reabrir o banco.
    @Test
    fun atendimentoContinuaSalvoAposReabrirBanco() = runBlocking {

        // Gera um UUID exclusivo para identificar o atendimento.
        val id = UUID.randomUUID().toString()

        // Cria um atendimento fictício para o teste.
        // Não utilizamos dados reais de clientes.
        val atendimento = AtendimentoEntity(
            idAtendimento = id,
            numeroProtocolo = null,
            dadosJson = """{"descricao":"Teste de persistencia"}""",
            origemRegistro = "DISPOSITIVO",
            statusLocal = "PENDENTE",
            versaoBase = 0,
            syncSeqServidor = null,
            dataCriacaoLocal = System.currentTimeMillis()
        )

        // Salva o atendimento no banco SQLite.
        // O método salvar pertence ao AtendimentoDao.
        database.atendimentoDao().salvar(atendimento)

        // Fecha a conexão com o banco.
        // Isso simula o encerramento do acesso ao banco de dados.
        database.close()

        // Abre novamente o mesmo arquivo SQLite.
        // Os dados gravados anteriormente devem continuar existindo.
        database = abrirBanco()

        // Busca o atendimento pelo UUID após reabrir o banco.
        val recuperado =
            database.atendimentoDao().buscarPorId(id)

        // Verifica se o atendimento foi encontrado.
        assertNotNull(
            "O atendimento deveria continuar salvo após reabrir o banco",
            recuperado
        )

        // Confirma se o identificador recuperado é o mesmo.
        assertEquals(
            id,
            recuperado?.idAtendimento
        )

        // Confirma se o conteúdo JSON foi preservado.
        assertEquals(
            atendimento.dadosJson,
            recuperado?.dadosJson
        )

        // Confirma se o status local foi preservado.
        assertEquals(
            atendimento.statusLocal,
            recuperado?.statusLocal
        )
    }

    // Executa depois de cada teste.
    @After
    fun finalizar() {

        // Fecha a conexão com o banco.
        database.close()

        // Apaga somente o banco temporário utilizado pelo teste.
        // O banco real do aplicativo não é afetado.
        context.deleteDatabase(nomeBanco)
    }
}