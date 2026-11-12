package com.netafim.posvenda.atendimento.ui.detalhe

import com.netafim.posvenda.atendimento.dominio.EtapaDaLinha
import com.netafim.posvenda.atendimento.dominio.SituacaoEtapa
import com.netafim.posvenda.atendimento.dominio.TipoEtapa
import com.netafim.posvenda.atendimento.ui.comum.razaoDeContraste
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoresLinhaDoTempoTest {

    private fun contraste(etapa: EtapaDaLinha): Double {
        val cores = coresDaEtapa(etapa)
        return razaoDeContraste(cores.simbolo, cores.fundo)
    }

    // Elementos gráficos precisam de pelo menos 3 para 1 sobre o fundo (WCAG 2).
    @Test
    fun checkDaEtapaFeitaTemContrasteMinimo() {
        val razao = contraste(EtapaDaLinha(TipoEtapa.ABERTO, SituacaoEtapa.FEITA))
        assertTrue("contraste $razao abaixo de 3,0", razao >= 3.0)
    }

    @Test
    fun pontoDaEtapaAtualTemContrasteMinimo() {
        val razao = contraste(EtapaDaLinha(TipoEtapa.EM_ATENDIMENTO, SituacaoEtapa.ATUAL))
        assertTrue("contraste $razao abaixo de 3,0", razao >= 3.0)
    }

    @Test
    fun xDaEtapaCanceladaTemContrasteMinimo() {
        val razao = contraste(EtapaDaLinha(TipoEtapa.CANCELADO, SituacaoEtapa.ATUAL))
        assertTrue("contraste $razao abaixo de 3,0", razao >= 3.0)
    }

    @Test
    fun linhaDeLigacaoEVerdeSoQuandoAEtapaDeCimaFoiFeita() {
        assertEquals(0xFF219959, corDaLinhaDeLigacao(EtapaDaLinha(TipoEtapa.ABERTO, SituacaoEtapa.FEITA)))
        assertEquals(0xFFC9C9C2, corDaLinhaDeLigacao(EtapaDaLinha(TipoEtapa.EM_ATENDIMENTO, SituacaoEtapa.ATUAL)))
        assertEquals(0xFFC9C9C2, corDaLinhaDeLigacao(EtapaDaLinha(TipoEtapa.CONCLUIDO, SituacaoEtapa.PENDENTE)))
    }

    @Test
    fun etapaPendenteTemContornoEAsOutrasNao() {
        assertEquals(0xFFC9C9C2, coresDaEtapa(EtapaDaLinha(TipoEtapa.CONCLUIDO, SituacaoEtapa.PENDENTE)).contorno)
        assertEquals(null, coresDaEtapa(EtapaDaLinha(TipoEtapa.ABERTO, SituacaoEtapa.FEITA)).contorno)
    }
}
