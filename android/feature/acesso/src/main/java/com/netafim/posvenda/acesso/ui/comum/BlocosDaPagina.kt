package com.netafim.posvenda.acesso.ui.comum

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.netafim.posvenda.acesso.ui.usuarios.iniciaisDe

// Blocos que as telas fora do protótipo (perfil, início) repetem, para ficarem iguais entre si e
// iguais ao desenho: borda C9C9C2, fundo branco, raio 10.

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

/** Círculo com a foto da pessoa ou, sem foto, com as iniciais do nome ("Ana Paula Reis" vira "AR"). */
@Composable
fun AvatarDeIniciais(nome: String, tamanho: Int = 40, modifier: Modifier = Modifier, foto: ImageBitmap? = null) {
    val cores = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(esc(tamanho)).clip(CircleShape).background(cores.primary.copy(alpha = 0.12f)),
    ) {
        if (foto != null) {
            Image(
                bitmap = foto,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(iniciaisDe(nome), color = cores.primary, style = estilo(tamanho * 14 / 40, FontWeight.SemiBold))
        }
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
            .background(cores.background)
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
            Text(descricao, color = cores.onSurfaceVariant, style = estilo(12), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = cores.onSurfaceVariant,
            modifier = Modifier.size(esc(22)),
        )
    }
}

/** Cartão do protótipo (Figma, "04 Aprovação de cadastro"): fundo branco, borda C9C9C2 de 1 dp, raio 10. */
@Composable
fun CartaoDoPrototipo(modifier: Modifier = Modifier, conteudo: @Composable () -> Unit) {
    val forma = RoundedCornerShape(esc(10))
    Box(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background, forma)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, forma)
            .padding(esc(16)),
    ) { conteudo() }
}
