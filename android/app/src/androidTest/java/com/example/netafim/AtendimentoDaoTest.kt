package com.example.netafim

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.netafim.data.local.AtendimentoEntity
import com.example.netafim.data.local.NetafimDatabase
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AtendimentoPersistenciaTest {

    // Contexto do aplicativo usado para acessar os arquivos do dispositivo.
    private lateinit var context: Context

    // Nome exclusivo do banco usado neste teste, separado do banco do aplicativo.
    private val nomeBanco = "netafim_persistencia_teste.db"

    // Instância do banco que será aberta e fechada durante o teste.
    private lateinit var database: NetafimDatabase

    @Before
    fun configurar() {
        // Obtém o contexto de teste do Android.
        context = ApplicationProvider.getApplicationContext()

        // Apaga um eventual banco de teste de uma execução anterior.
        context.deleteDatabase(nomeBanco)

        // Cria um banco SQLite em disco, e não em memória.
        database = abrirBanco()
    }

    // Cria uma nova instância apontando para o mesmo arquivo SQLite.
    private fun abrirBanco(): NetafimDatabase {
        return Room.databaseBuilder(
            context,
            NetafimDatabase::class.java,
            nomeBanco
        ).build()
    }

    @Test
    fun atendimentoContinuaSalvoAposReabrirBanco() = runBlocking {
        // Gera um identificador único para o atendimento de teste.
        val id = UUID.randomUUID().toString()

        // Cria um registro fictício, sem dados reais de clientes.
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

        // Salva o atendimento no arquivo SQLite.
        database.atendimentoDao().salvar(atendimento)

        // Fecha a conexão com o banco para simular seu encerramento.
        database.close()

        // Reabre o mesmo arquivo SQLite, preservando os dados gravados.
        database = abrirBanco()

        // Busca o atendimento após a reabertura do banco.
        val recuperado = database.atendimentoDao().buscarPorId(id)

        // Confirma que o atendimento ainda existe.
        assertNotNull(
            "O atendimento deveria continuar salvo após reabrir o banco",
            recuperado
        )

        // Confirma que os dados recuperados são iguais aos dados gravados.
        assertEquals(id, recuperado?.idAtendimento)
        assertEquals(
            atendimento.dadosJson,
            recuperado?.dadosJson
        )
        assertEquals(
            atendimento.statusLocal,
            recuperado?.statusLocal
        )
    }

    @After
    fun finalizar() {
        // Fecha o banco após o teste.
        database.close()

        // Apaga somente o arquivo de teste, sem tocar no banco do aplicativo.
        context.deleteDatabase(nomeBanco)
    }
}