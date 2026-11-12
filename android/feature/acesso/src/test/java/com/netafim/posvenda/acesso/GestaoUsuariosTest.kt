package com.netafim.posvenda.acesso

import com.netafim.posvenda.acesso.dados.AcessoApi
import com.netafim.posvenda.acesso.dados.ProvedorTokens
import com.netafim.posvenda.acesso.dados.Rede
import com.netafim.posvenda.acesso.dados.RepositorioUsuariosRemoto
import com.netafim.posvenda.acesso.dominio.DadosNovoUsuario
import com.netafim.posvenda.acesso.dominio.ErroAcesso
import com.netafim.posvenda.acesso.dominio.Perfil
import com.netafim.posvenda.acesso.dominio.RepositorioUsuarios
import com.netafim.posvenda.acesso.dominio.Resultado
import com.netafim.posvenda.acesso.dominio.Sessao
import com.netafim.posvenda.acesso.dominio.Usuario
import com.netafim.posvenda.acesso.ui.usuarios.CampoNovoUsuario
import com.netafim.posvenda.acesso.ui.usuarios.NovoUsuarioViewModel
import com.netafim.posvenda.acesso.ui.usuarios.UsuariosViewModel
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun pessoa(id: Int, nome: String, tipo: String = "distribuidor", nivel: String = "usuario") = Usuario(
    id = id, nome = nome, email = "$id@exemplo.com", telefone = null, situacao = "ativo",
    perfil = Perfil(id, "Perfil $nivel", tipo, nivel), organizacao = null,
    permissoes = if (nivel == "admin") setOf("criar_usuarios") else emptySet(), exigeTrocaSenha = false,
)

private class UsuariosFalso : RepositorioUsuarios {
    var lista: Resultado<List<Usuario>> = Resultado.Sucesso(listOf(pessoa(1, "Marcos Silva", nivel = "admin"), pessoa(2, "Ana Paula Reis")))
    var resultadoCriar: Resultado<Usuario> = Resultado.Sucesso(pessoa(9, "Novo"))
    var chamadasListar = 0
    var criados = mutableListOf<DadosNovoUsuario>()

    override suspend fun listar(): Resultado<List<Usuario>> {
        chamadasListar++
        return lista
    }

