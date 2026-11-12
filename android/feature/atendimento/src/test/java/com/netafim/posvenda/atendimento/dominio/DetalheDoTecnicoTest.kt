package com.netafim.posvenda.atendimento.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetalheDoTecnicoTest {

    @Test
    fun podeIniciarSoParaAgendado() {
        StatusAtendimento.entries.forEach { status ->
            assertEquals(status.name, status == StatusAtendimento.AGENDADO, podeIniciar(status))
        }
    }

    @Test
    fun listaVaziaNaoTemTexto() {
        assertNull(culturasEmTexto(emptyList()))
    }

    @Test
    fun umaCulturaFicaSemVirgula() {
        assertEquals("Citrus", culturasEmTexto(listOf("Citrus")))
    }

    @Test
    fun variasCulturasSaoJuntasComVirgula() {
        assertEquals("Citrus, Soja, Milho", culturasEmTexto(listOf("Citrus", "Soja", "Milho")))
    }

    @Test
    fun espacosNasPontasSaoAparados() {
        assertEquals("Citrus, Soja", culturasEmTexto(listOf("  Citrus ", "Soja\t")))
    }

    @Test
    fun itensVaziosOuSoDeEspacosSaoIgnorados() {
        assertEquals("Citrus, Milho", culturasEmTexto(listOf("", "Citrus", "   ", "Milho")))
    }

    @Test
    fun listaSoDeItensVaziosNaoTemTexto() {
        assertNull(culturasEmTexto(listOf("", "  ", "\t")))
    }
}
