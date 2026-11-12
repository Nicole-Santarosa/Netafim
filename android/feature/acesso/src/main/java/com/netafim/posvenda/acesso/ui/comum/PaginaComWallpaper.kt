package com.netafim.posvenda.acesso.ui.comum

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.netafim.posvenda.acesso.R

/** A partir daqui é tablet (ou celular em paisagem). */
private val LARGURA_TABLET: Dp = 600.dp

/**
 * Página com wallpaper da marca e o conteúdo numa folha de vidro fosco (ver [Vidro]).
 *
 * Os wallpapers trazem o logo no alto e um ícone dentro de uma gota; [alturaDoHero] diz que fração
 * da altura da tela fica livre para eles, e a folha começa logo abaixo. Em celular e tablet em
 * retrato a página inteira rola (a folha sobe por cima da imagem); em paisagem, o wallpaper fica à
 * esquerda e o conteúdo num cartão à direita, sobre o mesmo wallpaper desfocado.
 *
 * @param aoVoltar mostra uma seta de voltar sobre a imagem (as telas sem cabeçalho precisam dela).
 * @param acaoNoTopo canto superior direito (ex.: o avatar que abre o perfil).
 */
@Composable
fun PaginaComWallpaper(
    @DrawableRes wallpaper: Int,
    descricaoDoWallpaper: String?,
    alturaDoHero: Float,
    modifier: Modifier = Modifier,
    alinhamentoDoWallpaper: Alignment = Alignment.TopCenter,
    aoVoltar: (() -> Unit)? = null,
    acaoNoTopo: (@Composable () -> Unit)? = null,
    conteudo: @Composable ColumnScope.() -> Unit,
) {
    TelaResponsiva(modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary)) {
            val largura = maxWidth
            val altura = maxHeight
            Box(Modifier.fillMaxSize()) {
                if (largura >= LARGURA_TABLET && largura > altura) {
                    EmDuasColunas(wallpaper, descricaoDoWallpaper, alinhamentoDoWallpaper, conteudo)
                } else {
                    HeroESolha(
                        compacto = largura < LARGURA_TABLET,
                        largura = largura,
                        altura = altura,
                        wallpaper = wallpaper,
                        descricaoDoWallpaper = descricaoDoWallpaper,
                        alinhamento = alinhamentoDoWallpaper,
                        alturaDoHero = alturaDoHero,
                        conteudo = conteudo,
                    )
                }
                BotoesDoTopo(aoVoltar, acaoNoTopo)
            }
        }
    }
}

@Composable
private fun Wallpaper(@DrawableRes id: Int, descricao: String?, alinhamento: Alignment, modifier: Modifier) {
    Image(
        painter = painterResource(id),
        contentDescription = descricao,
        contentScale = ContentScale.Crop,
        alignment = alinhamento,
        modifier = modifier,
    )
}

@Composable
private fun HeroESolha(
    compacto: Boolean,
    largura: Dp,
    altura: Dp,
    @DrawableRes wallpaper: Int,
    descricaoDoWallpaper: String?,
    alinhamento: Alignment,
    alturaDoHero: Float,
    conteudo: @Composable ColumnScope.() -> Unit,
) {
    BarraDeStatusSobreImagem(navegacaoSobreImagem = false)
    Box(Modifier.fillMaxSize()) {
        Wallpaper(wallpaper, descricaoDoWallpaper, alinhamento, Modifier.fillMaxSize())
        val alturaDoHeroEmDp = altura * alturaDoHero
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()) {
            Spacer(Modifier.height(alturaDoHeroEmDp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Vidro(
                    forma = if (compacto) {
                        RoundedCornerShape(topStart = esc(28), topEnd = esc(28))
                    } else {
                        RoundedCornerShape(esc(28))
                    },
                    wallpaper = wallpaper,
                    larguraDaTela = largura,
                    alturaDaTela = altura,
                    modifier = if (compacto) {
                        // Chega até o fim da tela mesmo com pouco conteúdo.
                        Modifier.fillMaxWidth().heightIn(min = altura - alturaDoHeroEmDp)
                    } else {
                        Modifier
                            .widthIn(max = larguraConteudo() + esc(48))
                            .fillMaxWidth()
                            .padding(horizontal = esc(24))
                            .padding(bottom = esc(32))
                    },
                ) {
                    Column(if (compacto) Modifier.navigationBarsPadding() else Modifier) { conteudo() }
                }
            }
        }
        // Degradê sob a barra de status: ao rolar, o texto da folha não colide com o relógio e os ícones.
        val alturaDaBarra = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Box(
            Modifier
                .fillMaxWidth()
                .height(alturaDaBarra + 12.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.75f), Color.Transparent),
                    ),
                ),
        )
    }
}

