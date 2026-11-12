package com.netafim.posvenda.atendimento.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class MotivoAtendimentoTest {

    @Test
    fun motivosDoClienteTemCincoItensNaOrdemDoPrototipo() {
        assertEquals(
            listOf(
                MotivoAtendimento.DIGITAL_FARMING,
                MotivoAtendimento.DUVIDAS,
                MotivoAtendimento.ENTREGA_TECNICA,
                MotivoAtendimento.POS_VENDA,
                MotivoAtendimento.TREINAMENTO,
            ),
            MotivoAtendimento.motivosDoCliente,
        )
    }

    @Test
    fun motivosDoClienteNaoTemProgramacaoDeFeiras() {
        assertFalse(MotivoAtendimento.PROGRAMACAO_FEIRAS in MotivoAtendimento.motivosDoCliente)
    }

    @Test
    fun codigosSaoOsDoContratoDaApi() {
        assertEquals("digital_farming", MotivoAtendimento.DIGITAL_FARMING.codigo)
        assertEquals("duvidas", MotivoAtendimento.DUVIDAS.codigo)
        assertEquals("entrega_tecnica", MotivoAtendimento.ENTREGA_TECNICA.codigo)
        assertEquals("pos_venda", MotivoAtendimento.POS_VENDA.codigo)
        assertEquals("programacao_feiras", MotivoAtendimento.PROGRAMACAO_FEIRAS.codigo)
        assertEquals("treinamento", MotivoAtendimento.TREINAMENTO.codigo)
        assertEquals("projeto", TipoVenda.PROJETO.codigo)
        assertEquals("venda_regular", TipoVenda.VENDA_REGULAR.codigo)
    }

    @Test
    fun deCodigoReconheceOMotivoEDevolveNuloParaDesconhecido() {
        assertEquals(MotivoAtendimento.POS_VENDA, MotivoAtendimento.deCodigo("pos_venda"))
        assertNull(MotivoAtendimento.deCodigo("outro"))
    }
}
