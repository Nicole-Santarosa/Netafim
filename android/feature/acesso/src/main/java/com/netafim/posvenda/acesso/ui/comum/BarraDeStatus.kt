package com.netafim.posvenda.acesso.ui.comum

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Enquanto a tela está visível, os ícones da barra de status ficam claros (a tela tem uma imagem
 * escura no topo). Com [navegacaoSobreImagem], os da barra de navegação também ficam claros (ela
 * passa sobre a imagem ou sobre o azul da marca); sem ele, ficam escuros (passa sobre o branco).
 * Ao sair, volta ao que estava, para as telas de fundo branco.
 */
@Composable
fun BarraDeStatusSobreImagem(navegacaoSobreImagem: Boolean = false) {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view, navegacaoSobreImagem) {
        val janela = view.context.encontrarAtividade()?.window
        if (janela == null) {
            onDispose { }
        } else {
            val controle = WindowCompat.getInsetsController(janela, view)
            val statusAntes = controle.isAppearanceLightStatusBars
            val navegacaoAntes = controle.isAppearanceLightNavigationBars
            controle.isAppearanceLightStatusBars = false
            controle.isAppearanceLightNavigationBars = !navegacaoSobreImagem
            onDispose {
                controle.isAppearanceLightStatusBars = statusAntes
                controle.isAppearanceLightNavigationBars = navegacaoAntes
            }
        }
    }
}

private tailrec fun Context.encontrarAtividade(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.encontrarAtividade()
    else -> null
}