@Composable
private fun EmDuasColunas(
    @DrawableRes wallpaper: Int,
    descricaoDoWallpaper: String?,
    alinhamento: Alignment,
    conteudo: @Composable ColumnScope.() -> Unit,
) {
    BarraDeStatusSobreImagem(navegacaoSobreImagem = true)
    Row(Modifier.fillMaxSize()) {
        Wallpaper(wallpaper, descricaoDoWallpaper, alinhamento, Modifier.weight(1f).fillMaxHeight())
        // O painel da direita repete a parte de baixo do wallpaper, desfocada e escurecida com o azul
        // da marca; sem desfoque (antes do Android 12), fica só o azul. A escala vem da largura do painel.
        Box(Modifier.weight(1f).fillMaxHeight()) {
            if (DESFOQUE_SUPORTADO) {
                Image(
                    painter = painterResource(wallpaper),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.BottomCenter,
                    modifier = Modifier.fillMaxSize().blur(32.dp, BlurredEdgeTreatment.Unbounded),
                )
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)))
            }
            TelaResponsiva(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val forma = RoundedCornerShape(esc(28))
                    Box(
                        Modifier
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(esc(24))
                            .widthIn(max = larguraConteudo() + esc(48))
                            .clip(forma)
                            .background(if (DESFOQUE_SUPORTADO) VIDRO_COM_DESFOQUE else VIDRO_SEM_DESFOQUE)
                            .border(1.dp, BORDA_DO_VIDRO, forma),
                    ) {
                        Column(Modifier.verticalScroll(rememberScrollState())) { conteudo() }
                    }
                }
            }
        }
    }
}

/** Seta de voltar à esquerda e ação opcional à direita, por cima de tudo (inclusive da folha). */
@Composable
private fun BotoesDoTopo(aoVoltar: (() -> Unit)?, acaoNoTopo: (@Composable () -> Unit)?) {
    if (aoVoltar == null && acaoNoTopo == null) return
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = esc(12), vertical = esc(8)),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (aoVoltar != null) BotaoDeVoltar(aoVoltar) else Spacer(Modifier.size(esc(44)))
        acaoNoTopo?.invoke()
    }
}

/**
 * Botão circular com fundo azul-marinho translúcido e ícone branco: aparece tanto sobre a foto
 * quanto sobre a folha branca que sobe por baixo dele ao rolar.
 */
@Composable
fun BotaoCircularSobreImagem(icone: ImageVector, descricao: String, aoClicar: () -> Unit, habilitado: Boolean = true) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(esc(44))
            .clip(CircleShape)
            .background(Color(0x99011147))
            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
            .clickable(enabled = habilitado, role = Role.Button, onClick = aoClicar),
    ) {
        Icon(
            icone,
            contentDescription = descricao,
            tint = Color.White.copy(alpha = if (habilitado) 1f else 0.5f),
            modifier = Modifier.size(esc(22)),
        )
    }
}

@Composable
private fun BotaoDeVoltar(aoVoltar: () -> Unit) {
    BotaoCircularSobreImagem(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.acesso_voltar), aoVoltar)
}
