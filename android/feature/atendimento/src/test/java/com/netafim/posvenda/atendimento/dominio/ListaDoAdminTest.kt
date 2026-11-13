package com.netafim.posvenda.atendimento.dominio

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListaDoAdminTest {

    private fun item(
        id: String,
        protocolo: String = "OS-$id",
        nome: String = "Cliente $id",
        origem: OrigemAtendimento = OrigemAtendimento.NETAFIM,
        aberto: String = "2026-11-01T12:00:00Z",
    ) = ItemDaListaAdmin(id, protocolo, nome, origem, StatusAtendimento.DESTINADO, Instant.parse(aberto))

    private fun ids(lista: List<ItemDaListaAdmin>) = lista.map { it.id }

    // ---- OrigemAtendimento.deCodigo

    @Test
    fun deCodigoDevolveCadaOrigemDoContrato() {
        assertEquals(OrigemAtendimento.NETAFIM, OrigemAtendimento.deCodigo("netafim"))
        assertEquals(OrigemAtendimento.DISTRIBUIDOR, OrigemAtendimento.deCodigo("distribuidor"))
        assertEquals(OrigemAtendimento.CLIENTE_FINAL, OrigemAtendimento.deCodigo("cliente_final"))
    }

    @Test
    fun deCodigoDesconhecidoDevolveNull() {
        assertNull(OrigemAtendimento.deCodigo("revenda"))
        assertNull(OrigemAtendimento.deCodigo(""))
    }

    // ---- aceita

    @Test
    fun todasAceitaAsTresOrigens() {
        OrigemAtendimento.entries.forEach { assertTrue(it.name, FiltroDeOrigem.TODAS.aceita(it)) }
    }

    @Test
    fun cadaFiltroAceitaSoASuaOrigem() {
        val correspondente = mapOf(
            FiltroDeOrigem.NETAFIM to OrigemAtendimento.NETAFIM,
            FiltroDeOrigem.DISTRIBUIDOR to OrigemAtendimento.DISTRIBUIDOR,
            FiltroDeOrigem.CLIENTE_FINAL to OrigemAtendimento.CLIENTE_FINAL,
        )
        correspondente.forEach { (filtro, esperada) ->
            OrigemAtendimento.entries.forEach { origem ->
                assertEquals("$filtro $origem", origem == esperada, filtro.aceita(origem))
            }
        }
    }

    // ---- filtrarListaAdmin

    @Test
    fun filtrarPorOrigem() {
        val itens = listOf(
            item("1", origem = OrigemAtendimento.NETAFIM),
            item("2", origem = OrigemAtendimento.DISTRIBUIDOR),
            item("3", origem = OrigemAtendimento.CLIENTE_FINAL),
        )
        assertEquals(3, filtrarListaAdmin(itens, FiltroDeOrigem.TODAS).size)
        assertEquals(listOf("1"), ids(filtrarListaAdmin(itens, FiltroDeOrigem.NETAFIM)))
        assertEquals(listOf("2"), ids(filtrarListaAdmin(itens, FiltroDeOrigem.DISTRIBUIDOR)))
        assertEquals(listOf("3"), ids(filtrarListaAdmin(itens, FiltroDeOrigem.CLIENTE_FINAL)))
    }

    @Test
    fun ordenaDoMaisRecenteParaOMaisAntigo() {
        val itens = listOf(
            item("a", aberto = "2026-11-01T12:00:00Z"),
            item("b", aberto = "2026-11-10T12:00:00Z"),
            item("c", aberto = "2026-11-05T12:00:00Z"),
        )
        assertEquals(listOf("b", "c", "a"), ids(filtrarListaAdmin(itens, FiltroDeOrigem.TODAS)))
    }

    @Test
    fun empateDeDataDesempataPeloProtocolo() {
        val itens = listOf(
            item("1", protocolo = "OS-0300", aberto = "2026-11-05T12:00:00Z"),
            item("2", protocolo = "OS-0100", aberto = "2026-11-05T12:00:00Z"),
            item("3", protocolo = "OS-0200", aberto = "2026-11-05T12:00:00Z"),
        )
        assertEquals(listOf("2", "3", "1"), ids(filtrarListaAdmin(itens, FiltroDeOrigem.TODAS)))
    }

    @Test
    fun empateDeDataDesempataPeloNumeroDoProtocolo() {
        val itens = listOf(
            item("dez-mil", protocolo = "OS-10000", aberto = "2026-11-05T12:00:00Z"),
            item("nove-mil", protocolo = "OS-9999", aberto = "2026-11-05T12:00:00Z"),
            item("mais-novo", protocolo = "OS-0001", aberto = "2026-11-09T12:00:00Z"),
        )
        // A data continua mandando: o mais novo vem primeiro, e só o empate usa o número do protocolo.
        assertEquals(
            listOf("mais-novo", "nove-mil", "dez-mil"),
            ids(filtrarListaAdmin(itens, FiltroDeOrigem.TODAS)),
        )
    }

    @Test
    fun protocoloForaDoFormatoComparaComoTextoNaListaDoAdmin() {
        val itens = listOf(item("b", protocolo = "XYZ"), item("a", protocolo = "ABC"))
        assertEquals(listOf("a", "b"), ids(filtrarListaAdmin(itens, FiltroDeOrigem.TODAS)))
    }

    @Test
    fun listaVaziaContinuaVazia() {
        assertTrue(filtrarListaAdmin(emptyList(), FiltroDeOrigem.TODAS).isEmpty())
        assertTrue(filtrarListaAdmin(emptyList(), FiltroDeOrigem.CLIENTE_FINAL).isEmpty())
    }

    @Test
    fun filtroSemCorrespondenciaDevolveVazio() {
        val itens = listOf(item("1", origem = OrigemAtendimento.NETAFIM))
        assertTrue(filtrarListaAdmin(itens, FiltroDeOrigem.DISTRIBUIDOR).isEmpty())
    }

    // ---- textoDaLinha

    @Test
    fun textoDaLinhaJuntaProtocoloEClienteComPontoMedio() {
        assertEquals("OS-0506 · Vanderlei Zucchi Rodas", textoDaLinha(item("1", "OS-0506", "Vanderlei Zucchi Rodas")))
    }
}
