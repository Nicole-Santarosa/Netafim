package com.netafim.posvenda.acesso.ui.cadastro

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.netafim.posvenda.acesso.R
import com.netafim.posvenda.acesso.ui.comum.BotaoContorno
import com.netafim.posvenda.acesso.ui.comum.PaginaComWallpaper
import com.netafim.posvenda.acesso.ui.comum.TituloDaFolha
import com.netafim.posvenda.acesso.ui.comum.esc
import com.netafim.posvenda.acesso.ui.comum.estilo
import com.netafim.posvenda.acesso.ui.comum.margensDaFolha
import com.netafim.posvenda.acesso.ui.comum.textoSuaveNoVidro

/**
 * Aguardando validação: o que a pessoa vê depois de enviar o cadastro. O wallpaper traz o ícone de
 * documento com relógio; a folha explica em que ponto do caminho ela está (enviado, em análise,
 * acesso liberado) e tem um botão para voltar ao login. O botão voltar do sistema faz o mesmo.
 */
@Composable
fun AguardandoValidacaoTela(aoVoltar: () -> Unit) {
    BackHandler(onBack = aoVoltar)
    PaginaComWallpaper(
        wallpaper = R.drawable.fundo_aguardando,
        descricaoDoWallpaper = stringResource(R.string.acesso_marca),
        alturaDoHero = 0.62f,
    ) {
        Column(Modifier.margensDaFolha()) {
            TituloDaFolha(
                titulo = stringResource(R.string.acesso_aguardando_titulo),
                subtitulo = stringResource(R.string.acesso_aguardando_mensagem),
            )
            Spacer(Modifier.height(esc(8)))
            Text(
                stringResource(R.string.acesso_aguardando_detalhe),
                color = textoSuaveNoVidro(),
                style = estilo(13),
            )
            Spacer(Modifier.height(esc(20)))
            Passo(R.string.aguardando_passo_enviado, R.string.aguardando_passo_enviado_detalhe, Situacao.FEITO, ultimo = false)
            Passo(R.string.aguardando_passo_analise, R.string.aguardando_passo_analise_detalhe, Situacao.ATUAL, ultimo = false)
            Passo(R.string.aguardando_passo_liberado, R.string.aguardando_passo_liberado_detalhe, Situacao.PENDENTE, ultimo = true)
            Spacer(Modifier.height(esc(24)))
            BotaoContorno(stringResource(R.string.acesso_voltar_login), aoVoltar)
        }
    }
}

private enum class Situacao { FEITO, ATUAL, PENDENTE }

/** Um passo da linha do tempo: marcador (check, ponto ou vazio), uma linha até o próximo e o texto. */
@Composable
private fun Passo(titulo: Int, detalhe: Int, situacao: Situacao, ultimo: Boolean) {
    val cores = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(esc(14))) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(esc(24))
                    .clip(CircleShape)
                    .background(if (situacao == Situacao.FEITO) cores.primary else Color.Transparent)
                    .border(2.dp, if (situacao == Situacao.PENDENTE) cores.outlineVariant else cores.primary, CircleShape),
            ) {
                when (situacao) {
                    Situacao.FEITO -> Icon(Icons.Outlined.Check, contentDescription = null, tint = cores.onPrimary, modifier = Modifier.size(esc(14)))
                    Situacao.ATUAL -> Box(Modifier.size(esc(8)).clip(CircleShape).background(cores.tertiary))
                    Situacao.PENDENTE -> Unit
                }
            }
            if (!ultimo) {
                Box(Modifier.width(esc(2)).height(esc(22)).background(cores.outlineVariant))
            }
        }
        Column(Modifier.fillMaxHeight()) {
            Text(
                stringResource(titulo),
                color = if (situacao == Situacao.PENDENTE) textoSuaveNoVidro() else cores.onSurface,
                style = estilo(14, if (situacao == Situacao.ATUAL) FontWeight.SemiBold else FontWeight.Medium),
            )
            Text(stringResource(detalhe), color = textoSuaveNoVidro(), style = estilo(12))
            if (!ultimo) Spacer(Modifier.height(esc(10)))
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Preview(showBackground = true, widthDp = 800, heightDp = 1280)
@Composable
private fun AguardandoValidacaoTelaPreview() {
    MaterialTheme { AguardandoValidacaoTela(aoVoltar = {}) }
}
