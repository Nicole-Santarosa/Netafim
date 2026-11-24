package com.example.netafim

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.netafim.data.local.NetafimDatabase
import com.example.netafim.data.repository.SyncOperationRepository
import com.example.netafim.data.sync.SyncOperationEntity
import com.example.netafim.data.sync.SyncOperationStatus
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
// Testes de integração para o repositório de operações de sincronização.
class SyncOperationRepositoryTest {

    private lateinit var context: Context // Contexto do aplicativo para acessar recursos e banco de dados.
    private lateinit var database: NetafimDatabase // Instância do banco de dados Room para testes.
    private lateinit var repository: SyncOperationRepository // Instância do repositório de operações de sincronização a ser testada.

    private val nomeBanco = "netafim_sync_operation_teste.db" // Nome do banco de dados usado para testes, garantindo isolamento dos dados de produção.

    private val usuarioA = 1 // Identificador de usuário fictício para testes.
    private val usuarioB = 2

    // Configuração inicial antes de cada teste, garantindo um banco de dados limpo e uma instância do repositório pronta para uso.
    @Before
    fun configurar() {
        context = ApplicationProvider.getApplicationContext() // Obtém o contexto do aplicativo para acessar recursos e banco de dados.

        context.deleteDatabase(nomeBanco) // Garante que o banco de dados de teste seja excluído antes de cada teste, evitando interferência de dados anteriores.

        abrirBanco()
    }

    // Abre o banco de dados de teste para testes.
    private fun abrirBanco() {
        database = Room.databaseBuilder( // Cria uma instância do banco de dados Room para testes.
            context, // Contexto do aplicativo para acessar recursos e banco de dados.
            NetafimDatabase::class.java, // Classe do banco de dados Room que define a estrutura e os DAOs.
            nomeBanco // Nome do banco de dados usado para testes.
        ).build()

        repository = SyncOperationRepository(database.syncOperationDao()) // Inicializa o repositório de operações de sincronização com o DAO do banco de dados.
    }

    // Cria uma entidade de operação de sincronização para testes.
    private fun criarOperacao(
        usuarioDonoId: Int = usuarioA, // Identificador do usuário dono da operação, padrão é usuarioA.
        idOperacao: String = UUID.randomUUID().toString(), // Identificador único da operação, gerado aleatoriamente.
        idAtendimento: String = UUID.randomUUID().toString(), // Identificador do atendimento associado à operação, gerado aleatoriamente.
        estado: String = SyncOperationStatus.PENDENTE // Estado inicial da operação, padrão é PENDENTE.
    ): SyncOperationEntity { // Retorna uma instância de SyncOperationEntity com os parâmetros fornecidos, representando uma operação de sincronização para testes.
        return SyncOperationEntity(
            idOperacao = idOperacao,
            usuarioDonoId = usuarioDonoId,
            entidade = "atendimento",
            id = idAtendimento,
            tipo = "criar",
            versaoBase = null,
            campos = """{"descricao":"Teste de sincronizacao"}""",
            feitoEm = "2026-11-23T12:00:00Z",
            tentativas = 0,
            estado = estado
        )
    }

    @Test
    // Testa se o repositório permite registrar e buscar uma operação de sincronização pelo UUID, garantindo que a operação seja salva e recuperada corretamente.
    fun deveRegistrarEBuscarOperacaoPeloUuid() = runBlocking { // RunBlocking é usado para executar corrotinas de forma síncrona em testes.
        val operacao = criarOperacao() // Cria uma operação de sincronização fictícia para teste.

        repository.registrarOperacao(operacao) // Registra a operação no repositório, salvando-a no banco de dados.

        // Busca a operação registrada pelo UUID e pelo usuário dono, garantindo que a operação foi salva corretamente.
        val recuperada = repository.buscarOperacao(
            usuarioA,
            operacao.idOperacao
        )

        assertNotNull(recuperada) // Verifica se a operação recuperada não é nula, confirmando que a operação foi registrada com sucesso.
        assertEquals(operacao.idOperacao, recuperada?.idOperacao) // Verifica se o UUID da operação recuperada é igual ao UUID da operação original, garantindo integridade dos dados.
        assertEquals(operacao.usuarioDonoId, recuperada?.usuarioDonoId) // Verifica se o ID do usuário dono da operação recuperada é igual ao ID do usuário dono da operação original, garantindo que a operação pertence ao usuário correto.
        assertEquals(operacao.id, recuperada?.id) // Verifica se o ID do atendimento da operação recuperada é igual ao ID do atendimento da operação original, garantindo que a operação está associada ao atendimento correto.
        assertEquals(operacao.campos, recuperada?.campos) // Verifica se os campos da operação recuperada são iguais aos campos da operação original, garantindo que os dados da operação foram preservados corretamente.
        assertEquals(operacao.estado, recuperada?.estado) // Verifica se o estado da operação recuperada é igual ao estado da operação original, garantindo que o estado da operação foi registrado corretamente.
        assertEquals(0, recuperada?.tentativas) // Verifica se o número de tentativas da operação recuperada é igual a 0, garantindo que a operação foi registrada com o número correto de tentativas iniciais.
    }

