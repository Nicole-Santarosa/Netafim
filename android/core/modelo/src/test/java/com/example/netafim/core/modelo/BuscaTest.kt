package com.example.netafim.core.modelo

import org.junit.Assert.assertEquals
import org.junit.Test

class BuscaTest {

    @Test
    fun tiraAcentosEIgnoraMaiusculas() {
        assertEquals("ribeirao preto", normalizarParaBusca("Ribeirão Preto"))
        assertEquals("sao jose do rio pardo", normalizarParaBusca("SÃO JOSÉ DO RIO PARDO"))
    }

    @Test
    fun aparaOsEspacosDasPontas() {
        assertEquals("os-0477", normalizarParaBusca("  OS-0477 \n"))
    }

    @Test
    fun mantemOsEspacosDoMeio() {
        assertEquals("boa vista", normalizarParaBusca("Boa Vista"))
    }

    @Test
    fun textoSoDeEspacosViraVazio() {
        assertEquals("", normalizarParaBusca("   "))
    }
}
