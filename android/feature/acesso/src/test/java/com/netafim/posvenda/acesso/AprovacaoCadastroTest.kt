package com.netafim.posvenda.acesso

import com.netafim.posvenda.acesso.dados.AcessoApi
import com.netafim.posvenda.acesso.dados.ProvedorTokens
import com.netafim.posvenda.acesso.dados.Rede
import com.netafim.posvenda.acesso.dados.RepositorioCadastrosRemoto
import com.netafim.posvenda.acesso.dominio.DadosAprovacao
import com.netafim.posvenda.acesso.dominio.ErroAcesso
import com.netafim.posvenda.acesso.dominio.Opcao
import com.netafim.posvenda.acesso.dominio.OpcoesAprovacao
import com.netafim.posvenda.acesso.dominio.Perfil
import com.netafim.posvenda.acesso.dominio.RepositorioCadastros
import com.netafim.posvenda.acesso.dominio.Resultado
import com.netafim.posvenda.acesso.dominio.TiposOrganizacao
import com.netafim.posvenda.acesso.dominio.Usuario
import com.netafim.posvenda.acesso.ui.admin.AprovacaoViewModel
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

private fun pendente(id: Int, nome: String) = Usuario(
    id = id, nome = nome, email = "$nome@exemplo.com".replace(' ', '.').lowercase(), telefone = null,
    situacao = "pendente", perfil = null, organizacao = null, permissoes = emptySet(), exigeTrocaSenha = false,
)

private val PERFIS = listOf(
    Perfil(1, "Netafim Admin", TiposOrganizacao.NETAFIM, "admin"),
    Perfil(3, "Distribuidor Admin", TiposOrganizacao.DISTRIBUIDOR, "admin"),
    Perfil(4, "Distribuidor Usuário", TiposOrganizacao.DISTRIBUIDOR, "usuario"),
    Perfil(5, "Cliente Admin", TiposOrganizacao.CLIENTE, "admin"),
)

private class CadastrosFalso : RepositorioCadastros {
    var fila: Resultado<List<Usuario>> = Resultado.Sucesso(listOf(pendente(1, "Ana Reis"), pendente(2, "Beto Lima")))
    var opcoes: Resultado<OpcoesAprovacao> = Resultado.Sucesso(
        OpcoesAprovacao(PERFIS, listOf(Opcao(10, "Agro Irriga")), listOf(Opcao(20, "Fazenda Sol"))),
    )
    var resultadoAprovar: Resultado<Usuario> = Resultado.Sucesso(pendente(1, "Ana Reis"))
    var resultadoRecusar: Resultado<Usuario> = Resultado.Sucesso(pendente(1, "Ana Reis"))
    var chamadasOpcoes = 0
    var aprovacoes = mutableListOf<Pair<Int, DadosAprovacao>>()
    var recusas = mutableListOf<Int>()

    override suspend fun pendentes() = fila
    override suspend fun opcoes(): Resultado<OpcoesAprovacao> {
        chamadasOpcoes++
        return opcoes
    }

    override suspend fun aprovar(idUsuario: Int, dados: DadosAprovacao): Resultado<Usuario> {
        aprovacoes += idUsuario to dados
        return resultadoAprovar
    }

