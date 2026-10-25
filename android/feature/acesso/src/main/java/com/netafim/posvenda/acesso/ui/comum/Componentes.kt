package com.netafim.posvenda.acesso.ui.comum

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.netafim.posvenda.acesso.R

/** Cabeçalho com a marca. Substituir pelo logotipo oficial quando o design estiver pronto. */
@Composable
fun CabecalhoMarca(modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Outlined.WaterDrop,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Text(
            stringResource(R.string.acesso_marca),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(R.string.acesso_slogan),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun CampoTexto(
    valor: String,
    aoMudar: (String) -> Unit,
    rotulo: String,
    modifier: Modifier = Modifier,
    erro: String? = null,
    dica: String? = null,
    icone: ImageVector? = null,
    tipoTeclado: KeyboardType = KeyboardType.Text,
    acaoIme: ImeAction = ImeAction.Next,
    aoConcluir: (() -> Unit)? = null,
    habilitado: Boolean = true,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = aoMudar,
        label = { Text(rotulo) },
        leadingIcon = icone?.let { { Icon(it, contentDescription = null) } },
        isError = erro != null,
        supportingText = textoDeApoio(erro, dica),
        singleLine = true,
        enabled = habilitado,
        keyboardOptions = KeyboardOptions(keyboardType = tipoTeclado, imeAction = acaoIme),
        keyboardActions = KeyboardActions(onDone = { aoConcluir?.invoke() }),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun CampoSenha(
    valor: String,
    aoMudar: (String) -> Unit,
    rotulo: String,
    visivel: Boolean,
    aoAlternarVisibilidade: () -> Unit,
    modifier: Modifier = Modifier,
    erro: String? = null,
    dica: String? = null,
    acaoIme: ImeAction = ImeAction.Done,
    aoConcluir: (() -> Unit)? = null,
    habilitado: Boolean = true,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = aoMudar,
        label = { Text(rotulo) },
        isError = erro != null,
        supportingText = textoDeApoio(erro, dica),
        singleLine = true,
        enabled = habilitado,
        visualTransformation = if (visivel) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = aoAlternarVisibilidade) {
                Icon(
                    if (visivel) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = stringResource(
                        if (visivel) R.string.acesso_ocultar_senha else R.string.acesso_mostrar_senha,
                    ),
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = acaoIme),
        keyboardActions = KeyboardActions(onDone = { aoConcluir?.invoke() }),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Erro de campo com ícone + texto: a informação nunca depende só da cor (RNF27). */
private fun textoDeApoio(erro: String?, dica: String?): (@Composable () -> Unit)? {
    // A lambda vai numa variável tipada: "return @Composable { }" seria lido
    // pelo Kotlin como o rótulo "return@Composable" e não compila.
    if (erro != null) {
        val conteudoErro: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(erro)
            }
        }
        return conteudoErro
    }
    if (dica != null) {
        val conteudoDica: @Composable () -> Unit = { Text(dica) }
        return conteudoDica
    }
    return null
}

/** Faixa de erro geral, anunciada pelo leitor de tela assim que aparece. */
@Composable
fun MensagemErro(mensagem: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = stringResource(R.string.acesso_erro))
            Text(mensagem, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun BotaoPrincipal(
    texto: String,
    aoClicar: () -> Unit,
    carregando: Boolean,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = aoClicar,
        enabled = !carregando,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        if (carregando) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(texto, style = MaterialTheme.typography.titleMedium)
        }
    }
}
