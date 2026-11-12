package com.netafim.posvenda.atendimento.dominio

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// Quantos dias o atendimento está em aberto. Conta dias de calendário na zona informada, não blocos
// de 24 horas: um atendimento aberto às 23:59 de ontem já tem 1 dia às 00:01 de hoje.
// Nunca devolve valor negativo (relógio do aparelho adiantado em relação ao servidor, por exemplo).
fun diasEmAberto(abertoEm: Instant, agora: Instant, zona: ZoneId): Int {
    val diaDaAbertura = abertoEm.atZone(zona).toLocalDate()
    val diaDeHoje = agora.atZone(zona).toLocalDate()
    return ChronoUnit.DAYS.between(diaDaAbertura, diaDeHoje).toInt().coerceAtLeast(0)
}
