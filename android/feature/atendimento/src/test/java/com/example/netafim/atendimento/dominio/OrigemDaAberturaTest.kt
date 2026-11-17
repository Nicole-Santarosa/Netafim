package com.example.netafim.atendimento.dominio

import com.example.netafim.core.modelo.OrigemAtendimento
import com.example.netafim.core.modelo.sessao.TipoDeOrganizacao
import com.example.netafim.core.modelo.sessao.UsuarioDaSessao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrigemDaAberturaTest {

    private fun usuario(tipo: TipoDeOrganizacao, idDaOrganizacao: Int?) = UsuarioDaSessao(
        id = 1,
        nome = "Usuário de teste",
        tipoDeOrganizacao = tipo,
        idDaOrganizacao = idDaOrganizacao,
        ehAdmin = false,
        permissoes = emptySet(),
    )

    @Test
    fun netafimAbreComoOrigemNetafimSemCliente() {
        // A Netafim não tem organização dona: o id vem nulo.
        val origem = usuario(TipoDeOrganizacao.NETAFIM, idDaOrganizacao = null).comoOrigemDaAbertura()
        assertEquals(OrigemDaAbertura(OrigemAtendimento.NETAFIM, idCliente = null, idDistribuidorOrigem = null), origem)
    }

    @Test
    fun distribuidorAbreComoOrigemDistribuidorComAPropriaOrganizacaoComoOrigem() {
        // A organização do distribuidor vira o distribuidor de origem, nunca o idCliente.
        val origem = usuario(TipoDeOrganizacao.DISTRIBUIDOR, idDaOrganizacao = 7).comoOrigemDaAbertura()
        assertEquals(
            OrigemDaAbertura(OrigemAtendimento.DISTRIBUIDOR, idCliente = null, idDistribuidorOrigem = 7),
            origem,
        )
    }

    @Test
    fun distribuidorSemOrganizacaoNaoTemOrigemDeAbertura() {
        assertNull(usuario(TipoDeOrganizacao.DISTRIBUIDOR, idDaOrganizacao = null).comoOrigemDaAbertura())
    }

    @Test
    fun clienteAbreComoClienteFinalComAPropriaOrganizacaoComoCliente() {
        val origem = usuario(TipoDeOrganizacao.CLIENTE, idDaOrganizacao = 42).comoOrigemDaAbertura()
        assertEquals(
            OrigemDaAbertura(OrigemAtendimento.CLIENTE_FINAL, idCliente = 42, idDistribuidorOrigem = null),
            origem,
        )
    }

    @Test
    fun clienteSemOrganizacaoNaoTemOrigemDeAbertura() {
        assertNull(usuario(TipoDeOrganizacao.CLIENTE, idDaOrganizacao = null).comoOrigemDaAbertura())
    }
}
