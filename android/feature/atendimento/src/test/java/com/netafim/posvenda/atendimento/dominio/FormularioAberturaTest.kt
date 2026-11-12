package com.netafim.posvenda.atendimento.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormularioAberturaTest {

    // Formulário completo e válido, base dos demais testes.
    private val completo = FormularioAbertura(
        motivo = MotivoAtendimento.POS_VENDA,
        culturas = setOf(1, 2),
        localidade = "Ribeirão Preto",
        contato = "16999990000",
        idDistribuidor = 7,
        tipoVenda = null,
    )

    @Test
    fun formularioCompletoEValido() {
        val erros = validar(completo)
        assertTrue(erros.valido)
        assertNull(erros.motivo)
        assertNull(erros.localidade)
        assertNull(erros.contato)
    }

    @Test
    fun motivoNuloEObrigatorio() {
        val erros = validar(completo.copy(motivo = null))
        assertEquals(ErroCampo.OBRIGATORIO, erros.motivo)
        assertFalse(erros.valido)
    }

    @Test
    fun localidadeVaziaOuSoComEspacosFalha() {
        assertEquals(ErroCampo.OBRIGATORIO, validar(completo.copy(localidade = "")).localidade)
        assertEquals(ErroCampo.OBRIGATORIO, validar(completo.copy(localidade = "   ")).localidade)
    }

    @Test
    fun contatoVazioOuSoComEspacosFalha() {
        assertEquals(ErroCampo.OBRIGATORIO, validar(completo.copy(contato = "")).contato)
        assertEquals(ErroCampo.OBRIGATORIO, validar(completo.copy(contato = "  ")).contato)
    }

    @Test
    fun limiteDaLocalidade() {
        assertNull(validar(completo.copy(localidade = "a".repeat(LIMITE_LOCALIDADE))).localidade)
        assertEquals(
            ErroCampo.MUITO_LONGO,
            validar(completo.copy(localidade = "a".repeat(LIMITE_LOCALIDADE + 1))).localidade,
        )
    }

    @Test
    fun limiteDoContato() {
        assertNull(validar(completo.copy(contato = "1".repeat(LIMITE_CONTATO))).contato)
        assertEquals(
            ErroCampo.MUITO_LONGO,
            validar(completo.copy(contato = "1".repeat(LIMITE_CONTATO + 1))).contato,
        )
    }

    @Test
    fun tamanhoContaDepoisDeAparar() {
        // No limite mais espaços nas pontas: ainda passa.
        assertNull(validar(completo.copy(contato = "  " + "1".repeat(LIMITE_CONTATO) + "  ")).contato)
        assertNull(validar(completo.copy(localidade = " " + "a".repeat(LIMITE_LOCALIDADE) + " ")).localidade)
    }

    @Test
    fun tipoVendaEfetivoParaDigitalFarmingSemTipoEProjeto() {
        val f = completo.copy(motivo = MotivoAtendimento.DIGITAL_FARMING, tipoVenda = null)
        assertEquals(TipoVenda.PROJETO, tipoVendaEfetivo(f))
    }

    @Test
    fun tipoVendaEfetivoParaDigitalFarmingComTipoMantemOTipo() {
        val f = completo.copy(motivo = MotivoAtendimento.DIGITAL_FARMING, tipoVenda = TipoVenda.VENDA_REGULAR)
        assertEquals(TipoVenda.VENDA_REGULAR, tipoVendaEfetivo(f))
    }

    @Test
    fun tipoVendaEfetivoParaOutroMotivoENulo() {
        val f = completo.copy(motivo = MotivoAtendimento.DUVIDAS, tipoVenda = TipoVenda.PROJETO)
        assertNull(tipoVendaEfetivo(f))
        assertNull(tipoVendaEfetivo(completo.copy(motivo = null)))
    }

    @Test
    fun distribuidorECulturasAusentesNaoGeramErro() {
        val f = completo.copy(idDistribuidor = null, culturas = emptySet())
        assertTrue(validar(f).valido)
    }
}
