package com.example.netafim

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.netafim.data.local.HistoricoEntity
import com.example.netafim.data.local.NetafimDatabase
import com.example.netafim.data.repository.HistoricoRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// Testes unitários para a classe HistoricoRepository, que gerencia o acesso ao banco de dados de histórico de eventos.
@RunWith(AndroidJUnit4::class)
class HistoricoRepositoryTest {

    private lateinit var context: Context // Contexto do aplicativo para acessar recursos e serviços do Android.
    private lateinit var database: NetafimDatabase // Instância do banco de dados Room para testes.
    private lateinit var repository: HistoricoRepository // Instância do repositório que será testada, responsável por operações de CRUD no histórico.

    private val nomeBanco = "netafim_historico_teste.db" // Nome do banco de dados usado para os testes, garantindo que não interfira com o banco de dados real do aplicativo.

    private val usuarioA = 1 // ID de um usuário fictício usado nos testes para simular operações de histórico.
    private val usuarioB = 2

    @Before
    // Configura o ambiente de teste antes de cada teste unitário, garantindo que o banco de dados esteja limpo e pronto para uso.
    fun configurar() {
        context = ApplicationProvider.getApplicationContext()

        context.deleteDatabase(nomeBanco)

        abrirBanco()
    }

    // Abre o banco de dados Room para os testes, criando uma instância do banco e do repositório.
    private fun abrirBanco() {
        database = Room.databaseBuilder(
            context,
            NetafimDatabase::class.java,
            nomeBanco
        ).build()

        repository = HistoricoRepository(database.historicoDao())
    }

    // Cria um evento de histórico para os testes.
    private fun criarEvento(
        usuarioDonoId: Int = usuarioA,
        idHistorico: Long = 1L,
        idAtendimento: String = "atendimento-001",
        tipoEvento: String = "status_alterado",
        origemEvento: String = "usuario",
        valorAnterior: String? = "aberto",
        valorNovo: String = "em_andamento",
        dataEvento: String = "2026-11-24T10:00:00-03:00"
    ): HistoricoEntity {
        return HistoricoEntity(
            usuarioDonoId = usuarioDonoId,
            idHistorico = idHistorico,
            idAtendimento = idAtendimento,
            tipoEvento = tipoEvento,
            origemEvento = origemEvento,
            valorAnterior = valorAnterior,
            valorNovo = valorNovo,
            dataEvento = dataEvento
        )
    }

    // Testa se o repositório consegue salvar e buscar um evento corretamente.
    @Test
    fun deveSalvarEBuscarEvento() = runBlocking {
        val evento = criarEvento() // Cria um evento de teste.

        val idInserido = repository.salvarEvento(evento) // Salva o evento no banco de dados e obtém o ID inserido.

        // Busca o evento salvo no banco de dados usando o ID do usuário e o ID do histórico.
        val recuperado = repository.buscarEvento(
            usuarioA,
            evento.idHistorico
        )

        assertTrue(idInserido > 0) // Verifica se o ID inserido é maior que zero, indicando que a inserção foi bem-sucedida.
        assertNotNull(recuperado) // Verifica se o evento recuperado não é nulo.
        assertEquals(evento, recuperado) // Verifica se o evento recuperado é igual ao evento original.
    }

    // Testa se o repositório não permite a duplicação de eventos do mesmo usuário.
    @Test
    fun naoDeveDuplicarEventoDoMesmoUsuario() = runBlocking {
        val evento = criarEvento() // Cria um evento de teste.

        val primeiraInsercao = repository.salvarEvento(evento) // Salva o evento no banco de dados e obtém o ID inserido.
        val segundaInsercao = repository.salvarEvento(evento) // Tenta salvar o mesmo evento novamente.

        // Busca o histórico do usuário para verificar quantos eventos foram salvos.
        val resultado = repository.listarHistorico(
            usuarioA,
            evento.idAtendimento
        )

        assertTrue(primeiraInsercao > 0) // Verifica se a primeira inserção foi bem-sucedida.
        assertEquals(-1L, segundaInsercao) // Verifica se a segunda inserção foi ignorada, retornando -1L
        assertEquals(1, resultado.size) // Verifica se apenas um evento foi salvo no histórico do usuário.
    }

