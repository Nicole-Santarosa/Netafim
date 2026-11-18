package com.example.netafim.acesso

import com.example.netafim.acesso.dados.AcessoApi
import com.example.netafim.acesso.dados.ProvedorTokens
import com.example.netafim.acesso.dados.Rede
import com.example.netafim.acesso.dados.RepositorioDominiosRemoto
import com.example.netafim.acesso.dominio.DadosAprovacao
import com.example.netafim.acesso.dominio.DadosNovoDominio
import com.example.netafim.acesso.dominio.Dominio
import com.example.netafim.acesso.dominio.ErroAcesso
import com.example.netafim.acesso.dominio.Opcao
import com.example.netafim.acesso.dominio.OpcoesAprovacao
import com.example.netafim.acesso.dominio.Organizacao
import com.example.netafim.acesso.dominio.Perfil
import com.example.netafim.acesso.dominio.RepositorioCadastros
import com.example.netafim.acesso.dominio.RepositorioDominios
import com.example.netafim.acesso.dominio.Resultado
import com.example.netafim.acesso.dominio.TiposOrganizacao
import com.example.netafim.acesso.dominio.Usuario
import com.example.netafim.acesso.dominio.ValidacaoDominio
import com.example.netafim.acesso.dominio.podeGerenciarDominios
import com.example.netafim.acesso.ui.dominios.AvisoDominio
import com.example.netafim.acesso.ui.dominios.DominiosViewModel
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

private fun admin(tipo: String, nivel: String) = Usuario(
    id = 1, nome = "A", email = "a@x.com", telefone = null, situacao = "ativo",
    perfil = Perfil(1, "$tipo $nivel", tipo, nivel), organizacao = Organizacao(tipo, null, "Org"),
    permissoes = emptySet(), exigeTrocaSenha = false,
)

class ValidacaoDominioTest {
    @Test
    fun `aceita dominios comuns e normaliza`() {
        assertNull(ValidacaoDominio.validar("netafim.com.br"))
        assertNull(ValidacaoDominio.validar("  @Agro-Irriga.COM.br "))
        assertEquals("agro-irriga.com.br", ValidacaoDominio.normalizar("  @Agro-Irriga.COM.br "))
    }

    @Test
    fun `recusa vazio, sem ponto, com caracteres invalidos e muito longo`() {
        assertEquals("Informe o domínio.", ValidacaoDominio.validar("   "))
        assertEquals("Informe o domínio.", ValidacaoDominio.validar("@"))
        listOf("netafim", "a@b.com", "empresa..com", "-empresa.com", "empresa-.com", "empresa com.br", "http://empresa.com", "empresa.com/x", ".com")
            .forEach { assertNotNull(it, ValidacaoDominio.validar(it)) }
        assertEquals("Domínio muito longo.", ValidacaoDominio.validar("a".repeat(118) + ".com"))
    }

    @Test
    fun `so o Netafim Admin gerencia dominios`() {
        assertTrue(admin("netafim", "admin").podeGerenciarDominios())
        assertFalse(admin("netafim", "usuario").podeGerenciarDominios())
        assertFalse(admin("distribuidor", "admin").podeGerenciarDominios())
        assertFalse(admin("cliente", "admin").podeGerenciarDominios())
    }
}

private class DominiosFalso : RepositorioDominios {
    var lista: Resultado<List<Dominio>> = Resultado.Sucesso(
        listOf(
            Dominio(2, "agroirriga.com.br", TiposOrganizacao.DISTRIBUIDOR, 3, true),
            Dominio(1, "netafim.com.br", TiposOrganizacao.NETAFIM, null, true),
        ),
    )
    var resultadoCriar: Resultado<Dominio>? = null
    var resultadoAtivo: Resultado<Dominio>? = null
    var resultadoExcluir: Resultado<Unit> = Resultado.Sucesso(Unit)
    val chamadas = mutableListOf<String>()
    val criados = mutableListOf<DadosNovoDominio>()

    override suspend fun listar(): Resultado<List<Dominio>> = lista.also { chamadas += "listar" }

    override suspend fun criar(dados: DadosNovoDominio): Resultado<Dominio> {
        chamadas += "criar"
        criados += dados
        return resultadoCriar ?: Resultado.Sucesso(Dominio(9, dados.dominio, dados.tipoOrganizacao, dados.idDistribuidor, true))
    }

