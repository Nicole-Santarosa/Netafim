package com.example.netafim.core.modelo

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SolicitacaoDeAberturaTest {

    private fun solicitacao(
        origem: OrigemAtendimento = OrigemAtendimento.CLIENTE_FINAL,
        motivo: MotivoAtendimento = MotivoAtendimento.POS_VENDA,
        tipoVenda: TipoVenda = TipoVenda.PROJETO,
        areaSetor: AreaDoSetor? = null,
        idDistribuidorOrigem: Int? = 3,
    ) = SolicitacaoDeAbertura(
        id = "7b1f0c9e-3f5a-4a52-9f0e-6f3c2a1d8b40",
        origem = origem,
        motivo = motivo,
        idCliente = 12,
        idDistribuidorOrigem = idDistribuidorOrigem,
        nomeProdutor = "",
        nomeFazenda = "Fazenda Boa Vista",
        cidade = "Ribeirão Preto",
        areaSetor = areaSetor,
        contato = "16999990000",
        tipoVenda = tipoVenda,
        culturas = setOf(1, 2),
        abertoEm = Instant.parse("2026-11-16T12:00:00Z"),
    )

    @Test
    fun solicitacaoValidaGuardaOsDados() {
        val pedido = solicitacao(motivo = MotivoAtendimento.DIGITAL_FARMING, tipoVenda = TipoVenda.PROJETO)
        assertEquals(setOf(1, 2), pedido.culturas)
        assertEquals(TipoVenda.PROJETO, pedido.tipoVenda)
    }

    @Test
    fun tipoDeVendaVemSempreEscolhidoEEmQualquerMotivo() {
        // O tipo de venda é obrigatório em todo atendimento (decisão da Netafim de 18/11/2026): não é mais
        // nulo, e vale para qualquer motivo, não só para o Digital Farming.
        for (tipo in listOf(TipoVenda.PROJETO, TipoVenda.VENDA_REGULAR)) {
            assertEquals(tipo, solicitacao(motivo = MotivoAtendimento.POS_VENDA, tipoVenda = tipo).tipoVenda)
        }
    }

    @Test
    fun distribuidorDeOrigemNuloEAceito() {
        // Cliente final sem distribuidor padrão: o atendimento nasce sem distribuidor de origem.
        assertNull(solicitacao(origem = OrigemAtendimento.CLIENTE_FINAL, idDistribuidorOrigem = null).idDistribuidorOrigem)
    }

    @Test
    fun recusaOrigemDesconhecida() {
        val erro = assertThrows(IllegalArgumentException::class.java) {
            solicitacao(origem = OrigemAtendimento.DESCONHECIDO)
        }
        assertEquals("A origem do atendimento não pode ser DESCONHECIDO.", erro.message)
    }

    @Test
    fun recusaMotivoDesconhecido() {
        val erro = assertThrows(IllegalArgumentException::class.java) {
            solicitacao(motivo = MotivoAtendimento.DESCONHECIDO)
        }
        assertEquals("O motivo do atendimento não pode ser DESCONHECIDO.", erro.message)
    }

    @Test
    fun recusaTipoDeVendaDesconhecido() {
        val erro = assertThrows(IllegalArgumentException::class.java) {
            solicitacao(tipoVenda = TipoVenda.DESCONHECIDO)
        }
        assertEquals("O tipo de venda não pode ser DESCONHECIDO.", erro.message)
    }

    @Test
    fun netafimComAreaEAceita() {
        val pedido = solicitacao(origem = OrigemAtendimento.NETAFIM, areaSetor = AreaDoSetor.ENGENHARIA)
        assertEquals(AreaDoSetor.ENGENHARIA, pedido.areaSetor)
    }

    @Test
    fun netafimSemAreaERecusada() {
        val erro = assertThrows(IllegalArgumentException::class.java) {
            solicitacao(origem = OrigemAtendimento.NETAFIM, areaSetor = null)
        }
        assertEquals("A origem Netafim exige a área ou setor.", erro.message)
    }

    @Test
    fun outrasOrigensNaoPodemTerArea() {
        listOf(OrigemAtendimento.CLIENTE_FINAL, OrigemAtendimento.DISTRIBUIDOR).forEach { origem ->
            val erro = assertThrows(origem.name, IllegalArgumentException::class.java) {
                solicitacao(origem = origem, areaSetor = AreaDoSetor.COMERCIAL)
            }
            assertEquals("Só a origem Netafim informa a área ou setor.", erro.message)
            // Sem área, as duas são aceitas.
            assertNull(solicitacao(origem = origem, areaSetor = null).areaSetor)
        }
    }

    @Test
    fun distribuidorSemDistribuidorDeOrigemERecusado() {
        // A API devolve 400 no campo id_distribuidor_origem para o perfil Distribuidor sem o campo; o modelo
        // espelha a regra para o app nem montar o pedido.
        val erro = assertThrows(IllegalArgumentException::class.java) {
            solicitacao(origem = OrigemAtendimento.DISTRIBUIDOR, idDistribuidorOrigem = null)
        }
        assertEquals("A origem Distribuidor exige o idDistribuidorOrigem.", erro.message)
    }

    @Test
    fun distribuidorComDistribuidorDeOrigemEAceito() {
        val pedido = solicitacao(origem = OrigemAtendimento.DISTRIBUIDOR, idDistribuidorOrigem = 7)
        assertEquals(7, pedido.idDistribuidorOrigem)
    }

    @Test
    fun codigosDaAreaSaoOsDaApi() {
        // Os cinco códigos que o contrato e o CHECK do banco aceitam, na mesma ordem.
        assertEquals(
            listOf("comercial", "engenharia", "instalacao", "marketing", "pos_venda"),
            AreaDoSetor.entries.map { it.codigo },
        )
    }
}
