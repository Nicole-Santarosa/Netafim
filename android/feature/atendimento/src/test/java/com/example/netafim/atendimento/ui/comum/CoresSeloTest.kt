package com.example.netafim.atendimento.ui.comum

import com.example.netafim.atendimento.dominio.IndicadorDeSincronizacao
import com.example.netafim.atendimento.dominio.SeloStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoresSeloTest {

    @Test
    fun textoTemContrasteMinimoDeQuatroEMeioSobreOFundo() {
        SeloStatus.entries.forEach { selo ->
            val cores = coresDoSelo(selo)
            val razao = razaoDeContraste(cores.texto, cores.fundo)
            assertTrue("$selo: contraste $razao abaixo de 4,5", razao >= 4.5)
        }
    }

    // O ícone de sincronização é elemento gráfico: o mínimo do WCAG para ele é 3 para 1 sobre o fundo do cartão.
    @Test
    fun iconeDeSincronizacaoTemContrasteMinimoDeTresSobreOCartao() {
        IndicadorDeSincronizacao.entries.forEach { indicador ->
            val razao = razaoDeContraste(corDoIndicador(indicador), FUNDO_DO_CARTAO)
            assertTrue("$indicador: contraste $razao abaixo de 3", razao >= 3.0)
        }
    }

    // O aviso de erro das telas de ação é texto normal: precisa de 4,5 para 1 sobre o fundo da tela.
    @Test
    fun avisoDeErroTemContrasteMinimoDeQuatroEMeioSobreATela() {
        val razao = razaoDeContraste(COR_DO_AVISO_DE_ERRO, FUNDO_DA_TELA)
        assertTrue("contraste $razao abaixo de 4,5", razao >= 4.5)
    }

    @Test
    fun razaoDeContrasteBateComOsValoresConhecidosDoWcag() {
        // Preto sobre branco é o máximo (21 para 1) e cor igual dá 1 para 1.
        assertEquals(21.0, razaoDeContraste(0xFF000000, 0xFFFFFFFF), 0.01)
        assertEquals(1.0, razaoDeContraste(0xFF336699, 0xFF336699), 0.0001)
        // A ordem dos argumentos não muda o resultado.
        assertEquals(
            razaoDeContraste(0xFF1A5FB4, 0xFFDCECFF),
            razaoDeContraste(0xFFDCECFF, 0xFF1A5FB4),
            0.0001,
        )
    }
}
