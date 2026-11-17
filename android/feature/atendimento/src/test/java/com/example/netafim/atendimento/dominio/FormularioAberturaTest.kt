package com.example.netafim.atendimento.dominio

import com.example.netafim.core.modelo.MotivoAtendimento
import com.example.netafim.core.modelo.TipoVenda
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
        cidade = "Ribeirão Preto",
        nomeFazenda = "Fazenda Santa Rita",
        contato = "16999990000",
        idDistribuidor = 7,
        tipoVenda = null,
    )

    @Test
    fun formularioCompletoEValido() {
        val erros = validar(completo)
        assertTrue(erros.valido)
        assertNull(erros.motivo)
        assertNull(erros.cidade)
        assertNull(erros.nomeFazenda)
        assertNull(erros.culturas)
        assertNull(erros.distribuidor)
        assertNull(erros.contato)
    }

    @Test
    fun motivoNuloEObrigatorio() {
        val erros = validar(completo.copy(motivo = null))
        assertEquals(ErroCampo.OBRIGATORIO, erros.motivo)
        assertFalse(erros.valido)
    }

    @Test
    fun cidadeVaziaOuSoComEspacosFalha() {
        assertEquals(ErroCampo.OBRIGATORIO, validar(completo.copy(cidade = "")).cidade)
        assertEquals(ErroCampo.OBRIGATORIO, validar(completo.copy(cidade = "   ")).cidade)
        assertFalse(validar(completo.copy(cidade = "")).valido)
    }

    @Test
    fun nomeDaFazendaVazioOuSoComEspacosFalha() {
        assertEquals(ErroCampo.OBRIGATORIO, validar(completo.copy(nomeFazenda = "")).nomeFazenda)
        assertEquals(ErroCampo.OBRIGATORIO, validar(completo.copy(nomeFazenda = "   ")).nomeFazenda)
        assertFalse(validar(completo.copy(nomeFazenda = "")).valido)
    }

    @Test
    fun semCulturaFalha() {
        val erros = validar(completo.copy(culturas = emptySet()))
        assertEquals(ErroCampo.OBRIGATORIO, erros.culturas)
        assertFalse(erros.valido)
    }

    @Test
    fun umaCulturaSoJaBasta() {
        assertNull(validar(completo.copy(culturas = setOf(1))).culturas)
    }

    @Test
    fun semDistribuidorFalha() {
        val erros = validar(completo.copy(idDistribuidor = null))
        assertEquals(ErroCampo.OBRIGATORIO, erros.distribuidor)
        assertFalse(erros.valido)
    }

    @Test
    fun formularioVazioApontaTodosOsObrigatorios() {
        // O vazio é o que a tela mostra antes de o cliente preencher; todos os erros vêm de uma vez.
        val erros = validar(FormularioAbertura())
        assertEquals(ErroCampo.OBRIGATORIO, erros.motivo)
        assertEquals(ErroCampo.OBRIGATORIO, erros.cidade)
        assertEquals(ErroCampo.OBRIGATORIO, erros.nomeFazenda)
        assertEquals(ErroCampo.OBRIGATORIO, erros.culturas)
        assertEquals(ErroCampo.OBRIGATORIO, erros.distribuidor)
        assertEquals(ErroCampo.OBRIGATORIO, erros.contato)
    }

    @Test
    fun contatoVazioOuSoComEspacosFalha() {
        assertEquals(ErroCampo.OBRIGATORIO, validar(completo.copy(contato = "")).contato)
        assertEquals(ErroCampo.OBRIGATORIO, validar(completo.copy(contato = "  ")).contato)
    }

    @Test
    fun limiteDaCidade() {
        assertEquals(100, LIMITE_CIDADE)
        assertNull(validar(completo.copy(cidade = "a".repeat(LIMITE_CIDADE))).cidade)
        assertEquals(
            ErroCampo.MUITO_LONGO,
            validar(completo.copy(cidade = "a".repeat(LIMITE_CIDADE + 1))).cidade,
        )
    }

    @Test
    fun limiteDoNomeDaFazenda() {
        // 150 é o maxLength de nome_fazenda_informado no contrato da API.
        assertEquals(150, LIMITE_NOME_FAZENDA)
        assertNull(validar(completo.copy(nomeFazenda = "a".repeat(LIMITE_NOME_FAZENDA))).nomeFazenda)
        assertEquals(
            ErroCampo.MUITO_LONGO,
            validar(completo.copy(nomeFazenda = "a".repeat(LIMITE_NOME_FAZENDA + 1))).nomeFazenda,
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
        assertNull(validar(completo.copy(cidade = " " + "a".repeat(LIMITE_CIDADE) + " ")).cidade)
        assertNull(
            validar(completo.copy(nomeFazenda = " " + "a".repeat(LIMITE_NOME_FAZENDA) + " ")).nomeFazenda,
        )
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
}
