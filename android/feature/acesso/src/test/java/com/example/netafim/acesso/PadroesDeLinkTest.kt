package com.example.netafim.acesso

import com.example.netafim.acesso.dominio.padroesDeLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PadroesDeLinkTest {
    @Test
    fun `release com dominio aceita so o link https verificado`() {
        assertEquals(
            listOf("https://app.netafim.com.br/convite?acao={acao}&token={token}"),
            padroesDeLink("app.netafim.com.br", esquemaProprio = false),
        )
    }

    @Test
    fun `debug acrescenta o esquema proprio`() {
        val padroes = padroesDeLink("app.netafim.com.br", esquemaProprio = true)
        assertEquals(2, padroes.size)
        assertTrue("netafim://convite?acao={acao}&token={token}" in padroes)
    }

    @Test
    fun `debug sem dominio fica so com o esquema proprio`() {
        assertEquals(
            listOf("netafim://convite?acao={acao}&token={token}"),
            padroesDeLink("", esquemaProprio = true),
        )
    }

    @Test
    fun `sem dominio e sem esquema proprio nao aceita link nenhum`() {
        assertEquals(emptyList<String>(), padroesDeLink("", esquemaProprio = false))
        assertEquals(emptyList<String>(), padroesDeLink("   ", esquemaProprio = false))
    }

    @Test
    fun `dominio e normalizado para minusculas`() {
        assertEquals(
            listOf("https://app.netafim.com.br/convite?acao={acao}&token={token}"),
            padroesDeLink("  App.Netafim.com.br ", esquemaProprio = false),
        )
    }

    @Test
    fun `recusa valor que nao e so o dominio`() {
        listOf(
            "https://app.netafim.com.br",
            "app.netafim.com.br/convite",
            "app.netafim.com.br:8080",
            "netafim",
            "app netafim.com.br",
            "-app.netafim.com.br",
            "app..netafim.com.br",
            "a@b.com",
        ).forEach { invalido ->
            assertThrows(invalido, IllegalArgumentException::class.java) {
                padroesDeLink(invalido, esquemaProprio = false)
            }
        }
    }
}
