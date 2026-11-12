package com.netafim.posvenda.acesso.ui.comum

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.netafim.posvenda.acesso.ui.usuarios.iniciaisDe

// Blocos que as telas com wallpaper repetem, para elas ficarem iguais entre si.

/** Margens da folha de vidro: 24 dp dos lados, 28 em cima e 24 embaixo (valores do protótipo, escalados). */
@Composable
fun Modifier.margensDaFolha(): Modifier =
    this.padding(start = esc(24), end = esc(24), top = esc(28), bottom = esc(24))

/** Título da folha (22 sp, cabeçalho para o leitor de tela) e, opcional, o subtítulo abaixo dele. */
@Composable
fun TituloDaFolha(titulo: String, subtitulo: String? = null) {
    Column {
        Text(
            titulo,
            color = MaterialTheme.colorScheme.onSurface,
            style = estilo(22, FontWeight.SemiBold),
            modifier = Modifier.semantics { heading() },
        )
        subtitulo?.let {
            Spacer(Modifier.height(esc(4)))
            Text(it, color = textoSuaveNoVidro(), style = estilo(13))
        }
    }
}

/** Título de um grupo de campos dentro da folha (proximidade: o que é do mesmo assunto fica junto). */
@Composable
fun TituloDeGrupo(texto: String) {
    Text(
        texto,
        color = MaterialTheme.colorScheme.primary,
        style = estilo(12, FontWeight.SemiBold),
        modifier = Modifier.semantics { heading() },
    )
}

/** Círculo com as iniciais do nome ("Ana Paula Reis" vira "AR"). */
@Composable
fun AvatarDeIniciais(nome: String, tamanho: Int = 40, modifier: Modifier = Modifier) {
    val cores = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(esc(tamanho)).clip(CircleShape).background(cores.primary.copy(alpha = 0.12f)),
    ) {
        Text(iniciaisDe(nome), color = cores.primary, style = estilo(tamanho * 14 / 40, FontWeight.SemiBold))
    }
}

/** Atalho em cartão (ícone, título, descrição e uma seta), para as telas de entrada de cada área. */
@Composable
fun AtalhoEmCartao(
    icone: ImageVector,
    titulo: String,
    descricao: String,
    aoClicar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cores = MaterialTheme.colorScheme
    val forma = RoundedCornerShape(esc(16))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(esc(14)),
        modifier = modifier
            .fillMaxWidth()
            .clip(forma)
            .background(fundoDeCartaoNoVidro())
            .border(1.dp, cores.outlineVariant, forma)
            .clickable(role = Role.Button, onClick = aoClicar)
            .padding(esc(14)),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(esc(44)).clip(CircleShape).background(cores.primary),
        ) {
            Icon(icone, contentDescription = null, tint = cores.onPrimary, modifier = Modifier.size(esc(22)))
        }
        Column(Modifier.weight(1f)) {
            Text(titulo, color = cores.onSurface, style = estilo(15, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(descricao, color = textoSuaveNoVidro(), style = estilo(12), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = textoSuaveNoVidro(),
            modifier = Modifier.size(esc(22)),
        )
    }
}

/** Cartão branco quase opaco para agrupar um item dentro da folha (ex.: um cadastro pendente). */
@Composable
fun CartaoNoVidro(modifier: Modifier = Modifier, conteudo: @Composable () -> Unit) {
    val forma = RoundedCornerShape(esc(16))
    Box(
        modifier
            .fillMaxWidth()
            .clip(forma)
            .background(fundoDeCartaoNoVidro())
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, forma)
            .padding(esc(14)),
    ) { conteudo() }
}
