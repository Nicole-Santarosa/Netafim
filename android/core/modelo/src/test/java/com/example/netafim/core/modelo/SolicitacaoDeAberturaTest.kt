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
        tipoVenda: TipoVenda? = null,
    ) = SolicitacaoDeAbertura(
        id = "7b1f0c9e-3f5a-4a52-9f0e-6f3c2a1d8b40",
        origem = origem,
        motivo = motivo,
        idCliente = 12,
        idDistribuidorOrigem = 3,
        nomeProdutor = "",
        nomeFazenda = "Fazenda Boa Vista",
        cidade = "Ribeirão Preto",
        areaSetor = "",
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
    fun tipoDeVendaNuloEAceito() {
        assertNull(solicitacao(tipoVenda = null).tipoVenda)
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
}
