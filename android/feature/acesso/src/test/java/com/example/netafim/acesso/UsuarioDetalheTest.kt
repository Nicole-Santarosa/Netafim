package com.example.netafim.acesso

import com.example.netafim.acesso.dados.AcessoApi
import com.example.netafim.acesso.dados.ProvedorTokens
import com.example.netafim.acesso.dados.Rede
import com.example.netafim.acesso.dados.RepositorioUsuariosRemoto
import com.example.netafim.acesso.dominio.AcoesDoUsuario
import com.example.netafim.acesso.dominio.DadosNovoUsuario
import com.example.netafim.acesso.dominio.ErroAcesso
import com.example.netafim.acesso.dominio.Organizacao
import com.example.netafim.acesso.dominio.Perfil
import com.example.netafim.acesso.dominio.Permissoes
import com.example.netafim.acesso.dominio.RepositorioUsuarios
import com.example.netafim.acesso.dominio.Resultado
import com.example.netafim.acesso.dominio.Sessao
import com.example.netafim.acesso.dominio.SituacoesUsuario
import com.example.netafim.acesso.dominio.Usuario
import com.example.netafim.acesso.dominio.acoesPermitidas
import com.example.netafim.acesso.ui.usuarios.AvisoUsuario
import com.example.netafim.acesso.ui.usuarios.ConfirmacaoUsuario
import com.example.netafim.acesso.ui.usuarios.UsuarioDetalheViewModel
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

private val TODAS = setOf(
    Permissoes.EDITAR_USUARIO, Permissoes.CRIAR_USUARIO, Permissoes.EXCLUIR_USUARIO, Permissoes.DEFINIR_PERFIL,
)

private fun perfil(id: Int, tipo: String, nivel: String) = Perfil(id, "$tipo $nivel", tipo, nivel)

private fun pessoa(
    id: Int,
    situacao: String = SituacoesUsuario.ATIVO,
    tipo: String = "distribuidor",
    nivel: String = "usuario",
    organizacaoId: Int? = 1,
    permissoes: Set<String> = emptySet(),
    semOrganizacao: Boolean = false,
) = Usuario(
    id = id, nome = "Pessoa $id", email = "p$id@exemplo.com", telefone = null, situacao = situacao,
    perfil = perfil(if (tipo == "netafim") 1 else if (tipo == "distribuidor") 3 else 5, tipo, nivel),
    organizacao = if (semOrganizacao) null else Organizacao(tipo, if (tipo == "netafim") null else organizacaoId, "Org"),
    permissoes = permissoes, exigeTrocaSenha = false,
)

private val NETAFIM_ADMIN = pessoa(1, tipo = "netafim", nivel = "admin", permissoes = TODAS)
private val DISTRIBUIDOR_ADMIN = pessoa(10, tipo = "distribuidor", nivel = "admin", permissoes = setOf(Permissoes.EDITAR_USUARIO, Permissoes.CRIAR_USUARIO, Permissoes.EXCLUIR_USUARIO))

/** Quais botões o detalhe mostra (regra pura, sem tela). */
class AcoesPermitidasTest {

    @Test
    fun `ninguem gerencia a si mesmo`() {
        assertEquals(AcoesDoUsuario(), acoesPermitidas(NETAFIM_ADMIN, NETAFIM_ADMIN))
    }

    @Test
    fun `editar dados segue a permissao e a gestao (nao para a si mesmo nem para admin de outra empresa)`() {
        assertTrue(acoesPermitidas(NETAFIM_ADMIN, pessoa(2, tipo = "cliente")).editar)
        assertTrue(acoesPermitidas(DISTRIBUIDOR_ADMIN, pessoa(2, organizacaoId = 1)).editar)
        assertFalse(acoesPermitidas(DISTRIBUIDOR_ADMIN, pessoa(3, nivel = "admin", organizacaoId = 1)).editar)
        assertFalse(acoesPermitidas(NETAFIM_ADMIN, NETAFIM_ADMIN).editar)
    }

