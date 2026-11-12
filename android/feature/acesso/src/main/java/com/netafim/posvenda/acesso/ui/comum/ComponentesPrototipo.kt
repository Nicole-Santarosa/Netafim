package com.netafim.posvenda.acesso.ui.comum

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Componentes do protótipo do Figma (arquivo "Netafim", seção Acesso). As cores vêm do tema:
// primary 181F4E, surfaceVariant E9E9E4 (campos), outlineVariant C9C9C2 (bordas),
// onSurfaceVariant 666661 (rótulos). Nada de cor fixa aqui, exceto a sombra do botão
// (azul 3B98FF a 35%, que o protótipo usa só nele).
//
// RESPONSIVO: o protótipo é de 360 dp (celular). Em telas mais largas tudo cresce na mesma
// proporção (fator de escala contínuo, pela largura) e o conteúdo fica centralizado, em vez de
// esticar os campos pela tela inteira. Os números abaixo são os do protótipo, em escala 1.

/** Largura do quadro do protótipo; é a largura do conteúdo em escala 1. */
private const val LarguraPrototipo = 360

/** Fator de escala da tela atual; 1 = celular (protótipo). Ver [TelaResponsiva]. */
val LocalEscala = compositionLocalOf { 1f }

/**
 * Escala contínua: até 600 dp (celular) vale 1; acima disso o conteúdo ocupa cerca de 70% da
 * largura da tela (tablet 823 dp -> 1,6; paisagem 1316 dp -> 2,0, que é o teto). Campos, botões,
 * textos e espaços crescem na mesma proporção, então a tela se ajusta a qualquer tamanho.
 */
private fun escalaPara(largura: Dp): Float =
    if (largura < 600.dp) 1f else (largura.value * 0.7f / LarguraPrototipo).coerceIn(1f, 2f)

/** Mede a largura disponível e entrega a escala para tudo o que estiver dentro. */
@Composable
fun TelaResponsiva(modifier: Modifier = Modifier, conteudo: @Composable () -> Unit) {
    BoxWithConstraints(modifier = modifier) {
        CompositionLocalProvider(LocalEscala provides escalaPara(maxWidth)) { conteudo() }
    }
}

/** Valor do protótipo, em dp, já na escala da tela atual. */
@Composable
@ReadOnlyComposable
fun esc(valor: Number): Dp = (valor.toFloat() * LocalEscala.current).dp

@Composable
@ReadOnlyComposable
private fun escSp(valor: Int): TextUnit = (valor * LocalEscala.current).sp

/** Largura máxima do conteúdo: 360 dp no celular, proporcionalmente maior no tablet. */
@Composable
@ReadOnlyComposable
fun larguraConteudo(): Dp = esc(LarguraPrototipo)

/** Estilo com a família do tema (Outfit); um TextStyle solto cairia na fonte padrão do sistema. */
@Composable
internal fun estilo(tamanho: Int, peso: FontWeight = FontWeight.Normal): TextStyle =
    MaterialTheme.typography.bodyMedium.copy(
        fontSize = escSp(tamanho),
        fontWeight = peso,
        lineHeight = TextUnit.Unspecified,
        // o Material soma espaçamento entre letras; o protótipo não tem
        letterSpacing = 0.sp,
    )

private val SombraBotao = Color(0x593B98FF)

@Composable
private fun raio() = RoundedCornerShape(esc(8))

/** Barra azul do topo, atrás da barra de status (edge-to-edge) e com o título à esquerda. */
@Composable
fun CabecalhoTela(titulo: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .statusBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = larguraConteudo())
                .fillMaxWidth()
                .height(esc(64))
                .align(Alignment.Center)
                .padding(horizontal = esc(20)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(titulo, color = MaterialTheme.colorScheme.onPrimary, style = estilo(18, FontWeight.SemiBold))
        }
    }
}

