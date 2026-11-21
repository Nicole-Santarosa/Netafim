package com.netafim.posvenda.acesso

import com.netafim.posvenda.acesso.dados.AcessoApi
import com.netafim.posvenda.acesso.dados.ProvedorTokens
import com.netafim.posvenda.acesso.dados.Rede
import com.netafim.posvenda.acesso.dados.RepositorioFilaPrivacidadeRemoto
import com.netafim.posvenda.acesso.dominio.AcoesDoPedido
import com.netafim.posvenda.acesso.dominio.ErroAcesso
import com.netafim.posvenda.acesso.dominio.Organizacao
import com.netafim.posvenda.acesso.dominio.PedidoAdmin
import com.netafim.posvenda.acesso.dominio.Perfil
import com.netafim.posvenda.acesso.dominio.RepositorioFilaPrivacidade
import com.netafim.posvenda.acesso.dominio.Resultado
import com.netafim.posvenda.acesso.dominio.Sessao
import com.netafim.posvenda.acesso.dominio.StatusPedido
import com.netafim.posvenda.acesso.dominio.acoesDoPedido
import com.netafim.posvenda.acesso.dominio.podeAtenderPedidosLgpd
import com.netafim.posvenda.acesso.ui.privacidade.AvisoPedido
import com.netafim.posvenda.acesso.ui.privacidade.FilaPrivacidadeViewModel
import com.netafim.posvenda.acesso.ui.privacidade.FiltroFila
import com.netafim.posvenda.acesso.ui.privacidade.LIMITE_JUSTIFICATIVA
import com.netafim.posvenda.acesso.ui.privacidade.PedidoAdminViewModel
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

private val ATOR_ADMIN = USUARIO_EXEMPLO.copy(
    id = 1, perfil = Perfil(1, "Netafim Admin", "netafim", "admin"), organizacao = Organizacao("netafim", null, "Netafim"),
)
private val ATOR_COMUM = USUARIO_EXEMPLO.copy(id = 2, perfil = Perfil(2, "Netafim Usuário", "netafim", "usuario"))
private val ATOR_DISTRIBUIDOR_ADMIN = USUARIO_EXEMPLO.copy(id = 3)

private fun pedidoAdmin(
    id: Int,
    tipo: String = "acesso",
    status: String = StatusPedido.RECEBIDA,
    idUsuario: Int? = 50,
    atrasada: Boolean = false,
) = PedidoAdmin(
    id = id, idUsuario = idUsuario, nomeTitular = if (idUsuario == null) null else "Titular $id",
    emailContato = "titular$id@exemplo.com.br", tipo = tipo, status = status, observacao = null,
    dataSolicitacao = "2026-11-01T12:00:00Z", dataPrazo = "2026-11-16T12:00:00Z", dataConclusao = null,
    idResponsavel = null, justificativaNegativa = null, atrasada = atrasada,
)

private class FilaFalsa : RepositorioFilaPrivacidade {
    var pedidos: Resultado<List<PedidoAdmin>> = Resultado.Sucesso(emptyList())
    var resposta: Resultado<PedidoAdmin>? = null
    var copia: Resultado<String> = Resultado.Sucesso("{}")
    val chamadas = mutableListOf<String>()

    private fun achar(id: Int) = (pedidos as Resultado.Sucesso).valor.first { it.id == id }

    override suspend fun fila() = pedidos

    override suspend fun analisar(idPedido: Int): Resultado<PedidoAdmin> {
        chamadas += "analisar:$idPedido"
        return resposta ?: Resultado.Sucesso(achar(idPedido).copy(status = StatusPedido.EM_ANALISE))
    }

    override suspend fun atender(idPedido: Int): Resultado<PedidoAdmin> {
        chamadas += "atender:$idPedido"
        return resposta ?: Resultado.Sucesso(achar(idPedido).copy(status = StatusPedido.ATENDIDA))
    }

