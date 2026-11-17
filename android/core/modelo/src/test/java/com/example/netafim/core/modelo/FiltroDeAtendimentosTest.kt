package com.example.netafim.core.modelo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FiltroDeAtendimentosTest {

    @Test
    fun filtroPadraoNaoRestringeNada() {
        val filtro = FiltroDeAtendimentos()
        assertTrue(filtro.status.isEmpty())
        assertTrue(filtro.origem.isEmpty())
        assertNull(filtro.origemDoRegistro)
        assertFalse(filtro.somenteMeusComoTecnico)
        assertEquals("", filtro.busca)
    }

    @Test
    fun ordemPadraoEMaisRecentePrimeiro() {
        assertEquals(OrdemDaLista.MAIS_RECENTE_PRIMEIRO, FiltroDeAtendimentos().ordem)
    }
}