    // Testa se o repositório consegue salvar vários eventos de uma vez e recuperá-los corretamente.
    @Test
    fun deveSalvarVariosEventos() = runBlocking {
        val eventos = listOf(
            criarEvento(idHistorico = 1L),
            criarEvento(
                idHistorico = 2L,
                tipoEvento = "responsavel_alterado",
                valorAnterior = "usuario-01",
                valorNovo = "usuario-02"
            ),
            criarEvento(
                idHistorico = 3L,
                tipoEvento = "status_alterado",
                valorAnterior = "em_andamento",
                valorNovo = "concluido"
            )
        )

        val idsInseridos = repository.salvarEventos(eventos) // Salva a lista de eventos no banco de dados e obtém os IDs inseridos.

        // Busca o histórico do usuário para verificar quantos eventos foram salvos.
        val resultado = repository.listarHistorico(
            usuarioA,
            "atendimento-001"
        )

        assertEquals(3, idsInseridos.size) // Verifica se três IDs foram retornados, indicando que três eventos foram salvos.
        assertTrue(idsInseridos.all { it > 0 }) // Verifica se todos os IDs inseridos são maiores que zero, indicando que todas as inserções foram bem-sucedidas.
        assertEquals(3, resultado.size) // Verifica se três eventos foram recuperados do histórico do usuário.
    }

    // Testa se o repositório ignora eventos duplicados ao salvar uma lista de eventos.
    @Test
    fun deveIgnorarEventosDuplicadosAoSalvarLista() = runBlocking {
        val eventoOriginal = criarEvento(idHistorico = 1L) // Cria um evento original para ser salvo no banco de dados.

        repository.salvarEvento(eventoOriginal) // Salva o evento original no banco de dados.

        // Cria uma lista de eventos, incluindo o evento original e um novo evento com um ID diferente.
        val eventos = listOf(
            eventoOriginal,
            criarEvento(idHistorico = 2L)
        )

        val idsInseridos = repository.salvarEventos(eventos) // Salva a lista de eventos no banco de dados e obtém os IDs inseridos.

        // Busca o histórico do usuário para verificar quantos eventos foram salvos.
        val resultado = repository.listarHistorico(
            usuarioA,
            "atendimento-001"
        )

        assertEquals(-1L, idsInseridos[0]) // Verifica se o primeiro ID inserido é -1L, indicando que o evento original foi ignorado como duplicado.
        assertTrue(idsInseridos[1] > 0) // Verifica se o segundo ID inserido é maior que zero, indicando que o novo evento foi salvo com sucesso.
        assertEquals(2, resultado.size) // Verifica se dois eventos foram recuperados do histórico do usuário.
    }

    // Testa se o repositório exclui o histórico de um usuário e atendimento específicos.
    @Test
    fun naoDeveConsultarHistoricoDeOutroUsuario() = runBlocking {
        val evento = criarEvento(usuarioDonoId = usuarioA) // Cria um evento de teste para o usuário A.

        repository.salvarEvento(evento) // Salva o evento no banco de dados.

        // Tenta listar o histórico do usuário B para o mesmo atendimento, que não deve retornar nenhum evento.
        val resultado = repository.listarHistorico(
            usuarioB,
            evento.idAtendimento
        )

        // Tenta buscar o evento pelo ID do histórico para o usuário B, que não deve encontrar o evento.
        val buscaPorId = repository.buscarEvento(
            usuarioB,
            evento.idHistorico
        )

        assertTrue(resultado.isEmpty()) // Verifica se o resultado da listagem do histórico do usuário B está vazio, indicando que ele não tem acesso ao histórico do usuário A.
        assertNull(buscaPorId) // Verifica se a busca por ID do histórico para o usuário B retorna nulo, confirmando que ele não pode acessar o evento do usuário A.
    }

    // Testa se o repositório lista os eventos em ordem cronológica.
    @Test
    fun deveListarHistoricoEmOrdemCronologica() = runBlocking {
        val eventos = listOf(
            criarEvento(
                idHistorico = 3L,
                dataEvento = "2026-11-24T12:00:00-03:00"
            ),
            criarEvento(
                idHistorico = 1L,
                dataEvento = "2026-11-24T10:00:00-03:00"
            ),
            criarEvento(
                idHistorico = 2L,
                dataEvento = "2026-11-24T11:00:00-03:00"
            )
        )

        repository.salvarEventos(eventos) // Salva a lista de eventos no banco de dados.

       // Busca o histórico do usuário para o atendimento específico, que deve retornar os eventos em ordem cronológica.
        val resultado = repository.listarHistorico(
            usuarioA,
            "atendimento-001"
        )

        // Verifica se os IDs dos eventos retornados estão na ordem esperada (1, 2, 3), confirmando que a listagem está em ordem cronológica.
        assertEquals(
            listOf(1L, 2L, 3L),
            resultado.map { it.idHistorico }
        )
    }

