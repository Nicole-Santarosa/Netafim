package com.netafim.posvenda.acesso.ui.perfil

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.netafim.posvenda.acesso.R
import com.netafim.posvenda.acesso.ui.comum.BotaoCheio
import com.netafim.posvenda.acesso.ui.comum.BotaoContorno
import com.netafim.posvenda.acesso.ui.comum.CampoDoPrototipo
import com.netafim.posvenda.acesso.ui.comum.CampoSenhaDoPrototipo
import com.netafim.posvenda.acesso.ui.comum.CartaoNoVidro
import com.netafim.posvenda.acesso.ui.comum.MensagemErro
import com.netafim.posvenda.acesso.ui.comum.esc
import com.netafim.posvenda.acesso.ui.comum.estilo
import com.netafim.posvenda.acesso.ui.comum.textoSuaveNoVidro

/** Cartão "Verificação em duas etapas" do perfil. Não aparece enquanto o estado não chegou. */
@Composable
fun MfaCartao(
    estado: MfaEstadoTela,
    aoAtivar: () -> Unit,
    aoDesativar: () -> Unit,
    aoMudarCodigo: (String) -> Unit,
    aoMudarSenha: (String) -> Unit,
    aoAlternarSenha: () -> Unit,
    aoConfirmarAtivacao: () -> Unit,
    aoConfirmarDesativacao: () -> Unit,
    aoFechar: () -> Unit,
) {
    val situacao = estado.estado ?: return
    CartaoNoVidro {
        Column(verticalArrangement = Arrangement.spacedBy(esc(8))) {
            Text(
                stringResource(R.string.acesso_mfa_titulo),
                color = MaterialTheme.colorScheme.onSurface,
                style = estilo(15, FontWeight.SemiBold),
                modifier = Modifier.semantics { heading() },
            )
            Text(
                if (situacao.ativo) {
                    stringResource(R.string.acesso_mfa_ativa, situacao.codigosRecuperacaoRestantes)
                } else {
                    stringResource(R.string.acesso_mfa_inativa)
                },
                color = textoSuaveNoVidro(),
                style = estilo(13),
            )
            if (estado.etapa == EtapaMfa.Nenhuma) estado.erro?.let { MensagemErro(it) }
            Spacer(Modifier.height(esc(4)))
            if (situacao.ativo) {
                BotaoContorno(stringResource(R.string.acesso_mfa_desativar), aoDesativar)
            } else {
                BotaoCheio(
                    texto = stringResource(R.string.acesso_mfa_ativar),
                    aoClicar = aoAtivar,
                    habilitado = !estado.carregando,
                )
            }
        }
    }
    when (estado.etapa) {
        EtapaMfa.Ativando -> DialogoAtivacao(estado, aoMudarCodigo, aoConfirmarAtivacao, aoFechar)
        EtapaMfa.CodigosDeRecuperacao -> DialogoCodigos(estado.codigosRecuperacao, aoFechar)
        EtapaMfa.Desativando -> DialogoDesativacao(
            estado, aoMudarCodigo, aoMudarSenha, aoAlternarSenha, aoConfirmarDesativacao, aoFechar,
        )
        EtapaMfa.Nenhuma -> Unit
    }
}