    override suspend fun negar(idPedido: Int, justificativa: String): Resultado<PedidoAdmin> {
        chamadas += "negar:$idPedido:$justificativa"
        return resposta ?: Resultado.Sucesso(achar(idPedido).copy(status = StatusPedido.NEGADA, justificativaNegativa = justificativa))
    }

    override suspend fun dadosDoTitular(idPedido: Int): Resultado<String> {
        chamadas += "dados:$idPedido"
        return copia
    }
}

/** Quais botões o pedido mostra (regra pura, sem tela). */
class AcoesDoPedidoTest {

    @Test
    fun `so o Netafim Admin atende pedidos de titulares`() {
        assertTrue(ATOR_ADMIN.podeAtenderPedidosLgpd())
        assertFalse(ATOR_COMUM.podeAtenderPedidosLgpd())
        assertFalse(ATOR_DISTRIBUIDOR_ADMIN.podeAtenderPedidosLgpd())
        assertEquals(AcoesDoPedido(), acoesDoPedido(ATOR_COMUM, pedidoAdmin(1)))
        assertEquals(AcoesDoPedido(), acoesDoPedido(ATOR_DISTRIBUIDOR_ADMIN, pedidoAdmin(1)))
    }

    @Test
    fun `pedido recebido pode ser analisado, atendido ou negado`() {
        val a = acoesDoPedido(ATOR_ADMIN, pedidoAdmin(1, "correcao", StatusPedido.RECEBIDA))
        assertTrue(a.analisar && a.atender && a.negar)
        assertFalse("correção não tem cópia de dados", a.verDados)
    }

    @Test
    fun `pedido em analise nao pode ser analisado de novo`() {
        val a = acoesDoPedido(ATOR_ADMIN, pedidoAdmin(1, status = StatusPedido.EM_ANALISE))
        assertFalse(a.analisar)
        assertTrue(a.atender && a.negar)
    }

    @Test
    fun `pedido concluido nao muda`() {
        val atendido = acoesDoPedido(ATOR_ADMIN, pedidoAdmin(1, "acesso", StatusPedido.ATENDIDA))
        assertFalse(atendido.analisar || atendido.atender || atendido.negar)
        val negado = acoesDoPedido(ATOR_ADMIN, pedidoAdmin(2, "acesso", StatusPedido.NEGADA))
        assertEquals(AcoesDoPedido(), negado)
    }

    @Test
    fun `a copia dos dados so existe para acesso e portabilidade de titular com conta`() {
        assertTrue(acoesDoPedido(ATOR_ADMIN, pedidoAdmin(1, "acesso")).verDados)
        assertTrue(acoesDoPedido(ATOR_ADMIN, pedidoAdmin(2, "portabilidade", StatusPedido.ATENDIDA)).verDados)
        assertFalse(acoesDoPedido(ATOR_ADMIN, pedidoAdmin(3, "confirmacao")).verDados)
        assertFalse("conta anonimizada não tem o que exportar", acoesDoPedido(ATOR_ADMIN, pedidoAdmin(4, "acesso", idUsuario = null)).verDados)
    }

