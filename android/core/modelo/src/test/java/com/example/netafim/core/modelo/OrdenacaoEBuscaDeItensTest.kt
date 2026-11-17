package com.example.netafim.core.modelo

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class OrdenacaoEBuscaDeItensTest {

    private fun item(
        id: String,
        protocolo: String? = "OS-0001",
        status: StatusAtendimento = StatusAtendimento.AGENDADO,
        aberto: String = "2026-10-01T12:00:00Z",
        nomeCliente: String? = null,
        local: String? = null,
    ) = ItemDeAtendimento(
        id = id,
        protocolo = protocolo,
        motivo = MotivoAtendimento.POS_VENDA,
        status = status,
        origem = OrigemAtendimento.CLIENTE_FINAL,
        abertoEm = Instant.parse(aberto),
        nomeCliente = nomeCliente,
        local = local,
        idTecnicoResponsavel = null,
        sincronizacao = SituacaoDeSincronizacao.SINCRONIZADO,
    )

    private fun ids(itens: List<ItemDeAtendimento>) = itens.map { it.id }

    // ---- fila do técnico

    @Test
    fun filaPoeOEmAtendimentoPrimeiroEDepoisOsMaisAntigos() {
        val itens = listOf(
            item("novo-recente", aberto = "2026-10-05T12:00:00Z"),
            item("em-atendimento-recente", status = StatusAtendimento.EM_ATENDIMENTO, aberto = "2026-10-04T12:00:00Z"),
            item("novo-antigo", aberto = "2026-10-01T12:00:00Z"),
            item("em-atendimento-antigo", status = StatusAtendimento.EM_ATENDIMENTO, aberto = "2026-10-02T12:00:00Z"),
        )
        assertEquals(
            listOf("em-atendimento-antigo", "em-atendimento-recente", "novo-antigo", "novo-recente"),
            ids(ordenarPelaFilaDoTecnico(itens)),
        )
    }

    @Test
    fun filaDesempataPeloNumeroDoProtocolo() {
        val itens = listOf(
            item("b", protocolo = "OS-10000"),
            item("a", protocolo = "OS-9999"),
        )
        assertEquals(listOf("a", "b"), ids(ordenarPelaFilaDoTecnico(itens)))
    }

    @Test
    fun filaMandaProtocoloNuloParaDepoisNoEmpate() {
        val itens = listOf(
            item("sem-protocolo", protocolo = null),
            item("com-protocolo", protocolo = "OS-0002"),
        )
        assertEquals(listOf("com-protocolo", "sem-protocolo"), ids(ordenarPelaFilaDoTecnico(itens)))
    }

    @Test
    fun protocoloNuloSoEntraComoUltimoCriterio() {
        // Sem protocolo, mas mais antigo: a data manda, o protocolo só desempata.
        val itens = listOf(
            item("com-protocolo", protocolo = "OS-0001", aberto = "2026-10-02T12:00:00Z"),
            item("sem-protocolo", protocolo = null, aberto = "2026-10-01T12:00:00Z"),
        )
        assertEquals(listOf("sem-protocolo", "com-protocolo"), ids(ordenarPelaFilaDoTecnico(itens)))
    }

    // ---- mais recentes primeiro

    @Test
    fun maisRecentesVemPrimeiro() {
        val itens = listOf(
            item("antigo", aberto = "2026-10-01T12:00:00Z"),
            item("recente", aberto = "2026-10-03T12:00:00Z"),
            item("meio", aberto = "2026-10-02T12:00:00Z"),
        )
        assertEquals(listOf("recente", "meio", "antigo"), ids(ordenarPelosMaisRecentes(itens)))
    }

    @Test
    fun maisRecentesDesempataPeloIdCrescente() {
        val itens = listOf(item("c"), item("a"), item("b"))
        assertEquals(listOf("a", "b", "c"), ids(ordenarPelosMaisRecentes(itens)))
    }

    @Test
    fun ordenarAplicaAOrdemPedida() {
        val itens = listOf(
            item("recente", aberto = "2026-10-03T12:00:00Z"),
            item("em-atendimento", status = StatusAtendimento.EM_ATENDIMENTO, aberto = "2026-10-01T12:00:00Z"),
            item("antigo", aberto = "2026-10-02T12:00:00Z"),
        )
        assertEquals(
            ids(ordenarPelaFilaDoTecnico(itens)),
            ids(ordenar(itens, OrdemDaLista.FILA_DO_TECNICO)),
        )
        assertEquals(
            ids(ordenarPelosMaisRecentes(itens)),
            ids(ordenar(itens, OrdemDaLista.MAIS_RECENTE_PRIMEIRO)),
        )
    }

    // ---- busca nos itens

    private val itensDaBusca = listOf(
        item("1", protocolo = "OS-0477", nomeCliente = "Fazenda São José", local = "Ribeirão Preto"),
        item("2", protocolo = "OS-0478", nomeCliente = "Sítio Boa Vista", local = null),
        item("3", protocolo = null, nomeCliente = null, local = "Franca"),
    )

    @Test
    fun buscaVaziaOuSoDeEspacosNaoFiltra() {
        assertEquals(ids(itensDaBusca), ids(buscarNosItens(itensDaBusca, "")))
        assertEquals(ids(itensDaBusca), ids(buscarNosItens(itensDaBusca, "   ")))
    }

    @Test
    fun buscaPeloProtocolo() {
        assertEquals(listOf("2"), ids(buscarNosItens(itensDaBusca, "os-0478")))
    }

    @Test
    fun buscaPeloNomeDoClienteSemAcentoENemCaixa() {
        assertEquals(listOf("1"), ids(buscarNosItens(itensDaBusca, "  SAO jose ")))
    }

    @Test
    fun buscaPeloLocal() {
        assertEquals(listOf("1"), ids(buscarNosItens(itensDaBusca, "ribeirao")))
        assertEquals(listOf("3"), ids(buscarNosItens(itensDaBusca, "franca")))
    }

    @Test
    fun camposNulosNaoQuebramNemCasam() {
        assertEquals(emptyList<String>(), ids(buscarNosItens(itensDaBusca, "campinas")))
    }
}
