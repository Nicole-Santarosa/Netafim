package com.example.netafim.acesso

import com.example.netafim.acesso.dominio.Aparelho
import com.example.netafim.acesso.dominio.DadosNovoUsuario
import com.example.netafim.acesso.dominio.ErroAcesso
import com.example.netafim.acesso.dominio.Perfil
import com.example.netafim.acesso.dominio.RepositorioUsuarios
import com.example.netafim.acesso.dominio.Resultado
import com.example.netafim.acesso.dominio.Usuario
import com.example.netafim.acesso.ui.usuarios.CampoEditarUsuario
import com.example.netafim.acesso.ui.usuarios.EditarUsuarioViewModel
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

private fun alvoEditavel() = Usuario(
    id = 2, nome = "Ana Paula Reis", email = "ana@agroirriga.com.br", telefone = "16997389535", situacao = "ativo",
    perfil = null, organizacao = null, permissoes = emptySet(), exigeTrocaSenha = false,
)

private class UsuariosEdicaoFalso : RepositorioUsuarios {
    var alvo: Usuario = alvoEditavel()
    var resultadoObter: Resultado<Usuario>? = null
    var resultadoEditar: Resultado<Usuario>? = null
    val edicoes = mutableListOf<Triple<Int, String, String>>()

    private val naoUsado = Resultado.Falha(ErroAcesso.Inesperado())

    override suspend fun listar(): Resultado<List<Usuario>> = Resultado.Sucesso(emptyList())
    override suspend fun criar(dados: DadosNovoUsuario): Resultado<Usuario> = Resultado.Sucesso(alvo)
    override suspend fun redefinirMfa(idUsuario: Int): Resultado<Unit> = Resultado.Sucesso(Unit)
    override suspend fun aparelhos(idUsuario: Int): Resultado<List<Aparelho>> = Resultado.Sucesso(emptyList())
    override suspend fun revogarAparelho(idAparelho: String): Resultado<Aparelho> =
        Resultado.Sucesso(Aparelho(idAparelho, null, null, null, true))
    override suspend fun obter(idUsuario: Int): Resultado<Usuario> = resultadoObter ?: Resultado.Sucesso(alvo)
    override suspend fun editar(idUsuario: Int, nome: String, telefone: String): Resultado<Usuario> {
        edicoes += Triple(idUsuario, nome, telefone)
        return resultadoEditar ?: Resultado.Sucesso(alvo.copy(nome = nome, telefone = telefone.ifEmpty { null }))
    }
    override suspend fun alterarSituacao(idUsuario: Int, situacao: String) = naoUsado
    override suspend fun definirPerfil(idUsuario: Int, idPerfil: Int) = naoUsado
    override suspend fun reenviarConvite(idUsuario: Int) = naoUsado
    override suspend fun excluir(idUsuario: Int) = naoUsado
    override suspend fun perfis(): Resultado<List<Perfil>> = Resultado.Sucesso(emptyList())
}

/** Editar nome e telefone de um usuário: preenchimento, validação e tratamento das respostas da API. */
@OptIn(ExperimentalCoroutinesApi::class)
class EditarUsuarioViewModelTest {

    private val usuarios = UsuariosEdicaoFalso()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = EditarUsuarioViewModel(usuarios, 2)

    @Test
    fun `preenche os campos com os dados atuais e mostra o e-mail so para leitura`() {
        val e = vm().estado.value
        assertEquals("Ana Paula Reis", e.nome)
        assertEquals("16997389535", e.telefone)
        assertEquals("ana@agroirriga.com.br", e.email)
        assertTrue(e.carregado)
        assertFalse(e.carregando)
    }

    @Test
    fun `falha ao carregar mostra o erro e permite tentar de novo`() {
        usuarios.resultadoObter = Resultado.Falha(ErroAcesso.SemConexao)
        val vm = vm()
        assertFalse(vm.estado.value.carregado)
        assertEquals(ErroAcesso.SemConexao.mensagem, vm.estado.value.erroGeral)

        usuarios.resultadoObter = null
        vm.carregar()
        assertTrue(vm.estado.value.carregado)
    }

    @Test
    fun `salvar envia nome sem espacos nas pontas e telefone so com digitos`() {
        val vm = vm()
        vm.aoMudar(CampoEditarUsuario.NOME, "  Ana Reis  ")
        vm.aoMudar(CampoEditarUsuario.TELEFONE, "(16) 99738-9999")

        vm.salvar()

        assertEquals(listOf(Triple(2, "Ana Reis", "16997389999")), usuarios.edicoes)
        assertTrue(vm.estado.value.salvo)
        assertFalse(vm.estado.value.salvando)
    }

    @Test
    fun `telefone apagado e enviado vazio para limpar`() {
        val vm = vm()
        vm.aoMudar(CampoEditarUsuario.TELEFONE, "")
        vm.salvar()
        assertEquals("", usuarios.edicoes.single().third)
    }

    @Test
    fun `sem mudanca nao chama a API e a tela so volta`() {
        val vm = vm()
        vm.salvar()
        assertTrue(usuarios.edicoes.isEmpty())
        assertTrue(vm.estado.value.salvo)
    }

    @Test
    fun `nome curto e telefone incompleto nao sao enviados`() {
        val vm = vm()
        vm.aoMudar(CampoEditarUsuario.NOME, "Al")
        vm.aoMudar(CampoEditarUsuario.TELEFONE, "123")

        vm.salvar()

        assertTrue(usuarios.edicoes.isEmpty())
        assertNotNull(vm.estado.value.erros[CampoEditarUsuario.NOME])
        assertNotNull(vm.estado.value.erros[CampoEditarUsuario.TELEFONE])
        assertFalse(vm.estado.value.salvo)
    }

    @Test
    fun `erro de campo da API aparece no campo certo`() {
        usuarios.resultadoEditar = Resultado.Falha(
            ErroAcesso.Campos(mapOf("telefone" to "Telefone inválido."), "Telefone inválido."),
        )
        val vm = vm()
        vm.aoMudar(CampoEditarUsuario.NOME, "Ana Reis")

        vm.salvar()

        assertEquals("Telefone inválido.", vm.estado.value.erros[CampoEditarUsuario.TELEFONE])
        assertNull(vm.estado.value.erroGeral)
        assertFalse(vm.estado.value.salvo)
        assertFalse(vm.estado.value.salvando)
    }

    @Test
    fun `sem permissao ou sem rede mostra a mensagem geral`() {
        usuarios.resultadoEditar = Resultado.Falha(ErroAcesso.Api("sem_permissao", "Você não tem permissão para esta ação.", 403))
        val vm = vm()
        vm.aoMudar(CampoEditarUsuario.NOME, "Ana Reis")

        vm.salvar()

        assertEquals("Você não tem permissão para esta ação.", vm.estado.value.erroGeral)
        assertFalse(vm.estado.value.salvo)
    }

    @Test
    fun `editar um campo limpa o erro dele`() {
        val vm = vm()
        vm.aoMudar(CampoEditarUsuario.NOME, "Al")
        vm.salvar()
        assertNotNull(vm.estado.value.erros[CampoEditarUsuario.NOME])

        vm.aoMudar(CampoEditarUsuario.NOME, "Alice")

        assertNull(vm.estado.value.erros[CampoEditarUsuario.NOME])
    }
}
