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

    // Cancelado a partir de em atendimento mantém essa etapa como feita.
    private val canceladoAposEmAtendimento = etapas(
        TipoEtapa.ABERTO to SituacaoEtapa.FEITA,
        TipoEtapa.EM_ATENDIMENTO to SituacaoEtapa.FEITA,
        TipoEtapa.CANCELADO to SituacaoEtapa.ATUAL,
    )

    @Test
    fun canceladoVindoDeCadaStatusPossivel() {
        // Só em atendimento muda a linha. Os demais, inclusive os que o banco nem permite antes de cancelar
        // (resolvido e os finais), seguem como se o status anterior não fosse conhecido.
        StatusAtendimento.entries.forEach { anterior ->
            val esperado = if (anterior == StatusAtendimento.EM_ATENDIMENTO) canceladoAposEmAtendimento else cancelado
            assertEquals(anterior.name, esperado, linhaDoTempo(StatusAtendimento.CANCELADO, anterior))
        }
    }

    @Test
    fun canceladoAPartirDeAbertoDestinadoOuAgendadoTemDuasEtapas() {
        listOf(StatusAtendimento.ABERTO, StatusAtendimento.DESTINADO, StatusAtendimento.AGENDADO).forEach {
            assertEquals(it.name, cancelado, linhaDoTempo(StatusAtendimento.CANCELADO, it))
        }
    }

    @Test
    fun canceladoSemStatusAnteriorMantemOComportamentoAntigo() {
        assertEquals(cancelado, linhaDoTempo(StatusAtendimento.CANCELADO))
        assertEquals(cancelado, linhaDoTempo(StatusAtendimento.CANCELADO, null))
    }

    @Test
    fun canceladoComCodigoAnteriorDesconhecidoMantemOComportamentoAntigo() {
        // A camada de dados converte o código com deCodigo, que devolve nulo para o que o app não conhece.
        val anterior = StatusAtendimento.deCodigo("status_novo_do_servidor")
        assertNull(anterior)
        assertEquals(cancelado, linhaDoTempo(StatusAtendimento.CANCELADO, anterior))
    }

    @Test
    fun statusAnteriorEIgnoradoQuandoOStatusNaoECancelado() {
        StatusAtendimento.entries.filter { it != StatusAtendimento.CANCELADO }.forEach { status ->
            assertEquals(
                status.name,
                linhaDoTempo(status),
                linhaDoTempo(status, StatusAtendimento.EM_ATENDIMENTO),
            )
        }
    }

    @Test
    fun detalheGuardaOStatusAnteriorEOPadraoENulo() {
        assertNull(detalhe(null, null).statusAntesDoCancelamento)
        assertEquals(
            StatusAtendimento.EM_ATENDIMENTO,
            detalhe(null, null).copy(statusAntesDoCancelamento = StatusAtendimento.EM_ATENDIMENTO)
                .statusAntesDoCancelamento,
        )
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