    @Test
    // Testa se o repositório permite listar operações por estado e usuário, garantindo que apenas uma operação pendente seja encontrada para o usuário A.
    fun deveListarOperacoesPorUsuarioEEstado() = runBlocking { // RunBlocking é usado para executar corrotinas de forma síncrona em testes.
        val pendenteA = criarOperacao(usuarioDonoId = usuarioA) // Cria uma operação de sincronização fictícia com estado PENDENTE para o usuário A.
        val pendenteB = criarOperacao(usuarioDonoId = usuarioB)
        val conflitoA = criarOperacao( // Cria uma operação de sincronização fictícia com estado CONFLITO para o usuário A.
            usuarioDonoId = usuarioA,
            estado = SyncOperationStatus.CONFLITO
        )

        repository.registrarOperacao(pendenteA) // Registra as operações no repositório, salvando-as no banco de dados.
        repository.registrarOperacao(pendenteB)
        repository.registrarOperacao(conflitoA)

        // Busca as operações pendentes do usuário A, garantindo que apenas uma foi encontrada.
        val resultado = repository.listarOperacoesPorEstado(
            usuarioA,
            SyncOperationStatus.PENDENTE
        )

        assertEquals(1, resultado.size) // Verifica se o tamanho da lista de operações recuperadas é igual a 1, garantindo que apenas uma operação pendente foi encontrada para o usuário A.
        assertEquals(pendenteA.idOperacao, resultado.first().idOperacao) // Verifica se o UUID da operação encontrada é igual ao UUID da operação original, garantindo que a operação foi recuperada corretamente.
    }

    @Test
    // Testa se o repositório incrementa o número de tentativas de uma operação de sincronização, garantindo que o UUID da operação não seja alterado.
    fun deveIncrementarTentativasSemAlterarUuid() = runBlocking { // RunBlocking é usado para executar corrotinas de forma síncrona em testes.
        val operacao = criarOperacao() // Cria uma operação de sincronização fictícia para teste.

        repository.registrarOperacao(operacao) // Registra a operação no repositório, salvando-a no banco de dados.

        // Incrementa o número de tentativas da operação registrada, garantindo que o UUID da operação não seja alterado.
        val primeiraTentativa = repository.incrementarTentativas(
            usuarioA,
            operacao.idOperacao
        )

        // Incrementa novamente o número de tentativas, garantindo que o UUID da operação não seja alterado.
        val segundaTentativa = repository.incrementarTentativas(
            usuarioA,
            operacao.idOperacao
        )

        // Busca a operação registrada novamente, garantindo que o número de tentativas foi incrementado corretamente.
        val recuperada = repository.buscarOperacao(
            usuarioA,
            operacao.idOperacao
        )

        assertTrue(primeiraTentativa) // Verifica se a primeira tentativa de incremento foi bem-sucedida, garantindo que o número de tentativas foi incrementado corretamente.
        assertTrue(segundaTentativa)
        assertNotNull(recuperada) // Verifica se a operação recuperada não é nula, garantindo que a operação foi encontrada corretamente.
        assertEquals(operacao.idOperacao, recuperada?.idOperacao) // Verifica se o UUID da operação recuperada é igual ao UUID da operação original, garantindo que a operação foi recuperada corretamente.
        assertEquals(2, recuperada?.tentativas) // Verifica se o número de tentativas da operação recuperada é igual a 2, garantindo que o número de tentativas foi incrementado corretamente.
    }