@Composable
private fun DialogoAtivacao(
    estado: MfaEstadoTela,
    aoMudarCodigo: (String) -> Unit,
    aoConfirmar: () -> Unit,
    aoFechar: () -> Unit,
) {
    val configuracao = estado.configuracao ?: return
    val area = LocalClipboardManager.current
    val abrir = LocalUriHandler.current
    var semApp by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = aoFechar,
        containerColor = MaterialTheme.colorScheme.background,
        title = { Text(stringResource(R.string.acesso_mfa_ativar_titulo)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.acesso_mfa_ativar_passo1), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.acesso_mfa_chave),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SelectionContainer {
                    Text(
                        agrupar(configuracao.segredo),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { area.setText(AnnotatedString(configuracao.segredo)) }) {
                        Text(stringResource(R.string.acesso_mfa_copiar_chave))
                    }
                    TextButton(onClick = {
                        // Sem aplicativo que trate otpauth://, a chave manual acima continua valendo.
                        runCatching { abrir.openUri(configuracao.otpauthUri) }
                            .onFailure { semApp = true }
                    }) { Text(stringResource(R.string.acesso_mfa_abrir_app)) }
                }
                if (semApp) Text(stringResource(R.string.acesso_mfa_sem_app), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.acesso_mfa_ativar_passo2), style = MaterialTheme.typography.bodyMedium)
                estado.erro?.let { MensagemErro(it) }
                CampoDoPrototipo(
                    valor = estado.codigo,
                    aoMudar = aoMudarCodigo,
                    rotulo = stringResource(R.string.acesso_mfa_codigo),
                    erro = estado.erroCodigo,
                    tipoTeclado = KeyboardType.Number,
                    acaoIme = ImeAction.Done,
                    aoConcluir = aoConfirmar,
                    habilitado = !estado.carregando,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = aoConfirmar, enabled = !estado.carregando) {
                Text(stringResource(R.string.acesso_mfa_confirmar))
            }
        },
        dismissButton = {
            TextButton(onClick = aoFechar) { Text(stringResource(R.string.acesso_cancelar)) }
        },
    )
}

@Composable
private fun DialogoCodigos(codigos: List<String>, aoFechar: () -> Unit) {
    val area = LocalClipboardManager.current
    // Sem onDismissRequest útil: tocar fora não pode perder códigos que não voltam a aparecer.
    AlertDialog(
        onDismissRequest = {},
        containerColor = MaterialTheme.colorScheme.background,
        title = { Text(stringResource(R.string.acesso_mfa_recuperacao_titulo)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.acesso_mfa_recuperacao_explicacao),
                    style = MaterialTheme.typography.bodyMedium,
                )
                SelectionContainer {
                    Text(
                        codigos.joinToString("\n"),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                TextButton(onClick = { area.setText(AnnotatedString(codigos.joinToString("\n"))) }) {
                    Text(stringResource(R.string.acesso_mfa_copiar_codigos))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = aoFechar) { Text(stringResource(R.string.acesso_mfa_guardei)) }
        },
    )
}

@Composable
private fun DialogoDesativacao(
    estado: MfaEstadoTela,
    aoMudarCodigo: (String) -> Unit,
    aoMudarSenha: (String) -> Unit,
    aoAlternarSenha: () -> Unit,
    aoConfirmar: () -> Unit,
    aoFechar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = aoFechar,
        containerColor = MaterialTheme.colorScheme.background,
        title = { Text(stringResource(R.string.acesso_mfa_desativar_titulo)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.acesso_mfa_desativar_explicacao),
                    style = MaterialTheme.typography.bodyMedium,
                )
                estado.erro?.let { MensagemErro(it) }
                CampoSenhaDoPrototipo(
                    valor = estado.senha,
                    aoMudar = aoMudarSenha,
                    rotulo = stringResource(R.string.acesso_senha),
                    visivel = estado.senhaVisivel,
                    aoAlternarVisibilidade = aoAlternarSenha,
                    descricaoMostrar = stringResource(R.string.acesso_mostrar_senha),
                    descricaoOcultar = stringResource(R.string.acesso_ocultar_senha),
                    acaoIme = ImeAction.Next,
                    habilitado = !estado.carregando,
                )
                CampoDoPrototipo(
                    valor = estado.codigo,
                    aoMudar = aoMudarCodigo,
                    rotulo = stringResource(R.string.acesso_mfa_codigo),
                    erro = estado.erroCodigo,
                    tipoTeclado = KeyboardType.Ascii,
                    acaoIme = ImeAction.Done,
                    aoConcluir = aoConfirmar,
                    habilitado = !estado.carregando,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = aoConfirmar, enabled = !estado.carregando) {
                Text(stringResource(R.string.acesso_mfa_desativar))
            }
        },
        dismissButton = {
            TextButton(onClick = aoFechar) { Text(stringResource(R.string.acesso_cancelar)) }
        },
    )
}

/** Chave em blocos de 4 para facilitar a digitação (o aplicativo ignora os espaços). */
internal fun agrupar(segredo: String): String = segredo.chunked(4).joinToString(" ")