/** Campo do protótipo: rótulo pequeno acima e caixa cinza de 44 dp (sem rótulo flutuante). */
@Composable
fun CampoDoPrototipo(
    valor: String,
    aoMudar: (String) -> Unit,
    rotulo: String,
    modifier: Modifier = Modifier,
    erro: String? = null,
    dica: String? = null,
    tipoTeclado: KeyboardType = KeyboardType.Text,
    acaoIme: ImeAction = ImeAction.Next,
    aoConcluir: (() -> Unit)? = null,
    habilitado: Boolean = true,
    transformacao: VisualTransformation = VisualTransformation.None,
    acaoFinal: (@Composable () -> Unit)? = null,
    /** Fundo do campo; o padrão é o cinza do protótipo (E9E9E4). Em superfícies translúcidas, uma versão translúcida dele. */
    fundo: Color? = null,
    /** Cor do rótulo; o padrão é o cinza do protótipo (666661). */
    corDoRotulo: Color? = null,
) {
    val cores = MaterialTheme.colorScheme
    val corBorda = if (erro != null) cores.error else cores.outlineVariant
    val forma = raio()
    Column(modifier = modifier.fillMaxWidth()) {
        Text(rotulo, color = corDoRotulo ?: cores.onSurfaceVariant, style = estilo(12))
        Spacer(Modifier.height(esc(3)))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(esc(44))
                .background(fundo ?: cores.surfaceVariant, forma)
                .border(1.dp, corBorda, forma)
                .padding(start = esc(12), end = if (acaoFinal != null) esc(4) else esc(12)),
        ) {
            BasicTextField(
                value = valor,
                onValueChange = aoMudar,
                enabled = habilitado,
                singleLine = true,
                textStyle = estilo(14).copy(color = cores.onSurface),
                cursorBrush = SolidColor(cores.primary),
                visualTransformation = transformacao,
                keyboardOptions = KeyboardOptions(keyboardType = tipoTeclado, imeAction = acaoIme),
                keyboardActions = KeyboardActions(onDone = { aoConcluir?.invoke() }),
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = rotulo },
            )
            acaoFinal?.invoke()
        }
        if (erro != null) {
            Row(
                modifier = Modifier.padding(top = esc(4)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(esc(4)),
            ) {
                // Ícone + texto: a informação nunca depende só da cor (RNF27).
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = cores.error, modifier = Modifier.size(esc(14)))
                Text(erro, color = cores.error, style = estilo(12))
            }
        } else if (dica != null) {
            Text(dica, color = cores.onSurfaceVariant, style = estilo(12), modifier = Modifier.padding(top = esc(4)))
        }
    }
}

/** Campo de senha do protótipo, com o olho de mostrar/ocultar dentro da caixa. */
@Composable
fun CampoSenhaDoPrototipo(
    valor: String,
    aoMudar: (String) -> Unit,
    rotulo: String,
    visivel: Boolean,
    aoAlternarVisibilidade: () -> Unit,
    descricaoMostrar: String,
    descricaoOcultar: String,
    modifier: Modifier = Modifier,
    erro: String? = null,
    dica: String? = null,
    acaoIme: ImeAction = ImeAction.Done,
    aoConcluir: (() -> Unit)? = null,
    habilitado: Boolean = true,
    fundo: Color? = null,
    corDoRotulo: Color? = null,
) {
    CampoDoPrototipo(
        valor = valor,
        aoMudar = aoMudar,
        rotulo = rotulo,
        modifier = modifier,
        erro = erro,
        dica = dica,
        tipoTeclado = KeyboardType.Password,
        acaoIme = acaoIme,
        aoConcluir = aoConcluir,
        habilitado = habilitado,
        transformacao = if (visivel) VisualTransformation.None else PasswordVisualTransformation(),
        fundo = fundo,
        corDoRotulo = corDoRotulo,
        acaoFinal = {
            IconButton(onClick = aoAlternarVisibilidade, modifier = Modifier.size(esc(36))) {
                Icon(
                    if (visivel) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (visivel) descricaoOcultar else descricaoMostrar,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(esc(20)),
                )
            }
        },
    )
}

/** Botão cheio azul do protótipo: 48 dp, raio 8, sombra azul suave. */
@Composable
fun BotaoCheio(
    texto: String,
    aoClicar: () -> Unit,
    modifier: Modifier = Modifier,
    carregando: Boolean = false,
    habilitado: Boolean = true,
) {
    val cores = MaterialTheme.colorScheme
    val forma = raio()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(esc(48))
            .alpha(if (habilitado) 1f else 0.4f)
            .shadow(esc(12), forma, ambientColor = Color.Transparent, spotColor = SombraBotao)
            .background(cores.primary, forma)
            .clickable(enabled = habilitado && !carregando, role = Role.Button, onClick = aoClicar),
    ) {
        if (carregando) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(esc(22)), color = cores.onPrimary)
        } else {
            Text(texto, color = cores.onPrimary, textAlign = TextAlign.Center, style = estilo(14, FontWeight.Medium))
        }
    }
}

