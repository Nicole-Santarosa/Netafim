package com.example.netafim.core.modelo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusAtendimentoTest {

    @Test
    fun deCodigoReconheceOsOitoCodigosDaApi() {
        val codigos = mapOf(
            "aberto" to StatusAtendimento.ABERTO,
            "destinado" to StatusAtendimento.DESTINADO,
            "agendado" to StatusAtendimento.AGENDADO,
            "em_atendimento" to StatusAtendimento.EM_ATENDIMENTO,
            "resolvido" to StatusAtendimento.RESOLVIDO,
            "encerrado" to StatusAtendimento.ENCERRADO,
            "encerrado_sem_avaliacao" to StatusAtendimento.ENCERRADO_SEM_AVALIACAO,
            "cancelado" to StatusAtendimento.CANCELADO,
        )
        codigos.forEach { (codigo, status) -> assertEquals(status, StatusAtendimento.deCodigo(codigo)) }
    }

    @Test
    fun deCodigoDevolveDesconhecidoParaCodigoNaoReconhecido() {
        assertEquals(StatusAtendimento.DESCONHECIDO, StatusAtendimento.deCodigo("pausado"))
        assertEquals(StatusAtendimento.DESCONHECIDO, StatusAtendimento.deCodigo(""))
        // O código é sensível a maiúsculas: a API sempre manda minúsculas.
        assertEquals(StatusAtendimento.DESCONHECIDO, StatusAtendimento.deCodigo("ABERTO"))
    }

    @Test
    fun soEncerradosECanceladoSaoFinais() {
        val finais = setOf(
            StatusAtendimento.ENCERRADO,
            StatusAtendimento.ENCERRADO_SEM_AVALIACAO,
            StatusAtendimento.CANCELADO,
        )
        StatusAtendimento.entries.forEach { status ->
            if (status in finais) assertTrue(status.name, status.ehFinal) else assertFalse(status.name, status.ehFinal)
        }
    }
}
