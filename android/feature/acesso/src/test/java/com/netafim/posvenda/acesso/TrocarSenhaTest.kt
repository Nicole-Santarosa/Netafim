package com.netafim.posvenda.acesso

import com.netafim.posvenda.acesso.dados.AcessoApi
import com.netafim.posvenda.acesso.dados.InfoAparelho
import com.netafim.posvenda.acesso.dados.ProvedorTokens
import com.netafim.posvenda.acesso.dados.Rede
import com.netafim.posvenda.acesso.dados.RepositorioAcessoRemoto
import com.netafim.posvenda.acesso.dominio.ErroAcesso
import com.netafim.posvenda.acesso.dominio.Resultado
import com.netafim.posvenda.acesso.ui.perfil.CampoTrocaSenha
import com.netafim.posvenda.acesso.ui.perfil.TrocarSenhaViewModel
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

/** Troca da própria senha: contrato com a API (MockWebServer) e comportamento do ViewModel (repositório falso). */
@OptIn(ExperimentalCoroutinesApi::class)
class TrocarSenhaTest {

    private lateinit var servidor: MockWebServer
    private lateinit var remoto: RepositorioAcessoRemoto
    private val falso = RepositorioFalso()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        servidor = MockWebServer().apply { start() }
        val tokens = object : ProvedorTokens {
            lateinit var alvo: ProvedorTokens
            override fun accessTokenAtual() = alvo.accessTokenAtual()
            override suspend fun renovar(accessUsado: String?) = alvo.renovar(accessUsado)
        }
        val rede = Rede(servidor.url("/api/v1/").toString(), tokens, depuracao = false)
        remoto = RepositorioAcessoRemoto(
            apiPublica = rede.retrofit(rede.publico).create(AcessoApi::class.java),
            apiAutenticada = rede.retrofit(rede.autenticado).create(AcessoApi::class.java),
            armazenamento = ArmazenamentoEmMemoria(),
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

    private fun json(corpo: String, status: Int) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(corpo)

    private fun preenchido(vm: TrocarSenhaViewModel, atual: String = "SenhaAntiga1", nova: String = "NovaSenha2026", conf: String = nova) {
        vm.aoMudar(CampoTrocaSenha.ATUAL, atual)
        vm.aoMudar(CampoTrocaSenha.NOVA, nova)
        vm.aoMudar(CampoTrocaSenha.CONFIRMACAO, conf)
    }

    // ------------------------------------------------------------- contrato com a API

    @Test
    fun `troca de senha envia senha_atual e nova_senha em POST me senha`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(204))

        val resultado = remoto.alterarSenha("SenhaAntiga1", "NovaSenha2026")

