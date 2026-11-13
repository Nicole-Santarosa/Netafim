package com.netafim.posvenda.atendimento.dominio

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilaDoTecnicoTest {

    private fun item(
        id: String,
        protocolo: String = "OS-$id",
        cliente: String = "Cliente $id",
        local: String? = "Cidade $id",
        status: StatusAtendimento = StatusAtendimento.DESTINADO,
        aberto: String = "2026-11-01T12:00:00Z",
    ) = ItemDaFila(id, protocolo, cliente, local, MotivoAtendimento.DUVIDAS, status, Instant.parse(aberto))

    private fun ids(lista: List<ItemDaFila>) = lista.map { it.id }

    @Test
    fun estaNaFilaSoParaDestinadoAgendadoEEmAtendimento() {
        val naFila = setOf(StatusAtendimento.DESTINADO, StatusAtendimento.AGENDADO, StatusAtendimento.EM_ATENDIMENTO)
        StatusAtendimento.entries.forEach { status ->
            assertEquals(status.name, status in naFila, estaNaFila(status))
        }
    }

    @Test
    fun aceitaDeCadaFiltroParaOsOitoStatus() {
        val novo = setOf(StatusAtendimento.DESTINADO, StatusAtendimento.AGENDADO)
        val todos = novo + StatusAtendimento.EM_ATENDIMENTO
        StatusAtendimento.entries.forEach { status ->
            assertEquals("TODOS ${status.name}", status in todos, FiltroDaFila.TODOS.aceita(status))
            assertEquals("NOVO ${status.name}", status in novo, FiltroDaFila.NOVO.aceita(status))
            assertEquals(
                "EM_ATENDIMENTO ${status.name}",
                status == StatusAtendimento.EM_ATENDIMENTO,
                FiltroDaFila.EM_ATENDIMENTO.aceita(status),
            )
        }
    }

    @Test
    fun filtraPorFiltro() {
        val itens = listOf(
            item("1", status = StatusAtendimento.DESTINADO),
            item("2", status = StatusAtendimento.AGENDADO),
            item("3", status = StatusAtendimento.EM_ATENDIMENTO),
        )
        assertEquals(setOf("1", "2", "3"), ids(filtrarFila(itens, FiltroDaFila.TODOS, "")).toSet())
        assertEquals(setOf("1", "2"), ids(filtrarFila(itens, FiltroDaFila.NOVO, "")).toSet())
        assertEquals(listOf("3"), ids(filtrarFila(itens, FiltroDaFila.EM_ATENDIMENTO, "")))
    }

    @Test
    fun filtraPorProtocoloClienteELocal() {
        val itens = listOf(
            item("1", protocolo = "OS-0506", cliente = "Vanderlei", local = "Arealva, SP"),
            item("2", protocolo = "OS-0477", cliente = "Citrosuco", local = "Boa Esperança do Sul, SP"),
            item("3", protocolo = "OS-0512", cliente = "Santa Rita", local = "Ribeirão Preto, SP"),
        )
        assertEquals(listOf("1"), ids(filtrarFila(itens, FiltroDaFila.TODOS, "0506")))
        assertEquals(listOf("2"), ids(filtrarFila(itens, FiltroDaFila.TODOS, "citrosuco")))
        assertEquals(listOf("3"), ids(filtrarFila(itens, FiltroDaFila.TODOS, "ribeirao")))
    }

    @Test
    fun buscaIgnoraAcentosEMaiusculas() {
        val itens = listOf(
            item("1", cliente = "Citrosuco", local = "Ribeirão Preto"),
        )
        assertEquals(listOf("1"), ids(filtrarFila(itens, FiltroDaFila.TODOS, "CITROSUCO")))
        assertEquals(listOf("1"), ids(filtrarFila(itens, FiltroDaFila.TODOS, "ribeirao")))
        // O acento também vale na busca digitada.
        assertEquals(listOf("1"), ids(filtrarFila(itens, FiltroDaFila.TODOS, "RIBEIRÃO")))
    }

    @Test
    fun buscaApagaEspacosDasPontas() {
        val itens = listOf(item("1", cliente = "Citrosuco"))
        assertEquals(listOf("1"), ids(filtrarFila(itens, FiltroDaFila.TODOS, "  citro  ")))
    }

    @Test
    fun buscaEmBrancoNaoFiltraNada() {
        val itens = listOf(item("1"), item("2"))
        assertEquals(2, filtrarFila(itens, FiltroDaFila.TODOS, "").size)
        assertEquals(2, filtrarFila(itens, FiltroDaFila.TODOS, "   ").size)
    }

    @Test
    fun buscaSemResultadoDevolveListaVazia() {
        val itens = listOf(item("1", cliente = "Citrosuco"))
        assertTrue(filtrarFila(itens, FiltroDaFila.TODOS, "zzz").isEmpty())
    }

    @Test
    fun itemSemLocalNaoQuebraABusca() {
        val itens = listOf(item("1", cliente = "Citrosuco", local = null))
        assertEquals(listOf("1"), ids(filtrarFila(itens, FiltroDaFila.TODOS, "citro")))
        assertTrue(filtrarFila(itens, FiltroDaFila.TODOS, "ribeirao").isEmpty())
    }

    @Test
    fun itensForaDaFilaNuncaAparecem() {
        val fora = listOf(
            StatusAtendimento.ABERTO,
            StatusAtendimento.RESOLVIDO,
            StatusAtendimento.ENCERRADO,
            StatusAtendimento.ENCERRADO_SEM_AVALIACAO,
            StatusAtendimento.CANCELADO,
        ).mapIndexed { i, status -> item("f$i", status = status) }
        FiltroDaFila.entries.forEach { filtro ->
            assertFalse(filtro.name, filtrarFila(fora, filtro, "").isNotEmpty())
        }
    }

    @Test
    fun ordenaComEmAtendimentoPrimeiro() {
        val itens = listOf(
            item("a", status = StatusAtendimento.DESTINADO, aberto = "2026-10-01T12:00:00Z"),
            item("b", status = StatusAtendimento.EM_ATENDIMENTO, aberto = "2026-11-05T12:00:00Z"),
        )
        assertEquals(listOf("b", "a"), ids(ordenarFila(itens)))
    }

    @Test
    fun ordenaPorAberturaDoMaisAntigoParaOMaisNovo() {
        val itens = listOf(
            item("novo", aberto = "2026-11-10T12:00:00Z"),
            item("antigo", aberto = "2026-11-01T12:00:00Z"),
            item("meio", aberto = "2026-11-05T12:00:00Z"),
        )
        assertEquals(listOf("antigo", "meio", "novo"), ids(ordenarFila(itens)))
    }

    @Test
    fun empateDeAberturaDesempataPeloProtocolo() {
        val itens = listOf(
            item("1", protocolo = "OS-0600"),
            item("2", protocolo = "OS-0500"),
            item("3", protocolo = "OS-0550"),
        )
        assertEquals(listOf("2", "3", "1"), ids(ordenarFila(itens)))
    }

    @Test
    fun empateDeAberturaDesempataPeloNumeroDoProtocolo() {
        val itens = listOf(
            item("dez-mil", protocolo = "OS-10000"),
            item("nove-mil", protocolo = "OS-9999"),
        )
        assertEquals(listOf("nove-mil", "dez-mil"), ids(ordenarFila(itens)))
    }

    @Test
    fun protocoloFeitoDeTextoNaoDerrubaAOrdenacaoDaFila() {
        val itens = listOf(item("b", protocolo = "XYZ"), item("a", protocolo = "ABC"))
        assertEquals(listOf("a", "b"), ids(ordenarFila(itens)))
    }

    @Test
    fun protocoloIgualMantemAOrdemDeEntradaEOutrosCriteriosIntactos() {
        val itens = listOf(
            item("primeiro", protocolo = "OS-0100"),
            item("segundo", protocolo = "OS-100"),
            item("em-curso", protocolo = "OS-0900", status = StatusAtendimento.EM_ATENDIMENTO),
            item("antigo", protocolo = "OS-0950", aberto = "2026-10-01T12:00:00Z"),
        )
        // Em atendimento primeiro, depois a abertura mais antiga e só então o protocolo (empate mantém a entrada).
        assertEquals(listOf("em-curso", "antigo", "primeiro", "segundo"), ids(ordenarFila(itens)))
    }

    @Test
    fun filtrarFilaJaDevolveOrdenado() {
        val itens = listOf(
            item("1", status = StatusAtendimento.AGENDADO, aberto = "2026-10-01T12:00:00Z"),
            item("2", status = StatusAtendimento.EM_ATENDIMENTO, aberto = "2026-11-09T12:00:00Z"),
        )
        assertEquals(listOf("2", "1"), ids(filtrarFila(itens, FiltroDaFila.TODOS, "")))
    }
}
