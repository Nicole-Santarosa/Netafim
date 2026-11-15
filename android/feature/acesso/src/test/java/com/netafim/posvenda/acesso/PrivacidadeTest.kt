package com.netafim.posvenda.acesso

import com.netafim.posvenda.acesso.dados.AcessoApi
import com.netafim.posvenda.acesso.dados.ProvedorTokens
import com.netafim.posvenda.acesso.dados.Rede
import com.netafim.posvenda.acesso.dados.RepositorioPrivacidadeRemoto
import com.netafim.posvenda.acesso.dominio.ErroAcesso
import com.netafim.posvenda.acesso.dominio.LIMITE_OBSERVACAO_PEDIDO
import com.netafim.posvenda.acesso.dominio.PedidoTitular
import com.netafim.posvenda.acesso.dominio.RepositorioPrivacidade
import com.netafim.posvenda.acesso.dominio.Resultado
import com.netafim.posvenda.acesso.dominio.StatusPedido
import com.netafim.posvenda.acesso.dominio.TipoPedido
import com.netafim.posvenda.acesso.ui.privacidade.CampoNovoPedido
import com.netafim.posvenda.acesso.ui.privacidade.NovoPedidoViewModel
import com.netafim.posvenda.acesso.ui.privacidade.PrivacidadeViewModel
import com.netafim.posvenda.acesso.ui.privacidade.formatarDataDoPedido
import com.netafim.posvenda.acesso.ui.privacidade.textoDoStatus
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun pedido(id: Int, tipo: String = "acesso", status: String = StatusPedido.RECEBIDA) = PedidoTitular(
    id = id, tipo = tipo, status = status, observacao = null,
    dataSolicitacao = "2026-11-10T12:00:00Z", dataPrazo = "2026-11-25T12:00:00Z",
    dataConclusao = null, justificativaNegativa = null,
)

private class PrivacidadeFalsa : RepositorioPrivacidade {
    var pedidos: Resultado<List<PedidoTitular>> = Resultado.Sucesso(emptyList())
    var abrir: Resultado<PedidoTitular>? = null
    var copia: Resultado<String> = Resultado.Sucesso("{}")
    val abertos = mutableListOf<Pair<TipoPedido, String>>()
    var buscasDeCopia = 0

    override suspend fun meusPedidos() = pedidos

    override suspend fun abrirPedido(tipo: TipoPedido, observacao: String): Resultado<PedidoTitular> {
        abertos += tipo to observacao
        return abrir ?: Resultado.Sucesso(pedido(99, tipo.codigo))
    }

    override suspend fun copiaDosDados(): Resultado<String> {
        buscasDeCopia++
        return copia
    }
}

/** Tipos de pedido e rótulos: o que o app aceita e como mostra. */
class RegrasDePrivacidadeTest {

    @Test
    fun `os codigos dos tipos sao os que a API aceita`() {
        assertEquals(
            setOf(
                "confirmacao", "acesso", "correcao", "anonimizacao", "eliminacao",
                "portabilidade", "compartilhamento", "revogacao_consentimento",
            ),
            TipoPedido.entries.map { it.codigo }.toSet(),
        )
        assertEquals(8, TipoPedido.entries.size)
    }

    @Test
    fun `so anonimizacao e eliminacao sao irreversiveis`() {
        assertEquals(setOf(TipoPedido.ANONIMIZACAO, TipoPedido.ELIMINACAO), TipoPedido.entries.filter { it.irreversivel }.toSet())
    }

    @Test
    fun `deCodigo acha o tipo e ignora o desconhecido`() {
        assertEquals(TipoPedido.PORTABILIDADE, TipoPedido.deCodigo("portabilidade"))
        assertNull(TipoPedido.deCodigo("tipo_novo"))
    }

    @Test
    fun `pedido recebido ou em analise esta aberto`() {
        assertTrue(pedido(1, status = StatusPedido.RECEBIDA).aberto)
        assertTrue(pedido(1, status = StatusPedido.EM_ANALISE).aberto)
        assertFalse(pedido(1, status = StatusPedido.ATENDIDA).aberto)
        assertFalse(pedido(1, status = StatusPedido.NEGADA).aberto)
    }

    @Test
    fun `status conhecidos tem texto e o desconhecido nao`() {
        listOf("recebida", "em_analise", "atendida", "negada").forEach { assertNotNull(it, textoDoStatus(it)) }
        assertNull(textoDoStatus("arquivada"))
    }