    @Test
    fun `Netafim Admin desativa, exclui e define o perfil de um usuario ativo de qualquer organizacao`() {
        val acoes = acoesPermitidas(NETAFIM_ADMIN, pessoa(2, tipo = "cliente"))
        assertTrue(acoes.desativar)
        assertFalse(acoes.ativar)
        assertTrue(acoes.excluir)
        assertTrue(acoes.definirPerfil)
    }

    @Test
    fun `usuario inativo so pode ser ativado`() {
        val acoes = acoesPermitidas(NETAFIM_ADMIN, pessoa(2, SituacoesUsuario.INATIVO))
        assertTrue(acoes.ativar)
        assertFalse(acoes.desativar)
    }

    @Test
    fun `desbloquear e so do Netafim Admin`() {
        val bloqueado = pessoa(2, SituacoesUsuario.BLOQUEADO)
        assertTrue(acoesPermitidas(NETAFIM_ADMIN, bloqueado).ativar)
        assertFalse(acoesPermitidas(DISTRIBUIDOR_ADMIN, bloqueado).ativar)
    }

    @Test
    fun `usuario pendente recebe so o reenvio do convite (alem do que o perfil permite)`() {
        val acoes = acoesPermitidas(DISTRIBUIDOR_ADMIN, pessoa(2, SituacoesUsuario.PENDENTE))
        assertTrue(acoes.reenviarConvite)
        assertFalse(acoes.ativar)
        assertFalse(acoes.desativar)
    }

    @Test
    fun `admin de distribuidor gerencia usuario da propria organizacao mas nao admin nem outra organizacao`() {
        assertTrue(acoesPermitidas(DISTRIBUIDOR_ADMIN, pessoa(2, organizacaoId = 1)).desativar)
        assertFalse("outro admin", acoesPermitidas(DISTRIBUIDOR_ADMIN, pessoa(3, nivel = "admin", organizacaoId = 1)).alguma)
        assertFalse("outra organização", acoesPermitidas(DISTRIBUIDOR_ADMIN, pessoa(4, organizacaoId = 2)).alguma)
        assertFalse("outro tipo", acoesPermitidas(DISTRIBUIDOR_ADMIN, pessoa(5, tipo = "cliente", organizacaoId = 1)).alguma)
    }

    @Test
    fun `admin de distribuidor nao define perfil (permissao so do Netafim Admin)`() {
        assertFalse(acoesPermitidas(DISTRIBUIDOR_ADMIN, pessoa(2)).definirPerfil)
    }

    @Test
    fun `sem a permissao nao ha acao`() {
        val semPermissao = pessoa(20, tipo = "distribuidor", nivel = "admin", permissoes = emptySet())
        assertFalse(acoesPermitidas(semPermissao, pessoa(2)).alguma)
    }

    @Test
    fun `perfil so pode ser definido para quem ja tem organizacao`() {
        assertFalse(acoesPermitidas(NETAFIM_ADMIN, pessoa(2, SituacoesUsuario.PENDENTE, semOrganizacao = true)).definirPerfil)
    }
}

/** Contrato com a API: rotas, métodos e corpos das ações de gestão (MockWebServer). */
class UsuarioDetalheRemotoTest {

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

    private val usuarioJson = """{"id_usuario":2,"nome":"Ana","email":"a@x.com","situacao":"inativo","permissoes":[]}"""

    @Test
    fun `obter usa GET usuarios id`() = runTest {
        servidor.enqueue(json(usuarioJson))
        val r = repositorio.obter(2)
        assertEquals(2, (r as Resultado.Sucesso).valor.id)
        val pedido = servidor.takeRequest()
        assertEquals("GET", pedido.method)
        assertEquals("/api/v1/usuarios/2", pedido.path)
    }

    @Test
    fun `alterar situacao envia a situacao em POST usuarios id situacao`() = runTest {
        servidor.enqueue(json(usuarioJson))
        repositorio.alterarSituacao(2, "inativo")
        val pedido = servidor.takeRequest()
        assertEquals("POST", pedido.method)
        assertEquals("/api/v1/usuarios/2/situacao", pedido.path)
        assertTrue(pedido.body.readUtf8().contains("\"situacao\":\"inativo\""))
    }

