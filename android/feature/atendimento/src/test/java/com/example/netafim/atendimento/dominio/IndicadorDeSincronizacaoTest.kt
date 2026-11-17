package com.example.netafim.atendimento.dominio

import com.example.netafim.core.modelo.SituacaoDeSincronizacao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IndicadorDeSincronizacaoTest {

    @Test
    fun sincronizadoNaoMostraIndicador() {
        assertNull(SituacaoDeSincronizacao.SINCRONIZADO.comoIndicador())
    }

    @Test
    fun pendenteDeEnvioMostraPendente() {
        assertEquals(IndicadorDeSincronizacao.PENDENTE, SituacaoDeSincronizacao.PENDENTE_DE_ENVIO.comoIndicador())
    }

    @Test
    fun conflitoMostraAtencao() {
        assertEquals(IndicadorDeSincronizacao.ATENCAO, SituacaoDeSincronizacao.CONFLITO.comoIndicador())
    }

    @Test
    fun erroMostraAtencao() {
        assertEquals(IndicadorDeSincronizacao.ATENCAO, SituacaoDeSincronizacao.ERRO.comoIndicador())
    }
}