    @Test
    fun `o administrador nao atende o proprio pedido de anonimizacao ou eliminacao mas pode negar`() {
        listOf("anonimizacao", "eliminacao").forEach { tipo ->
            val a = acoesDoPedido(ATOR_ADMIN, pedidoAdmin(1, tipo, idUsuario = ATOR_ADMIN.id))
            assertFalse("$tipo próprio não é atendido pelo mesmo administrador", a.atender)
            assertTrue(a.negar)
        }
        // o pedido de outra pessoa e o próprio pedido de um tipo que não apaga a conta seguem normais
        assertTrue(acoesDoPedido(ATOR_ADMIN, pedidoAdmin(2, "anonimizacao", idUsuario = 50)).atender)
        assertTrue(acoesDoPedido(ATOR_ADMIN, pedidoAdmin(3, "acesso", idUsuario = ATOR_ADMIN.id)).atender)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FilaPrivacidadeViewModelTest {

    private val fila = FilaFalsa()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun lista() = listOf(
        pedidoAdmin(1, status = StatusPedido.RECEBIDA, atrasada = true),
        pedidoAdmin(2, status = StatusPedido.EM_ANALISE),
        pedidoAdmin(3, status = StatusPedido.ATENDIDA),
        pedidoAdmin(4, status = StatusPedido.NEGADA),
    )

    @Test
    fun `mostra os abertos por padrao na ordem da API`() {
        fila.pedidos = Resultado.Sucesso(lista())
        val e = FilaPrivacidadeViewModel(fila).estado.value
        assertEquals(FiltroFila.ABERTOS, e.filtro)
        assertEquals(listOf(1, 2), e.visiveis.map { it.id })
        assertFalse(e.carregando)
    }

    @Test
    fun `cada filtro mostra o seu recorte`() {
        fila.pedidos = Resultado.Sucesso(lista())
        val vm = FilaPrivacidadeViewModel(fila)
        vm.escolherFiltro(FiltroFila.ATENDIDOS)
        assertEquals(listOf(3), vm.estado.value.visiveis.map { it.id })
        vm.escolherFiltro(FiltroFila.NEGADOS)
        assertEquals(listOf(4), vm.estado.value.visiveis.map { it.id })
        vm.escolherFiltro(FiltroFila.TODOS)
        assertEquals(listOf(1, 2, 3, 4), vm.estado.value.visiveis.map { it.id })
    }

    @Test
    fun `conta os pedidos fora do prazo`() {
        fila.pedidos = Resultado.Sucesso(lista())
        assertEquals(1, FilaPrivacidadeViewModel(fila).estado.value.atrasados)
    }

    @Test
    fun `falha ao carregar mostra o erro e permite tentar de novo`() {
        fila.pedidos = Resultado.Falha(ErroAcesso.Api("sem_permissao", "Somente o Administrador Netafim atende pedidos de titulares.", 403))
        val vm = FilaPrivacidadeViewModel(fila)
        assertEquals("Somente o Administrador Netafim atende pedidos de titulares.", vm.estado.value.erro)
        assertFalse(vm.estado.value.carregando)

        fila.pedidos = Resultado.Sucesso(lista())
        vm.carregar()
        assertNull(vm.estado.value.erro)
        assertEquals(4, vm.estado.value.pedidos.size)
    }

    @Test
    fun `ao retomar atualiza a lista sem mostrar carregamento e mantem o filtro`() {
        fila.pedidos = Resultado.Sucesso(lista())
        val vm = FilaPrivacidadeViewModel(fila)
        vm.escolherFiltro(FiltroFila.TODOS)
        fila.pedidos = Resultado.Sucesso(lista() + pedidoAdmin(5))
        vm.aoRetomar()
        assertEquals(5, vm.estado.value.visiveis.size)
        assertEquals(FiltroFila.TODOS, vm.estado.value.filtro)
        assertFalse(vm.estado.value.carregando)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PedidoAdminViewModelTest {

    private val fila = FilaFalsa()
    private val acesso = RepositorioFalso().also { it.sessao.value = Sessao("a", "r", ATOR_ADMIN) }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(id: Int = 1): PedidoAdminViewModel = PedidoAdminViewModel(fila, acesso, id)

    @Test
    fun `carrega o pedido da lista e calcula as acoes`() {
        fila.pedidos = Resultado.Sucesso(listOf(pedidoAdmin(1, "acesso"), pedidoAdmin(2)))
        val e = vm(1).estado.value
        assertEquals(1, e.pedido?.id)
        assertTrue(e.acoes.analisar && e.acoes.atender && e.acoes.negar && e.acoes.verDados)
        assertFalse(e.carregando)
    }

    @Test
    fun `pedido que nao esta na lista mostra o erro`() {
        fila.pedidos = Resultado.Sucesso(listOf(pedidoAdmin(2)))
        val e = vm(1).estado.value
        assertNull(e.pedido)
        assertEquals("Pedido não encontrado.", e.erro)
    }

    @Test
    fun `falha ao carregar permite tentar de novo`() {
        fila.pedidos = Resultado.Falha(ErroAcesso.SemConexao)
        val vm = vm()
        assertEquals(ErroAcesso.SemConexao.mensagem, vm.estado.value.erro)
        fila.pedidos = Resultado.Sucesso(listOf(pedidoAdmin(1)))
        vm.carregar()
        assertNotNull(vm.estado.value.pedido)
    }

    @Test
    fun `analisar atualiza o pedido e avisa`() {
        fila.pedidos = Resultado.Sucesso(listOf(pedidoAdmin(1)))
        val vm = vm()
        vm.analisar()
        assertEquals(listOf("analisar:1"), fila.chamadas)
        assertEquals(StatusPedido.EM_ANALISE, vm.estado.value.pedido?.status)
        assertEquals(AvisoPedido.EM_ANALISE, vm.estado.value.aviso)
        assertFalse("depois de analisado o botão some", vm.estado.value.acoes.analisar)
    }

    @Test
    fun `atender pede confirmacao e so chama a API depois dela`() {
        fila.pedidos = Resultado.Sucesso(listOf(pedidoAdmin(1)))
        val vm = vm()
        vm.pedirAtender()
        assertTrue(vm.estado.value.confirmandoAtender)
        assertTrue(fila.chamadas.isEmpty())

        vm.cancelarAtender()
        assertFalse(vm.estado.value.confirmandoAtender)
        assertTrue(fila.chamadas.isEmpty())

        vm.pedirAtender()
        vm.confirmarAtender()
        assertEquals(listOf("atender:1"), fila.chamadas)
        assertEquals(StatusPedido.ATENDIDA, vm.estado.value.pedido?.status)
        assertEquals(AvisoPedido.ATENDIDO, vm.estado.value.aviso)
        assertFalse(vm.estado.value.confirmandoAtender)
        assertEquals("concluído: nada mais a fazer além da cópia", AcoesDoPedido(verDados = true), vm.estado.value.acoes)
    }

    @Test
    fun `confirmar sem ter pedido a confirmacao nao atende`() {
        fila.pedidos = Resultado.Sucesso(listOf(pedidoAdmin(1)))
        val vm = vm()
        vm.confirmarAtender()
        assertTrue(fila.chamadas.isEmpty())
    }

    @Test
    fun `atender anonimizacao ou eliminacao avisa que nao ha volta`() {
        fila.pedidos = Resultado.Sucesso(listOf(pedidoAdmin(1, "anonimizacao"), pedidoAdmin(2, "eliminacao"), pedidoAdmin(3, "acesso")))
        assertTrue(vm(1).estado.value.atenderAnonimiza)
        assertTrue(vm(2).estado.value.atenderAnonimiza)
        assertFalse(vm(3).estado.value.atenderAnonimiza)
    }

    @Test
    fun `negar exige o motivo`() {
        fila.pedidos = Resultado.Sucesso(listOf(pedidoAdmin(1)))
        val vm = vm()
        vm.pedirNegar()
        vm.mudarJustificativa("   ")
        vm.confirmarNegar()
        assertTrue(fila.chamadas.isEmpty())
        assertNotNull(vm.estado.value.erroJustificativa)
        assertTrue("o diálogo continua aberto", vm.estado.value.negando)

        vm.mudarJustificativa("a".repeat(LIMITE_JUSTIFICATIVA + 1))
        vm.confirmarNegar()
        assertTrue(fila.chamadas.isEmpty())
        assertNotNull(vm.estado.value.erroJustificativa)
    }

    @Test
    fun `negar envia o motivo sem espacos nas pontas e mostra o resultado`() {
        fila.pedidos = Resultado.Sucesso(listOf(pedidoAdmin(1)))
        val vm = vm()
        vm.pedirNegar()
        vm.mudarJustificativa("  Não localizamos o cadastro.  ")
        vm.confirmarNegar()
        assertEquals(listOf("negar:1:Não localizamos o cadastro."), fila.chamadas)
        assertEquals(StatusPedido.NEGADA, vm.estado.value.pedido?.status)
        assertEquals("Não localizamos o cadastro.", vm.estado.value.pedido?.justificativaNegativa)
        assertEquals(AvisoPedido.NEGADO, vm.estado.value.aviso)
        assertFalse(vm.estado.value.negando)
    }

    @Test
    fun `recusa da API aparece como erro e fecha os dialogos`() {
        fila.pedidos = Resultado.Sucesso(listOf(pedidoAdmin(1, "anonimizacao")))
        fila.resposta = Resultado.Falha(
            ErroAcesso.Api("proprio_pedido", "Outro administrador precisa atender o seu pedido.", 403),
        )
        val vm = vm()
        vm.pedirAtender()
        vm.confirmarAtender()
        val e = vm.estado.value
        assertEquals("Outro administrador precisa atender o seu pedido.", e.erroAcao)
        assertFalse(e.confirmandoAtender)
        assertFalse(e.processando)
        assertEquals("o pedido continua como estava", StatusPedido.RECEBIDA, e.pedido?.status)
    }

    @Test
    fun `baixar dados guarda a copia ate a tela entregar`() {
        fila.pedidos = Resultado.Sucesso(listOf(pedidoAdmin(1, "acesso")))
        fila.copia = Resultado.Sucesso("""{"formato":1}""")
        val vm = vm()
        vm.baixarDados()
        assertEquals(listOf("dados:1"), fila.chamadas)
        assertEquals("""{"formato":1}""", vm.estado.value.copia)
        assertFalse(vm.estado.value.baixando)
        vm.copiaEntregue()
        assertNull(vm.estado.value.copia)
    }

    @Test
    fun `falha ao baixar dados mostra a mensagem`() {
        fila.pedidos = Resultado.Sucesso(listOf(pedidoAdmin(1, "acesso")))
        fila.copia = Resultado.Falha(ErroAcesso.Api("titular_sem_conta", "O titular deste pedido não tem conta no sistema.", 400))
        val vm = vm()
        vm.baixarDados()
        assertEquals("O titular deste pedido não tem conta no sistema.", vm.estado.value.erroAcao)
        assertNull(vm.estado.value.copia)
        assertFalse(vm.estado.value.baixando)
    }
}

/** Contrato com a API: rotas, métodos e corpos (MockWebServer). */
class RepositorioFilaPrivacidadeRemotoTest {

    private lateinit var servidor: MockWebServer
    private lateinit var repositorio: RepositorioFilaPrivacidadeRemoto

    @Before
    fun setUp() {
        servidor = MockWebServer().apply { start() }
        val tokens = object : ProvedorTokens {
            override fun accessTokenAtual() = "acc-1"
            override suspend fun renovar(accessUsado: String?): String? = null
        }
        val rede = Rede(servidor.url("/api/v1/").toString(), tokens, depuracao = false)
        repositorio = RepositorioFilaPrivacidadeRemoto(rede.retrofit(rede.autenticado).create(AcessoApi::class.java), rede.json)
    }

    @After
    fun tearDown() = servidor.shutdown()

    private fun json(corpo: String, status: Int = 200) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(corpo)

    private val pedidoJson = """
        {"id_solicitacao":12,"id_usuario":50,"nome_titular":"Ana Souza","email_contato":"ana@agroirriga.com.br",
         "tipo":"acesso","status":"em_analise","observacao":"Quero uma cópia",
         "data_solicitacao":"2026-11-08T22:00:00Z","data_prazo":"2026-11-23T22:00:00Z","data_conclusao":null,
         "id_responsavel":1,"justificativa_negativa":null,"atrasada":true,"campo_novo_ignorado":1}
    """.trimIndent()

    @Test
    fun `fila le a lista de solicitacoes-lgpd`() = runTest {
        servidor.enqueue(json("[$pedidoJson]"))
        val p = (repositorio.fila() as Resultado.Sucesso).valor.single()
        assertEquals(12, p.id)
        assertEquals(50, p.idUsuario)
        assertEquals("Ana Souza", p.nomeTitular)
        assertEquals("ana@agroirriga.com.br", p.emailContato)
        assertTrue(p.atrasada)
        val chamada = servidor.takeRequest()
        assertEquals("GET", chamada.method)
        assertEquals("/api/v1/solicitacoes-lgpd", chamada.path)
        assertEquals("Bearer acc-1", chamada.getHeader("Authorization"))
    }

    @Test
    fun `titular anonimizado volta com nome e usuario nulos`() = runTest {
        servidor.enqueue(json("""[{"id_solicitacao":3,"id_usuario":null,"nome_titular":null,"email_contato":"","tipo":"eliminacao","status":"atendida","data_solicitacao":"2026-11-01T00:00:00Z","data_prazo":"2026-11-16T00:00:00Z"}]"""))
        val p = (repositorio.fila() as Resultado.Sucesso).valor.single()
        assertNull(p.idUsuario)
        assertNull(p.nomeTitular)
        assertFalse(p.atrasada)
    }

    @Test
    fun `analisar e atender usam POST em solicitacoes-lgpd id`() = runTest {
        servidor.enqueue(json(pedidoJson))
        servidor.enqueue(json(pedidoJson.replace("em_analise", "atendida")))
        assertTrue(repositorio.analisar(12) is Resultado.Sucesso)
        val atendido = (repositorio.atender(12) as Resultado.Sucesso).valor
        assertEquals(StatusPedido.ATENDIDA, atendido.status)
        val primeira = servidor.takeRequest()
        assertEquals("POST", primeira.method)
        assertEquals("/api/v1/solicitacoes-lgpd/12/analisar", primeira.path)
        val segunda = servidor.takeRequest()
        assertEquals("POST", segunda.method)
        assertEquals("/api/v1/solicitacoes-lgpd/12/atender", segunda.path)
    }

    @Test
    fun `negar envia a justificativa sem espacos nas pontas`() = runTest {
        servidor.enqueue(json(pedidoJson.replace("em_analise", "negada")))
        repositorio.negar(12, "  Não localizamos o cadastro.  ")
        val chamada = servidor.takeRequest()
        assertEquals("POST", chamada.method)
        assertEquals("/api/v1/solicitacoes-lgpd/12/negar", chamada.path)
        assertTrue(chamada.body.readUtf8().contains("\"justificativa\":\"Não localizamos o cadastro.\""))
    }

    @Test
    fun `dados do titular vem formatados`() = runTest {
        servidor.enqueue(json("""{"formato":1,"conta":{"nome":"Ana"}}"""))
        val texto = (repositorio.dadosDoTitular(12) as Resultado.Sucesso).valor
        assertTrue(texto, texto.contains("\"formato\": 1"))
        val chamada = servidor.takeRequest()
        assertEquals("GET", chamada.method)
        assertEquals("/api/v1/solicitacoes-lgpd/12/dados", chamada.path)
    }

    @Test
    fun `o proprio pedido de anonimizacao volta com o codigo proprio_pedido`() = runTest {
        servidor.enqueue(json("""{"detail":"Outro administrador precisa atender o seu pedido.","codigo":"proprio_pedido"}""", 403))
        val erro = (repositorio.atender(12) as Resultado.Falha).erro as ErroAcesso.Api
        assertEquals("proprio_pedido", erro.codigo)
        assertEquals(403, erro.status)
        assertEquals("Outro administrador precisa atender o seu pedido.", erro.mensagem)
    }

    @Test
    fun `pedido ja concluido volta com 409`() = runTest {
        servidor.enqueue(json("""{"detail":"Este pedido já foi concluído.","codigo":"solicitacao_encerrada"}""", 409))
        val erro = (repositorio.negar(12, "motivo") as Resultado.Falha).erro as ErroAcesso.Api
        assertEquals("solicitacao_encerrada", erro.codigo)
        assertEquals(409, erro.status)
    }
}
