package com.netafim.posvenda.acesso

import com.netafim.posvenda.acesso.dados.AcessoApi
import com.netafim.posvenda.acesso.dados.ProvedorTokens
import com.netafim.posvenda.acesso.dados.Rede
import com.netafim.posvenda.acesso.dados.RepositorioPerfisRemoto
import com.netafim.posvenda.acesso.dominio.ErroAcesso
import com.netafim.posvenda.acesso.dominio.Perfil
import com.netafim.posvenda.acesso.dominio.PerfilComPermissoes
import com.netafim.posvenda.acesso.dominio.PermissaoDoSistema
import com.netafim.posvenda.acesso.dominio.Permissoes
import com.netafim.posvenda.acesso.dominio.RepositorioPerfis
import com.netafim.posvenda.acesso.dominio.Resultado
import com.netafim.posvenda.acesso.dominio.permissoesEssenciais
import com.netafim.posvenda.acesso.dominio.podeGerenciarPermissoes
import com.netafim.posvenda.acesso.ui.permissoes.PerfisViewModel
import com.netafim.posvenda.acesso.ui.permissoes.PermissoesPerfilViewModel
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private val ADMIN_NETAFIM = Perfil(1, "Netafim Admin", "netafim", "admin")
private val DISTRIBUIDOR_ADMIN = Perfil(3, "Distribuidor Admin", "distribuidor", "admin")
private val DISTRIBUIDOR_USUARIO = Perfil(4, "Distribuidor Usuário", "distribuidor", "usuario")
private val CLIENTE_ADMIN = Perfil(5, "Cliente Admin", "cliente", "admin")

private fun comPermissoes(perfil: Perfil, vararg codigos: String) = PerfilComPermissoes(perfil, null, codigos.toSet())

private val CATALOGO = listOf(
    Permissoes.ABRIR_ATENDIMENTO, Permissoes.CRIAR_USUARIO, Permissoes.APROVAR_CADASTRO, Permissoes.DEFINIR_PERFIL,
).map { PermissaoDoSistema(it, it) }

/** Regras de quem gerencia permissões e das permissões que não podem ser retiradas. */
class RegrasDePermissoesTest {

    @Test
    fun `so o Netafim Admin gerencia permissoes`() {
        assertTrue(pessoaComPerfil(ADMIN_NETAFIM).podeGerenciarPermissoes())
        assertFalse(pessoaComPerfil(DISTRIBUIDOR_ADMIN).podeGerenciarPermissoes())
        assertFalse(pessoaComPerfil(Perfil(2, "Netafim Usuário", "netafim", "usuario")).podeGerenciarPermissoes())
        assertFalse(pessoaComPerfil(null).podeGerenciarPermissoes())
    }

    @Test
    fun `aprovar cadastros e definir perfis sao essenciais so para o Netafim Admin`() {
        assertEquals(setOf(Permissoes.APROVAR_CADASTRO, Permissoes.DEFINIR_PERFIL), permissoesEssenciais(ADMIN_NETAFIM))
        assertTrue(permissoesEssenciais(DISTRIBUIDOR_ADMIN).isEmpty())
    }

    private fun pessoaComPerfil(perfil: Perfil?) = USUARIO_EXEMPLO.copy(perfil = perfil)
}

/** Contrato com a API: rotas, métodos e corpos (MockWebServer). */
class RepositorioPerfisRemotoTest {

    private lateinit var servidor: MockWebServer
    private lateinit var repositorio: RepositorioPerfisRemoto

    @Before
    fun setUp() {
        servidor = MockWebServer().apply { start() }
        val tokens = object : ProvedorTokens {
            override fun accessTokenAtual() = "acc-1"
            override suspend fun renovar(accessUsado: String?): String? = null
        }
        val rede = Rede(servidor.url("/api/v1/").toString(), tokens, depuracao = false)
        repositorio = RepositorioPerfisRemoto(rede.retrofit(rede.autenticado).create(AcessoApi::class.java), rede.json)
    }

    @After
    fun tearDown() = servidor.shutdown()

    private fun json(corpo: String, status: Int = 200) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(corpo)

    private val perfilJson =
        """{"id_perfil":1,"nome_perfil":"Netafim Admin","tipo_organizacao":"netafim","nivel":"admin","descricao":"Acesso total","permissoes":["aprovar_cadastro","definir_perfil"]}"""

