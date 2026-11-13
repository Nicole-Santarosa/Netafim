package com.netafim.posvenda.acesso.ui.comum

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Página do protótipo (Figma, seção Acesso): barra azul de 64 dp com o título à esquerda (atrás da
 * barra de status) e, abaixo, o corpo branco com 20 dp de margem lateral e o primeiro conteúdo 36 dp
 * abaixo da barra. Em tela larga o conteúdo fica centralizado, com a largura do protótipo escalada
 * (ver [TelaResponsiva]).
 *
 * @param rolavel falso quando o corpo tem a sua própria lista rolável (ex.: LazyColumn).
 * @param acaoNoTopo canto direito da barra azul, fora do protótipo (ex.: atualizar, abrir o perfil).
 */
@Composable
fun PaginaComCabecalho(
    titulo: String,
    modifier: Modifier = Modifier,
    rolavel: Boolean = true,
    acaoNoTopo: (@Composable () -> Unit)? = null,
    conteudo: @Composable ColumnScope.() -> Unit,
) {
    TelaResponsiva(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .imePadding(),
        ) {
            CabecalhoTela(titulo, acao = acaoNoTopo)
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    modifier = Modifier
                        .widthIn(max = larguraConteudo())
                        .fillMaxWidth()
                        .then(if (rolavel) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                        .navigationBarsPadding()
                        .padding(start = esc(20), end = esc(20), top = esc(36), bottom = esc(32)),
                    content = conteudo,
                )
            }
        }
    }
}