    @Test
    fun `definir perfil envia id_perfil em POST usuarios id perfil`() = runTest {
        servidor.enqueue(json(usuarioJson))
        repositorio.definirPerfil(2, 7)
        val pedido = servidor.takeRequest()
        assertEquals("/api/v1/usuarios/2/perfil", pedido.path)
        assertTrue(pedido.body.readUtf8().contains("\"id_perfil\":7"))
    }

    @Test
    fun `editar envia nome e telefone em PATCH usuarios id`() = runTest {
        servidor.enqueue(json(usuarioJson))
        repositorio.editar(2, "  Ana Reis ", "16997389535")
        val pedido = servidor.takeRequest()
        assertEquals("PATCH", pedido.method)
        assertEquals("/api/v1/usuarios/2", pedido.path)
        val corpo = pedido.body.readUtf8()
        assertTrue(corpo.contains("\"nome\":\"Ana Reis\""))
        assertTrue(corpo.contains("\"telefone\":\"16997389535\""))
    }

    @Test
    fun `telefone vazio e enviado como vazio para limpar o campo`() = runTest {
        servidor.enqueue(json(usuarioJson))
        repositorio.editar(2, "Ana Reis", "")
        assertTrue(servidor.takeRequest().body.readUtf8().contains("\"telefone\":\"\""))
    }

    @Test
    fun `reenviar convite e excluir respondem 204`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(204))
        servidor.enqueue(MockResponse().setResponseCode(204))
        assertTrue(repositorio.reenviarConvite(2) is Resultado.Sucesso)
        assertTrue(repositorio.excluir(2) is Resultado.Sucesso)
        val convite = servidor.takeRequest()
        assertEquals("POST", convite.method)
        assertEquals("/api/v1/usuarios/2/reenviar-convite", convite.path)
        val exclusao = servidor.takeRequest()
        assertEquals("DELETE", exclusao.method)
        assertEquals("/api/v1/usuarios/2", exclusao.path)
    }

    @Test
    fun `recusa da API volta como erro de negocio com a mensagem`() = runTest {
        servidor.enqueue(json("""{"detail":"Não é possível desativar o último Administrador Netafim ativo.","codigo":"ultimo_admin"}""", 400))
        val erro = (repositorio.alterarSituacao(1, "inativo") as Resultado.Falha).erro as ErroAcesso.Api
        assertEquals("ultimo_admin", erro.codigo)
        assertTrue(erro.mensagem.contains("último Administrador"))
    }
}

private class UsuariosDetalheFalso : RepositorioUsuarios {
    var alvo: Usuario = pessoa(2)
    var resultadoObter: Resultado<Usuario>? = null
    var resultadoSituacao: Resultado<Usuario>? = null
    var resultadoPerfil: Resultado<Usuario>? = null
    var resultadoConvite: Resultado<Unit> = Resultado.Sucesso(Unit)
    var resultadoExcluir: Resultado<Unit> = Resultado.Sucesso(Unit)
    var listaPerfis: List<Perfil> = listOf(perfil(3, "distribuidor", "admin"), perfil(4, "distribuidor", "usuario"), perfil(5, "cliente", "usuario"))
    val chamadas = mutableListOf<String>()

    override suspend fun listar(): Resultado<List<Usuario>> = Resultado.Sucesso(emptyList())
    override suspend fun criar(dados: DadosNovoUsuario): Resultado<Usuario> = Resultado.Sucesso(alvo)
    override suspend fun obter(idUsuario: Int): Resultado<Usuario> {
        chamadas += "obter"
        return resultadoObter ?: Resultado.Sucesso(alvo)
    }
    var resultadoEditar: Resultado<Usuario>? = null
    var edicoes = mutableListOf<Triple<Int, String, String>>()