    override suspend fun alterarAtivo(idDominio: Int, ativo: Boolean): Resultado<Dominio> {
        chamadas += "ativo:$idDominio:$ativo"
        return resultadoAtivo ?: Resultado.Sucesso(Dominio(idDominio, "netafim.com.br", TiposOrganizacao.NETAFIM, null, ativo))
    }

    override suspend fun excluir(idDominio: Int): Resultado<Unit> {
        chamadas += "excluir:$idDominio"
        return resultadoExcluir
    }
}

private class CadastrosParaDominiosFalso(var opcoes: Resultado<OpcoesAprovacao>) : RepositorioCadastros {
    override suspend fun pendentes(): Resultado<List<Usuario>> = Resultado.Sucesso(emptyList())
    override suspend fun opcoes(): Resultado<OpcoesAprovacao> = opcoes
    override suspend fun aprovar(idUsuario: Int, dados: DadosAprovacao): Resultado<Usuario> = Resultado.Falha(ErroAcesso.SemConexao)
    override suspend fun recusar(idUsuario: Int): Resultado<Usuario> = Resultado.Falha(ErroAcesso.SemConexao)
}

@OptIn(ExperimentalCoroutinesApi::class)
class DominiosViewModelTest {
    private val repo = DominiosFalso()
    private val cadastros = CadastrosParaDominiosFalso(
        Resultado.Sucesso(OpcoesAprovacao(emptyList(), listOf(Opcao(3, "Agro Irriga Ltda"), Opcao(4, "Campo Verde")), emptyList())),
    )

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = DominiosViewModel(repo, cadastros)

    @Test
    fun `carrega os dominios em ordem alfabetica e os nomes dos distribuidores`() {
        val e = vm().estado.value
        assertFalse(e.carregando)
        assertEquals(listOf("agroirriga.com.br", "netafim.com.br"), e.dominios.map { it.dominio })
        assertEquals(listOf("Agro Irriga Ltda", "Campo Verde"), e.distribuidores.map { it.nome })
    }

    @Test
    fun `falha ao listar mostra o erro e permite tentar de novo`() {
        repo.lista = Resultado.Falha(ErroAcesso.SemConexao)
        val vm = vm()
        assertEquals(ErroAcesso.SemConexao.mensagem, vm.estado.value.erro)
        repo.lista = Resultado.Sucesso(emptyList())
        vm.carregar()
        assertNull(vm.estado.value.erro)
    }

    @Test
    fun `falha ao buscar os distribuidores nao impede a lista`() {
        cadastros.opcoes = Resultado.Falha(ErroAcesso.SemConexao)
        val e = vm().estado.value
        assertEquals(2, e.dominios.size)
        assertTrue(e.distribuidores.isEmpty())
    }

    @Test
    fun `cadastra dominio da Netafim normalizado, sem distribuidor`() {
        val vm = vm()
        vm.abrirFormulario()
        vm.mudarDominio("  @Novo-Dominio.COM.br ")
        vm.salvar()
        assertEquals(DadosNovoDominio("novo-dominio.com.br", TiposOrganizacao.NETAFIM, null), repo.criados.single())
        val e = vm.estado.value
        assertFalse(e.formularioAberto)
        assertEquals(AvisoDominio.CRIADO, e.aviso)
        assertTrue(e.dominios.any { it.dominio == "novo-dominio.com.br" })
    }

    @Test
    fun `dominio invalido nao chama a API`() {
        val vm = vm()
        vm.abrirFormulario()
        vm.mudarDominio("empresa")
        vm.salvar()
        assertNotNull(vm.estado.value.erroDominio)
        assertTrue(repo.criados.isEmpty())
        assertTrue(vm.estado.value.formularioAberto)
    }

    @Test
    fun `dominio de distribuidor exige escolher o distribuidor e o envia`() {
        val vm = vm()
        vm.abrirFormulario()
        vm.mudarDominio("campoverde.com.br")
        vm.escolherTipo(TiposOrganizacao.DISTRIBUIDOR)
        vm.salvar()
        assertEquals("Escolha o distribuidor.", vm.estado.value.erroDistribuidor)
        assertTrue(repo.criados.isEmpty())

        vm.escolherDistribuidor(4)
        vm.salvar()
        assertEquals(DadosNovoDominio("campoverde.com.br", TiposOrganizacao.DISTRIBUIDOR, 4), repo.criados.single())
    }

