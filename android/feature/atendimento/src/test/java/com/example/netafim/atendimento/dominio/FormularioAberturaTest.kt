package com.example.netafim.atendimento.dominio

import com.example.netafim.core.modelo.MotivoAtendimento
import com.example.netafim.core.modelo.OrigemAtendimento
import com.example.netafim.core.modelo.TipoVenda
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    @Test
    fun motivoDesconhecidoContaComoMotivoNaoEscolhido() {
        assertEquals(ErroCampo.OBRIGATORIO, validar(completo.copy(motivo = MotivoAtendimento.DESCONHECIDO)).motivo)
    }

    @Test
    fun tipoVendaDesconhecidoCaiNoPadraoDoDigitalFarming() {
        val formulario = completo.copy(motivo = MotivoAtendimento.DIGITAL_FARMING, tipoVenda = TipoVenda.DESCONHECIDO)
        assertEquals(TipoVenda.PROJETO, tipoVendaEfetivo(formulario))
    }

    // ---- comoSolicitacao

    private val id = "7b1f0c9e-3f5a-4a52-9f0e-6f3c2a1d8b40"
    private val agora = Instant.parse("2026-11-16T12:00:00Z")

    private fun converter(formulario: FormularioAbertura, origem: OrigemAtendimento = OrigemAtendimento.CLIENTE_FINAL) =
        comoSolicitacao(formulario, id, origem, idCliente = 12, abertoEm = agora)

    @Test
    fun formularioValidoViraSolicitacaoComOsDadosDeFora() {
        val solicitacao = converter(completo)!!
        // Vindos de fora, sem a função gerar nada.
        assertEquals(id, solicitacao.id)
        assertEquals(OrigemAtendimento.CLIENTE_FINAL, solicitacao.origem)
        assertEquals(12, solicitacao.idCliente)
        assertEquals(agora, solicitacao.abertoEm)
        // Vindos do formulário.
        assertEquals(MotivoAtendimento.POS_VENDA, solicitacao.motivo)
        assertEquals(7, solicitacao.idDistribuidorOrigem)
        assertEquals("Fazenda Santa Rita", solicitacao.nomeFazenda)
        assertEquals("Ribeirão Preto", solicitacao.cidade)
        assertEquals("16999990000", solicitacao.contato)
        assertEquals(setOf(1, 2), solicitacao.culturas)
    }

    @Test
    fun camposQueOFormularioNaoColetaVaoVazios() {
        val solicitacao = converter(completo)!!
        assertEquals("", solicitacao.nomeProdutor)
        assertEquals("", solicitacao.areaSetor)
    }

    @Test
    fun textosDigitadosSaemAparados() {
        val formulario = completo.copy(cidade = "  Arealva ", nomeFazenda = " Boa Vista  ", contato = " 14999990000 ")
        val solicitacao = converter(formulario)!!
        assertEquals("Arealva", solicitacao.cidade)
        assertEquals("Boa Vista", solicitacao.nomeFazenda)
        assertEquals("14999990000", solicitacao.contato)
    }

    @Test
    fun tipoDeVendaSegueARegraDoDigitalFarming() {
        // Fora do Digital Farming o tipo vai nulo, mesmo que o formulário tenha um valor escondido.
        assertNull(converter(completo.copy(tipoVenda = TipoVenda.PROJETO))!!.tipoVenda)
        // No Digital Farming, sem escolha vale o padrão; com escolha, vale a escolha.
        val digital = completo.copy(motivo = MotivoAtendimento.DIGITAL_FARMING)
        assertEquals(TipoVenda.PROJETO, converter(digital)!!.tipoVenda)
        assertEquals(TipoVenda.VENDA_REGULAR, converter(digital.copy(tipoVenda = TipoVenda.VENDA_REGULAR))!!.tipoVenda)
    }

    @Test
    fun idDoDistribuidorNuloEFormularioInvalidoENaoViraSolicitacao() {
        // O distribuidor é obrigatório: sem ele a validação barra e nada é convertido.
        assertNull(converter(completo.copy(idDistribuidor = null)))
    }

    @Test
    fun formularioInvalidoNuncaViraSolicitacao() {
        assertNull(converter(FormularioAbertura()))
        assertNull(converter(completo.copy(culturas = emptySet())))
        assertNull(converter(completo.copy(cidade = "   ")))
        assertNull(converter(completo.copy(contato = "x".repeat(LIMITE_CONTATO + 1))))
    }

    @Test
    fun culturasViajamComoConjuntoDeIds() {
        val solicitacao = converter(completo.copy(culturas = setOf(5, 3, 3)))!!
        assertEquals(setOf(3, 5), solicitacao.culturas)
    }

    @Test
    fun formularioValidoNuncaProduzSolicitacaoQueOCoreRecuse() {
        // Todas as combinações de motivo e tipo de venda, inclusive DESCONHECIDO e nulo. Para cada uma, ou o
        // formulário é inválido e nada é convertido, ou a conversão sai sem o require do core recusar.
        val motivos = listOf<MotivoAtendimento?>(null) + MotivoAtendimento.entries
        val tipos = listOf<TipoVenda?>(null) + TipoVenda.entries
        var convertidos = 0
        for (motivo in motivos) {
            for (tipo in tipos) {
                val formulario = completo.copy(motivo = motivo, tipoVenda = tipo)
                val solicitacao = converter(formulario)
                if (validar(formulario).valido) {
                    assertNotNull("$motivo $tipo", solicitacao)
                    convertidos++
                } else {
                    assertNull("$motivo $tipo", solicitacao)
                }
            }
        }
        // Os seis motivos reais contam; nulo e DESCONHECIDO não.
        assertEquals((MotivoAtendimento.entries.size - 1) * tipos.size, convertidos)
    }
}