    @Test
    fun `perfis traz as permissoes de cada um`() = runTest {
        servidor.enqueue(json("[$perfilJson]"))
        val lista = (repositorio.perfis() as Resultado.Sucesso).valor
        assertEquals(setOf("aprovar_cadastro", "definir_perfil"), lista.single().permissoes)
        assertEquals("Acesso total", lista.single().descricao)
        assertEquals("/api/v1/perfis", servidor.takeRequest().path)
    }

    @Test
    fun `permissoes lista o catalogo do sistema`() = runTest {
        servidor.enqueue(json("""[{"id_permissao":1,"codigo":"abrir_atendimento","descricao":"Abrir atendimento"}]"""))
        val lista = (repositorio.permissoes() as Resultado.Sucesso).valor
        assertEquals(PermissaoDoSistema("abrir_atendimento", "Abrir atendimento"), lista.single())
        assertEquals("/api/v1/permissoes", servidor.takeRequest().path)
    }

    @Test
    fun `salvar envia as permissoes ordenadas em PUT perfis id permissoes`() = runTest {
        servidor.enqueue(json(perfilJson))
        val r = repositorio.salvarPermissoes(1, setOf("definir_perfil", "aprovar_cadastro"))
        assertTrue(r is Resultado.Sucesso)
        val pedido = servidor.takeRequest()
        assertEquals("PUT", pedido.method)
        assertEquals("/api/v1/perfis/1/permissoes", pedido.path)
        assertTrue(pedido.body.readUtf8().contains("\"permissoes\":[\"aprovar_cadastro\",\"definir_perfil\"]"))
    }

    @Test
    fun `recusa de permissao essencial volta com a mensagem da API`() = runTest {
        servidor.enqueue(json("""{"detail":"O Netafim Admin não pode perder aprovar cadastros e definir perfis.","codigo":"permissao_essencial"}""", 400))
        val erro = (repositorio.salvarPermissoes(1, emptySet()) as Resultado.Falha).erro as ErroAcesso.Api
        assertEquals("permissao_essencial", erro.codigo)
    }
}

private class PerfisFalso : RepositorioPerfis {
    var lista = listOf(
        comPermissoes(CLIENTE_ADMIN, Permissoes.ABRIR_ATENDIMENTO),
        comPermissoes(DISTRIBUIDOR_USUARIO, Permissoes.ABRIR_ATENDIMENTO),
        comPermissoes(ADMIN_NETAFIM, Permissoes.APROVAR_CADASTRO, Permissoes.DEFINIR_PERFIL),
        comPermissoes(DISTRIBUIDOR_ADMIN, Permissoes.ABRIR_ATENDIMENTO, Permissoes.CRIAR_USUARIO),
    )
    var resultadoPerfis: Resultado<List<PerfilComPermissoes>>? = null
    var resultadoSalvar: Resultado<PerfilComPermissoes>? = null
    val salvos = mutableListOf<Pair<Int, Set<String>>>()