    @Test
    fun `trocar para Netafim limpa o distribuidor escolhido`() {
        val vm = vm()
        vm.abrirFormulario()
        vm.escolherTipo(TiposOrganizacao.DISTRIBUIDOR)
        vm.escolherDistribuidor(3)
        vm.escolherTipo(TiposOrganizacao.NETAFIM)
        assertNull(vm.estado.value.idDistribuidor)
    }

    @Test
    fun `erro de campo da API aparece no campo`() {
        repo.resultadoCriar = Resultado.Falha(ErroAcesso.Campos(mapOf("dominio" to "Já existe um domínio com esse nome."), "Dados inválidos"))
        val vm = vm()
        vm.abrirFormulario()
        vm.mudarDominio("netafim.com.br")
        vm.salvar()
        assertEquals("Já existe um domínio com esse nome.", vm.estado.value.erroDominio)
        assertNull(vm.estado.value.erroFormulario)
        assertFalse(vm.estado.value.salvando)
        assertTrue(vm.estado.value.formularioAberto)
    }

    @Test
    fun `erro sem campo aparece acima do formulario`() {
        repo.resultadoCriar = Resultado.Falha(ErroAcesso.SemConexao)
        val vm = vm()
        vm.abrirFormulario()
        vm.mudarDominio("novo.com.br")
        vm.salvar()
        assertEquals(ErroAcesso.SemConexao.mensagem, vm.estado.value.erroFormulario)
    }

    @Test
    fun `desativar e ativar troca o estado do dominio na lista`() {
        val vm = vm()
        val netafim = vm.estado.value.dominios.first { it.id == 1 }
        vm.alternarAtivo(netafim)
        assertTrue(repo.chamadas.contains("ativo:1:false"))
        assertFalse(vm.estado.value.dominios.first { it.id == 1 }.ativo)
        assertEquals(AvisoDominio.DESATIVADO, vm.estado.value.aviso)
        assertNull(vm.estado.value.processandoId)

        vm.alternarAtivo(vm.estado.value.dominios.first { it.id == 1 })
        assertTrue(repo.chamadas.contains("ativo:1:true"))
        assertEquals(AvisoDominio.ATIVADO, vm.estado.value.aviso)
    }

    @Test
    fun `recusa da API ao alterar aparece como mensagem e a lista nao muda`() {
        repo.resultadoAtivo = Resultado.Falha(ErroAcesso.Api("sem_permissao", "Você não tem permissão para esta ação.", 403))
        val vm = vm()
        vm.alternarAtivo(vm.estado.value.dominios.first())
        assertEquals("Você não tem permissão para esta ação.", vm.estado.value.erroAcao)
        assertTrue(vm.estado.value.dominios.all { it.ativo })
    }

    @Test
    fun `excluir pede confirmacao e so chama a API depois dela`() {
        val vm = vm()
        val alvo = vm.estado.value.dominios.first { it.id == 2 }
        vm.pedirExcluir(alvo)
        assertEquals(alvo, vm.estado.value.dominioParaExcluir)
        assertFalse(repo.chamadas.any { it.startsWith("excluir") })

        vm.confirmarExcluir()
        assertTrue(repo.chamadas.contains("excluir:2"))
        assertEquals(listOf(1), vm.estado.value.dominios.map { it.id })
        assertEquals(AvisoDominio.EXCLUIDO, vm.estado.value.aviso)
        assertNull(vm.estado.value.dominioParaExcluir)
    }

    @Test
    fun `cancelar a exclusao nao chama a API`() {
        val vm = vm()
        vm.pedirExcluir(vm.estado.value.dominios.first())
        vm.cancelarExcluir()
        assertNull(vm.estado.value.dominioParaExcluir)
        assertFalse(repo.chamadas.any { it.startsWith("excluir") })
    }

    @Test
    fun `recusa da API ao excluir fecha o dialogo e mostra a mensagem`() {
        repo.resultadoExcluir = Resultado.Falha(ErroAcesso.Api("protegido", "Este domínio não pode ser excluído.", 409))
        val vm = vm()
        vm.pedirExcluir(vm.estado.value.dominios.first())
        vm.confirmarExcluir()
        assertEquals("Este domínio não pode ser excluído.", vm.estado.value.erroAcao)
        assertNull(vm.estado.value.dominioParaExcluir)
        assertEquals(2, vm.estado.value.dominios.size)
    }
}

