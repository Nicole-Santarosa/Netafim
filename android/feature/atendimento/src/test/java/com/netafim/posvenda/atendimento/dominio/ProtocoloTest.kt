package com.netafim.posvenda.atendimento.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocoloTest {

    @Test
    fun noveMilNovecentosENoventaENoveVemAntesDeDezMil() {
        // Como texto, "OS-10000" viria antes de "OS-9999"; como número, não.
        assertTrue(compararProtocolos("OS-9999", "OS-10000") < 0)
        assertTrue(compararProtocolos("OS-10000", "OS-9999") > 0)
    }

    @Test
    fun comparaPeloNumeroEntreProtocolosComAMesmaQuantidadeDeDigitos() {
        assertTrue(compararProtocolos("OS-0477", "OS-0506") < 0)
        assertTrue(compararProtocolos("OS-0506", "OS-0477") > 0)
    }

    @Test
    fun mesmoNumeroDaEmpate() {
        assertEquals(0, compararProtocolos("OS-0477", "OS-0477"))
        // Zeros à esquerda não mudam o número.
        assertEquals(0, compararProtocolos("OS-0050", "OS-50"))
    }

    @Test
    fun formatoInesperadoVoltaAComparacaoDeTexto() {
        assertTrue(compararProtocolos("ABC", "ABD") < 0)
        assertTrue(compararProtocolos("OS-12A", "OS-12B") < 0)
        assertTrue(compararProtocolos("os-0001", "os-0002") < 0)
        assertEquals(0, compararProtocolos("", ""))
    }

    @Test
    fun protocoloValidoVemAntesDoInvalido() {
        // Mantém a ordem total: sem isso, 9, 10 e 1x formariam um ciclo e o sort poderia falhar.
        assertTrue(compararProtocolos("OS-9", "OS-1x") < 0)
        assertTrue(compararProtocolos("OS-1x", "OS-9") > 0)
    }

    @Test
    fun numeroMuitoGrandeNaoEstoura() {
        assertTrue(compararProtocolos("OS-99999999999999999999", "OS-100000000000000000000") < 0)
    }

    @Test
    fun ordenarUmaListaMistaNaoQuebra() {
        val lista = listOf("OS-10000", "OS-1x", "OS-9", "OS-0100", "ABC", "OS-9999")
        assertEquals(
            listOf("OS-9", "OS-0100", "OS-9999", "OS-10000", "ABC", "OS-1x"),
            lista.sortedWith(ComparadorDeProtocolo),
        )
    }
}