    override suspend fun perfis(): Resultado<List<PerfilComPermissoes>> = resultadoPerfis ?: Resultado.Sucesso(lista)
    override suspend fun permissoes(): Resultado<List<PermissaoDoSistema>> = Resultado.Sucesso(CATALOGO.reversed())
    override suspend fun salvarPermissoes(idPerfil: Int, codigos: Set<String>): Resultado<PerfilComPermissoes> {
        salvos += idPerfil to codigos
        return resultadoSalvar ?: Resultado.Sucesso(lista.first { it.perfil.id == idPerfil }.copy(permissoes = codigos))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PermissoesViewModelsTest {

    private val perfis = PerfisFalso()
    private val acesso = RepositorioFalso()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `lista ordena por organizacao, admin antes do usuario`() {
        val vm = PerfisViewModel(perfis)
        assertEquals(
            listOf("Netafim Admin", "Distribuidor Admin", "Distribuidor Usuário", "Cliente Admin"),
            vm.estado.value.perfis.map { it.perfil.nome },
        )
    }

    @Test
    fun `falha ao listar mostra o erro e permite tentar de novo`() {
        perfis.resultadoPerfis = Resultado.Falha(ErroAcesso.SemConexao)
        val vm = PerfisViewModel(perfis)
        assertEquals(ErroAcesso.SemConexao.mensagem, vm.estado.value.erro)

        perfis.resultadoPerfis = null
        vm.carregar()
        assertEquals(4, vm.estado.value.perfis.size)
    }

    @Test
    fun `ao retomar a lista se atualiza sem piscar`() {
        val vm = PerfisViewModel(perfis)
        perfis.lista = perfis.lista.map { if (it.perfil.id == 3) it.copy(permissoes = setOf(Permissoes.ABRIR_ATENDIMENTO)) else it }
        vm.aoRetomar()
        assertEquals(1, vm.estado.value.perfis.first { it.perfil.id == 3 }.permissoes.size)
        assertFalse(vm.estado.value.carregando)
    }

    private fun edicao(idPerfil: Int) = PermissoesPerfilViewModel(perfis, acesso, idPerfil)

    @Test
    fun `carrega o perfil e o catalogo e comeca sem mudanca`() {
        val e = edicao(3).estado.value
        assertEquals("Distribuidor Admin", e.perfil?.perfil?.nome)
        assertEquals(setOf(Permissoes.ABRIR_ATENDIMENTO, Permissoes.CRIAR_USUARIO), e.selecionadas)
        assertFalse(e.mudou)
        assertEquals(4, e.catalogo.size)
    }

    @Test
    fun `perfil que nao existe mostra erro`() {
        val e = edicao(999).estado.value
        assertEquals("Perfil não encontrado.", e.erro)
        assertEquals(null, e.perfil)
    }

    @Test
    fun `marcar e desmarcar muda o conjunto e liga o salvar`() {
        val vm = edicao(3)
        vm.alternar(Permissoes.DEFINIR_PERFIL)
        vm.alternar(Permissoes.CRIAR_USUARIO)
        assertEquals(setOf(Permissoes.ABRIR_ATENDIMENTO, Permissoes.DEFINIR_PERFIL), vm.estado.value.selecionadas)
        assertTrue(vm.estado.value.mudou)

        vm.alternar(Permissoes.DEFINIR_PERFIL)
        vm.alternar(Permissoes.CRIAR_USUARIO)
        assertFalse("voltou ao original", vm.estado.value.mudou)
    }

    @Test
    fun `permissoes essenciais do Netafim Admin nao podem ser desmarcadas`() {
        val vm = edicao(1)
        assertEquals(setOf(Permissoes.APROVAR_CADASTRO, Permissoes.DEFINIR_PERFIL), vm.estado.value.essenciais)
        vm.alternar(Permissoes.APROVAR_CADASTRO)
        assertTrue(Permissoes.APROVAR_CADASTRO in vm.estado.value.selecionadas)
        assertFalse(vm.estado.value.mudou)
    }

    @Test
    fun `salvar sem mudanca nao chama a API`() {
        val vm = edicao(3)
        vm.salvar()
        assertTrue(perfis.salvos.isEmpty())
        assertFalse(vm.estado.value.salvo)
    }

    @Test
    fun `salvar envia o conjunto, atualiza o perfil de quem esta logado e volta`() {
        val vm = edicao(3)
        vm.alternar(Permissoes.DEFINIR_PERFIL)

        vm.salvar()

        assertEquals(listOf(3 to setOf(Permissoes.ABRIR_ATENDIMENTO, Permissoes.CRIAR_USUARIO, Permissoes.DEFINIR_PERFIL)), perfis.salvos)
        assertEquals("a pessoa logada pode ter ganhado ou perdido permissão", 1, acesso.atualizacoesDePerfil)
        assertTrue(vm.estado.value.salvo)
        assertFalse(vm.estado.value.salvando)
    }

    @Test
    fun `recusa da API aparece como erro e nao volta nem atualiza o perfil`() {
        perfis.resultadoSalvar = Resultado.Falha(ErroAcesso.Api("permissao_essencial", "O Netafim Admin não pode perder aprovar cadastros e definir perfis.", 400))
        val vm = edicao(3)
        vm.alternar(Permissoes.DEFINIR_PERFIL)

        vm.salvar()

        assertNotNull(vm.estado.value.erroAcao)
        assertFalse(vm.estado.value.salvo)
        assertEquals(0, acesso.atualizacoesDePerfil)
        assertTrue("o que a pessoa marcou fica", Permissoes.DEFINIR_PERFIL in vm.estado.value.selecionadas)
    }
}
