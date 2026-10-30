package com.netafim.posvenda.acesso

import com.netafim.posvenda.acesso.dados.AcessoApi
import com.netafim.posvenda.acesso.dados.InfoAparelho
import com.netafim.posvenda.acesso.dados.ProvedorTokens
import com.netafim.posvenda.acesso.dados.Rede
import com.netafim.posvenda.acesso.dados.RepositorioAcessoRemoto
import com.netafim.posvenda.acesso.dominio.DadosCadastro
import com.netafim.posvenda.acesso.dominio.ErroAcesso
import com.netafim.posvenda.acesso.dominio.Resultado
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RepositorioAcessoRemotoTest {

    private lateinit var servidor: MockWebServer
    private lateinit var armazenamento: ArmazenamentoEmMemoria
    private lateinit var repositorio: RepositorioAcessoRemoto

    @Before
    fun setUp() {
        servidor = MockWebServer().apply { start() }
        armazenamento = ArmazenamentoEmMemoria()
        val tokens = object : ProvedorTokens {
            lateinit var alvo: ProvedorTokens
            override fun accessTokenAtual() = alvo.accessTokenAtual()
            override suspend fun renovar(accessUsado: String?) = alvo.renovar(accessUsado)
        }
        val rede = Rede(servidor.url("/api/v1/").toString(), tokens, depuracao = false)
        repositorio = RepositorioAcessoRemoto(
            apiPublica = rede.retrofit(rede.publico).create(AcessoApi::class.java),
            apiAutenticada = rede.retrofit(rede.autenticado).create(AcessoApi::class.java),
            armazenamento = armazenamento,
            json = rede.json,
            aparelho = InfoAparelho("Pixel 8", "0.1.0"),
        )
        tokens.alvo = repositorio
    }

    @After
    fun tearDown() = servidor.shutdown()

    private fun usuarioJson(nome: String = "Ana Souza") = """
        {"id_usuario":7,"nome":"$nome","email":"ana@agroirriga.com.br",
         "telefone":null,"situacao":"ativo","tipo_organizacao":"distribuidor",
         "organizacao":{"tipo":"distribuidor","id":1,"nome":"Agro Irriga Ltda"},
         "perfil":{"id_perfil":3,"nome_perfil":"Distribuidor Admin",
                   "tipo_organizacao":"distribuidor","nivel":"admin"},
         "convite_pendente":false,"permissoes":["abrir_atendimento","vincular_tecnico"],
         "exige_troca_senha":false,"campo_novo_ignorado":123}
    """.trimIndent()

    private fun tokensJson(access: String = "acc-1", refresh: String = "ref-1") = """
        {"access":"$access","refresh":"$refresh",
         "access_expira_em":"2026-10-25T03:00:00Z","refresh_expira_em":"2026-11-24T03:00:00Z",
         "usuario":${usuarioJson()}}
    """.trimIndent()

    private fun json(corpo: String, status: Int = 200) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(corpo)

    @Test
    fun `login guarda a sessao e envia o aparelho`() = runTest {
        servidor.enqueue(json(tokensJson()))

        val r = repositorio.entrar(" ana@agroirriga.com.br ", "Irrigacao#2026")

        assertTrue(r is Resultado.Sucesso)
        val usuario = (r as Resultado.Sucesso).valor
        assertEquals("Distribuidor Admin", usuario.perfil?.nome)
        assertTrue(usuario.temPermissao("vincular_tecnico"))
        assertEquals("acc-1", repositorio.sessao.value?.accessToken)
        assertNotNull(armazenamento.guardada)

        val corpo = servidor.takeRequest().body.readUtf8()
        assertTrue(corpo.contains("\"email\":\"ana@agroirriga.com.br\""))
        assertTrue(corpo.contains("\"id_dispositivo\""))
        assertTrue(corpo.contains("\"modelo\":\"Pixel 8\""))
    }

    @Test
    fun `erro de negocio vira mensagem pelo codigo`() = runTest {
        servidor.enqueue(json("""{"detail":"Cadastro em análise.","codigo":"conta_pendente"}""", 403))

        val r = repositorio.entrar("ana@agroirriga.com.br", "x") as Resultado.Falha

        val erro = r.erro as ErroAcesso.Api
        assertEquals("conta_pendente", erro.codigo)
        assertTrue(erro.mensagem.contains("em análise"))
        assertNull(repositorio.sessao.value)
    }

    @Test
    fun `aparelho de outro usuario gera novo identificador e tenta de novo`() = runTest {
        servidor.enqueue(json("""{"detail":"x","codigo":"dispositivo_de_outro_usuario"}""", 409))
        servidor.enqueue(json(tokensJson()))

        val r = repositorio.entrar("ana@agroirriga.com.br", "x")

        assertTrue(r is Resultado.Sucesso)
        assertEquals(1, armazenamento.idsGerados)
        val primeiro = servidor.takeRequest().body.readUtf8()
        val segundo = servidor.takeRequest().body.readUtf8()
        assertTrue(primeiro != segundo)
    }

    @Test
    fun `access expirado e renovado automaticamente`() = runTest {
        servidor.enqueue(json(tokensJson()))
        repositorio.entrar("ana@agroirriga.com.br", "x")

        servidor.enqueue(json("""{"detail":"Token expirado"}""", 401)) // GET /me
        servidor.enqueue(json(tokensJson(access = "acc-2", refresh = "ref-2"))) // refresh
        servidor.enqueue(json(usuarioJson(nome = "Ana S."))) // GET /me repetido

        val r = repositorio.atualizarPerfil()

        assertTrue(r is Resultado.Sucesso)
        assertEquals("Ana S.", repositorio.sessao.value?.usuario?.nome)
        assertEquals("acc-2", repositorio.sessao.value?.accessToken)
        servidor.takeRequest() // login
        assertEquals("Bearer acc-1", servidor.takeRequest().getHeader("Authorization"))
        assertEquals("/api/v1/auth/refresh", servidor.takeRequest().path)
        assertEquals("Bearer acc-2", servidor.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `refresh recusado encerra a sessao local`() = runTest {
        servidor.enqueue(json(tokensJson()))
        repositorio.entrar("ana@agroirriga.com.br", "x")

        servidor.enqueue(json("""{"detail":"x"}""", 401)) // GET /me
        servidor.enqueue(json("""{"detail":"Sessão encerrada","codigo":"token_invalido"}""", 401))

        val r = repositorio.atualizarPerfil()

        assertTrue(r is Resultado.Falha)
        assertNull(repositorio.sessao.value)
        assertNull(armazenamento.guardada)
    }

    @Test
    fun `logout com access expirado revoga tambem o refresh renovado`() = runTest {
        servidor.enqueue(json(tokensJson()))
        repositorio.entrar("ana@agroirriga.com.br", "x")

        servidor.enqueue(json("""{"detail":"Token expirado"}""", 401)) // logout com ref-1
        servidor.enqueue(json(tokensJson(access = "acc-2", refresh = "ref-2"))) // refresh
        servidor.enqueue(MockResponse().setResponseCode(204)) // logout repetido (corpo antigo)
        servidor.enqueue(MockResponse().setResponseCode(204)) // logout com ref-2

        repositorio.sair()

        assertNull(repositorio.sessao.value)
        assertNull(armazenamento.guardada)
        servidor.takeRequest() // login
        assertTrue(servidor.takeRequest().body.readUtf8().contains("ref-1"))
        assertEquals("/api/v1/auth/refresh", servidor.takeRequest().path)
        assertTrue(servidor.takeRequest().body.readUtf8().contains("ref-1"))
        val ultimo = servidor.takeRequest()
        assertEquals("/api/v1/auth/logout", ultimo.path)
        assertTrue(ultimo.body.readUtf8().contains("ref-2"))
    }

    @Test
    fun `sessao restaurada do aparelho sem internet`() = runTest {
        servidor.enqueue(json(tokensJson()))
        repositorio.entrar("ana@agroirriga.com.br", "x")
        servidor.shutdown()

        val outra = RepositorioAcessoRemoto(
            apiPublica = repositorioApiInalcancavel(),
            apiAutenticada = repositorioApiInalcancavel(),
            armazenamento = armazenamento,
            json = Rede("http://localhost/", NADA, false).json,
            aparelho = InfoAparelho(null, null),
        )
        val sessao = outra.restaurar()

        assertEquals("Ana Souza", sessao?.usuario?.nome)
        assertEquals("ref-1", sessao?.refreshToken)
    }

    @Test
    fun `erros de validacao por campo`() = runTest {
        servidor.enqueue(json("""{"senha":["Esta senha é muito comum."]}""", 400))

        val r = repositorio.solicitarCadastro(
            DadosCadastro("João", "joao@gmail.com", null, "12345678", "1.0"),
        ) as Resultado.Falha

        val erro = r.erro as ErroAcesso.Campos
        assertEquals("Esta senha é muito comum.", erro.campos["senha"])
        val corpo = servidor.takeRequest().body.readUtf8()
        assertTrue(corpo.contains("\"aceite_termo\":true"))
        assertTrue(!corpo.contains("\"telefone\""))
    }

    @Test
    fun `aceitar convite envia token senha e aceite do termo`() = runTest {
        servidor.enqueue(json("""{"detail":"Senha definida."}"""))

        val r = repositorio.aceitarConvite("tok-" + "a".repeat(40), "Irrigacao#2026", "1.0")

        assertEquals("Senha definida.", (r as Resultado.Sucesso).valor)
        val requisicao = servidor.takeRequest()
        assertEquals("/api/v1/auth/convite/aceitar", requisicao.path)
        val corpo = requisicao.body.readUtf8()
        assertTrue(corpo.contains("\"token\":\"tok-"))
        assertTrue(corpo.contains("\"aceite_termo\":true"))
        assertTrue(corpo.contains("\"versao_termo\":\"1.0\""))
        assertNull(requisicao.getHeader("Authorization"))
    }

    @Test
    fun `redefinir senha envia token e nova senha e mapeia link invalido`() = runTest {
        servidor.enqueue(json("""{"detail":"Senha redefinida."}"""))
        servidor.enqueue(json("""{"detail":"Link inválido.","codigo":"token_invalido"}""", 401))

        assertTrue(repositorio.redefinirSenha("tok-" + "b".repeat(40), "Nova#Senha2026") is Resultado.Sucesso)
        val requisicao = servidor.takeRequest()
        assertEquals("/api/v1/auth/senha/redefinir", requisicao.path)
        assertTrue(requisicao.body.readUtf8().contains("\"nova_senha\":\"Nova#Senha2026\""))

        val falha = repositorio.redefinirSenha("tok-" + "c".repeat(40), "Nova#Senha2026") as Resultado.Falha
        assertEquals("token_invalido", (falha.erro as ErroAcesso.Api).codigo)
    }

    @Test
    fun `sem conexao`() = runTest {
        servidor.shutdown()
        val r = repositorio.entrar("ana@agroirriga.com.br", "x") as Resultado.Falha
        assertEquals(ErroAcesso.SemConexao, r.erro)
    }

    private fun repositorioApiInalcancavel(): AcessoApi =
        Rede("http://127.0.0.1:9/", NADA, false).let { it.retrofit(it.publico).create(AcessoApi::class.java) }

    private companion object {
        val NADA = object : ProvedorTokens {
            override fun accessTokenAtual(): String? = null
            override suspend fun renovar(accessUsado: String?): String? = null
        }
    }
}
