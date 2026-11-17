package com.example.netafim.core.modelo.sessao

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsuarioDaSessaoTest {

    private fun usuario(permissoes: Set<String>) = UsuarioDaSessao(
        id = 1,
        nome = "Técnico de teste",
        tipoDeOrganizacao = TipoDeOrganizacao.DISTRIBUIDOR,
        idDaOrganizacao = 7,
        ehAdmin = false,
        permissoes = permissoes,
    )

    @Test
    fun temPermissaoQuandoOCodigoEstaNoConjunto() {
        val usuario = usuario(setOf("abrir_atendimento", "vincular_tecnico"))
        assertTrue(usuario.temPermissao("abrir_atendimento"))
        assertTrue(usuario.temPermissao("vincular_tecnico"))
    }

    @Test
    fun naoTemPermissaoQuandoOCodigoNaoEstaNoConjunto() {
        assertFalse(usuario(setOf("abrir_atendimento")).temPermissao("vincular_tecnico"))
    }

    @Test
    fun semPermissoesNaoTemNenhuma() {
        assertFalse(usuario(emptySet()).temPermissao("abrir_atendimento"))
    }

    @Test
    fun codigoPrecisaSerExato() {
        // Sem diferença de tamanho de letra ou prefixo: o código é comparado do jeito que veio do servidor.
        val usuario = usuario(setOf("abrir_atendimento"))
        assertFalse(usuario.temPermissao("ABRIR_ATENDIMENTO"))
        assertFalse(usuario.temPermissao("abrir"))
    }

    // Usuário só com o tipo e a organização mudando, para os casos de semOrganizacao.
    private fun daOrganizacao(tipo: TipoDeOrganizacao, idDaOrganizacao: Int?) = UsuarioDaSessao(
        id = 2,
        nome = "Usuário de teste",
        tipoDeOrganizacao = tipo,
        idDaOrganizacao = idDaOrganizacao,
        ehAdmin = false,
        permissoes = emptySet(),
    )

    @Test
    fun netafimSemOrganizacaoNaoEhContaSemEmpresa() {
        // Para a Netafim o idDaOrganizacao nulo é o normal.
        assertFalse(daOrganizacao(TipoDeOrganizacao.NETAFIM, null).semOrganizacao)
    }

    @Test
    fun distribuidorSemOrganizacaoEhContaSemEmpresa() {
        assertTrue(daOrganizacao(TipoDeOrganizacao.DISTRIBUIDOR, null).semOrganizacao)
    }

    @Test
    fun clienteSemOrganizacaoEhContaSemEmpresa() {
        assertTrue(daOrganizacao(TipoDeOrganizacao.CLIENTE, null).semOrganizacao)
    }

    @Test
    fun clienteComOrganizacaoNaoEhContaSemEmpresa() {
        assertFalse(daOrganizacao(TipoDeOrganizacao.CLIENTE, 3).semOrganizacao)
    }
}