    override suspend fun criar(dados: DadosNovoUsuario): Resultado<Usuario> {
        criados += dados
        return resultadoCriar
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class UsuariosViewModelTest {

    private val repositorio = UsuariosFalso()
    private val acesso = RepositorioFalso()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun logarComo(usuario: Usuario) {
        acesso.sessao.value = Sessao("a", "r", usuario)
    }

    @Test
    fun `carrega a lista ao abrir`() {
        logarComo(pessoa(1, "Marcos", nivel = "admin"))
        val vm = UsuariosViewModel(repositorio, acesso)
        assertFalse(vm.estado.value.carregando)
        assertEquals(listOf("Marcos Silva", "Ana Paula Reis"), vm.estado.value.usuarios.map { it.nome })
    }

    @Test
    fun `admin de distribuidor e de cliente podem adicionar`() {
        logarComo(pessoa(1, "Marcos", tipo = "distribuidor", nivel = "admin"))
        assertTrue(UsuariosViewModel(repositorio, acesso).estado.value.podeAdicionar)
        logarComo(pessoa(2, "Lia", tipo = "cliente", nivel = "admin"))
        assertTrue(UsuariosViewModel(repositorio, acesso).estado.value.podeAdicionar)
    }

    @Test
    fun `Netafim Admin nao ve o botao de adicionar e usuario comum tambem nao`() {
        logarComo(pessoa(1, "Chefe", tipo = "netafim", nivel = "admin"))
        assertFalse(UsuariosViewModel(repositorio, acesso).estado.value.podeAdicionar)
        logarComo(pessoa(2, "Comum", tipo = "distribuidor", nivel = "usuario"))
        assertFalse(UsuariosViewModel(repositorio, acesso).estado.value.podeAdicionar)
    }

    @Test
    fun `falha ao carregar mostra o erro e deixa tentar de novo`() {
        repositorio.lista = Resultado.Falha(ErroAcesso.SemConexao)
        val vm = UsuariosViewModel(repositorio, acesso)
        assertEquals(ErroAcesso.SemConexao.mensagem, vm.estado.value.erro)
        repositorio.lista = Resultado.Sucesso(listOf(pessoa(1, "Marcos")))
        vm.carregar()
        assertNull(vm.estado.value.erro)
        assertEquals(1, vm.estado.value.usuarios.size)
    }

    @Test
    fun `ao retomar a primeira vez nao recarrega e depois atualiza a lista`() {
        val vm = UsuariosViewModel(repositorio, acesso)
        assertEquals(1, repositorio.chamadasListar)
        vm.aoRetomar()
        assertEquals(1, repositorio.chamadasListar)

        repositorio.lista = Resultado.Sucesso(listOf(pessoa(1, "Marcos"), pessoa(2, "Ana"), pessoa(9, "Novo")))
        vm.aoRetomar()
        assertEquals(2, repositorio.chamadasListar)
        assertEquals(3, vm.estado.value.usuarios.size)
        assertFalse(vm.estado.value.carregando)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class NovoUsuarioViewModelTest {

    private val repositorio = UsuariosFalso()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun preencher(vm: NovoUsuarioViewModel, nome: String = "Ana Paula Reis", email: String = "ana@exemplo.com", tel: String = "(16) 99738-9535") {
        vm.aoMudar(CampoNovoUsuario.NOME, nome)
        vm.aoMudar(CampoNovoUsuario.EMAIL, email)
        vm.aoMudar(CampoNovoUsuario.TELEFONE, tel)
    }

    @Test
    fun `valida os campos antes de chamar a API`() {
        val vm = NovoUsuarioViewModel(repositorio)
        vm.enviar()
        assertTrue(CampoNovoUsuario.NOME in vm.estado.value.erros)
        assertTrue(CampoNovoUsuario.EMAIL in vm.estado.value.erros)
        assertTrue(repositorio.criados.isEmpty())
    }

    @Test
    fun `envia o convite com o telefone so em digitos e sinaliza criado`() {
        val vm = NovoUsuarioViewModel(repositorio)
        preencher(vm)
        vm.enviar()
        assertEquals(listOf(DadosNovoUsuario("Ana Paula Reis", "ana@exemplo.com", "16997389535")), repositorio.criados)
        assertTrue(vm.estado.value.criado)
        assertFalse(vm.estado.value.carregando)
    }

    @Test
    fun `telefone e opcional`() {
        val vm = NovoUsuarioViewModel(repositorio)
        preencher(vm, tel = "")
        vm.enviar()
        assertNull(repositorio.criados.single().telefone)
        assertTrue(vm.estado.value.criado)
    }

    @Test
    fun `email em uso aparece como erro geral e nao marca criado`() {
        repositorio.resultadoCriar = Resultado.Falha(ErroAcesso.Api("email_em_uso", "Já existe um usuário com este e-mail.", 400))
        val vm = NovoUsuarioViewModel(repositorio)
        preencher(vm)
        vm.enviar()
        assertEquals("Já existe um usuário com este e-mail.", vm.estado.value.erroGeral)
        assertFalse(vm.estado.value.criado)
        assertFalse(vm.estado.value.carregando)
    }

    @Test
    fun `erro de campo da API vai para o campo certo`() {
        repositorio.resultadoCriar = Resultado.Falha(ErroAcesso.Campos(mapOf("email" to "Informe um endereço de email válido."), "Informe um endereço de email válido."))
        val vm = NovoUsuarioViewModel(repositorio)
        preencher(vm)
        vm.enviar()
        assertEquals("Informe um endereço de email válido.", vm.estado.value.erros[CampoNovoUsuario.EMAIL])
        assertNull(vm.estado.value.erroGeral)
    }

    @Test
    fun `digitar de novo limpa o erro do campo`() {
        val vm = NovoUsuarioViewModel(repositorio)
        vm.enviar()
        vm.aoMudar(CampoNovoUsuario.NOME, "Ana Paula")
        assertFalse(CampoNovoUsuario.NOME in vm.estado.value.erros)
        assertTrue(CampoNovoUsuario.EMAIL in vm.estado.value.erros)
    }
}

class RepositorioUsuariosRemotoTest {

    private lateinit var servidor: MockWebServer
    private lateinit var repositorio: RepositorioUsuariosRemoto

    @Before
    fun setUp() {
        servidor = MockWebServer().apply { start() }
        val tokens = object : ProvedorTokens {
            override fun accessTokenAtual() = "acc-1"
            override suspend fun renovar(accessUsado: String?): String? = null
        }
        val rede = Rede(servidor.url("/api/v1/").toString(), tokens, depuracao = false)
        repositorio = RepositorioUsuariosRemoto(
            api = rede.retrofit(rede.autenticado).create(AcessoApi::class.java),
            json = rede.json,
        )
    }

    @After
    fun tearDown() = servidor.shutdown()

    private fun json(corpo: String, status: Int = 200) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(corpo)

    private fun usuario(id: Int, nome: String, nivel: String = "usuario") =
        """{"id_usuario":$id,"nome":"$nome","email":"$id@x.com","telefone":null,"situacao":"ativo",""" +
            """"perfil":{"id_perfil":$id,"nome_perfil":"P","tipo_organizacao":"distribuidor","nivel":"$nivel"}}"""

    @Test
    fun `listar nao filtra por situacao e percorre as paginas`() = runTest {
        servidor.enqueue(json("""{"next":"http://x/api/v1/usuarios?page=2","results":[${usuario(1, "Marcos", "admin")}]}"""))
        servidor.enqueue(json("""{"next":null,"results":[${usuario(2, "Ana")}]}"""))

        val r = repositorio.listar() as Resultado.Sucesso

        assertEquals(listOf("Marcos", "Ana"), r.valor.map { it.nome })
        assertTrue(r.valor[0].perfil!!.ehAdmin)
        assertEquals("/api/v1/usuarios?page=1", servidor.takeRequest().path)
        assertEquals("/api/v1/usuarios?page=2", servidor.takeRequest().path)
    }

    @Test
    fun `criar envia nome, e-mail e telefone`() = runTest {
        servidor.enqueue(json(usuario(9, "Ana"), 201))

        val r = repositorio.criar(DadosNovoUsuario(" Ana Paula ", "ana@x.com ", "16997389535"))

        assertTrue(r is Resultado.Sucesso)
        val pedido = servidor.takeRequest()
        assertEquals("POST", pedido.method)
        assertEquals("/api/v1/usuarios", pedido.path)
        assertEquals("""{"nome":"Ana Paula","email":"ana@x.com","telefone":"16997389535"}""", pedido.body.readUtf8())
    }

    @Test
    fun `criar sem telefone omite o campo`() = runTest {
        servidor.enqueue(json(usuario(9, "Ana"), 201))
        repositorio.criar(DadosNovoUsuario("Ana", "ana@x.com", null))
        assertEquals("""{"nome":"Ana","email":"ana@x.com"}""", servidor.takeRequest().body.readUtf8())
    }

    @Test
    fun `erro de regra da API vira falha com o codigo`() = runTest {
        servidor.enqueue(json("""{"detail":"Já existe um usuário com este e-mail.","codigo":"email_em_uso"}""", 400))

        val r = repositorio.criar(DadosNovoUsuario("Ana", "ana@x.com", null))

        assertEquals("email_em_uso", ((r as Resultado.Falha).erro as ErroAcesso.Api).codigo)
    }
}
