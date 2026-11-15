package com.netafim.posvenda.acesso

import com.netafim.posvenda.acesso.dominio.AcaoLink
import com.netafim.posvenda.acesso.dominio.ErroAcesso
import com.netafim.posvenda.acesso.dominio.LinkAcesso
import com.netafim.posvenda.acesso.dominio.Resultado
import com.netafim.posvenda.acesso.ui.link.CampoLink
import com.netafim.posvenda.acesso.ui.link.LinkViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private val TOKEN = "Xy_9-" + "k".repeat(59) // 64 caracteres, como o back-end gera

class LinkAcessoTest {
    @Test
    fun `aceita convite e redefinicao com token no formato da API`() {
        assertEquals(AcaoLink.CONVITE, LinkAcesso.de("convite", TOKEN)?.acao)
        assertEquals(AcaoLink.REDEFINIR_SENHA, LinkAcesso.de("redefinir-senha", TOKEN)?.acao)
    }

    @Test
    fun `recusa acao desconhecida e token fora do formato`() {
        assertNull(LinkAcesso.de(null, TOKEN))
        assertNull(LinkAcesso.de("apagar-tudo", TOKEN))
        assertNull(LinkAcesso.de("convite", null))
        assertNull(LinkAcesso.de("convite", "curto"))
        assertNull(LinkAcesso.de("convite", "a".repeat(129)))
        assertNull(LinkAcesso.de("convite", "token com espaco " + "a".repeat(20)))
        assertNull(LinkAcesso.de("convite", "../../etc/passwd" + "a".repeat(20)))
    }

    @Test
    fun `o token nao aparece no toString`() {
        val texto = LinkAcesso.de("convite", TOKEN).toString()
        assertFalse(texto.contains(TOKEN))
        assertTrue(texto.contains("***"))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LinkViewModelTest {

    private val repositorio = RepositorioFalso()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun convitePreenchido() = LinkViewModel(repositorio).apply {
        definirLink("convite", TOKEN)
        aoMudar(CampoLink.SENHA, "Irrigacao#2026")
        aoMudar(CampoLink.CONFIRMACAO, "Irrigacao#2026")
        aoMudarAceite(true)
    }

    @Test
    fun `link invalido nao chama a API e avisa a tela`() {
        val vm = LinkViewModel(repositorio)
        vm.definirLink("convite", "token-ruim")
        assertTrue(vm.estado.value.linkInvalido)
        vm.enviar()
        assertNull(repositorio.ultimoConvite)
    }

    @Test
    fun `convite carrega a versao do termo uma vez so`() {
        val vm = LinkViewModel(repositorio)
        vm.definirLink("convite", TOKEN)
        vm.definirLink("convite", TOKEN) // recomposição
        assertEquals("1.0", vm.estado.value.versaoTermo)
        assertEquals(1, repositorio.chamadasTermo)
    }

    @Test
    fun `redefinicao nao pede o aviso de privacidade`() {
        val vm = LinkViewModel(repositorio)
        vm.definirLink("redefinir-senha", TOKEN)
        assertEquals(0, repositorio.chamadasTermo)
    }

    @Test
    fun `convite exige senhas iguais e aceite do termo`() {
        val vm = LinkViewModel(repositorio)
        vm.definirLink("convite", TOKEN)
        vm.aoMudar(CampoLink.SENHA, "Irrigacao#2026")
        vm.aoMudar(CampoLink.CONFIRMACAO, "Outra#2026")
        vm.enviar()
        val erros = vm.estado.value.erros
        assertTrue(CampoLink.CONFIRMACAO in erros)
        assertTrue(CampoLink.TERMO in erros)
        assertNull(repositorio.ultimoConvite)
    }

    @Test
    fun `convite com sucesso envia versao do termo e descarta o token`() {
        val vm = convitePreenchido()
        vm.enviar()
        assertEquals(Triple(TOKEN, "Irrigacao#2026", "1.0"), repositorio.ultimoConvite)
        val estado = vm.estado.value
        assertEquals("Senha definida.", estado.concluido)
        assertNull(estado.link) // o token de uso único não fica guardado
        assertEquals("", estado.senha)
        assertFalse(estado.carregando)
    }

    @Test
    fun `redefinicao com sucesso nao exige aceite`() {
        val vm = LinkViewModel(repositorio).apply {
            definirLink("redefinir-senha", TOKEN)
            aoMudar(CampoLink.SENHA, "Irrigacao#2026")
            aoMudar(CampoLink.CONFIRMACAO, "Irrigacao#2026")
        }
        vm.enviar()
        assertEquals(TOKEN to "Irrigacao#2026", repositorio.ultimaRedefinicao)
        assertEquals("Senha redefinida.", vm.estado.value.concluido)
    }

    @Test
    fun `token recusado pela API mostra a mensagem de link expirado`() {
        repositorio.resultadoConvite = falhaApi("token_invalido", "Sua sessão expirou.", 401)
        val vm = convitePreenchido()
        vm.enviar()
        assertEquals(LinkViewModel.MENSAGEM_LINK_INVALIDO, vm.estado.value.erroGeral)
        assertNull(vm.estado.value.concluido)
    }

    @Test
    fun `erro de senha da API aparece no campo da senha`() {
        repositorio.resultadoConvite = Resultado.Falha(
            ErroAcesso.Campos(mapOf("senha" to "Esta senha é muito comum."), "Esta senha é muito comum."),
        )
        val vm = convitePreenchido()
        vm.enviar()
        assertEquals("Esta senha é muito comum.", vm.estado.value.erros[CampoLink.SENHA])
        assertNull(vm.estado.value.erroGeral)
    }

    @Test
    fun `termo desatualizado pede novo aceite`() {
        repositorio.resultadoConvite = falhaApi("termo_desatualizado", "Aceite a nova versão")
        val vm = convitePreenchido()
        repositorio.resultadoTermo = Resultado.Sucesso("1.1")
        vm.enviar()
        assertFalse(vm.estado.value.aceiteTermo)
        assertEquals("1.1", vm.estado.value.versaoTermo)
        assertNotNull(vm.estado.value.erroGeral)
    }
}
