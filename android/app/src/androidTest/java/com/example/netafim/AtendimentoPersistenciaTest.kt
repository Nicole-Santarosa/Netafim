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
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AtendimentoPersistenciaTest {

    // Contexto do aplicativo utilizado para acessar os arquivos do dispositivo.
    private lateinit var context: Context

    // Nome exclusivo do banco de dados utilizado neste teste.
    // Assim, os testes não alteram o banco de dados real do aplicativo.
    private val nomeBanco = "netafim_persistencia_teste.db"

    // Instância do banco de dados que será aberta e fechada durante o teste.
    private lateinit var database: NetafimDatabase

    // Identificador fictício do usuário proprietário dos dados de teste.
    private val usuarioDonoId = 1

    @Before
    fun configurar() {
        // Obtém o contexto de teste do Android.
        context = ApplicationProvider.getApplicationContext()

        // Apaga um eventual banco de teste de uma execução anterior,
        // garantindo que o teste comece com o banco vazio.
        context.deleteDatabase(nomeBanco)

        // Cria uma nova instância do banco de dados de teste.
        database = abrirBanco()
    }

    // Cria uma instância do banco apontando para o mesmo arquivo SQLite.
    // Isso permite fechar e reabrir o banco sem perder os dados gravados.
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

        // Cria um atendimento fictício, sem utilizar dados reais de clientes.
        val atendimento = AtendimentoEntity(
            usuarioDonoId = usuarioDonoId,
            idAtendimento = id,
            numeroProtocolo = null,
            dadosJson = """{"descricao":"Teste de persistencia"}""",
            origemRegistro = OrigemRegistro.LOCAL,
            statusLocal = SyncStatus.PENDENTE_ENVIO,
            versaoBase = 0,
            syncSeqServidor = null,
            dataCriacaoLocal = System.currentTimeMillis()
        )

        // Salva o atendimento localmente no banco SQLite.
        // O método também impede a substituição acidental de um registro existente.
        database.atendimentoDao().salvarLocalmente(atendimento)

        // Fecha a conexão com o banco para simular o encerramento do aplicativo.
        database.close()

        // Reabre o mesmo arquivo SQLite, verificando se os dados persistiram.
        database = abrirBanco()

        // Busca o atendimento utilizando o ID do usuário proprietário
        // e o identificador do atendimento.
        val recuperado = database.atendimentoDao()
            .buscarPorId(usuarioDonoId, id)

        // Confirma que o atendimento continua armazenado após a reabertura.
        assertNotNull(
            "O atendimento deveria continuar salvo após reabrir o banco",
            recuperado
        )

        // Confirma que o identificador do atendimento foi preservado.
        assertEquals(id, recuperado?.idAtendimento)

        // Confirma que o proprietário do atendimento foi preservado.
        assertEquals(atendimento.usuarioDonoId, recuperado?.usuarioDonoId)

        // Confirma que os dados JSON foram preservados.
        assertEquals(atendimento.dadosJson, recuperado?.dadosJson)

        // Confirma que o estado de sincronização continua pendente de envio.
        assertEquals(atendimento.statusLocal, recuperado?.statusLocal)
    }

    @After
    fun finalizar() {
        // Fecha o banco de dados ao terminar o teste.
        database.close()

        // Apaga somente o arquivo utilizado neste teste,
        // sem afetar o banco de dados do aplicativo.
        context.deleteDatabase(nomeBanco)
    }
}
