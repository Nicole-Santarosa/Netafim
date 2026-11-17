package com.example.netafim.atendimento.dominio

import com.example.netafim.core.modelo.StatusAtendimento
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SeloDoStatusTest {

    @Test
    fun cadaStatusVaiParaOSeloCerto() {
        val esperado = mapOf(
            StatusAtendimento.ABERTO to SeloStatus.ABERTO,
            StatusAtendimento.DESTINADO to SeloStatus.ABERTO,
            StatusAtendimento.AGENDADO to SeloStatus.ABERTO,
            StatusAtendimento.EM_ATENDIMENTO to SeloStatus.EM_ATENDIMENTO,
            StatusAtendimento.RESOLVIDO to SeloStatus.CONCLUIDO,
            StatusAtendimento.ENCERRADO to SeloStatus.CONCLUIDO,
            StatusAtendimento.ENCERRADO_SEM_AVALIACAO to SeloStatus.CONCLUIDO,
            StatusAtendimento.CANCELADO to SeloStatus.CANCELADO,
            StatusAtendimento.DESCONHECIDO to SeloStatus.DESCONHECIDO,
        )
        assertEquals(StatusAtendimento.entries.size, esperado.size)
        esperado.forEach { (status, selo) -> assertEquals(status.name, selo, status.selo) }
    }

    @Test
    fun statusDesconhecidoTemSeloNeutroProprioEDiferenteDeTodosOsOutros() {
        // O desconhecido não herda o selo de nenhuma etapa: o app não sabe em que ponto o atendimento está.
        assertEquals(SeloStatus.DESCONHECIDO, StatusAtendimento.DESCONHECIDO.selo)
        StatusAtendimento.entries.filter { it != StatusAtendimento.DESCONHECIDO }.forEach { status ->
            assertNotEquals(status.name, SeloStatus.DESCONHECIDO, status.selo)
        }
    }
}
