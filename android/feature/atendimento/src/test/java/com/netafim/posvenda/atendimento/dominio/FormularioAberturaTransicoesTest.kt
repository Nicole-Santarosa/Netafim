package com.netafim.posvenda.atendimento.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormularioAberturaTransicoesTest {

    @Test
    fun digitalFarmingSemTipoDefineProjeto() {
        val f = FormularioAbertura().comMotivo(MotivoAtendimento.DIGITAL_FARMING)
        assertEquals(MotivoAtendimento.DIGITAL_FARMING, f.motivo)
        assertEquals(TipoVenda.PROJETO, f.tipoVenda)
    }

    @Test
    fun digitalFarmingComVendaRegularJaEscolhidaMantem() {
        val f = FormularioAbertura(tipoVenda = TipoVenda.VENDA_REGULAR).comMotivo(MotivoAtendimento.DIGITAL_FARMING)
        assertEquals(TipoVenda.VENDA_REGULAR, f.tipoVenda)
    }

    @Test
    fun sairDeDigitalFarmingZeraOTipoDeVenda() {
        val f = FormularioAbertura()
            .comMotivo(MotivoAtendimento.DIGITAL_FARMING)
            .comMotivo(MotivoAtendimento.DUVIDAS)
        assertEquals(MotivoAtendimento.DUVIDAS, f.motivo)
        assertNull(f.tipoVenda)
    }

    @Test
    fun outroMotivoSemTipoContinuaSemTipo() {
        assertNull(FormularioAbertura().comMotivo(MotivoAtendimento.POS_VENDA).tipoVenda)
    }

    @Test
    fun trocaDeMotivoNaoMexeNosOutrosCampos() {
        val antes = FormularioAbertura(culturas = setOf(1), localidade = "X", contato = "1", idDistribuidor = 3)
        val depois = antes.comMotivo(MotivoAtendimento.TREINAMENTO)
        assertEquals(setOf(1), depois.culturas)
        assertEquals("X", depois.localidade)
        assertEquals("1", depois.contato)
        assertEquals(3, depois.idDistribuidor)
    }

    @Test
    fun culturaAlternadaColocaETira() {
        val vazio = FormularioAbertura()
        val com = vazio.comCulturaAlternada(5)
        assertEquals(setOf(5), com.culturas)
        assertEquals(setOf(5, 7), com.comCulturaAlternada(7).culturas)
        assertEquals(emptySet<Int>(), com.comCulturaAlternada(5).culturas)
    }
}