/** Contrato com a API: rotas, métodos e corpos (MockWebServer). */
class DominiosRemotoTest {

    private lateinit var servidor: MockWebServer
    private lateinit var repositorio: RepositorioDominiosRemoto

    @Before
    fun setUp() {
        servidor = MockWebServer().apply { start() }
        val tokens = object : ProvedorTokens {
            override fun accessTokenAtual() = "acc-1"
            override suspend fun renovar(accessUsado: String?): String? = null
        }
        val rede = Rede(servidor.url("/api/v1/").toString(), tokens, depuracao = false)
        repositorio = RepositorioDominiosRemoto(
            api = rede.retrofit(rede.autenticado).create(AcessoApi::class.java),
            json = rede.json,
        )
    }

    @After
    fun tearDown() = servidor.shutdown()

    private fun json(corpo: String, status: Int = 200) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(corpo)

    private val dominioJson = """{"id_dominio":5,"dominio":"netafim.com.br","tipo_organizacao":"netafim","id_distribuidor":null,"ativo":true,"data_criacao":"2026-11-18T10:00:00Z"}"""

    @Test
    fun `listar percorre as paginas de GET dominios`() = runTest {
        servidor.enqueue(json("""{"next":"http://x/api/v1/dominios?page=2","results":[$dominioJson]}"""))
        servidor.enqueue(json("""{"next":null,"results":[{"id_dominio":6,"dominio":"agro.com.br","tipo_organizacao":"distribuidor","id_distribuidor":3,"ativo":false}]}"""))
        val lista = (repositorio.listar() as Resultado.Sucesso).valor
        assertEquals(listOf(5, 6), lista.map { it.id })
        assertNull(lista[0].idDistribuidor)
        assertEquals(3, lista[1].idDistribuidor)
        assertFalse(lista[1].ativo)
        assertEquals("/api/v1/dominios?page=1", servidor.takeRequest().path)
        assertEquals("/api/v1/dominios?page=2", servidor.takeRequest().path)
    }

    @Test
    fun `criar dominio da Netafim envia POST sem id_distribuidor`() = runTest {
        servidor.enqueue(json(dominioJson, 201))
        val r = repositorio.criar(DadosNovoDominio("netafim.com.br", "netafim", null))
        assertEquals(5, (r as Resultado.Sucesso).valor.id)
        val pedido = servidor.takeRequest()
        assertEquals("POST", pedido.method)
        assertEquals("/api/v1/dominios", pedido.path)
        val corpo = pedido.body.readUtf8()
        assertTrue(corpo.contains("\"dominio\":\"netafim.com.br\""))
        assertTrue(corpo.contains("\"tipo_organizacao\":\"netafim\""))
        assertFalse(corpo.contains("id_distribuidor"))
    }

    @Test
    fun `criar dominio de distribuidor envia o id_distribuidor`() = runTest {
        servidor.enqueue(json(dominioJson, 201))
        repositorio.criar(DadosNovoDominio("agro.com.br", "distribuidor", 3))
        assertTrue(servidor.takeRequest().body.readUtf8().contains("\"id_distribuidor\":3"))
    }

    @Test
    fun `erro de validacao 400 vira erro por campo`() = runTest {
        servidor.enqueue(json("""{"id_distribuidor":["Obrigatório para domínio de distribuidor."]}""", 400))
        val erro = (repositorio.criar(DadosNovoDominio("agro.com.br", "distribuidor", null)) as Resultado.Falha).erro
        assertTrue(erro is ErroAcesso.Campos)
        assertEquals("Obrigatório para domínio de distribuidor.", (erro as ErroAcesso.Campos).campos["id_distribuidor"])
    }

    @Test
    fun `ativar e desativar usa PATCH dominios id com ativo`() = runTest {
        servidor.enqueue(json(dominioJson))
        repositorio.alterarAtivo(5, false)
        val pedido = servidor.takeRequest()
        assertEquals("PATCH", pedido.method)
        assertEquals("/api/v1/dominios/5", pedido.path)
        assertTrue(pedido.body.readUtf8().contains("\"ativo\":false"))
    }

    @Test
    fun `excluir usa DELETE dominios id e aceita 204`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(204))
        assertTrue(repositorio.excluir(5) is Resultado.Sucesso)
        val pedido = servidor.takeRequest()
        assertEquals("DELETE", pedido.method)
        assertEquals("/api/v1/dominios/5", pedido.path)
    }
}
