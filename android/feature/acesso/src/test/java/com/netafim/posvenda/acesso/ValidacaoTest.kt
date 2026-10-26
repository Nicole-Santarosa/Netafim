package com.netafim.posvenda.acesso

import com.netafim.posvenda.acesso.dominio.Validacao
import com.netafim.posvenda.acesso.ui.perfil.DescricaoPermissoes
import com.netafim.posvenda.acesso.ui.perfil.formatarTelefone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ValidacaoTest {

    @Test
    fun `email valido e invalido`() {
        assertNull(Validacao.email("tecnico@netafim.com"))
        assertNull(Validacao.email("  joao.silva@fazenda.com.br "))
        assertEquals("Informe o e-mail.", Validacao.email(" "))
        assertEquals("E-mail inválido.", Validacao.email("joao@"))
        assertEquals("E-mail inválido.", Validacao.email("joao.fazenda.com"))
    }

    @Test
    fun `senha nova segue a regra minima da API`() {
        assertNotNull(Validacao.novaSenha("curta1"))
        assertNotNull(Validacao.novaSenha("12345678"))
        assertNull(Validacao.novaSenha("Irrigacao#2026"))
    }

    @Test
    fun `confirmacao de senha`() {
        assertNull(Validacao.confirmacao("abc", "abc"))
        assertEquals("As senhas não conferem.", Validacao.confirmacao("abc", "abd"))
    }

    @Test
    fun `telefone opcional com DDD`() {
        assertNull(Validacao.telefone(""))
        assertNull(Validacao.telefone("(16) 99999-8888"))
        assertNull(Validacao.telefone("1633334444"))
        assertNotNull(Validacao.telefone("99999-8888"))
    }

    @Test
    fun `nome obrigatorio`() {
        assertNotNull(Validacao.nome(""))
        assertNotNull(Validacao.nome("Jo"))
        assertNull(Validacao.nome("João Produtor"))
    }

    @Test
    fun `formata telefone para exibicao`() {
        assertEquals("(16) 99999-8888", formatarTelefone("16999998888"))
        assertEquals("(16) 3333-4444", formatarTelefone("1633334444"))
        assertEquals("123", formatarTelefone("123"))
    }

    @Test
    fun `descreve so as permissoes do usuario na ordem definida`() {
        val textos = DescricaoPermissoes.para(setOf("criar_usuarios", "abrir_atendimento", "desconhecida"))
        assertEquals(listOf("Abrir atendimentos", "Cadastrar usuários"), textos)
    }
}