    override suspend fun editar(idUsuario: Int, nome: String, telefone: String): Resultado<Usuario> {
        chamadas += "editar"
        edicoes += Triple(idUsuario, nome, telefone)
        return resultadoEditar ?: Resultado.Sucesso(alvo.copy(nome = nome, telefone = telefone.ifEmpty { null })).also { alvo = it.valor }
    }

    override suspend fun alterarSituacao(idUsuario: Int, situacao: String): Resultado<Usuario> {
        chamadas += "situacao:$situacao"
        return resultadoSituacao ?: Resultado.Sucesso(alvo.copy(situacao = situacao)).also { alvo = it.valor }
    }
    override suspend fun definirPerfil(idUsuario: Int, idPerfil: Int): Resultado<Usuario> {
        chamadas += "perfil:$idPerfil"
        val novo = alvo.copy(perfil = listaPerfis.first { it.id == idPerfil })
        return resultadoPerfil ?: Resultado.Sucesso(novo).also { alvo = novo }
    }
    override suspend fun reenviarConvite(idUsuario: Int): Resultado<Unit> {
        chamadas += "convite"
        return resultadoConvite
    }
    override suspend fun excluir(idUsuario: Int): Resultado<Unit> {
        chamadas += "excluir"
        return resultadoExcluir
    }
    override suspend fun perfis(): Resultado<List<Perfil>> = Resultado.Sucesso(listaPerfis)
}

@OptIn(ExperimentalCoroutinesApi::class)
class UsuarioDetalheViewModelTest {

    private val usuarios = UsuariosDetalheFalso()
    private val acesso = RepositorioFalso().also { it.sessao.value = Sessao("a", "r", NETAFIM_ADMIN) }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = UsuarioDetalheViewModel(usuarios, acesso, 2)

    @Test
    fun `carrega o usuario e calcula as acoes de quem esta logado`() {
        val e = vm().estado.value
        assertEquals(2, e.usuario?.id)
        assertFalse(e.carregando)
        assertTrue(e.acoes.desativar)
    }

    @Test
    fun `falha ao carregar mostra o erro e permite tentar de novo`() {
        usuarios.resultadoObter = Resultado.Falha(ErroAcesso.SemConexao)
        val vm = vm()
        assertNull(vm.estado.value.usuario)
        assertEquals(ErroAcesso.SemConexao.mensagem, vm.estado.value.erro)

        usuarios.resultadoObter = null
        vm.carregar()
        assertNotNull(vm.estado.value.usuario)
        assertNull(vm.estado.value.erro)
    }

    @Test
    fun `desativar pede confirmacao e so chama a API depois dela`() {
        val vm = vm()
        vm.pedirDesativar()
        assertEquals(ConfirmacaoUsuario.DESATIVAR, vm.estado.value.confirmando)
        assertFalse("ainda não chamou", usuarios.chamadas.any { it.startsWith("situacao") })

        vm.confirmar()

        assertTrue(usuarios.chamadas.contains("situacao:inativo"))
        assertEquals(SituacoesUsuario.INATIVO, vm.estado.value.usuario?.situacao)
        assertEquals(AvisoUsuario.DESATIVADO, vm.estado.value.aviso)
        assertNull(vm.estado.value.confirmando)
        assertTrue("agora só pode ativar", vm.estado.value.acoes.ativar)
    }

    @Test
    fun `cancelar a confirmacao nao chama a API`() {
        val vm = vm()
        vm.pedirExcluir()
        vm.cancelarConfirmacao()
        assertNull(vm.estado.value.confirmando)
        assertFalse(usuarios.chamadas.contains("excluir"))
    }

    @Test
    fun `ativar chama a API direto e avisa`() {
        usuarios.alvo = pessoa(2, SituacoesUsuario.INATIVO)
        val vm = vm()
        vm.ativar()
        assertTrue(usuarios.chamadas.contains("situacao:ativo"))
        assertEquals(AvisoUsuario.ATIVADO, vm.estado.value.aviso)
    }

    @Test
    fun `excluir confirmado marca como excluido para a tela voltar`() {
        val vm = vm()
        vm.pedirExcluir()
        vm.confirmar()
        assertTrue(usuarios.chamadas.contains("excluir"))
        assertTrue(vm.estado.value.excluido)
    }