/** Botão só com contorno do protótipo (ex.: "Criar conta"): 48 dp, borda de 1,5 dp. */
@Composable
fun BotaoContorno(texto: String, aoClicar: () -> Unit, modifier: Modifier = Modifier, habilitado: Boolean = true) {
    val cor = MaterialTheme.colorScheme.primary
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(esc(48))
            .border(BorderStroke(1.5.dp, cor), raio())
            .clickable(enabled = habilitado, role = Role.Button, onClick = aoClicar),
    ) {
        Text(texto, color = cor, style = estilo(14, FontWeight.Medium))
    }
}

/** Link de texto centralizado (ex.: "Esqueci minha senha"), 13 sp na cor primária. */
@Composable
fun LinkTexto(texto: String, aoClicar: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = aoClicar)
            .padding(vertical = esc(16)), // 48 dp no total: alvo de toque mínimo
    ) {
        Text(texto, color = MaterialTheme.colorScheme.primary, style = estilo(13))
    }
}

/** Texto corrido (mensagens de confirmação etc.): 14 sp na cor de texto do tema. */
@Composable
fun TextoCorpo(texto: String, modifier: Modifier = Modifier) {
    Text(texto, color = MaterialTheme.colorScheme.onSurface, style = estilo(14), modifier = modifier)
}

/** Círculo de 72 dp do protótipo (preenchimento e borda vêm do tema: surfaceVariant e outlineVariant). */
@Composable
fun CirculoDoPrototipo(modifier: Modifier = Modifier) {
    val cores = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(esc(72))
            .clip(CircleShape)
            .background(cores.surfaceVariant)
            .border(1.dp, cores.outlineVariant, CircleShape),
    )
}

/** Título de uma tela de estado (ex.: "Seu cadastro está em análise"): 15 sp medium, centralizado. */
@Composable
fun TituloDeEstado(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        style = estilo(15, FontWeight.Medium),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Detalhe de uma tela de estado: 12 sp, cinza, centralizado. */
@Composable
fun DetalheDeEstado(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        style = estilo(12),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Botão de ação de 44 dp (Recusar / Aprovar do protótipo). Cheio: fundo na cor e texto em
 * [corTexto]; vazado: borda de 1,5 dp e texto na cor.
 */
@Composable
fun BotaoAcao(
    texto: String,
    aoClicar: () -> Unit,
    cor: Color,
    preenchido: Boolean,
    modifier: Modifier = Modifier,
    corTexto: Color = cor,
) {
    val forma = raio()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(esc(44))
            .then(if (preenchido) Modifier.background(cor, forma) else Modifier.border(BorderStroke(1.5.dp, cor), forma))
            .clickable(role = Role.Button, onClick = aoClicar),
    ) {
        Text(texto, color = corTexto, textAlign = TextAlign.Center, style = estilo(13, FontWeight.Medium))
    }
}

/** Opção de uma escolha (tipo, organização, perfil): destaca a selecionada com a cor primária. */
@Composable
fun OpcaoSelecionavel(
    texto: String,
    selecionada: Boolean,
    aoClicar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cores = MaterialTheme.colorScheme
    val forma = raio()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(esc(44))
            .background(if (selecionada) cores.primary else cores.surfaceVariant, forma)
            .border(1.dp, if (selecionada) cores.primary else cores.outlineVariant, forma)
            .clickable(role = Role.RadioButton, onClick = aoClicar),
    ) {
        Text(
            texto,
            color = if (selecionada) cores.onPrimary else cores.onSurface,
            textAlign = TextAlign.Center,
            style = estilo(13, FontWeight.Medium),
            modifier = Modifier.padding(horizontal = esc(8)),
        )
    }
}

/** Texto de apoio de formulário: 12 sp, cinza, alinhado à esquerda. */
@Composable
fun TextoApoio(texto: String, modifier: Modifier = Modifier) {
    Text(texto, color = MaterialTheme.colorScheme.onSurfaceVariant, style = estilo(12), modifier = modifier.fillMaxWidth())
}
