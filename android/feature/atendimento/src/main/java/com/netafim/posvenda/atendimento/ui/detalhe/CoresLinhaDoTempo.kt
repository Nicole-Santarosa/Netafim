package com.netafim.posvenda.atendimento.ui.detalhe

import com.netafim.posvenda.atendimento.dominio.EtapaDaLinha
import com.netafim.posvenda.atendimento.dominio.SituacaoEtapa
import com.netafim.posvenda.atendimento.dominio.TipoEtapa

// Cores do círculo de uma etapa, em ARGB (0xAARRGGBB), sem depender do Compose para poderem ser
// testadas. "simbolo" é a cor do desenho dentro do círculo (check, ponto ou X). "contorno" só existe
// na etapa pendente, que é um círculo vazio com borda.
data class CoresDaEtapa(val fundo: Long, val simbolo: Long, val contorno: Long? = null)

private const val VERDE_FEITO = 0xFF219959
private const val CINZA_CONTORNO = 0xFFC9C9C2

// O ponto da etapa atual é mais escuro que o azul do protótipo para ter contraste mínimo de 3 para 1
// em elementos gráficos sobre o fundo. A etapa pendente é só decorativa: o estado também é dito em
// texto para o leitor de tela, então o contorno claro não precisa de contraste.
fun coresDaEtapa(etapa: EtapaDaLinha): CoresDaEtapa = when {
    // A etapa de cancelamento tem aparência própria, em cinza neutro, com um X.
    etapa.tipo == TipoEtapa.CANCELADO -> CoresDaEtapa(fundo = 0xFFE9E9E4, simbolo = 0xFF21211F)
    etapa.situacao == SituacaoEtapa.FEITA -> CoresDaEtapa(fundo = 0xFFDEF5E6, simbolo = VERDE_FEITO)
    etapa.situacao == SituacaoEtapa.ATUAL -> CoresDaEtapa(fundo = 0xFFDCECFF, simbolo = 0xFF1A5FB4)
    else -> CoresDaEtapa(fundo = 0xFFFFFFFF, simbolo = CINZA_CONTORNO, contorno = CINZA_CONTORNO)
}

// Cor da linha vertical que liga uma etapa à de baixo: verde quando a etapa de cima já foi feita,
// cinza nos demais casos.
fun corDaLinhaDeLigacao(etapaDeCima: EtapaDaLinha): Long =
    if (etapaDeCima.situacao == SituacaoEtapa.FEITA) VERDE_FEITO else CINZA_CONTORNO