    @Test
    fun `recusa da API aparece como erro e fecha a confirmacao`() {
        usuarios.resultadoSituacao = Resultado.Falha(ErroAcesso.Api("ultimo_admin", "Não é possível desativar o último Administrador Netafim ativo.", 400))
        val vm = vm()
        vm.pedirDesativar()
        vm.confirmar()
        assertEquals("Não é possível desativar o último Administrador Netafim ativo.", vm.estado.value.erroAcao)
        assertNull(vm.estado.value.confirmando)
        assertFalse(vm.estado.value.processando)
        assertEquals("o usuário continua ativo", SituacoesUsuario.ATIVO, vm.estado.value.usuario?.situacao)
    }

    @Test
    fun `ao retomar atualiza o usuario sem mostrar carregamento`() {
        val vm = vm()
        usuarios.alvo = usuarios.alvo.copy(nome = "Nome corrigido")
        vm.aoRetomar()
        assertEquals("Nome corrigido", vm.estado.value.usuario?.nome)
        assertFalse(vm.estado.value.carregando)
    }

    @Test
    fun `ao retomar o aviso de uma acao anterior some`() {
        usuarios.alvo = pessoa(2, SituacoesUsuario.INATIVO)
        val vm = vm()
        vm.ativar()
        assertEquals(AvisoUsuario.ATIVADO, vm.estado.value.aviso)
        vm.aoRetomar()
        assertNull(vm.estado.value.aviso)
    }

    @Test
    fun `reenviar convite avisa`() {
        usuarios.alvo = pessoa(2, SituacoesUsuario.PENDENTE)
        val vm = vm()
        vm.reenviarConvite()
        assertEquals(AvisoUsuario.CONVITE_REENVIADO, vm.estado.value.aviso)
    }

    @Test
    fun `convite ja aceito aparece como erro`() {
        usuarios.alvo = pessoa(2, SituacoesUsuario.PENDENTE)
        usuarios.resultadoConvite = Resultado.Falha(ErroAcesso.Api("convite_ja_aceito", "Este usuário já definiu a senha.", 400))
        val vm = vm()
        vm.reenviarConvite()
        assertEquals("Este usuário já definiu a senha.", vm.estado.value.erroAcao)
    }

    @Test
    fun `perfis oferecidos sao so os do tipo de organizacao da pessoa`() {
        val vm = vm()
        vm.abrirPerfis()
        val e = vm.estado.value
        assertTrue(e.escolhendoPerfil)
        assertEquals(listOf(3, 4), e.perfis.map { it.id })
    }

    @Test
    fun `escolher outro perfil chama a API, atualiza e fecha a folha`() {
        val vm = vm()
        vm.abrirPerfis()
        vm.escolherPerfil(4)
        assertTrue(usuarios.chamadas.contains("perfil:4"))
        assertEquals(4, vm.estado.value.usuario?.perfil?.id)
        assertEquals(AvisoUsuario.PERFIL_ALTERADO, vm.estado.value.aviso)
        assertFalse(vm.estado.value.escolhendoPerfil)
    }

    @Test
    fun `escolher o perfil que a pessoa ja tem nao chama a API`() {
        val vm = vm()
        vm.abrirPerfis()
        vm.escolherPerfil(usuarios.alvo.perfil!!.id)
        assertFalse(usuarios.chamadas.any { it.startsWith("perfil") })
    }

    @Test
    fun `recusa ao definir o perfil aparece dentro da folha aberta`() {
        usuarios.resultadoPerfil = Resultado.Falha(ErroAcesso.Api("ultimo_admin", "Não é possível rebaixar o último Administrador Netafim ativo.", 400))
        val vm = vm()
        vm.abrirPerfis()
        vm.escolherPerfil(4)
        assertTrue(vm.estado.value.escolhendoPerfil)
        assertEquals("Não é possível rebaixar o último Administrador Netafim ativo.", vm.estado.value.erroPerfis)
        assertFalse(vm.estado.value.processando)
    }
}
