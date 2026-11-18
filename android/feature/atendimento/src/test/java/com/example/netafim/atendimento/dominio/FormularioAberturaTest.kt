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
        // O tipo de venda é obrigatório em todo atendimento: o formulário válido já traz uma escolha.
        tipoVenda = TipoVenda.PROJETO,
    )

    // Os testes deste arquivo são do formulário do cliente final, que era o único antes da abertura por
    // origem. As regras das outras origens ficam em FormularioPorOrigemTest.
    private fun validarCliente(formulario: FormularioAbertura) = validar(formulario, OrigemAtendimento.CLIENTE_FINAL)

    @Test
    fun formularioCompletoEValido() {
        val erros = validarCliente(completo)
        assertTrue(erros.valido)
        assertNull(erros.motivo)
        assertNull(erros.cidade)
        assertNull(erros.nomeFazenda)
        assertNull(erros.culturas)
        assertNull(erros.tipoVenda)
        assertNull(erros.contato)
    }

    @Test
    fun motivoNuloEObrigatorio() {
        val erros = validarCliente(completo.copy(motivo = null))
        assertEquals(ErroCampo.OBRIGATORIO, erros.motivo)
        assertFalse(erros.valido)
    }

    @Test
    fun cidadeVaziaOuSoComEspacosFalha() {
        assertEquals(ErroCampo.OBRIGATORIO, validarCliente(completo.copy(cidade = "")).cidade)
        assertEquals(ErroCampo.OBRIGATORIO, validarCliente(completo.copy(cidade = "   ")).cidade)
        assertFalse(validarCliente(completo.copy(cidade = "")).valido)
    }

    @Test
    fun nomeDaFazendaVazioOuSoComEspacosFalha() {
        assertEquals(ErroCampo.OBRIGATORIO, validarCliente(completo.copy(nomeFazenda = "")).nomeFazenda)
        assertEquals(ErroCampo.OBRIGATORIO, validarCliente(completo.copy(nomeFazenda = "   ")).nomeFazenda)
        assertFalse(validarCliente(completo.copy(nomeFazenda = "")).valido)
    }

    @Test
    fun semCulturaFalha() {
        val erros = validarCliente(completo.copy(culturas = emptySet()))
        assertEquals(ErroCampo.OBRIGATORIO, erros.culturas)
        assertFalse(erros.valido)
    }

    @Test
    fun umaCulturaSoJaBasta() {
        assertNull(validarCliente(completo.copy(culturas = setOf(1))).culturas)
    }

    @Test
    fun clienteFinalSemDistribuidorEValido() {
        // O distribuidor é opcional (decisão da Netafim de 18/11/2026): o cliente final sem distribuidor
        // padrão abre o atendimento e ele vai para a fila do Netafim Admin.
        val erros = validarCliente(completo.copy(idDistribuidor = null))
        assertTrue(erros.valido)
    }

    @Test
    fun formularioVazioApontaTodosOsObrigatorios() {
        // O vazio é o que a tela mostra antes de o cliente preencher; todos os erros vêm de uma vez.
        val erros = validarCliente(FormularioAbertura())
        assertEquals(ErroCampo.OBRIGATORIO, erros.motivo)
        assertEquals(ErroCampo.OBRIGATORIO, erros.cidade)
        assertEquals(ErroCampo.OBRIGATORIO, erros.nomeFazenda)
        assertEquals(ErroCampo.OBRIGATORIO, erros.culturas)
        assertEquals(ErroCampo.OBRIGATORIO, erros.tipoVenda)
        assertEquals(ErroCampo.OBRIGATORIO, erros.contato)
    }

    @Test
    fun contatoVazioOuSoComEspacosFalha() {
        assertEquals(ErroCampo.OBRIGATORIO, validarCliente(completo.copy(contato = "")).contato)
        assertEquals(ErroCampo.OBRIGATORIO, validarCliente(completo.copy(contato = "  ")).contato)
    }

    @Test
    fun limiteDaCidade() {
        assertEquals(100, LIMITE_CIDADE)
        assertNull(validarCliente(completo.copy(cidade = "a".repeat(LIMITE_CIDADE))).cidade)
        assertEquals(
            ErroCampo.MUITO_LONGO,
            validarCliente(completo.copy(cidade = "a".repeat(LIMITE_CIDADE + 1))).cidade,
        )
    }

    @Test
    fun limiteDoNomeDaFazenda() {
        // 150 é o maxLength de nome_fazenda_informado no contrato da API.
        assertEquals(150, LIMITE_NOME_FAZENDA)
        assertNull(validarCliente(completo.copy(nomeFazenda = "a".repeat(LIMITE_NOME_FAZENDA))).nomeFazenda)
        assertEquals(
            ErroCampo.MUITO_LONGO,
            validarCliente(completo.copy(nomeFazenda = "a".repeat(LIMITE_NOME_FAZENDA + 1))).nomeFazenda,
        )
    }

    @Test
    fun limiteDoContato() {
        assertNull(validarCliente(completo.copy(contato = "1".repeat(LIMITE_CONTATO))).contato)
        assertEquals(
            ErroCampo.MUITO_LONGO,
            validarCliente(completo.copy(contato = "1".repeat(LIMITE_CONTATO + 1))).contato,
        )
    }

    @Test
    fun tamanhoContaDepoisDeAparar() {
        // No limite mais espaços nas pontas: ainda passa.
        assertNull(validarCliente(completo.copy(contato = "  " + "1".repeat(LIMITE_CONTATO) + "  ")).contato)
        assertNull(validarCliente(completo.copy(cidade = " " + "a".repeat(LIMITE_CIDADE) + " ")).cidade)
        assertNull(
            validarCliente(completo.copy(nomeFazenda = " " + "a".repeat(LIMITE_NOME_FAZENDA) + " ")).nomeFazenda,
        )
    }

    @Test
    fun tipoVendaEfetivoSemEscolhaENuloEmQualquerMotivo() {
        // Não há valor padrão: sem escolha, nem o Digital Farming (que antes assumia Projeto) devolve um tipo.
        assertNull(tipoVendaEfetivo(completo.copy(motivo = MotivoAtendimento.DIGITAL_FARMING, tipoVenda = null)))
        assertNull(tipoVendaEfetivo(completo.copy(motivo = MotivoAtendimento.DUVIDAS, tipoVenda = null)))
        assertNull(tipoVendaEfetivo(completo.copy(motivo = null, tipoVenda = null)))
    }

    @Test
    fun tipoVendaEfetivoComEscolhaMantemOTipoEmQualquerMotivo() {
        for (motivo in listOf(MotivoAtendimento.DIGITAL_FARMING, MotivoAtendimento.DUVIDAS, null)) {
            val f = completo.copy(motivo = motivo, tipoVenda = TipoVenda.VENDA_REGULAR)
            assertEquals(TipoVenda.VENDA_REGULAR, tipoVendaEfetivo(f))
        }
    }

    @Test
    fun tipoVendaNuloEObrigatorioEmTodasAsOrigens() {
        // O erro aparece nas três origens, e só o tipo de venda é apontado quando é o único que falta.
        for (origem in listOf(OrigemAtendimento.CLIENTE_FINAL, OrigemAtendimento.DISTRIBUIDOR, OrigemAtendimento.NETAFIM)) {
            val erros = validar(completo.copy(tipoVenda = null), origem)
            assertEquals(origem.name, ErroCampo.OBRIGATORIO, erros.tipoVenda)
        }
        val soOTipo = validarCliente(completo.copy(tipoVenda = null))
        assertEquals(ErrosFormulario(tipoVenda = ErroCampo.OBRIGATORIO), soOTipo)
        assertFalse(soOTipo.valido)
    }

    @Test
    fun tipoVendaObrigatorioEmTodosOsMotivos() {
        // Antes só o Digital Farming tinha tipo de venda. Agora todo motivo exige a escolha.
        for (motivo in MotivoAtendimento.motivosDoCliente) {
            assertEquals(
                motivo.name,
                ErroCampo.OBRIGATORIO,
                validarCliente(completo.copy(motivo = motivo, tipoVenda = null)).tipoVenda,
            )
            assertNull(motivo.name, validarCliente(completo.copy(motivo = motivo)).tipoVenda)
        }
    }

    @Test
    fun osDoisTiposDeVendaSaoAceitos() {
        assertTrue(validarCliente(completo.copy(tipoVenda = TipoVenda.PROJETO)).valido)
        assertTrue(validarCliente(completo.copy(tipoVenda = TipoVenda.VENDA_REGULAR)).valido)
    }

    @Test
    fun motivoDesconhecidoContaComoMotivoNaoEscolhido() {
        assertEquals(
            ErroCampo.OBRIGATORIO,
            validarCliente(completo.copy(motivo = MotivoAtendimento.DESCONHECIDO)).motivo,
        )
    }

    @Test
    fun tipoVendaDesconhecidoContaComoNaoEscolhido() {
        // DESCONHECIDO não é uma escolha possível: não vira padrão, é erro e não chega à solicitação.
        val formulario = completo.copy(tipoVenda = TipoVenda.DESCONHECIDO)
        assertNull(tipoVendaEfetivo(formulario))
        assertEquals(ErroCampo.OBRIGATORIO, validarCliente(formulario).tipoVenda)
        assertNull(converter(formulario))
    }

    // ---- comoSolicitacao

    private val id = "7b1f0c9e-3f5a-4a52-9f0e-6f3c2a1d8b40"
    private val agora = Instant.parse("2026-11-16T12:00:00Z")

    private val origemDoCliente = OrigemDaAbertura(
        OrigemAtendimento.CLIENTE_FINAL,
        idCliente = 12,
        idDistribuidorOrigem = null,
    )

    private fun converter(formulario: FormularioAbertura) =
        comoSolicitacao(formulario, id, origemDoCliente, abertoEm = agora)

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
        assertEquals(TipoVenda.PROJETO, solicitacao.tipoVenda)
    }

    @Test
    fun camposQueOFormularioNaoColetaVaoVazios() {
        val solicitacao = converter(completo)!!
        assertEquals("", solicitacao.nomeProdutor)
        assertNull(solicitacao.areaSetor)
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
    fun tipoDeVendaViajaNaSolicitacaoEmQualquerMotivo() {
        // O tipo de venda é sempre o escolhido, em qualquer motivo (antes só o Digital Farming o enviava).
        for (motivo in MotivoAtendimento.motivosDoCliente) {
            for (tipo in listOf(TipoVenda.PROJETO, TipoVenda.VENDA_REGULAR)) {
                assertEquals(tipo, converter(completo.copy(motivo = motivo, tipoVenda = tipo))!!.tipoVenda)
            }
        }
    }

    @Test
    fun semTipoDeVendaOFormularioNaoViraSolicitacao() {
        // Sem escolha não há padrão: a validação barra e nada é convertido.
        assertNull(converter(completo.copy(tipoVenda = null)))
        assertNull(converter(completo.copy(motivo = MotivoAtendimento.DIGITAL_FARMING, tipoVenda = null)))
    }

    @Test
    fun clienteFinalSemDistribuidorViraSolicitacaoComDistribuidorNulo() {
        // O distribuidor é opcional: sem ele o atendimento nasce sem distribuidor de origem.
        val solicitacao = converter(completo.copy(idDistribuidor = null))!!
        assertNull(solicitacao.idDistribuidorOrigem)
        assertEquals(12, solicitacao.idCliente)
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
        // formulário é inválido e nada é convertido, ou a conversão sai sem o require do core recusar. Sem
        // tipo de venda escolhido (nulo ou DESCONHECIDO) o formulário é sempre inválido.
        val motivos = listOf<MotivoAtendimento?>(null) + MotivoAtendimento.entries
        val tipos = listOf<TipoVenda?>(null) + TipoVenda.entries
        var convertidos = 0
        for (motivo in motivos) {
            for (tipo in tipos) {
                val formulario = completo.copy(motivo = motivo, tipoVenda = tipo)
                val solicitacao = converter(formulario)
                if (validarCliente(formulario).valido) {
                    assertNotNull("$motivo $tipo", solicitacao)
                    convertidos++
                } else {
                    assertNull("$motivo $tipo", solicitacao)
                }
            }
        }
        // Só contam os motivos reais (nulo e DESCONHECIDO não) combinados com os dois tipos de venda reais.
        val tiposReais = TipoVenda.entries.count { it != TipoVenda.DESCONHECIDO }
        assertEquals((MotivoAtendimento.entries.size - 1) * tiposReais, convertidos)
    }
}
