package com.netafim.posvenda.atendimento.dominio

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class TempoDeAberturaTest {

    private val saoPaulo = ZoneId.of("America/Sao_Paulo")

    // Monta um instante a partir de uma data e hora locais na zona informada.
    private fun em(zona: ZoneId, data: String, hora: String): Instant =
        java.time.LocalDateTime.parse("${data}T$hora").atZone(zona).toInstant()

    @Test
    fun mesmoDiaDaZero() {
        val aberto = em(saoPaulo, "2026-11-11", "08:00")
        val agora = em(saoPaulo, "2026-11-11", "23:59")
        assertEquals(0, diasEmAberto(aberto, agora, saoPaulo))
    }

    @Test
    fun vinteETresEMeiaDeOntemContraZeroZeroUmDeHojeDaUm() {
        val aberto = em(saoPaulo, "2026-11-10", "23:59")
        val agora = em(saoPaulo, "2026-11-11", "00:01")
        assertEquals(1, diasEmAberto(aberto, agora, saoPaulo))
    }

    @Test
    fun trintaDias() {
        val aberto = em(saoPaulo, "2026-10-12", "10:00")
        val agora = em(saoPaulo, "2026-11-11", "10:00")
        assertEquals(30, diasEmAberto(aberto, agora, saoPaulo))
    }

    @Test
    fun dataFuturaDaZero() {
        val aberto = em(saoPaulo, "2026-11-15", "10:00")
        val agora = em(saoPaulo, "2026-11-11", "10:00")
        assertEquals(0, diasEmAberto(aberto, agora, saoPaulo))
    }

    @Test
    fun respeitaAZonaInformada() {
        // 23:30 UTC do dia 10 e 12:00 UTC do dia 11.
        val aberto = Instant.parse("2026-11-10T23:30:00Z")
        val agora = Instant.parse("2026-11-11T12:00:00Z")
        // Em UTC os dias são diferentes: 1 dia.
        assertEquals(1, diasEmAberto(aberto, agora, ZoneId.of("UTC")))
        // Em Tóquio (UTC+9) os dois instantes caem no dia 11: 0 dia.
        assertEquals(0, diasEmAberto(aberto, agora, ZoneId.of("Asia/Tokyo")))
    }
}