    @Test
    // Testa se o repositório atualiza o estado de uma operação de sincronização.
    fun deveAtualizarEstadoDaOperacao() = runBlocking { // RunBlocking é usado para executar corrotinas de forma síncrona em testes.
        val operacao = criarOperacao() // Cria uma operação de sincronização fictícia para teste.

        repository.registrarOperacao(operacao) // Registra a operação no repositório, salvando-a no banco de dados.

        // Atualiza o estado da operação registrada, garantindo que o estado seja alterado corretamente.
        val atualizado = repository.atualizarEstado(
            usuarioA,
            operacao.idOperacao,
            SyncOperationStatus.EM_PROCESSAMENTO
        )

        // Busca a operação registrada novamente, garantindo que o estado foi atualizado corretamente.
        val recuperada = repository.buscarOperacao(
            usuarioA,
            operacao.idOperacao
        )

        assertTrue(atualizado) // Verifica se a atualização do estado foi bem-sucedida, garantindo que o estado foi atualizado corretamente.
        assertEquals( // Verifica se o estado da operação recuperada é igual ao estado atualizado, garantindo que o estado foi alterado corretamente.
            SyncOperationStatus.EM_PROCESSAMENTO,
            recuperada?.estado
        )
    }

    @Test
    // Testa se o repositório não permite atualizar o estado de uma operação pertencente a outro usuário.
    fun naoDeveAtualizarOperacaoDeOutroUsuario() = runBlocking { // RunBlocking é usado para executar corrotinas de forma síncrona em testes.
        val operacao = criarOperacao(usuarioDonoId = usuarioA) // Cria uma operação de sincronização fictícia para o usuário A.

        repository.registrarOperacao(operacao) // Registra a operação no repositório, salvando-a no banco de dados.

        // Atualiza o estado da operação registrada, garantindo que o estado seja alterado corretamente.
        val atualizado = repository.atualizarEstado(
            usuarioB,
            operacao.idOperacao,
            SyncOperationStatus.CONFLITO
        )

        // Busca a operação registrada novamente, garantindo que o estado não foi alterado.
        val recuperada = repository.buscarOperacao(
            usuarioA,
            operacao.idOperacao
        )

        assertFalse(atualizado) // Verifica se a atualização do estado foi bem-sucedida, garantindo que o estado foi atualizado corretamente.
        assertEquals(SyncOperationStatus.PENDENTE, recuperada?.estado) // Verifica se o estado da operação recuperada é igual ao estado original, garantindo que o estado não foi alterado.
    }

    @Test
    // Testa se o repositório não permite consultar uma operação pertencente a outro usuário.
    fun naoDeveConsultarOperacaoDeOutroUsuario() = runBlocking { // RunBlocking é usado para executar corrotinas de forma síncrona em testes.
        val operacao = criarOperacao(usuarioDonoId = usuarioA) // Cria uma operação de sincronização fictícia para o usuário A.

        repository.registrarOperacao(operacao) // Registra a operação no repositório, salvando-a no banco de dados.

        // Busca a operação registrada pelo UUID e pelo usuário dono, garantindo que a operação não seja encontrada para outro usuário.
        val resultado = repository.buscarOperacao(
            usuarioB,
            operacao.idOperacao
        )

        assertNull(resultado) // Verifica se a operação recuperada é nula, garantindo que o repositório não permite consultar operações pertencentes a outro usuário.
    }

    @Test
    // Testa se o repositório permite remover uma operação pertencente a outro usuário sem afetar a operação do usuário dono.
    fun deveRemoverOperacaoSemAfetarOutroUsuario() = runBlocking { // RunBlocking é usado para executar corrotinas de forma síncrona em testes.
        val operacaoA = criarOperacao(usuarioDonoId = usuarioA) // Cria uma operação de sincronização fictícia para o usuário A.
        val operacaoB = criarOperacao(usuarioDonoId = usuarioB)

        repository.registrarOperacao(operacaoA) // Registra as operações no repositório, salvando-as no banco de dados.
        repository.registrarOperacao(operacaoB)

        // Remove a operação do usuário A, garantindo que a operação do usuário B não seja afetada.
        val removida = repository.removerAposConfirmacaoPersistida(
            usuarioA,
            operacaoA.idOperacao
        )

        // Busca as operações registradas novamente, garantindo que a operação do usuário A foi removida e a operação do usuário B ainda está presente.
        val resultadoA = repository.buscarOperacao(
            usuarioA,
            operacaoA.idOperacao
        )

        // Busca a operação do usuário B para garantir que ela ainda está presente no repositório.
        val resultadoB = repository.buscarOperacao(
            usuarioB,
            operacaoB.idOperacao
        )

        assertTrue(removida) // Verifica se a operação do usuário A foi removida com sucesso, garantindo que a operação foi removida corretamente.
        assertNull(resultadoA) // Verifica se a operação do usuário A não está mais presente no repositório, garantindo que a operação foi removida corretamente.
        assertNotNull(resultadoB)
    }

