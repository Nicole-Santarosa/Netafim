package com.netafim.posvenda.atendimento.dominio

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ResumoAtendimentoTest {

    private val saoPaulo = ZoneId.of("America/Sao_Paulo")
    private val agora = Instant.parse("2026-11-11T15:00:00Z")

    private fun resumo(status: StatusAtendimento, abertoEm: String) = ResumoAtendimento(
        id = "x",
        protocolo = "OS-0001",
        motivo = MotivoAtendimento.DUVIDAS,
        status = status,
        abertoEm = Instant.parse(abertoEm),
    )

    @Test
    fun abertoHojeQuandoAindaEmAndamentoNoMesmoDia() {
        val r = resumo(StatusAtendimento.ABERTO, "2026-11-11T12:00:00Z")
        assertEquals(ApoioDoCartao.AbertoHoje, apoioDoCartao(r, agora, saoPaulo))
    }

    @Test
    fun abertoHaUmDia() {
        val r = resumo(StatusAtendimento.DESTINADO, "2026-11-10T15:00:00Z")
        assertEquals(ApoioDoCartao.AbertoHaDias(1), apoioDoCartao(r, agora, saoPaulo))
    }

    @Test
    fun abertoHaVariosDiasEmAtendimento() {
        val r = resumo(StatusAtendimento.EM_ATENDIMENTO, "2026-11-01T15:00:00Z")
        assertEquals(ApoioDoCartao.AbertoHaDias(10), apoioDoCartao(r, agora, saoPaulo))
    }

    @Test
    fun agendadoTambemContaDias() {
        val r = resumo(StatusAtendimento.AGENDADO, "2026-11-08T15:00:00Z")
        assertEquals(ApoioDoCartao.AbertoHaDias(3), apoioDoCartao(r, agora, saoPaulo))
    }

    @Test
    fun concluidoMostraADataDeAbertura() {
        for (status in listOf(StatusAtendimento.RESOLVIDO, StatusAtendimento.ENCERRADO, StatusAtendimento.ENCERRADO_SEM_AVALIACAO)) {
            val r = resumo(status, "2026-10-20T15:00:00Z")
            assertEquals(status.name, ApoioDoCartao.AbertoEm(LocalDate.of(2026, 10, 20)), apoioDoCartao(r, agora, saoPaulo))
        }
    }

    @Test
    fun concluidoAbertoHojeAindaMostraAData() {
        // Mesmo aberto e concluído no mesmo dia, o selo é CONCLUIDO: mostra a data, não "hoje".
        val r = resumo(StatusAtendimento.ENCERRADO, "2026-11-11T12:00:00Z")
        assertEquals(ApoioDoCartao.AbertoEm(LocalDate.of(2026, 11, 11)), apoioDoCartao(r, agora, saoPaulo))
    }

    @Test
    fun canceladoMostraADataDeAbertura() {
        val r = resumo(StatusAtendimento.CANCELADO, "2026-09-30T15:00:00Z")
        assertEquals(ApoioDoCartao.AbertoEm(LocalDate.of(2026, 9, 30)), apoioDoCartao(r, agora, saoPaulo))
    }

    @Test
    fun dataDeAberturaRespeitaAZona() {
        // 02:00 UTC do dia 10 ainda é 23:00 do dia 9 em São Paulo (UTC-3).
        val r = resumo(StatusAtendimento.CANCELADO, "2026-11-10T02:00:00Z")
        assertEquals(ApoioDoCartao.AbertoEm(LocalDate.of(2026, 11, 9)), apoioDoCartao(r, agora, saoPaulo))
        assertEquals(ApoioDoCartao.AbertoEm(LocalDate.of(2026, 11, 10)), apoioDoCartao(r, agora, ZoneId.of("UTC")))
    }
}
