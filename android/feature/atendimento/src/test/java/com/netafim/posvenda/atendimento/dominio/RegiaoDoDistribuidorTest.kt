package com.netafim.posvenda.atendimento.dominio

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegiaoDoDistribuidorTest {

    private fun item(
        id: String,
        protocolo: String = "OS-$id",
        cliente: String = "Cliente $id",
        local: String? = "Cidade $id",
        status: StatusAtendimento = StatusAtendimento.DESTINADO,
        aberto: String = "2026-11-01T12:00:00Z",
    ) = ItemDaFila(id, protocolo, cliente, local, MotivoAtendimento.DUVIDAS, status, Instant.parse(aberto))

    private fun ids(lista: List<ItemDaFila>) = lista.map { it.id }

    // Um item para cada um dos oito status, para conferir os filtros.
    private fun umDeCadaStatus() = StatusAtendimento.entries.map { item(it.name, status = it) }

    // ---- aceita

    @Test
    fun todosAceitaOsOitoStatus() {
        StatusAtendimento.entries.forEach { status ->
            assertTrue(status.name, FiltroDaRegiao.TODOS.aceita(status))
        }
    }

    @Test
    fun emAndamentoAceitaSoOsSelosAbertoEEmAtendimento() {
        val esperados = setOf(
            StatusAtendimento.ABERTO,
            StatusAtendimento.DESTINADO,
            StatusAtendimento.AGENDADO,
            StatusAtendimento.EM_ATENDIMENTO,
        )
        StatusAtendimento.entries.forEach { status ->
            assertEquals(status.name, status in esperados, FiltroDaRegiao.EM_ANDAMENTO.aceita(status))
        }
    }

    @Test
    fun concluidosAceitaSoOSeloConcluido() {
        val esperados = setOf(
            StatusAtendimento.RESOLVIDO,
            StatusAtendimento.ENCERRADO,
            StatusAtendimento.ENCERRADO_SEM_AVALIACAO,
        )
        StatusAtendimento.entries.forEach { status ->
            assertEquals(status.name, status in esperados, FiltroDaRegiao.CONCLUIDOS.aceita(status))
        }
    }

    @Test
    fun canceladoSoApareceEmTodos() {
        assertTrue(FiltroDaRegiao.TODOS.aceita(StatusAtendimento.CANCELADO))
        assertEquals(false, FiltroDaRegiao.EM_ANDAMENTO.aceita(StatusAtendimento.CANCELADO))
        assertEquals(false, FiltroDaRegiao.CONCLUIDOS.aceita(StatusAtendimento.CANCELADO))
    }

    // ---- filtrarRegiao

    @Test
    fun filtrarRegiaoPorFiltro() {
        val itens = umDeCadaStatus()
        assertEquals(8, filtrarRegiao(itens, FiltroDaRegiao.TODOS, "").size)
        assertEquals(
            setOf("ABERTO", "DESTINADO", "AGENDADO", "EM_ATENDIMENTO"),
            ids(filtrarRegiao(itens, FiltroDaRegiao.EM_ANDAMENTO, "")).toSet(),
        )
        assertEquals(
            setOf("RESOLVIDO", "ENCERRADO", "ENCERRADO_SEM_AVALIACAO"),
            ids(filtrarRegiao(itens, FiltroDaRegiao.CONCLUIDOS, "")).toSet(),
        )
    }

    @Test
    fun buscaPorProtocolo() {
        val itens = listOf(item("1", protocolo = "OS-0506"), item("2", protocolo = "OS-0477"))
        assertEquals(listOf("1"), ids(filtrarRegiao(itens, FiltroDaRegiao.TODOS, "0506")))
    }

    @Test
    fun buscaPorCliente() {
        val itens = listOf(item("1", cliente = "Citrosuco"), item("2", cliente = "Fazenda Santa Rita"))
        assertEquals(listOf("1"), ids(filtrarRegiao(itens, FiltroDaRegiao.TODOS, "citro")))
    }

    @Test
    fun buscaPorLocal() {
        val itens = listOf(item("1", local = "Arealva, SP"), item("2", local = "Araraquara, SP"))
        assertEquals(listOf("1"), ids(filtrarRegiao(itens, FiltroDaRegiao.TODOS, "arealva")))
    }

    @Test
    fun buscaIgnoraAcentoEMaiusculas() {
        val itens = listOf(item("1", local = "Ribeirão Preto, SP"), item("2", local = "Araraquara, SP"))
        assertEquals(listOf("1"), ids(filtrarRegiao(itens, FiltroDaRegiao.TODOS, "RIBEIRAO")))
        assertEquals(listOf("1"), ids(filtrarRegiao(itens, FiltroDaRegiao.TODOS, "ribeirão")))
    }

    @Test
    fun buscaEmBrancoNaoFiltra() {
        val itens = listOf(item("1"), item("2"))
        assertEquals(2, filtrarRegiao(itens, FiltroDaRegiao.TODOS, "").size)
        assertEquals(2, filtrarRegiao(itens, FiltroDaRegiao.TODOS, "   ").size)
    }

    @Test
    fun buscaAparaEspacosDasPontas() {
        val itens = listOf(item("1", cliente = "Citrosuco"), item("2"))
        assertEquals(listOf("1"), ids(filtrarRegiao(itens, FiltroDaRegiao.TODOS, "  citro  ")))
    }

    @Test
    fun buscaSemResultadoDevolveListaVazia() {
        val itens = listOf(item("1"), item("2"))
        assertTrue(filtrarRegiao(itens, FiltroDaRegiao.TODOS, "xyz").isEmpty())
    }

    @Test
    fun itemSemLocalNaoQuebraABusca() {
        val itens = listOf(item("1", local = null, cliente = "Citrosuco"), item("2", local = "Arealva, SP"))
        assertEquals(listOf("1"), ids(filtrarRegiao(itens, FiltroDaRegiao.TODOS, "citro")))
        assertEquals(listOf("2"), ids(filtrarRegiao(itens, FiltroDaRegiao.TODOS, "arealva")))
    }

    @Test
    fun filtroEBuscaValemJuntos() {
        val itens = listOf(
            item("1", cliente = "Citrosuco", status = StatusAtendimento.ENCERRADO),
            item("2", cliente = "Citrus Norte", status = StatusAtendimento.EM_ATENDIMENTO),
        )
        assertEquals(listOf("1"), ids(filtrarRegiao(itens, FiltroDaRegiao.CONCLUIDOS, "citr")))
    }

    // ---- ordenarRegiao

    @Test
    fun ordenaDoMaisRecenteParaOMaisAntigo() {
        val itens = listOf(
            item("a", aberto = "2026-11-01T12:00:00Z"),
            item("b", aberto = "2026-11-10T12:00:00Z"),
            item("c", aberto = "2026-11-05T12:00:00Z"),
        )
        assertEquals(listOf("b", "c", "a"), ids(ordenarRegiao(itens)))
    }

    @Test
    fun empateDeDataDesempataPeloProtocolo() {
        val itens = listOf(
            item("1", protocolo = "OS-0300", aberto = "2026-11-05T12:00:00Z"),
            item("2", protocolo = "OS-0100", aberto = "2026-11-05T12:00:00Z"),
            item("3", protocolo = "OS-0200", aberto = "2026-11-05T12:00:00Z"),
        )
        assertEquals(listOf("2", "3", "1"), ids(ordenarRegiao(itens)))
    }

    @Test
    fun filtrarRegiaoJaDevolveOrdenado() {
        val itens = listOf(
            item("a", aberto = "2026-11-01T12:00:00Z"),
            item("b", aberto = "2026-11-10T12:00:00Z"),
            item("c", aberto = "2026-11-05T12:00:00Z", status = StatusAtendimento.CANCELADO),
        )
        assertEquals(listOf("b", "c", "a"), ids(filtrarRegiao(itens, FiltroDaRegiao.TODOS, "")))
    }

    // ---- ordem por protocolo

    @Test
    fun empateDeDataDesempataPeloNumeroDoProtocolo() {
        val itens = listOf(
            item("dez-mil", protocolo = "OS-10000", aberto = "2026-11-05T12:00:00Z"),
            item("nove-mil", protocolo = "OS-9999", aberto = "2026-11-05T12:00:00Z"),
            item("mais-novo", protocolo = "OS-0001", aberto = "2026-11-09T12:00:00Z"),
        )
        // O mais recente continua primeiro; só o empate de data usa o número do protocolo.
        assertEquals(listOf("mais-novo", "nove-mil", "dez-mil"), ids(ordenarRegiao(itens)))
    }

    @Test
    fun protocoloForaDoFormatoComparaComoTextoNaRegiao() {
        val itens = listOf(item("b", protocolo = "XYZ"), item("a", protocolo = "ABC"))
        assertEquals(listOf("a", "b"), ids(ordenarRegiao(itens)))
    }
}