    @Test
    // Testa se o repositório permite contar operações separadamente por usuário.
    fun deveContarOperacoesSeparadamentePorUsuario() = runBlocking { // RunBlocking é usado para executar corrotinas de forma síncrona em testes.
        repository.registrarOperacao(criarOperacao(usuarioDonoId = usuarioA)) // Registra operações para os usuários A e B, garantindo que o repositório conte as operações separadamente por usuário.
        repository.registrarOperacao(criarOperacao(usuarioDonoId = usuarioA))
        repository.registrarOperacao(criarOperacao(usuarioDonoId = usuarioB))

        assertEquals(2, repository.contarOperacoes(usuarioA)) // Verifica se o repositório conta corretamente o número de operações para o usuário A, garantindo que o repositório conte as operações separadamente por usuário.
        assertEquals(1, repository.contarOperacoes(usuarioB))
    }

    @Test
    // Testa se o repositório preserva o UUID da operação após fechar e reabrir o banco de dados.
    fun devePreservarUuidAposReabrirBanco() = runBlocking { // RunBlocking é usado para executar corrotinas de forma síncrona em testes.
        val operacao = criarOperacao() // Cria uma operação de sincronização fictícia para teste.

        repository.registrarOperacao(operacao) // Registra a operação no repositório, salvando-a no banco de dados.

        database.close()

        abrirBanco()

        // Busca a operação registrada pelo UUID e pelo usuário dono, garantindo que a operação foi encontrada corretamente.
        val recuperada = repository.buscarOperacao(
            usuarioA,
            operacao.idOperacao
        )

        assertNotNull(recuperada) // Verifica se a operação recuperada não é nula, garantindo que a operação foi encontrada corretamente.
        assertEquals(operacao.idOperacao, recuperada?.idOperacao) // Verifica se o UUID da operação recuperada é igual ao UUID da operação original, garantindo que a operação foi recuperada corretamente.
        assertEquals(operacao.campos, recuperada?.campos)
        assertEquals(operacao.tentativas, recuperada?.tentativas)
        assertEquals(operacao.estado, recuperada?.estado)
    }

    @Test
    // Testa se o repositório lança exceções ao receber parâmetros inválidos, garantindo que o repositório não aceite usuários inválidos.
    fun naoDeveAceitarUsuarioInvalido() {
        assertThrows(IllegalArgumentException::class.java) { // Verifica se o repositório lança uma exceção ao tentar contar operações para um usuário inválido (ID 0), garantindo que o repositório não aceite usuários inválidos.
            runBlocking {
                repository.contarOperacoes(0) // Tentativa de contar operações para um usuário inválido.
            }
        }

        assertThrows(IllegalArgumentException::class.java) { // Verifica se o repositório lança uma exceção ao tentar listar operações por estado com um usuário inválido (ID -1), garantindo que o repositório não aceite usuários inválidos.
            runBlocking {
                repository.listarOperacoesPorEstado( // Tentativa de listar operações por estado para um usuário inválido.
                    -1,
                    SyncOperationStatus.PENDENTE
                )
            }
        }
    }

    @Test
    // Testa se o repositório lista operações na ordem FIFO (First In, First Out), garantindo que a ordem de inserção seja preservada ao recuperar as operações.
    fun deveListarOperacoesNaOrdemFIFO() = runBlocking {
        // Arrange: criar três operações para o mesmo usuário.
        val primeira = criarOperacao()
        val segunda = criarOperacao()
        val terceira = criarOperacao()

        // Act: inserir as operações nessa sequência.
        repository.registrarOperacao(primeira)
        repository.registrarOperacao(segunda)
        repository.registrarOperacao(terceira)

        // Recuperar a fila de operações pendentes.
        val resultado = repository.listarOperacoesPorEstado(
            usuarioA,
            SyncOperationStatus.PENDENTE
        )

        // Assert: a ordem recuperada deve ser a ordem de inserção.
        assertEquals(3, resultado.size)

        // Verifica se a lista está na ordem FIFO (First In, First Out).
        assertEquals(
            listOf(
                primeira.idOperacao,
                segunda.idOperacao,
                terceira.idOperacao
            ),
            resultado.map { it.idOperacao } // Mapeia a lista de operações para uma lista de UUIDs.
        )
    }

    @After
    // Finaliza o teste, excluindo o banco de dados de teste e fechando o banco de dados.
    fun finalizar() {
        database.close()
        context.deleteDatabase(nomeBanco)
    }
}
