package com.netafim.posvenda.acesso.ui.comum

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Cores do quadro "HD 01 — Login" do Figma que não existem no tema (o resto do app usa o tema).
 * Ficam aqui, num lugar só, em vez de espalhadas pela tela.
 */
object CoresHd {
    val FundoDaTela = Color(0xFFF6F7F9)
    val TextoForte = Color(0xFF1C2133)
    val TextoSuave = Color(0xFF737885)
    val Placeholder = Color(0xFF9EA3AD)
    val Borda = Color(0xFFD9DBE0)
}

private val SombraDoBotaoHd = Color(0x663B98FF)

/**
 * Campo do "HD 01 — Login": rótulo de 11,5 sp acima e caixa branca de 52 dp com raio 12, borda de
 * 1,5 dp, ícone de 20 dp à esquerda e a dica (placeholder) em cinza. Com [erro], a borda e a mensagem
 * usam a cor de erro do tema (a informação nunca depende só da cor: há ícone e texto, RNF27).
 */
@Composable
fun CampoHd(
    valor: String,
    aoMudar: (String) -> Unit,
    rotulo: String,
    dica: String,
    @DrawableRes icone: Int,
    modifier: Modifier = Modifier,
    erro: String? = null,
    tipoTeclado: KeyboardType = KeyboardType.Text,
    acaoIme: ImeAction = ImeAction.Next,
    aoConcluir: (() -> Unit)? = null,
    habilitado: Boolean = true,
    transformacao: VisualTransformation = VisualTransformation.None,
    tamanhoDoTexto: Float = 13.5f,
    acaoFinal: (@Composable () -> Unit)? = null,
) {
    val cores = MaterialTheme.colorScheme
    val forma = RoundedCornerShape(esc(12))
    Column(modifier = modifier.fillMaxWidth()) {
        Text(rotulo, color = CoresHd.TextoSuave, style = estilo(11.5f, FontWeight.Medium))
        Spacer(Modifier.height(esc(6)))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(esc(52))
                .background(Color.White, forma)
                .border(BorderStroke(1.5.dp, if (erro != null) cores.error else CoresHd.Borda), forma)
                .padding(start = esc(16), end = if (acaoFinal != null) esc(4) else esc(16)),
        ) {
            Icon(
                painter = painterResource(icone),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(esc(20)),
            )
            Spacer(Modifier.width(esc(12)))
            BasicTextField(
                value = valor,
                onValueChange = aoMudar,
                enabled = habilitado,
                singleLine = true,
                textStyle = estilo(tamanhoDoTexto).copy(color = CoresHd.TextoForte),
                cursorBrush = SolidColor(cores.primary),
                visualTransformation = transformacao,
                keyboardOptions = KeyboardOptions(keyboardType = tipoTeclado, imeAction = acaoIme),
                keyboardActions = KeyboardActions(onDone = { aoConcluir?.invoke() }),
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = rotulo },
                decorationBox = { campo ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (valor.isEmpty()) {
                            Text(dica, color = CoresHd.Placeholder, style = estilo(tamanhoDoTexto))
                        }
                        campo()
                    }
                },
            )
            acaoFinal?.invoke()
        }
        if (erro != null) {
            Row(
                modifier = Modifier.padding(top = esc(4)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(esc(4)),
            ) {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = cores.error, modifier = Modifier.size(esc(14)))
                Text(erro, color = cores.error, style = estilo(12))
            }
        }
    }
}

/** Campo de senha do "HD 01 — Login": como [CampoHd], com o olho de mostrar/ocultar dentro da caixa. */
@Composable
fun CampoSenhaHd(
    valor: String,
    aoMudar: (String) -> Unit,
    rotulo: String,
    dica: String,
    @DrawableRes icone: Int,
    visivel: Boolean,
    aoAlternarVisibilidade: () -> Unit,
    descricaoMostrar: String,
    descricaoOcultar: String,
    modifier: Modifier = Modifier,
    erro: String? = null,
    acaoIme: ImeAction = ImeAction.Done,
    aoConcluir: (() -> Unit)? = null,
    habilitado: Boolean = true,
) {
    CampoHd(
        valor = valor,
        aoMudar = aoMudar,
        rotulo = rotulo,
        dica = dica,
        icone = icone,
        modifier = modifier,
        erro = erro,
        tipoTeclado = KeyboardType.Password,
        acaoIme = acaoIme,
        aoConcluir = aoConcluir,
        habilitado = habilitado,
        transformacao = if (visivel) VisualTransformation.None else PasswordVisualTransformation(),
        // A senha do desenho é uma fileira de pontos de 15 sp.
        tamanhoDoTexto = 15f,
        acaoFinal = {
            // Fora da ordem do teclado: o "Próximo" do campo vai para o campo seguinte, não para o olho.
            IconButton(
                onClick = aoAlternarVisibilidade,
                modifier = Modifier.size(esc(36)).focusProperties { canFocus = false },
            ) {
                Icon(
                    if (visivel) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (visivel) descricaoOcultar else descricaoMostrar,
                    tint = CoresHd.TextoSuave,
                    modifier = Modifier.size(esc(20)),
                )
            }
        },
    )
}

/** Botão principal do "HD 01 — Login": 54 dp, raio 14, sombra azul de 6 dp, texto de 15 sp semibold. */
@Composable
fun BotaoCheioHd(
    texto: String,
    aoClicar: () -> Unit,
    modifier: Modifier = Modifier,
    carregando: Boolean = false,
    habilitado: Boolean = true,
) {
    val cores = MaterialTheme.colorScheme
    val forma = RoundedCornerShape(esc(14))
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(esc(54))
            .alpha(if (habilitado) 1f else 0.4f)
            .shadow(esc(16), forma, ambientColor = Color.Transparent, spotColor = SombraDoBotaoHd)
            .background(cores.primary, forma)
            .clickable(enabled = habilitado && !carregando, role = Role.Button, onClick = aoClicar),
    ) {
        if (carregando) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(esc(22)), color = cores.onPrimary)
        } else {
            Text(texto, color = cores.onPrimary, textAlign = TextAlign.Center, style = estilo(15, FontWeight.SemiBold))
        }
    }
}

/** Divisor com texto no meio ("ou"): duas linhas de 1 dp e o texto de 11,5 sp. */
@Composable
fun DivisorComTexto(texto: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().height(esc(16)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(CoresHd.Borda))
        Text(
            texto,
            color = CoresHd.TextoSuave,
            textAlign = TextAlign.Center,
            style = estilo(11.5f, FontWeight.Medium),
            modifier = Modifier.width(esc(52)),
        )
        Box(Modifier.weight(1f).height(1.dp).background(CoresHd.Borda))
    }
}
