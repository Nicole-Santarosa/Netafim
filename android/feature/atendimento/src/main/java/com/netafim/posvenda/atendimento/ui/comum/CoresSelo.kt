package com.netafim.posvenda.atendimento.ui.comum

import com.netafim.posvenda.atendimento.dominio.SeloStatus
import kotlin.math.pow

// Cores de um selo de status, em ARGB (0xAARRGGBB). Ficam como Long, sem depender do Compose,
// para a razão de contraste poder ser conferida em teste unitário.
data class CoresDoSelo(val fundo: Long, val texto: Long, val bolinha: Long)

// A bolinha mantém a cor do protótipo. O texto é mais escuro que o do protótipo para atingir
// o contraste mínimo de 4,5 para 1 sobre o fundo (WCAG 2, texto normal).
fun coresDoSelo(selo: SeloStatus): CoresDoSelo = when (selo) {
    SeloStatus.ABERTO -> CoresDoSelo(fundo = 0xFFDCECFF, texto = 0xFF1A5FB4, bolinha = 0xFF3B98FF)
    SeloStatus.EM_ATENDIMENTO -> CoresDoSelo(fundo = 0xFFFFF3D2, texto = 0xFF8A5A00, bolinha = 0xFFFFBC03)
    SeloStatus.CONCLUIDO -> CoresDoSelo(fundo = 0xFFECF6D9, texto = 0xFF3F6B0F, bolinha = 0xFF96CC2F)
    SeloStatus.CANCELADO -> CoresDoSelo(fundo = 0xFFE9E9E4, texto = 0xFF21211F, bolinha = 0xFFA0A09C)
}

// Razão de contraste entre duas cores pela fórmula do WCAG 2: (L1 + 0,05) / (L2 + 0,05),
// com L1 a luminância da cor mais clara. O resultado vai de 1 (iguais) a 21 (preto e branco).
// O canal alfa é ignorado: as cores do selo são opacas.
fun razaoDeContraste(corA: Long, corB: Long): Double {
    val luminanciaA = luminanciaRelativa(corA)
    val luminanciaB = luminanciaRelativa(corB)
    val mais = maxOf(luminanciaA, luminanciaB)
    val menos = minOf(luminanciaA, luminanciaB)
    return (mais + 0.05) / (menos + 0.05)
}

// Luminância relativa: converte cada canal sRGB para linear e pondera pela sensibilidade do olho.
private fun luminanciaRelativa(cor: Long): Double {
    val vermelho = canalLinear(((cor shr 16) and 0xFF).toInt())
    val verde = canalLinear(((cor shr 8) and 0xFF).toInt())
    val azul = canalLinear((cor and 0xFF).toInt())
    return 0.2126 * vermelho + 0.7152 * verde + 0.0722 * azul
}

private fun canalLinear(canal: Int): Double {
    val c = canal / 255.0
    return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
}
