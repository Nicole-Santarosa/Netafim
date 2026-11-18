package com.example.netafim.atendimento.dominio

import com.example.netafim.core.modelo.AreaDoSetor
import com.example.netafim.core.modelo.MotivoAtendimento
import com.example.netafim.core.modelo.OrigemAtendimento
import com.example.netafim.core.modelo.TipoVenda
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Regras do formulário de abertura que mudam conforme a origem de quem abre (cliente final, distribuidor e
// Netafim). As regras comuns e as do cliente final continuam em FormularioAberturaTest.
class FormularioPorOrigemTest {

    // Campos comuns às três origens, já válidos. O tipo de venda é obrigatório em todas.
    private val comum = FormularioAbertura(
        motivo = MotivoAtendimento.POS_VENDA,
        culturas = setOf(1),
        cidade = "Bauru",
        contato = "14988880000",
        tipoVenda = TipoVenda.VENDA_REGULAR,
    )
    private val doCliente = comum.copy(nomeFazenda = "Fazenda Santa Rita", idDistribuidor = 9)
    private val doDistribuidor = comum.copy(nomeProdutor = "José da Silva")
    private val daNetafim = comum.copy(nomeProdutor = "Maria Souza", areaSetor = AreaDoSetor.ENGENHARIA)

    private val origemCliente =
        OrigemDaAbertura(OrigemAtendimento.CLIENTE_FINAL, idCliente = 42, idDistribuidorOrigem = null)
    private val origemDistribuidor =
        OrigemDaAbertura(OrigemAtendimento.DISTRIBUIDOR, idCliente = null, idDistribuidorOrigem = 7)
    private val origemNetafim =
        OrigemDaAbertura(OrigemAtendimento.NETAFIM, idCliente = null, idDistribuidorOrigem = null)

    private val id = "7b1f0c9e-3f5a-4a52-9f0e-6f3c2a1d8b40"
    private val agora = Instant.parse("2026-11-17T12:00:00Z")

    // ---- validar: o que cada origem cobra

    @Test
    fun formularioCompletoDeCadaOrigemEValido() {
        assertTrue(validar(doCliente, OrigemAtendimento.CLIENTE_FINAL).valido)
        assertTrue(validar(doDistribuidor, OrigemAtendimento.DISTRIBUIDOR).valido)
        assertTrue(validar(daNetafim, OrigemAtendimento.NETAFIM).valido)
    }

    @Test
    fun formularioVazioDoDistribuidorApontaSoOsCamposDele() {
        val erros = validar(FormularioAbertura(), OrigemAtendimento.DISTRIBUIDOR)
        assertEquals(
            ErrosFormulario(
                motivo = ErroCampo.OBRIGATORIO,
                cidade = ErroCampo.OBRIGATORIO,
                culturas = ErroCampo.OBRIGATORIO,
                contato = ErroCampo.OBRIGATORIO,
                nomeProdutor = ErroCampo.OBRIGATORIO,
                tipoVenda = ErroCampo.OBRIGATORIO,
            ),
            erros,
        )
    }

    @Test
    fun formularioVazioDaNetafimApontaSoOsCamposDela() {
        val erros = validar(FormularioAbertura(), OrigemAtendimento.NETAFIM)
        assertEquals(
            ErrosFormulario(
                motivo = ErroCampo.OBRIGATORIO,
                cidade = ErroCampo.OBRIGATORIO,
                culturas = ErroCampo.OBRIGATORIO,
                contato = ErroCampo.OBRIGATORIO,
                nomeProdutor = ErroCampo.OBRIGATORIO,
                areaSetor = ErroCampo.OBRIGATORIO,
                tipoVenda = ErroCampo.OBRIGATORIO,
            ),
            erros,
        )
    }

    @Test
    fun formularioVazioDoClienteApontaSoOsCamposDele() {
        val erros = validar(FormularioAbertura(), OrigemAtendimento.CLIENTE_FINAL)
        assertEquals(
            ErrosFormulario(
                motivo = ErroCampo.OBRIGATORIO,
                cidade = ErroCampo.OBRIGATORIO,
                nomeFazenda = ErroCampo.OBRIGATORIO,
                culturas = ErroCampo.OBRIGATORIO,
                contato = ErroCampo.OBRIGATORIO,
                tipoVenda = ErroCampo.OBRIGATORIO,
            ),
            erros,
        )
    }

    @Test
    fun produtorEmBrancoFalhaNoDistribuidorENaNetafim() {
        assertEquals(
            ErroCampo.OBRIGATORIO,
            validar(doDistribuidor.copy(nomeProdutor = "   "), OrigemAtendimento.DISTRIBUIDOR).nomeProdutor,
        )
        assertEquals(
            ErroCampo.OBRIGATORIO,
            validar(daNetafim.copy(nomeProdutor = ""), OrigemAtendimento.NETAFIM).nomeProdutor,
        )
    }

    @Test
    fun netafimSemAreaFalha() {
        val erros = validar(daNetafim.copy(areaSetor = null), OrigemAtendimento.NETAFIM)
        assertEquals(ErroCampo.OBRIGATORIO, erros.areaSetor)
    }

    @Test
    fun limiteDoNomeDoProdutor() {
        // 150, como a coluna nome_produtor do banco. O tamanho conta depois de aparar.
        assertEquals(150, LIMITE_NOME_PRODUTOR)
        val noLimite = " " + "a".repeat(LIMITE_NOME_PRODUTOR) + " "
        val passou = "a".repeat(LIMITE_NOME_PRODUTOR + 1)
        listOf(OrigemAtendimento.DISTRIBUIDOR to doDistribuidor, OrigemAtendimento.NETAFIM to daNetafim)
            .forEach { (origem, formulario) ->
                assertNull(origem.name, validar(formulario.copy(nomeProdutor = noLimite), origem).nomeProdutor)
                assertEquals(
                    origem.name,
                    ErroCampo.MUITO_LONGO,
                    validar(formulario.copy(nomeProdutor = passou), origem).nomeProdutor,
                )
            }
    }

    @Test
    fun clienteFinalSemDistribuidorEValidoEAsOutrasOrigensNaoCobramDistribuidor() {
        // O distribuidor não é cobrado de nenhuma origem (opcional), e o erro do distribuidor nem existe mais.
        assertTrue(validar(doCliente.copy(idDistribuidor = null), OrigemAtendimento.CLIENTE_FINAL).valido)
        assertTrue(validar(doDistribuidor.copy(idDistribuidor = null), OrigemAtendimento.DISTRIBUIDOR).valido)
        assertTrue(validar(daNetafim.copy(idDistribuidor = null), OrigemAtendimento.NETAFIM).valido)
    }

    @Test
    fun cidadeEObrigatoriaNasTresOrigens() {
        listOf(
            OrigemAtendimento.CLIENTE_FINAL to doCliente,
            OrigemAtendimento.DISTRIBUIDOR to doDistribuidor,
            OrigemAtendimento.NETAFIM to daNetafim,
        ).forEach { (origem, formulario) ->
            assertEquals(origem.name, ErroCampo.OBRIGATORIO, validar(formulario.copy(cidade = ""), origem).cidade)
            assertEquals(origem.name, ErroCampo.OBRIGATORIO, validar(formulario.copy(cidade = "  "), origem).cidade)
        }
    }

    @Test
    fun nomeDaFazendaEObrigatorioSoNoClienteFinal() {
        assertEquals(
            ErroCampo.OBRIGATORIO,
            validar(doCliente.copy(nomeFazenda = ""), OrigemAtendimento.CLIENTE_FINAL).nomeFazenda,
        )
        // Distribuidor e Netafim seguem válidos sem a fazenda.
        assertTrue(validar(doDistribuidor.copy(nomeFazenda = ""), OrigemAtendimento.DISTRIBUIDOR).valido)
        assertTrue(validar(daNetafim.copy(nomeFazenda = ""), OrigemAtendimento.NETAFIM).valido)
    }

    @Test
    fun semTipoDeVendaCadaOrigemApontaSoOTipo() {
        listOf(
            OrigemAtendimento.CLIENTE_FINAL to doCliente,
            OrigemAtendimento.DISTRIBUIDOR to doDistribuidor,
            OrigemAtendimento.NETAFIM to daNetafim,
        ).forEach { (origem, formulario) ->
            assertEquals(
                origem.name,
                ErrosFormulario(tipoVenda = ErroCampo.OBRIGATORIO),
                validar(formulario.copy(tipoVenda = null), origem),
            )
        }
    }

    @Test
    fun campoDeOutraOrigemEIgnorado() {
        // Valores que sobraram de outra origem, inclusive inválidos, não geram erro onde o campo não existe.
        val sobras = FormularioAbertura(
            nomeFazenda = "a".repeat(LIMITE_NOME_FAZENDA + 1),
            nomeProdutor = "a".repeat(LIMITE_NOME_PRODUTOR + 1),
        )
        // Cliente: produtor e área ignorados.
        assertTrue(validar(doCliente.copy(nomeProdutor = sobras.nomeProdutor), OrigemAtendimento.CLIENTE_FINAL).valido)
        // Distribuidor: fazenda, distribuidor escolhido e área ignorados.
        assertTrue(
            validar(
                doDistribuidor.copy(
                    nomeFazenda = sobras.nomeFazenda,
                    idDistribuidor = null,
                    areaSetor = AreaDoSetor.MARKETING,
                ),
                OrigemAtendimento.DISTRIBUIDOR,
            ).valido,
        )
        // Netafim: fazenda e distribuidor ignorados.
        assertTrue(validar(daNetafim.copy(nomeFazenda = sobras.nomeFazenda), OrigemAtendimento.NETAFIM).valido)
    }

    // ---- comoSolicitacao: o que cada origem manda

    @Test
    fun clienteMandaFazendaEDistribuidorEscolhidoSemProdutorNemArea() {
        // Produtor e área que sobraram no formulário não vão.
        val formulario = doCliente.copy(nomeProdutor = "Sobra", areaSetor = AreaDoSetor.COMERCIAL)
        val solicitacao = comoSolicitacao(formulario, id, origemCliente, agora)!!
        assertEquals(OrigemAtendimento.CLIENTE_FINAL, solicitacao.origem)
        assertEquals(42, solicitacao.idCliente)
        assertEquals(9, solicitacao.idDistribuidorOrigem)
        assertEquals("Fazenda Santa Rita", solicitacao.nomeFazenda)
        assertEquals("", solicitacao.nomeProdutor)
        assertNull(solicitacao.areaSetor)
    }

    @Test
    fun clienteFinalSemDistribuidorMandaDistribuidorNulo() {
        // Sem distribuidor escolhido a solicitação sai com o distribuidor de origem nulo, e o tipo de venda
        // sempre preenchido.
        val solicitacao = comoSolicitacao(doCliente.copy(idDistribuidor = null), id, origemCliente, agora)!!
        assertNull(solicitacao.idDistribuidorOrigem)
        assertEquals(42, solicitacao.idCliente)
        assertEquals(TipoVenda.VENDA_REGULAR, solicitacao.tipoVenda)
    }

    @Test
    fun distribuidorMandaProdutorEODistribuidorDaSessaoSemFazendaNemArea() {
        // O distribuidor escolhido no formulário (que nem aparece para ele) é trocado pelo da sessão.
        val formulario = doDistribuidor.copy(
            nomeProdutor = "  José da Silva ",
            nomeFazenda = "Sobra",
            idDistribuidor = 99,
            areaSetor = AreaDoSetor.MARKETING,
        )
        val solicitacao = comoSolicitacao(formulario, id, origemDistribuidor, agora)!!
        assertEquals(OrigemAtendimento.DISTRIBUIDOR, solicitacao.origem)
        assertNull(solicitacao.idCliente)
        assertEquals(7, solicitacao.idDistribuidorOrigem)
        assertEquals("José da Silva", solicitacao.nomeProdutor)
        assertEquals("", solicitacao.nomeFazenda)
        assertNull(solicitacao.areaSetor)
    }

    @Test
    fun netafimMandaProdutorEAreaSemFazendaNemDistribuidor() {
        val formulario = daNetafim.copy(nomeProdutor = " Maria Souza", nomeFazenda = "Sobra", idDistribuidor = 99)
        val solicitacao = comoSolicitacao(formulario, id, origemNetafim, agora)!!
        assertEquals(OrigemAtendimento.NETAFIM, solicitacao.origem)
        assertNull(solicitacao.idCliente)
        assertNull(solicitacao.idDistribuidorOrigem)
        assertEquals("Maria Souza", solicitacao.nomeProdutor)
        assertEquals(AreaDoSetor.ENGENHARIA, solicitacao.areaSetor)
        assertEquals("", solicitacao.nomeFazenda)
    }

    @Test
    fun camposComunsVaoIguaisEmTodasAsOrigens() {
        listOf(doCliente to origemCliente, doDistribuidor to origemDistribuidor, daNetafim to origemNetafim)
            .forEach { (formulario, origem) ->
                val solicitacao = comoSolicitacao(formulario, id, origem, agora)!!
                assertEquals(id, solicitacao.id)
                assertEquals(agora, solicitacao.abertoEm)
                assertEquals(MotivoAtendimento.POS_VENDA, solicitacao.motivo)
                assertEquals("Bauru", solicitacao.cidade)
                assertEquals("14988880000", solicitacao.contato)
                assertEquals(setOf(1), solicitacao.culturas)
                assertEquals(TipoVenda.VENDA_REGULAR, solicitacao.tipoVenda)
            }
    }

    @Test
    fun formularioIncompletoParaAOrigemNaoViraSolicitacao() {
        // O formulário do cliente não serve para o distribuidor (falta o produtor) nem para a Netafim (falta a
        // área também), e o do distribuidor não serve para a Netafim (falta a área).
        assertNull(comoSolicitacao(doCliente, id, origemDistribuidor, agora))
        assertNull(comoSolicitacao(doCliente, id, origemNetafim, agora))
        assertNull(comoSolicitacao(doDistribuidor, id, origemNetafim, agora))
        assertNull(comoSolicitacao(doDistribuidor, id, origemCliente, agora))
    }

    @Test
    fun origemDesconhecidaNaoViraSolicitacao() {
        val desconhecida =
            OrigemDaAbertura(OrigemAtendimento.DESCONHECIDO, idCliente = null, idDistribuidorOrigem = null)
        assertNull(comoSolicitacao(doCliente, id, desconhecida, agora))
    }
}