        assertTrue(resultado is Resultado.Sucesso)
        val pedido = servidor.takeRequest()
        assertEquals("POST", pedido.method)
        assertEquals("/api/v1/me/senha", pedido.path)
        val corpo = pedido.body.readUtf8()
        assertTrue(corpo.contains("\"senha_atual\":\"SenhaAntiga1\""))
        assertTrue(corpo.contains("\"nova_senha\":\"NovaSenha2026\""))
    }

    @Test
    fun `senha atual incorreta volta como erro de negocio com mensagem em portugues`() = runTest {
        servidor.enqueue(json("""{"detail":"A senha atual está incorreta.","codigo":"senha_atual_incorreta"}""", 400))

        val resultado = remoto.alterarSenha("errada", "NovaSenha2026")

        val erro = (resultado as Resultado.Falha).erro as ErroAcesso.Api
        assertEquals("senha_atual_incorreta", erro.codigo)
        assertEquals("A senha atual está incorreta.", erro.mensagem)
    }

    // ------------------------------------------------------------- ViewModel

    @Test
    fun `senha valida troca, encerra a sessao e marca como concluida`() {
        val vm = TrocarSenhaViewModel(falso)
        preenchido(vm)

        vm.salvar()

        assertEquals("SenhaAntiga1" to "NovaSenha2026", falso.ultimaTrocaSenha)
        assertTrue("o app sai para pedir o login com a nova senha", falso.saiu)
        assertTrue(vm.estado.value.concluida)
        assertFalse(vm.estado.value.carregando)
    }

    @Test
    fun `campos invalidos nao chamam a API e mostram o erro de cada campo`() {
        val vm = TrocarSenhaViewModel(falso)
        preenchido(vm, atual = "", nova = "123", conf = "outra")

        vm.salvar()

        assertNull("nada foi enviado", falso.ultimaTrocaSenha)
        val erros = vm.estado.value.erros
        assertNotNull(erros[CampoTrocaSenha.ATUAL])
        assertNotNull(erros[CampoTrocaSenha.NOVA])
        assertNotNull(erros[CampoTrocaSenha.CONFIRMACAO])
        assertFalse(vm.estado.value.concluida)
    }

    @Test
    fun `nova senha igual a atual e recusada no aparelho`() {
        val vm = TrocarSenhaViewModel(falso)
        preenchido(vm, atual = "SenhaIgual2026", nova = "SenhaIgual2026")

        vm.salvar()

        assertNull(falso.ultimaTrocaSenha)
        assertEquals("A nova senha deve ser diferente da atual.", vm.estado.value.erros[CampoTrocaSenha.NOVA])
    }

    @Test
    fun `confirmacao diferente da nova senha e recusada`() {
        val vm = TrocarSenhaViewModel(falso)
        preenchido(vm, conf = "NovaSenha2027")

        vm.salvar()

        assertNull(falso.ultimaTrocaSenha)
        assertNotNull(vm.estado.value.erros[CampoTrocaSenha.CONFIRMACAO])
    }

    @Test
    fun `senha atual incorreta aparece no campo da senha atual e nao encerra a sessao`() {
        falso.resultadoAlterarSenha = Resultado.Falha(
            ErroAcesso.Api("senha_atual_incorreta", "A senha atual está incorreta.", 400),
        )
        val vm = TrocarSenhaViewModel(falso)
        preenchido(vm)

        vm.salvar()

        assertEquals("A senha atual está incorreta.", vm.estado.value.erros[CampoTrocaSenha.ATUAL])
        assertFalse(falso.saiu)
        assertFalse(vm.estado.value.concluida)
        assertFalse(vm.estado.value.carregando)
    }

    @Test
    fun `erro de rede vira mensagem geral e permite tentar de novo`() {
        falso.resultadoAlterarSenha = Resultado.Falha(ErroAcesso.SemConexao)
        val vm = TrocarSenhaViewModel(falso)
        preenchido(vm)

        vm.salvar()

        assertEquals(ErroAcesso.SemConexao.mensagem, vm.estado.value.erroGeral)
        assertFalse(falso.saiu)

        falso.resultadoAlterarSenha = Resultado.Sucesso(Unit)
        vm.salvar()
        assertTrue(vm.estado.value.concluida)
    }

    @Test
    fun `erros de validacao da API aparecem no campo correspondente`() {
        falso.resultadoAlterarSenha = Resultado.Falha(
            ErroAcesso.Campos(mapOf("nova_senha" to "Esta senha é muito comum."), "Esta senha é muito comum."),
        )
        val vm = TrocarSenhaViewModel(falso)
        preenchido(vm)

        vm.salvar()

        assertEquals("Esta senha é muito comum.", vm.estado.value.erros[CampoTrocaSenha.NOVA])
        assertNull(vm.estado.value.erroGeral)
    }

    @Test
    fun `editar um campo limpa o erro dele`() {
        val vm = TrocarSenhaViewModel(falso)
        preenchido(vm, nova = "123", conf = "123")
        vm.salvar()
        assertNotNull(vm.estado.value.erros[CampoTrocaSenha.NOVA])

        vm.aoMudar(CampoTrocaSenha.NOVA, "OutraSenha2026")

        assertNull(vm.estado.value.erros[CampoTrocaSenha.NOVA])
    }

    @Test
    fun `alternar visibilidade vale para os tres campos`() {
        val vm = TrocarSenhaViewModel(falso)
        assertFalse(vm.estado.value.senhaVisivel)
        vm.alternarVisibilidade()
        assertTrue(vm.estado.value.senhaVisivel)
    }
}