    override suspend fun recusar(idUsuario: Int): Resultado<Usuario> {
        recusas += idUsuario
        return resultadoRecusar
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AprovacaoViewModelTest {

    private val repositorio = CadastrosFalso()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `carrega a fila de pendentes ao abrir`() {
        val vm = AprovacaoViewModel(repositorio)
        assertFalse(vm.estado.value.carregando)
        assertEquals(listOf("Ana Reis", "Beto Lima"), vm.estado.value.pendentes.map { it.nome })
    }

    @Test
    fun `falha ao carregar mostra o erro e deixa tentar de novo`() {
        repositorio.fila = Resultado.Falha(ErroAcesso.SemConexao)
        val vm = AprovacaoViewModel(repositorio)
        assertEquals(ErroAcesso.SemConexao.mensagem, vm.estado.value.erro)
        repositorio.fila = Resultado.Sucesso(listOf(pendente(1, "Ana Reis")))
        vm.carregar()
        assertNull(vm.estado.value.erro)
        assertEquals(1, vm.estado.value.pendentes.size)
    }

    @Test
    fun `aprovar pede organizacao e perfil antes de confirmar`() {
        val vm = AprovacaoViewModel(repositorio)
        vm.aoAprovar(vm.estado.value.pendentes[0])
        vm.escolherTipo(TiposOrganizacao.DISTRIBUIDOR)
        assertFalse(vm.estado.value.escolha!!.completa)
        vm.confirmarAprovacao()
        assertTrue(repositorio.aprovacoes.isEmpty())

        vm.escolherOrganizacao(10)
        assertFalse(vm.estado.value.escolha!!.completa)
        vm.escolherPerfil(3)
        assertTrue(vm.estado.value.escolha!!.completa)
    }

    @Test
    fun `os perfis oferecidos dependem do tipo e trocar o tipo limpa as escolhas`() {
        val vm = AprovacaoViewModel(repositorio)
        vm.aoAprovar(vm.estado.value.pendentes[0])
        vm.escolherTipo(TiposOrganizacao.DISTRIBUIDOR)
        assertEquals(listOf(3, 4), vm.estado.value.escolha!!.perfis.map { it.id })
        vm.escolherOrganizacao(10)
        vm.escolherPerfil(3)

        vm.escolherTipo(TiposOrganizacao.CLIENTE)
        val escolha = vm.estado.value.escolha!!
        assertEquals(listOf(5), escolha.perfis.map { it.id })
        assertNull(escolha.idOrganizacao)
        assertNull(escolha.idPerfil)
    }

    @Test
    fun `aprovar como distribuidor envia o distribuidor e tira da fila`() {
        val vm = AprovacaoViewModel(repositorio)
        vm.aoAprovar(vm.estado.value.pendentes[0])
        vm.escolherTipo(TiposOrganizacao.DISTRIBUIDOR)
        vm.escolherOrganizacao(10)
        vm.escolherPerfil(3)
        vm.confirmarAprovacao()

        assertEquals(listOf(1 to DadosAprovacao("distribuidor", 10, null, 3)), repositorio.aprovacoes)
        assertEquals(listOf("Beto Lima"), vm.estado.value.pendentes.map { it.nome })
        assertNull(vm.estado.value.escolha)
    }

    @Test
    fun `aprovar como cliente envia o cliente e como Netafim nao envia organizacao`() {
        val vm = AprovacaoViewModel(repositorio)
        vm.aoAprovar(vm.estado.value.pendentes[0])
        vm.escolherTipo(TiposOrganizacao.CLIENTE)
        vm.escolherOrganizacao(20)
        vm.escolherPerfil(5)
        vm.confirmarAprovacao()
        assertEquals(DadosAprovacao("cliente", null, 20, 5), repositorio.aprovacoes.last().second)

        vm.aoAprovar(vm.estado.value.pendentes[0])
        vm.escolherTipo(TiposOrganizacao.NETAFIM)
        assertFalse(vm.estado.value.escolha!!.precisaOrganizacao)
        vm.escolherPerfil(1)
        vm.confirmarAprovacao()
        assertEquals(DadosAprovacao("netafim", null, null, 1), repositorio.aprovacoes.last().second)
    }

    @Test
    fun `as opcoes sao carregadas uma vez so`() {
        val vm = AprovacaoViewModel(repositorio)
        vm.aoAprovar(vm.estado.value.pendentes[0])
        vm.cancelarAprovacao()
        vm.aoAprovar(vm.estado.value.pendentes[1])
        assertEquals(1, repositorio.chamadasOpcoes)
    }

    @Test
    fun `falha ao aprovar mantem a escolha aberta e mostra o erro`() {
        repositorio.resultadoAprovar = Resultado.Falha(ErroAcesso.Api("nao_pendente", "Somente cadastros pendentes podem ser aprovados.", 400))
        val vm = AprovacaoViewModel(repositorio)
        vm.aoAprovar(vm.estado.value.pendentes[0])
        vm.escolherTipo(TiposOrganizacao.NETAFIM)
        vm.escolherPerfil(1)
        vm.confirmarAprovacao()

        assertEquals("Somente cadastros pendentes podem ser aprovados.", vm.estado.value.erroAcao)
        assertEquals(2, vm.estado.value.pendentes.size)
        assertFalse(vm.estado.value.processando)
        assertEquals(1, vm.estado.value.escolha!!.usuario.id)
    }

    @Test
    fun `falha ao carregar as opcoes mostra o erro dentro da escolha`() {
        repositorio.opcoes = Resultado.Falha(ErroAcesso.SemConexao)
        val vm = AprovacaoViewModel(repositorio)
        vm.aoAprovar(vm.estado.value.pendentes[0])
        assertEquals(ErroAcesso.SemConexao.mensagem, vm.estado.value.escolha!!.erroOpcoes)
        repositorio.opcoes = Resultado.Sucesso(OpcoesAprovacao(PERFIS, emptyList(), emptyList()))
        vm.carregarOpcoes()
        assertNull(vm.estado.value.escolha!!.erroOpcoes)
    }

    @Test
    fun `recusar pede confirmacao e tira da fila`() {
        val vm = AprovacaoViewModel(repositorio)
        vm.aoRecusar(vm.estado.value.pendentes[1])
        assertEquals(2, vm.estado.value.recusando!!.id)
        assertTrue(repositorio.recusas.isEmpty())

        vm.confirmarRecusa()
        assertEquals(listOf(2), repositorio.recusas)
        assertEquals(listOf("Ana Reis"), vm.estado.value.pendentes.map { it.nome })
        assertNull(vm.estado.value.recusando)
    }

    @Test
    fun `cancelar a recusa nao chama a API`() {
        val vm = AprovacaoViewModel(repositorio)
        vm.aoRecusar(vm.estado.value.pendentes[0])
        vm.cancelarRecusa()
        assertTrue(repositorio.recusas.isEmpty())
        assertNull(vm.estado.value.recusando)
        assertEquals(2, vm.estado.value.pendentes.size)
    }
}

class RepositorioCadastrosRemotoTest {

    private lateinit var servidor: MockWebServer
    private lateinit var repositorio: RepositorioCadastrosRemoto

    @Before
    fun setUp() {
        servidor = MockWebServer().apply { start() }
        val tokens = object : ProvedorTokens {
            override fun accessTokenAtual() = "acc-1"
            override suspend fun renovar(accessUsado: String?): String? = null
        }
        val rede = Rede(servidor.url("/api/v1/").toString(), tokens, depuracao = false)
        repositorio = RepositorioCadastrosRemoto(
            api = rede.retrofit(rede.autenticado).create(AcessoApi::class.java),
            json = rede.json,
        )
    }

    @After
    fun tearDown() = servidor.shutdown()

    private fun json(corpo: String, status: Int = 200) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(corpo)

    private fun usuario(id: Int, nome: String) =
        """{"id_usuario":$id,"nome":"$nome","email":"$id@x.com","telefone":null,"situacao":"pendente"}"""

    @Test
    fun `pendentes filtra pela situacao e percorre todas as paginas`() = runTest {
        servidor.enqueue(json("""{"next":"http://x/api/v1/usuarios?page=2","results":[${usuario(1, "Ana")}]}"""))
        servidor.enqueue(json("""{"next":null,"results":[${usuario(2, "Beto")}]}"""))

        val r = repositorio.pendentes() as Resultado.Sucesso

        assertEquals(listOf("Ana", "Beto"), r.valor.map { it.nome })
        val primeira = servidor.takeRequest()
        assertEquals("/api/v1/usuarios?situacao=pendente&page=1", primeira.path)
        assertEquals("Bearer acc-1", primeira.getHeader("Authorization"))
        assertEquals("/api/v1/usuarios?situacao=pendente&page=2", servidor.takeRequest().path)
    }

    @Test
    fun `opcoes traz so distribuidores e clientes ativos`() = runTest {
        servidor.enqueue(json("""[{"id_perfil":1,"nome_perfil":"Netafim Admin","tipo_organizacao":"netafim","nivel":"admin","descricao":"x","permissoes":[]}]"""))
        servidor.enqueue(json("""{"next":null,"results":[{"id_distribuidor":10,"nome":"Agro","status":"ativo"},{"id_distribuidor":11,"nome":"Velho","status":"inativo"}]}"""))
        servidor.enqueue(json("""{"next":null,"results":[{"id_cliente":20,"nome":"Sol","status":"ativo"},{"id_cliente":21,"nome":"Lua","status":"inativo"}]}"""))

        val r = repositorio.opcoes() as Resultado.Sucesso

        assertEquals(listOf("Netafim Admin"), r.valor.perfis.map { it.nome })
        assertEquals(listOf(Opcao(10, "Agro")), r.valor.distribuidores)
        assertEquals(listOf(Opcao(20, "Sol")), r.valor.clientes)
    }

    @Test
    fun `aprovar envia a organizacao e o perfil no corpo`() = runTest {
        servidor.enqueue(json(usuario(7, "Ana").replace("pendente", "ativo")))

        val r = repositorio.aprovar(7, DadosAprovacao("distribuidor", 10, null, 3))

        assertTrue(r is Resultado.Sucesso)
        val pedido = servidor.takeRequest()
        assertEquals("POST", pedido.method)
        assertEquals("/api/v1/usuarios/7/aprovar", pedido.path)
        assertEquals("""{"tipo_organizacao":"distribuidor","id_distribuidor":10,"id_perfil":3}""", pedido.body.readUtf8())
    }

    @Test
    fun `recusar chama o endpoint de recusa`() = runTest {
        servidor.enqueue(json(usuario(7, "Ana").replace("pendente", "recusado")))

        assertTrue(repositorio.recusar(7) is Resultado.Sucesso)
        val pedido = servidor.takeRequest()
        assertEquals("POST", pedido.method)
        assertEquals("/api/v1/usuarios/7/recusar", pedido.path)
    }

    @Test
    fun `erro de regra da API vira falha com a mensagem`() = runTest {
        servidor.enqueue(json("""{"detail":"Defina o tipo de organização do usuário.","codigo":"organizacao_obrigatoria"}""", 400))

        val r = repositorio.aprovar(7, DadosAprovacao("netafim", null, null, 1))

        val falha = (r as Resultado.Falha).erro as ErroAcesso.Api
        assertEquals("organizacao_obrigatoria", falha.codigo)
    }
}
