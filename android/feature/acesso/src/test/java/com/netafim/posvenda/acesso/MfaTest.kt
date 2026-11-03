package com.netafim.posvenda.acesso

import com.netafim.posvenda.acesso.dados.AcessoApi
import com.netafim.posvenda.acesso.dados.InfoAparelho
import com.netafim.posvenda.acesso.dados.ProvedorTokens
import com.netafim.posvenda.acesso.dados.Rede
import com.netafim.posvenda.acesso.dados.RepositorioAcessoRemoto
import com.netafim.posvenda.acesso.dominio.ErroAcesso
import com.netafim.posvenda.acesso.dominio.EstadoMfa
import com.netafim.posvenda.acesso.dominio.Resultado
import com.netafim.posvenda.acesso.dominio.ResultadoEntrada
import com.netafim.posvenda.acesso.ui.login.LoginViewModel
import com.netafim.posvenda.acesso.ui.perfil.EtapaMfa
import com.netafim.posvenda.acesso.ui.perfil.MfaViewModel
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

/** MFA no app: contrato com a API (MockWebServer) e comportamento dos ViewModels (repositório falso). */
@OptIn(ExperimentalCoroutinesApi::class)
class MfaTest {

    private lateinit var servidor: MockWebServer
    private lateinit var armazenamento: ArmazenamentoEmMemoria
    private lateinit var remoto: RepositorioAcessoRemoto
    private val falso = RepositorioFalso()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        servidor = MockWebServer().apply { start() }
        armazenamento = ArmazenamentoEmMemoria()
        val tokens = object : ProvedorTokens {
            lateinit var alvo: ProvedorTokens
            override fun accessTokenAtual() = alvo.accessTokenAtual()
            override suspend fun renovar(accessUsado: String?) = alvo.renovar(accessUsado)
        }
        val rede = Rede(servidor.url("/api/v1/").toString(), tokens, depuracao = false)
        remoto = RepositorioAcessoRemoto(
            apiPublica = rede.retrofit(rede.publico).create(AcessoApi::class.java),
            apiAutenticada = rede.retrofit(rede.autenticado).create(AcessoApi::class.java),
            armazenamento = armazenamento,
            json = rede.json,
            aparelho = InfoAparelho("Pixel 8", "1.0"),
        )
        tokens.alvo = remoto
    }

    @After
    fun tearDown() {
        servidor.shutdown()
        Dispatchers.resetMain()
    }

    private fun json(corpo: String, status: Int = 200) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(corpo)

    private val usuarioJson = """
        {"id_usuario":7,"nome":"Ana Souza","email":"ana@agroirriga.com.br","situacao":"ativo",
         "permissoes":["abrir_atendimento"],"exige_troca_senha":false}
    """.trimIndent()

    private val tokensJson = """
        {"access":"acc-1","refresh":"ref-1","access_expira_em":"2026-10-25T03:00:00Z",
         "refresh_expira_em":"2026-11-24T03:00:00Z","usuario":$usuarioJson}
    """.trimIndent()

    // ------------------------------------------------------------- repositório remoto

    @Test
    fun `login de conta com MFA devolve o desafio e nao abre sessao`() = runTest {
        servidor.enqueue(json("""{"mfa_necessario":true,"desafio":"abc.def","desafio_expira_em":"2026-10-25T03:05:00Z"}"""))

        val r = remoto.entrar("ana@agroirriga.com.br", "Irrigacao#2026") as Resultado.Sucesso

        assertEquals(ResultadoEntrada.ExigeSegundoFator("abc.def"), r.valor)
        assertNull(remoto.sessao.value)
        assertNull(armazenamento.guardada)
    }

    @Test
    fun `concluir o login com o codigo guarda a sessao`() = runTest {
        servidor.enqueue(json(tokensJson))

        val r = remoto.concluirLoginMfa("abc.def", " 123456 ")

        assertTrue(r is Resultado.Sucesso)
        assertEquals("acc-1", remoto.sessao.value?.accessToken)
        val requisicao = servidor.takeRequest()
        assertEquals("/api/v1/auth/mfa/verificar", requisicao.path)
        val corpo = requisicao.body.readUtf8()
        assertTrue(corpo.contains("\"desafio\":\"abc.def\""))
        assertTrue(corpo.contains("\"codigo\":\"123456\""))
    }

    @Test
    fun `codigo errado vira mensagem pelo codigo da API`() = runTest {
        servidor.enqueue(json("""{"detail":"x","codigo":"codigo_mfa_invalido"}""", 401))

        val r = remoto.concluirLoginMfa("abc.def", "000000") as Resultado.Falha

        assertTrue((r.erro as ErroAcesso.Api).mensagem.contains("Código incorreto"))
        assertNull(remoto.sessao.value)
    }

    @Test
    fun `iniciar e confirmar o MFA conversam com as rotas certas`() = runTest {
        servidor.enqueue(json(tokensJson)) // sessão para o interceptor autenticar
        remoto.concluirLoginMfa("d", "123456")
        servidor.takeRequest()
        servidor.enqueue(json("""{"segredo":"ABCDEFGHIJKLMNOP","otpauth_uri":"otpauth://totp/x?secret=ABCDEFGHIJKLMNOP"}"""))
        servidor.enqueue(json("""{"codigos_recuperacao":["AAAAA-BBBBB","CCCCC-DDDDD"]}"""))

        val iniciado = (remoto.iniciarMfa() as Resultado.Sucesso).valor
        val codigos = (remoto.confirmarMfa(" 123456") as Resultado.Sucesso).valor

        assertEquals("ABCDEFGHIJKLMNOP", iniciado.segredo)
        assertEquals(listOf("AAAAA-BBBBB", "CCCCC-DDDDD"), codigos)
        val a = servidor.takeRequest()
        val b = servidor.takeRequest()
        assertEquals("/api/v1/me/mfa/iniciar", a.path)
        assertEquals("Bearer acc-1", a.getHeader("Authorization"))
        assertEquals("/api/v1/me/mfa/confirmar", b.path)
        assertTrue(b.body.readUtf8().contains("\"codigo\":\"123456\""))
    }

    @Test
    fun `estado e desativacao do MFA`() = runTest {
        servidor.enqueue(json(tokensJson))
        remoto.concluirLoginMfa("d", "123456")
        servidor.takeRequest()
        servidor.enqueue(json("""{"ativo":true,"codigos_recuperacao_restantes":9}"""))
        servidor.enqueue(MockResponse().setResponseCode(204))

        val estado = (remoto.estadoMfa() as Resultado.Sucesso).valor
        val fim = remoto.desativarMfa("Irrigacao#2026", "123456")

        assertEquals(EstadoMfa(true, 9), estado)
        assertTrue(fim is Resultado.Sucesso)
        servidor.takeRequest()
        val corpo = servidor.takeRequest().body.readUtf8()
        assertTrue(corpo.contains("\"senha\":\"Irrigacao#2026\""))
    }

    // ------------------------------------------------------------- LoginViewModel

    @Test
    fun `senha certa em conta com MFA pede o codigo e limpa a senha`() {
        falso.resultadoEntrar = Resultado.Sucesso(ResultadoEntrada.ExigeSegundoFator("desafio-1"))
        val vm = LoginViewModel(falso)
        vm.aoMudarEmail("ana@agroirriga.com.br")
        vm.aoMudarSenha("Irrigacao#2026")

        vm.entrar()

        val e = vm.estado.value
        assertTrue(e.pedindoCodigo)
        assertFalse(e.entrou)
        assertEquals("", e.senha)
        assertNull(falso.sessao.value)
    }

    @Test
    fun `codigo certo conclui o login enviando o desafio`() {
        falso.resultadoEntrar = Resultado.Sucesso(ResultadoEntrada.ExigeSegundoFator("desafio-1"))
        val vm = LoginViewModel(falso)
        vm.aoMudarEmail("ana@agroirriga.com.br")
        vm.aoMudarSenha("Irrigacao#2026")
        vm.entrar()

        vm.aoMudarCodigo("123456")
        vm.confirmarCodigo()

        assertEquals("desafio-1" to "123456", falso.ultimoLoginMfa)
        assertTrue(vm.estado.value.entrou)
        assertNotNull(falso.sessao.value)
    }

    @Test
    fun `codigo curto nao chama a API`() {
        falso.resultadoEntrar = Resultado.Sucesso(ResultadoEntrada.ExigeSegundoFator("desafio-1"))
        val vm = LoginViewModel(falso)
        vm.aoMudarEmail("ana@agroirriga.com.br")
        vm.aoMudarSenha("Irrigacao#2026")
        vm.entrar()

        vm.aoMudarCodigo("123")
        vm.confirmarCodigo()

        assertNull(falso.ultimoLoginMfa)
        assertNotNull(vm.estado.value.erroCodigo)
    }

    @Test
    fun `codigo errado mantem o segundo passo e mostra o erro`() {
        falso.resultadoEntrar = Resultado.Sucesso(ResultadoEntrada.ExigeSegundoFator("desafio-1"))
        falso.resultadoMfaLogin = falhaApi("codigo_mfa_invalido", "Código incorreto ou expirado.", 401)
        val vm = LoginViewModel(falso)
        vm.aoMudarEmail("ana@agroirriga.com.br")
        vm.aoMudarSenha("Irrigacao#2026")
        vm.entrar()
        vm.aoMudarCodigo("000000")

        vm.confirmarCodigo()

        val e = vm.estado.value
        assertTrue(e.pedindoCodigo)
        assertFalse(e.entrou)
        assertEquals("Código incorreto ou expirado.", e.erroGeral)
        assertEquals("", e.codigo)
    }

    @Test
    fun `desafio vencido volta para a tela de senha`() {
        falso.resultadoEntrar = Resultado.Sucesso(ResultadoEntrada.ExigeSegundoFator("desafio-1"))
        falso.resultadoMfaLogin = falhaApi("desafio_mfa_invalido", "Desafio inválido ou expirado.", 401)
        val vm = LoginViewModel(falso)
        vm.aoMudarEmail("ana@agroirriga.com.br")
        vm.aoMudarSenha("Irrigacao#2026")
        vm.entrar()
        vm.aoMudarCodigo("123456")

        vm.confirmarCodigo()

        val e = vm.estado.value
        assertFalse(e.pedindoCodigo)
        assertNotNull(e.erroGeral)
    }

    @Test
    fun `voltar para a senha descarta o desafio`() {
        falso.resultadoEntrar = Resultado.Sucesso(ResultadoEntrada.ExigeSegundoFator("desafio-1"))
        val vm = LoginViewModel(falso)
        vm.aoMudarEmail("ana@agroirriga.com.br")
        vm.aoMudarSenha("Irrigacao#2026")
        vm.entrar()

        vm.voltarParaSenha()

        assertFalse(vm.estado.value.pedindoCodigo)
    }

    // ------------------------------------------------------------- MfaViewModel (perfil)

    @Test
    fun `ativar MFA gera o segredo, confirma e mostra os codigos de recuperacao`() {
        val vm = MfaViewModel(falso)
        assertEquals(false, vm.estado.value.estado?.ativo)

        vm.comecarAtivacao()
        assertEquals(EtapaMfa.Ativando, vm.estado.value.etapa)
        assertEquals("ABCDEFGHIJKLMNOP", vm.estado.value.configuracao?.segredo)

        vm.aoMudarCodigo("123456")
        vm.confirmarAtivacao()

        val e = vm.estado.value
        assertEquals(EtapaMfa.CodigosDeRecuperacao, e.etapa)
        assertEquals(10, e.codigosRecuperacao.size)
        assertEquals(true, e.estado?.ativo)
        assertNull(e.configuracao)
    }

    @Test
    fun `fechar os codigos tira os codigos da memoria`() {
        val vm = MfaViewModel(falso)
        vm.comecarAtivacao()
        vm.aoMudarCodigo("123456")
        vm.confirmarAtivacao()

        vm.fechar()

        assertEquals(EtapaMfa.Nenhuma, vm.estado.value.etapa)
        assertTrue(vm.estado.value.codigosRecuperacao.isEmpty())
    }

    @Test
    fun `codigo errado na ativacao mantem a etapa e mostra o erro`() {
        falso.resultadoConfirmarMfa = falhaApi("codigo_mfa_invalido", "Código incorreto ou expirado.")
        val vm = MfaViewModel(falso)
        vm.comecarAtivacao()
        vm.aoMudarCodigo("000000")

        vm.confirmarAtivacao()

        assertEquals(EtapaMfa.Ativando, vm.estado.value.etapa)
        assertEquals("Código incorreto ou expirado.", vm.estado.value.erro)
        assertEquals(false, vm.estado.value.estado?.ativo)
    }

    @Test
    fun `desativar exige senha e codigo`() {
        falso.resultadoEstadoMfa = Resultado.Sucesso(EstadoMfa(true, 8))
        val vm = MfaViewModel(falso)
        vm.comecarDesativacao()

        vm.confirmarDesativacao()
        assertNull(falso.ultimaDesativacaoMfa)

        vm.aoMudarSenha("Irrigacao#2026")
        vm.aoMudarCodigo("123456")
        vm.confirmarDesativacao()

        assertEquals("Irrigacao#2026" to "123456", falso.ultimaDesativacaoMfa)
        assertEquals(false, vm.estado.value.estado?.ativo)
        assertEquals(EtapaMfa.Nenhuma, vm.estado.value.etapa)
        assertEquals("", vm.estado.value.senha)
    }

    @Test
    fun `senha incorreta ao desativar mostra o erro e mantem o MFA`() {
        falso.resultadoEstadoMfa = Resultado.Sucesso(EstadoMfa(true, 8))
        falso.resultadoDesativarMfa = falhaApi("senha_incorreta", "A senha está incorreta.")
        val vm = MfaViewModel(falso)
        vm.comecarDesativacao()
        vm.aoMudarSenha("errada")
        vm.aoMudarCodigo("123456")

        vm.confirmarDesativacao()

        assertEquals("A senha está incorreta.", vm.estado.value.erro)
        assertEquals(true, vm.estado.value.estado?.ativo)
        assertEquals(EtapaMfa.Desativando, vm.estado.value.etapa)
    }

    @Test
    fun `sem o estado do MFA o cartao nao aparece`() {
        falso.resultadoEstadoMfa = Resultado.Falha(ErroAcesso.SemConexao)
        val vm = MfaViewModel(falso)

        assertNull(vm.estado.value.estado)
    }
}
