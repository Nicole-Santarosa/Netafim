package com.netafim.posvenda.atendimento.dominio

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinhaDoTempoTest {

    private fun etapas(vararg pares: Pair<TipoEtapa, SituacaoEtapa>) = pares.map { EtapaDaLinha(it.first, it.second) }

    private val aberto = etapas(
        TipoEtapa.ABERTO to SituacaoEtapa.ATUAL,
        TipoEtapa.EM_ATENDIMENTO to SituacaoEtapa.PENDENTE,
        TipoEtapa.CONCLUIDO to SituacaoEtapa.PENDENTE,
    )
    private val emAtendimento = etapas(
        TipoEtapa.ABERTO to SituacaoEtapa.FEITA,
        TipoEtapa.EM_ATENDIMENTO to SituacaoEtapa.ATUAL,
        TipoEtapa.CONCLUIDO to SituacaoEtapa.PENDENTE,
    )
    private val concluido = etapas(
        TipoEtapa.ABERTO to SituacaoEtapa.FEITA,
        TipoEtapa.EM_ATENDIMENTO to SituacaoEtapa.FEITA,
        TipoEtapa.CONCLUIDO to SituacaoEtapa.FEITA,
    )
    private val cancelado = etapas(
        TipoEtapa.ABERTO to SituacaoEtapa.FEITA,
        TipoEtapa.CANCELADO to SituacaoEtapa.ATUAL,
    )

    @Test
    fun linhaDoTempoDeCadaUmDosOitoStatus() {
        val esperado = mapOf(
            StatusAtendimento.ABERTO to aberto,
            StatusAtendimento.DESTINADO to aberto,
            StatusAtendimento.AGENDADO to aberto,
            StatusAtendimento.EM_ATENDIMENTO to emAtendimento,
            StatusAtendimento.RESOLVIDO to concluido,
            StatusAtendimento.ENCERRADO to concluido,
            StatusAtendimento.ENCERRADO_SEM_AVALIACAO to concluido,
            StatusAtendimento.CANCELADO to cancelado,
        )
        assertEquals(StatusAtendimento.entries.size, esperado.size)
        esperado.forEach { (status, etapas) -> assertEquals(status.name, etapas, linhaDoTempo(status)) }
    }

    @Test
    fun canceladoTemSoDuasEtapas() {
        assertEquals(2, linhaDoTempo(StatusAtendimento.CANCELADO).size)
        assertEquals(3, linhaDoTempo(StatusAtendimento.ABERTO).size)
    }

    private fun detalhe(fazenda: String?, cidade: String?) = DetalheAtendimento(
        id = "1",
        protocolo = "OS-0001",
        motivo = MotivoAtendimento.DUVIDAS,
        status = StatusAtendimento.ABERTO,
        abertoEm = Instant.parse("2026-11-11T12:00:00Z"),
        nomeFazenda = fazenda,
        cidade = cidade,
    )

    @Test
    fun linhaDeLocalComOsDoisValores() {
        assertEquals("Fazenda Santa Rita · Ribeirão Preto, SP", linhaDeLocal(detalhe("Fazenda Santa Rita", "Ribeirão Preto, SP")))
    }

    @Test
    fun linhaDeLocalComSoUmValor() {
        assertEquals("Fazenda Santa Rita", linhaDeLocal(detalhe("Fazenda Santa Rita", null)))
        assertEquals("Arealva, SP", linhaDeLocal(detalhe(null, "Arealva, SP")))
        assertEquals("Arealva, SP", linhaDeLocal(detalhe("", "Arealva, SP")))
    }

    @Test
    fun linhaDeLocalSemNenhumValorDevolveNulo() {
        assertNull(linhaDeLocal(detalhe(null, null)))
    }

    @Test
    fun linhaDeLocalComSoEspacosDevolveNulo() {
        assertNull(linhaDeLocal(detalhe("   ", "  ")))
    }

    @Test
    fun linhaDeLocalApagaEspacosDasPontas() {
        assertEquals("Fazenda · Cidade", linhaDeLocal(detalhe("  Fazenda ", " Cidade  ")))
    }
}
