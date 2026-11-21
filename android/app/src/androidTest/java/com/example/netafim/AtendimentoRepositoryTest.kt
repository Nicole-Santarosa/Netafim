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
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

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

        // Obtém o DAO disponibilizado pelo banco.
        val atendimentoDao = database.atendimentoDao()

        // Injeta o DAO no Repository.
        repository = AtendimentoRepository(atendimentoDao)
    }

    // Verifica se o Repository consegue salvar e recuperar um atendimento.
    @Test
    fun deveSalvarEBuscarAtendimento() = runBlocking {

        // Gera um identificador único para o atendimento.
        val id = UUID.randomUUID().toString()

        // Cria um atendimento fictício para o teste.
        val atendimento = AtendimentoEntity(
            idAtendimento = id,
            usuarioDonoId = 1,
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
            usuarioDonoId = 1,
            atendimento = atendimento
        )

        // Busca o atendimento pelo identificador utilizado na gravação.
        val recuperado = repository.buscarAtendimento(
            usuarioDonoId = 1,
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

    // Fecha o banco após a execução do teste.
    @After
    fun finalizar() {
        database.close()
    }
}
