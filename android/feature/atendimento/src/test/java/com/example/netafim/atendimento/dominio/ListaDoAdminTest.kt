package com.example.netafim.atendimento.dominio

import com.example.netafim.core.modelo.ItemDeAtendimento
import com.example.netafim.core.modelo.MotivoAtendimento
import com.example.netafim.core.modelo.OrdemDaLista
import com.example.netafim.core.modelo.OrigemAtendimento
import com.example.netafim.core.modelo.SituacaoDeSincronizacao
import com.example.netafim.core.modelo.StatusAtendimento
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListaDoAdminTest {

    private fun item(
        id: String,
        protocolo: String = "OS-$id",
        nome: String = "Cliente $id",
        origem: OrigemAtendimento = OrigemAtendimento.NETAFIM,
        aberto: String = "2026-11-01T12:00:00Z",
    ) = ItemDeAtendimento(
        id = id,
        protocolo = protocolo,
        motivo = MotivoAtendimento.POS_VENDA,
        status = StatusAtendimento.DESTINADO,
        origem = origem,
        abertoEm = Instant.parse(aberto),
        nomeCliente = nome,
        local = null,
        idTecnicoResponsavel = null,
        sincronizacao = SituacaoDeSincronizacao.SINCRONIZADO,
    )

    private val semProtocolo = "Pendente de sincronização"

    // ---- OrigemAtendimento.deCodigo

    @Test
    fun deCodigoDevolveCadaOrigemDoContrato() {
        assertEquals(OrigemAtendimento.NETAFIM, OrigemAtendimento.deCodigo("netafim"))
        assertEquals(OrigemAtendimento.DISTRIBUIDOR, OrigemAtendimento.deCodigo("distribuidor"))
        assertEquals(OrigemAtendimento.CLIENTE_FINAL, OrigemAtendimento.deCodigo("cliente_final"))
    }

    @Test
    fun deCodigoDesconhecidoDevolveDesconhecido() {
        assertEquals(OrigemAtendimento.DESCONHECIDO, OrigemAtendimento.deCodigo("revenda"))
        assertEquals(OrigemAtendimento.DESCONHECIDO, OrigemAtendimento.deCodigo(""))
    }

    // ---- comoFiltroDeAtendimentos
    // A filtragem e a ordem em si são do repositório; aqui se confere só a tradução do chip no pedido.

    @Test
    fun todasNaoLimitaAOrigem() {
        // Conjunto vazio quer dizer sem filtro, inclusive para a origem que o app ainda não conhece.
        assertTrue(FiltroDeOrigem.TODAS.comoFiltroDeAtendimentos().origem.isEmpty())
    }

    @Test
    fun cadaChipPedeSoASuaOrigem() {
        val correspondente = mapOf(
            FiltroDeOrigem.NETAFIM to OrigemAtendimento.NETAFIM,
            FiltroDeOrigem.DISTRIBUIDOR to OrigemAtendimento.DISTRIBUIDOR,
            FiltroDeOrigem.CLIENTE_FINAL to OrigemAtendimento.CLIENTE_FINAL,
        )
        correspondente.forEach { (filtro, esperada) ->
            assertEquals(filtro.name, setOf(esperada), filtro.comoFiltroDeAtendimentos().origem)
        }
    }

    @Test
    fun aListaDoAdminSaiDoMaisRecenteSemBuscaNemLimiteDeStatus() {
        FiltroDeOrigem.entries.forEach { filtro ->
            val pedido = filtro.comoFiltroDeAtendimentos()
            assertEquals(filtro.name, OrdemDaLista.MAIS_RECENTE_PRIMEIRO, pedido.ordem)
            assertEquals(filtro.name, "", pedido.busca)
            assertTrue(filtro.name, pedido.status.isEmpty())
            assertFalse(filtro.name, pedido.somenteMeusComoTecnico)
        }
    }

    // ---- textoDaLinha

    @Test
    fun textoDaLinhaJuntaProtocoloEClienteComPontoMedio() {
        val item = item("1", "OS-0506", "Vanderlei Zucchi Rodas")
        assertEquals("OS-0506 · Vanderlei Zucchi Rodas", textoDaLinha(item, semProtocolo))
    }

    @Test
    fun textoDaLinhaSemProtocoloUsaOTextoRecebido() {
        val item = item("1", "OS-0506", "Vanderlei").copy(protocolo = null)
        assertEquals("Pendente de sincronização · Vanderlei", textoDaLinha(item, semProtocolo))
    }

    @Test
    fun textoDaLinhaSemNomeDoClienteMostraSoOProtocolo() {
        assertEquals("OS-0506", textoDaLinha(item("1", "OS-0506").copy(nomeCliente = null), semProtocolo))
        assertEquals("OS-0506", textoDaLinha(item("1", "OS-0506").copy(nomeCliente = "  "), semProtocolo))
    }
}
