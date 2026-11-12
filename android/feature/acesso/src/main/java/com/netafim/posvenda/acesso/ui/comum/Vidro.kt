package com.netafim.posvenda.acesso.ui.comum

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// "Vidro fosco" sobre o wallpaper: base visual das telas com wallpaper (login, recuperar senha,
// cadastro, início, aprovação...).

/** O desfoque de fundo precisa do Android 12 (API 31). */
internal val DESFOQUE_SUPORTADO = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

internal val VIDRO_COM_DESFOQUE = Color.White.copy(alpha = 0.70f)
internal val VIDRO_SEM_DESFOQUE = Color.White.copy(alpha = 0.92f)
internal val BORDA_DO_VIDRO = Color.White.copy(alpha = 0.60f)

/**
 * Vidro fosco: por baixo do conteúdo, uma cópia desfocada do mesmo wallpaper, alinhada com a tela
 * (a posição do vidro é lida e a cópia é deslocada no sentido contrário, também durante a rolagem),
 * e por cima um branco translúcido com uma borda clara. Antes do Android 12 não há desfoque e o
 * branco fica mais firme.
 */
@Composable
fun Vidro(
    forma: Shape,
    @DrawableRes wallpaper: Int,
    larguraDaTela: Dp,
    alturaDaTela: Dp,
    modifier: Modifier = Modifier,
    conteudo: @Composable () -> Unit,
) {
    var posicao by remember { mutableStateOf(IntOffset.Zero) }
    Box(
        modifier
            .clip(forma)
            .onGloballyPositioned { coordenadas ->
                val p = coordenadas.positionInRoot()
                posicao = IntOffset(p.x.roundToInt(), p.y.roundToInt())
            },
    ) {
        if (DESFOQUE_SUPORTADO) {
            Box(Modifier.matchParentSize()) {
                Image(
                    painter = painterResource(wallpaper),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .size(larguraDaTela, alturaDaTela)
                        .offset { IntOffset(-posicao.x, -posicao.y) }
                        .blur(28.dp, BlurredEdgeTreatment.Unbounded),
                )
            }
        }
        Box(
            Modifier
                .matchParentSize()
                .background(if (DESFOQUE_SUPORTADO) VIDRO_COM_DESFOQUE else VIDRO_SEM_DESFOQUE)
                .border(1.dp, BORDA_DO_VIDRO, forma),
        )
        conteudo()
    }
}

/**
 * Sobre o vidro, o cinza do protótipo (666661) perde contraste: o texto pequeno usa o preto do tema
 * um pouco suavizado.
 */
@Composable
fun textoSuaveNoVidro(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)

/** Campos do protótipo (E9E9E4) um pouco translúcidos, para combinar com o vidro. */
@Composable
fun fundoDeCampoNoVidro(): Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)

/** Cartão interno sobre o vidro (ex.: um cadastro pendente): branco quase opaco, para ler bem. */
@Composable
fun fundoDeCartaoNoVidro(): Color = Color.White.copy(alpha = 0.88f)