    @Test
    fun `a data vem no fuso do aparelho e o texto ilegivel volta como veio`() {
        assertEquals("23/11/2026", formatarDataDoPedido("2026-11-23T22:00:00Z", ZoneId.of("America/Sao_Paulo")))
        // 01h UTC ainda é o dia anterior em Brasília.
        assertEquals("22/11/2026", formatarDataDoPedido("2026-11-23T01:00:00Z", ZoneId.of("America/Sao_Paulo")))
        assertEquals("ontem", formatarDataDoPedido("ontem"))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PrivacidadeViewModelTest {

    private val repositorio = PrivacidadeFalsa()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `carrega os pedidos ao abrir`() {
        repositorio.pedidos = Resultado.Sucesso(listOf(pedido(2), pedido(1)))
        val e = PrivacidadeViewModel(repositorio).estado.value
        assertEquals(listOf(2, 1), e.pedidos.map { it.id })
        assertFalse(e.carregando)
        assertNull(e.erro)
    }

    @Test
    fun `falha ao listar mostra o erro e permite tentar de novo`() {
        repositorio.pedidos = Resultado.Falha(ErroAcesso.SemConexao)
        val vm = PrivacidadeViewModel(repositorio)
        assertEquals(ErroAcesso.SemConexao.mensagem, vm.estado.value.erro)
        assertFalse(vm.estado.value.carregando)

        repositorio.pedidos = Resultado.Sucesso(listOf(pedido(1)))
        vm.carregar()
        assertNull(vm.estado.value.erro)
        assertEquals(1, vm.estado.value.pedidos.size)
    }

    @Test
    fun `ao retomar atualiza a lista sem mostrar carregamento`() {
        val vm = PrivacidadeViewModel(repositorio)
        repositorio.pedidos = Resultado.Sucesso(listOf(pedido(5)))
        vm.aoRetomar()
        assertEquals(listOf(5), vm.estado.value.pedidos.map { it.id })
        assertFalse(vm.estado.value.carregando)
    }

    @Test
    fun `baixar dados guarda a copia ate a tela entregar`() {
        repositorio.copia = Resultado.Sucesso("""{"formato":1}""")
        val vm = PrivacidadeViewModel(repositorio)

        vm.baixarDados()
        assertEquals("""{"formato":1}""", vm.estado.value.copia)
        assertFalse(vm.estado.value.baixando)

        vm.copiaEntregue()
        assertNull("a copia não fica na memória depois de entregue", vm.estado.value.copia)
    }

    @Test
    fun `falha ao baixar mostra a mensagem e nao deixa copia`() {
        repositorio.copia = Resultado.Falha(ErroAcesso.SemConexao)
        val vm = PrivacidadeViewModel(repositorio)
        vm.baixarDados()
        assertEquals(ErroAcesso.SemConexao.mensagem, vm.estado.value.erroCopia)
        assertNull(vm.estado.value.copia)
        assertFalse(vm.estado.value.baixando)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class NovoPedidoViewModelTest {

    private val repositorio = PrivacidadeFalsa()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = NovoPedidoViewModel(repositorio)

    @Test
    fun `sem escolher o tipo nao envia`() {
        val vm = vm()
        vm.enviar()
        assertTrue(repositorio.abertos.isEmpty())
        assertNotNull(vm.estado.value.erros[CampoNovoPedido.TIPO])
        assertFalse(vm.estado.value.concluido)
    }

    @Test
    fun `observacao grande demais nao envia`() {
        val vm = vm()
        vm.escolherTipo(TipoPedido.ACESSO)
        vm.mudarObservacao("a".repeat(LIMITE_OBSERVACAO_PEDIDO + 1))
        vm.enviar()
        assertTrue(repositorio.abertos.isEmpty())
        assertNotNull(vm.estado.value.erros[CampoNovoPedido.OBSERVACAO])
    }

    @Test
    fun `pedido comum envia direto e conclui`() {
        val vm = vm()
        vm.escolherTipo(TipoPedido.ACESSO)
        vm.mudarObservacao("Quero uma cópia")
        vm.enviar()
        assertEquals(listOf(TipoPedido.ACESSO to "Quero uma cópia"), repositorio.abertos)
        assertFalse(vm.estado.value.confirmando)
        assertTrue(vm.estado.value.concluido)
        assertFalse(vm.estado.value.enviando)
    }

    @Test
    fun `anonimizacao e eliminacao pedem confirmacao antes de enviar`() {
        listOf(TipoPedido.ANONIMIZACAO, TipoPedido.ELIMINACAO).forEach { tipo ->
            val vm = NovoPedidoViewModel(PrivacidadeFalsa().also { repositorio.abertos.clear() })
            vm.escolherTipo(tipo)
            vm.enviar()
            assertTrue("$tipo pede confirmação", vm.estado.value.confirmando)
            assertFalse(vm.estado.value.concluido)
        }
        assertTrue(repositorio.abertos.isEmpty())
    }

    @Test
    fun `cancelar a confirmacao nao envia nada`() {
        val vm = vm()
        vm.escolherTipo(TipoPedido.ELIMINACAO)
        vm.enviar()
        vm.cancelarConfirmacao()
        assertFalse(vm.estado.value.confirmando)
        assertTrue(repositorio.abertos.isEmpty())
        assertFalse(vm.estado.value.concluido)
    }

    @Test
    fun `confirmar envia o pedido irreversivel`() {
        val vm = vm()
        vm.escolherTipo(TipoPedido.ANONIMIZACAO)
        vm.enviar()
        vm.confirmar()
        assertEquals(listOf(TipoPedido.ANONIMIZACAO to ""), repositorio.abertos)
        assertTrue(vm.estado.value.concluido)
        assertFalse(vm.estado.value.confirmando)
    }

    @Test
    fun `confirmar sem ter pedido a confirmacao nao envia`() {
        val vm = vm()
        vm.escolherTipo(TipoPedido.ANONIMIZACAO)
        vm.confirmar()
        assertTrue(repositorio.abertos.isEmpty())
    }

    @Test
    fun `pedido ja em aberto aparece no campo do tipo e fecha a confirmacao`() {
        repositorio.abrir = Resultado.Falha(
            ErroAcesso.Api("solicitacao_em_aberto", "Você já tem um pedido desse tipo em andamento.", 409),
        )
        val vm = vm()
        vm.escolherTipo(TipoPedido.ANONIMIZACAO)
        vm.enviar()
        vm.confirmar()
        val e = vm.estado.value
        assertEquals("Você já tem um pedido desse tipo em andamento.", e.erros[CampoNovoPedido.TIPO])
        assertNull(e.erroGeral)
        assertFalse(e.confirmando)
        assertFalse(e.enviando)
        assertFalse(e.concluido)
    }

    @Test
    fun `erro de campo da API vai para o campo certo`() {
        repositorio.abrir = Resultado.Falha(ErroAcesso.Campos(mapOf("observacao" to "Texto muito longo."), "Texto muito longo."))
        val vm = vm()
        vm.escolherTipo(TipoPedido.CORRECAO)
        vm.enviar()
        assertEquals("Texto muito longo.", vm.estado.value.erros[CampoNovoPedido.OBSERVACAO])
        assertNull(vm.estado.value.erroGeral)
    }

    @Test
    fun `sem conexao mostra a mensagem geral e permite tentar de novo`() {
        repositorio.abrir = Resultado.Falha(ErroAcesso.SemConexao)
        val vm = vm()
        vm.escolherTipo(TipoPedido.ACESSO)
        vm.enviar()
        assertEquals(ErroAcesso.SemConexao.mensagem, vm.estado.value.erroGeral)
        assertFalse(vm.estado.value.concluido)

        repositorio.abrir = null
        vm.enviar()
        assertTrue(vm.estado.value.concluido)
    }

    @Test
    fun `mudar a escolha ou o texto limpa o erro correspondente`() {
        val vm = vm()
        vm.enviar()
        assertNotNull(vm.estado.value.erros[CampoNovoPedido.TIPO])
        vm.escolherTipo(TipoPedido.ACESSO)
        assertNull(vm.estado.value.erros[CampoNovoPedido.TIPO])
    }

    @Test
    fun `depois de concluido nao envia de novo`() {
        val vm = vm()
        vm.escolherTipo(TipoPedido.ACESSO)
        vm.enviar()
        vm.enviar()
        assertEquals(1, repositorio.abertos.size)
    }
}

/** Contrato com a API: rotas, métodos e corpos (MockWebServer). */
class RepositorioPrivacidadeRemotoTest {

    private lateinit var servidor: MockWebServer
    private lateinit var repositorio: RepositorioPrivacidadeRemoto

    @Before
    fun setUp() {
        servidor = MockWebServer().apply { start() }
        val tokens = object : ProvedorTokens {
            override fun accessTokenAtual() = "acc-1"
            override suspend fun renovar(accessUsado: String?): String? = null
        }
        val rede = Rede(servidor.url("/api/v1/").toString(), tokens, depuracao = false)
        repositorio = RepositorioPrivacidadeRemoto(rede.retrofit(rede.autenticado).create(AcessoApi::class.java), rede.json)
    }

    @After
    fun tearDown() = servidor.shutdown()

    private fun json(corpo: String, status: Int = 200) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(corpo)

    private val pedidoJson = """
        {"id_solicitacao":12,"tipo":"acesso","status":"negada","observacao":"Quero uma cópia",
         "data_solicitacao":"2026-11-08T22:00:00Z","data_prazo":"2026-11-23T22:00:00Z",
         "data_conclusao":"2026-11-10T10:00:00Z","justificativa_negativa":"Não localizamos o cadastro.",
         "campo_novo_ignorado":1}
    """.trimIndent()

    @Test
    fun `meus pedidos le a lista de me solicitacoes-lgpd`() = runTest {
        servidor.enqueue(json("[$pedidoJson]"))
        val pedidos = (repositorio.meusPedidos() as Resultado.Sucesso).valor
        val p = pedidos.single()
        assertEquals(12, p.id)
        assertEquals("negada", p.status)
        assertEquals("Não localizamos o cadastro.", p.justificativaNegativa)
        assertEquals("2026-11-23T22:00:00Z", p.dataPrazo)
        val chamada = servidor.takeRequest()
        assertEquals("GET", chamada.method)
        assertEquals("/api/v1/me/solicitacoes-lgpd", chamada.path)
        assertEquals("Bearer acc-1", chamada.getHeader("Authorization"))
    }

    @Test
    fun `abrir pedido envia o tipo e a observacao sem espacos nas pontas`() = runTest {
        servidor.enqueue(json(pedidoJson, 201))
        val r = repositorio.abrirPedido(TipoPedido.ACESSO, "  Quero uma cópia  ")
        assertTrue(r is Resultado.Sucesso)
        val chamada = servidor.takeRequest()
        assertEquals("POST", chamada.method)
        assertEquals("/api/v1/me/solicitacoes-lgpd", chamada.path)
        val corpo = chamada.body.readUtf8()
        assertTrue(corpo, corpo.contains("\"tipo\":\"acesso\""))
        assertTrue(corpo, corpo.contains("\"observacao\":\"Quero uma cópia\""))
    }

    @Test
    fun `observacao em branco nao e enviada`() = runTest {
        servidor.enqueue(json(pedidoJson, 201))
        repositorio.abrirPedido(TipoPedido.ELIMINACAO, "   ")
        val corpo = servidor.takeRequest().body.readUtf8()
        assertTrue(corpo, corpo.contains("\"tipo\":\"eliminacao\""))
        assertFalse("a API não aceita observação nula", corpo.contains("observacao"))
    }

    @Test
    fun `pedido repetido volta com o codigo solicitacao_em_aberto`() = runTest {
        servidor.enqueue(json("""{"detail":"Já existe um pedido em aberto.","codigo":"solicitacao_em_aberto"}""", 409))
        val erro = (repositorio.abrirPedido(TipoPedido.ACESSO, "") as Resultado.Falha).erro as ErroAcesso.Api
        assertEquals("solicitacao_em_aberto", erro.codigo)
        assertEquals(409, erro.status)
        assertTrue(erro.mensagem.contains("em andamento"))
    }

    @Test
    fun `servidor sem a migracao 010 mostra a mensagem de indisponivel`() = runTest {
        servidor.enqueue(json("""{"detail":"indisponível","codigo":"lgpd_indisponivel"}""", 503))
        val erro = (repositorio.meusPedidos() as Resultado.Falha).erro as ErroAcesso.Api
        assertEquals("lgpd_indisponivel", erro.codigo)
        assertTrue(erro.mensagem.contains("não estão disponíveis"))
    }

    @Test
    fun `copia dos dados vem formatada e sem alterar o conteudo`() = runTest {
        servidor.enqueue(json("""{"formato":1,"conta":{"nome":"Ana"},"dispositivos":[]}"""))
        val texto = (repositorio.copiaDosDados() as Resultado.Sucesso).valor
        assertTrue(texto, texto.contains("\n"))
        assertTrue(texto, texto.contains("\"formato\": 1"))
        assertTrue(texto, texto.contains("\"nome\": \"Ana\""))
        val chamada = servidor.takeRequest()
        assertEquals("GET", chamada.method)
        assertEquals("/api/v1/me/dados", chamada.path)
    }

    @Test
    fun `copia que nao e JSON e entregue como veio`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(200).setBody("texto simples"))
        assertEquals("texto simples", (repositorio.copiaDosDados() as Resultado.Sucesso).valor)
    }

    @Test
    fun `sem conexao a copia falha com a mensagem de rede`() = runTest {
        servidor.shutdown()
        val erro = (repositorio.copiaDosDados() as Resultado.Falha).erro
        assertEquals(ErroAcesso.SemConexao, erro)
    }
}