    // Testa se o repositório exclui o histórico de um usuário e atendimento específicos.
    @Test
    fun deveExcluirSomenteHistoricoDoUsuarioEAtendimento() = runBlocking {
        // Salva eventos para dois usuários diferentes e dois atendimentos diferentes, garantindo que a exclusão seja específica para o usuário e atendimento corretos.
        repository.salvarEvento(
            criarEvento(
                usuarioDonoId = usuarioA,
                idHistorico = 1L,
                idAtendimento = "atendimento-001"
            )
        )

        repository.salvarEvento(
            criarEvento(
                usuarioDonoId = usuarioA,
                idHistorico = 2L,
                idAtendimento = "atendimento-002"
            )
        )

        repository.salvarEvento(
            criarEvento(
                usuarioDonoId = usuarioB,
                idHistorico = 1L,
                idAtendimento = "atendimento-001"
            )
        )

        // Exclui o histórico do usuário A e do atendimento "atendimento-001"
        val excluidos = repository.excluirHistoricoDoAtendimento(
            usuarioA,
            "atendimento-001"
        )

        // Verifica se o histórico do usuário A para o atendimento "atendimento-001" foi excluído corretamente, enquanto os outros históricos permanecem intactos.
        val historicoA = repository.listarHistorico(
            usuarioA,
            "atendimento-001"
        )

        // Verifica se o histórico do usuário A para o atendimento "atendimento-002" ainda existe, confirmando que apenas o histórico específico foi excluído.
        val historicoOutroAtendimento = repository.listarHistorico(
            usuarioA,
            "atendimento-002"
        )

        val historicoB = repository.listarHistorico(
            usuarioB,
            "atendimento-001"
        )

        assertEquals(1, excluidos) // Verifica se apenas um histórico foi excluído.
        assertTrue(historicoA.isEmpty()) // Verifica se o histórico do usuário A para o atendimento "atendimento-001" foi excluído.
        assertEquals(1, historicoOutroAtendimento.size) // Verifica se o histórico do usuário A para o atendimento "atendimento-002" ainda existe.
        assertEquals(1, historicoB.size) // Verifica se o histórico do usuário B para o atendimento "atendimento-001" ainda existe.
    }

    // Testa se o repositório preserva o histórico de eventos após o banco de dados ser fechado e reaberto.
    @Test
    fun devePreservarHistoricoAposReabrirBanco() = runBlocking {
        val evento = criarEvento()

        repository.salvarEvento(evento)

        database.close()

        abrirBanco()

        val recuperado = repository.buscarEvento(
            usuarioA,
            evento.idHistorico
        )

        assertNotNull(recuperado)
        assertEquals(evento, recuperado)
    }

    // Testa se o repositório lança exceções ao receber parâmetros inválidos, garantindo que a validação de entrada esteja funcionando corretamente.
    @Test
    fun naoDeveAceitarParametrosInvalidos() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.salvarEvento(
                    criarEvento(usuarioDonoId = 0)
                )
            }
        }

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.salvarEvento(
                    criarEvento(idHistorico = 0L)
                )
            }
        }

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.listarHistorico(
                    usuarioA,
                    ""
                )
            }
        }

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.buscarEvento(
                    usuarioA,
                    -1L
                )
            }
        }
    }

    @Test
    // Testa se o repositório permite o mesmo ID de histórico para usuários diferentes.
    fun devePermitirMesmoIdHistoricoParaUsuariosDiferentes() = runBlocking {
        val eventoA = criarEvento(
            usuarioDonoId = usuarioA,
            idHistorico = 1L
        )

        val eventoB = criarEvento(
            usuarioDonoId = usuarioB,
            idHistorico = 1L
        )

        val insercaoA = repository.salvarEvento(eventoA)
        val insercaoB = repository.salvarEvento(eventoB)

        assertTrue(insercaoA > 0)
        assertTrue(insercaoB > 0)

        assertEquals(
            eventoA,
            repository.buscarEvento(usuarioA, 1L)
        )

        assertEquals(
            eventoB,
            repository.buscarEvento(usuarioB, 1L)
        )
    }

    // Finaliza o banco de dados após os testes.
    @After
    fun finalizar() {
        database.close()
        context.deleteDatabase(nomeBanco)
    }
}
