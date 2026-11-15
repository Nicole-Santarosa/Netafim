package com.netafim.posvenda.acesso

import com.netafim.posvenda.acesso.dominio.ErroAcesso
import com.netafim.posvenda.acesso.dominio.Resultado
import com.netafim.posvenda.acesso.ui.cadastro.CadastroViewModel
import com.netafim.posvenda.acesso.ui.cadastro.CampoCadastro
import com.netafim.posvenda.acesso.ui.login.LoginViewModel
import com.netafim.posvenda.acesso.ui.perfil.PerfilViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelsTest {

    private val repositorio = RepositorioFalso()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    // ------------------------------------------------------------- login

    @Test
    fun `login valida campos antes de chamar a API`() {
        val vm = LoginViewModel(repositorio)
        vm.entrar()
        assertEquals("Informe o e-mail.", vm.estado.value.erroEmail)
        assertEquals("Informe a senha.", vm.estado.value.erroSenha)
        assertEquals(0, repositorio.chamadasEntrar)
    }

    @Test
    fun `login com sucesso sinaliza entrada e limpa a senha`() {
        val vm = LoginViewModel(repositorio)
        vm.aoMudarEmail("ana@agroirriga.com.br")
        vm.aoMudarSenha("Irrigacao#2026")
        vm.entrar()
        assertTrue(vm.estado.value.entrou)
        assertEquals("", vm.estado.value.senha)
        assertFalse(vm.estado.value.carregando)
    }

    @Test
    fun `login mostra a mensagem do erro da API`() {
        repositorio.resultadoEntrar = falhaApi("conta_pendente", "Seu cadastro ainda está em análise")
        val vm = LoginViewModel(repositorio)
        vm.aoMudarEmail("ana@agroirriga.com.br")
        vm.aoMudarSenha("Irrigacao#2026")
        vm.entrar()
        assertFalse(vm.estado.value.entrou)
        assertEquals("Seu cadastro ainda está em análise", vm.estado.value.erroGeral)
    }

    @Test
    fun `digitar de novo limpa o erro geral`() {
        repositorio.resultadoEntrar = Resultado.Falha(ErroAcesso.SemConexao)
        val vm = LoginViewModel(repositorio)
        vm.aoMudarEmail("ana@agroirriga.com.br")
        vm.aoMudarSenha("x")
        vm.entrar()
        assertNotNull(vm.estado.value.erroGeral)
        vm.aoMudarSenha("y")
        assertNull(vm.estado.value.erroGeral)
    }

    // ------------------------------------------------------------- cadastro

    private fun cadastroPreenchido() = CadastroViewModel(repositorio).apply {
        aoMudar(CampoCadastro.NOME, "João Produtor")
        aoMudar(CampoCadastro.EMAIL, "joao@gmail.com")
        aoMudar(CampoCadastro.TELEFONE, "(16) 98888-7777")
        aoMudar(CampoCadastro.SENHA, "Irrigacao#2026")
        aoMudar(CampoCadastro.CONFIRMACAO, "Irrigacao#2026")
        aoMudarAceite(true)
    }

    @Test
    fun `cadastro carrega a versao do termo`() {
        assertEquals("1.0", CadastroViewModel(repositorio).estado.value.versaoTermo)
    }

    @Test
    fun `cadastro exige aceite do termo e senhas iguais`() {
        val vm = CadastroViewModel(repositorio)
        vm.aoMudar(CampoCadastro.SENHA, "Irrigacao#2026")
        vm.aoMudar(CampoCadastro.CONFIRMACAO, "Outra#2026")
        vm.enviar()
        val erros = vm.estado.value.erros
        assertTrue(CampoCadastro.TERMO in erros)
        assertTrue(CampoCadastro.CONFIRMACAO in erros)
        assertTrue(CampoCadastro.NOME in erros)
        assertNull(repositorio.ultimoCadastro)
    }

    @Test
    fun `cadastro envia telefone so com digitos e mostra a confirmacao`() {
        val vm = cadastroPreenchido()
        vm.enviar()
        assertEquals("16988887777", repositorio.ultimoCadastro?.telefone)
        assertEquals("1.0", repositorio.ultimoCadastro?.versaoTermo)
        assertEquals("Solicitação recebida.", vm.estado.value.enviado)
        assertEquals("", vm.estado.value.senha)
    }

    @Test
    fun `erros de campo da API aparecem no campo certo`() {
        repositorio.resultadoCadastro = Resultado.Falha(
            ErroAcesso.Campos(mapOf("senha" to "Esta senha é muito comum."), "Esta senha é muito comum."),
        )
        val vm = cadastroPreenchido()
        vm.enviar()
        assertEquals("Esta senha é muito comum.", vm.estado.value.erros[CampoCadastro.SENHA])
        assertNull(vm.estado.value.erroGeral)
        assertNull(vm.estado.value.enviado)
    }

    @Test
    fun `termo desatualizado pede novo aceite`() {
        repositorio.resultadoCadastro = falhaApi("termo_desatualizado", "Aceite a nova versão")
        val vm = cadastroPreenchido()
        repositorio.resultadoTermo = Resultado.Sucesso("1.1")
        vm.enviar()
        assertFalse(vm.estado.value.aceiteTermo)
        assertEquals("1.1", vm.estado.value.versaoTermo)
    }

    // ------------------------------------------------------------- perfil

    @Test
    fun `perfil sem internet mostra os dados salvos com aviso`() = runTest {
        repositorio.entrar("ana@agroirriga.com.br", "x")
        repositorio.resultadoPerfil = Resultado.Falha(ErroAcesso.SemConexao)
        val vm = PerfilViewModel(repositorio)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.estado.collect {} }
        assertEquals("Ana Souza", vm.estado.value.usuario?.nome)
        assertNotNull(vm.estado.value.aviso)
    }

    @Test
    fun `sair encerra a sessao e sinaliza a navegacao`() = runTest {
        repositorio.entrar("ana@agroirriga.com.br", "x")
        val vm = PerfilViewModel(repositorio)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.estado.collect {} }
        vm.sair()
        assertTrue(repositorio.saiu)
        assertTrue(vm.estado.value.saiu)
    }
}
