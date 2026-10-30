package com.netafim.posvenda.acesso.ui.cadastro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netafim.posvenda.acesso.R
import com.netafim.posvenda.acesso.ui.comum.BotaoPrincipal
import com.netafim.posvenda.acesso.ui.comum.CampoSenha
import com.netafim.posvenda.acesso.ui.comum.CampoTexto
import com.netafim.posvenda.acesso.ui.comum.MensagemErro

@Composable
fun CadastroRota(viewModel: CadastroViewModel, aoVoltar: () -> Unit) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    CadastroTela(
        estado = estado,
        aoMudar = viewModel::aoMudar,
        aoMudarAceite = viewModel::aoMudarAceite,
        aoEnviar = viewModel::enviar,
        aoVoltar = aoVoltar,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CadastroTela(
    estado: CadastroEstado,
    aoMudar: (CampoCadastro, String) -> Unit,
    aoMudarAceite: (Boolean) -> Unit,
    aoEnviar: () -> Unit,
    aoVoltar: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.acesso_cadastro_titulo)) },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.acesso_voltar),
                        )
                    }
                },
            )
        },
    ) { margens ->
        if (estado.enviado != null) {
            CadastroEnviado(estado.enviado, aoVoltar, Modifier.padding(margens))
            return@Scaffold
        }
        var senhaVisivel by rememberSaveable { mutableStateOf(false) }
        val bloqueado = estado.carregando
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(margens)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.acesso_cadastro_explicacao),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            estado.erroGeral?.let { MensagemErro(it) }
            CampoTexto(
                valor = estado.nome,
                aoMudar = { aoMudar(CampoCadastro.NOME, it) },
                rotulo = stringResource(R.string.acesso_nome),
                erro = estado.erros[CampoCadastro.NOME],
                icone = Icons.Outlined.Badge,
                habilitado = !bloqueado,
            )
            CampoTexto(
                valor = estado.email,
                aoMudar = { aoMudar(CampoCadastro.EMAIL, it) },
                rotulo = stringResource(R.string.acesso_email),
                erro = estado.erros[CampoCadastro.EMAIL],
                icone = Icons.Outlined.Email,
                tipoTeclado = KeyboardType.Email,
                habilitado = !bloqueado,
            )
            CampoTexto(
                valor = estado.telefone,
                aoMudar = { aoMudar(CampoCadastro.TELEFONE, it) },
                rotulo = stringResource(R.string.acesso_telefone),
                erro = estado.erros[CampoCadastro.TELEFONE],
                icone = Icons.Outlined.Phone,
                tipoTeclado = KeyboardType.Phone,
                habilitado = !bloqueado,
            )
            CampoSenha(
                valor = estado.senha,
                aoMudar = { aoMudar(CampoCadastro.SENHA, it) },
                rotulo = stringResource(R.string.acesso_senha),
                visivel = senhaVisivel,
                aoAlternarVisibilidade = { senhaVisivel = !senhaVisivel },
                erro = estado.erros[CampoCadastro.SENHA],
                dica = stringResource(R.string.acesso_dica_senha),
                acaoIme = ImeAction.Next,
                habilitado = !bloqueado,
            )
            CampoSenha(
                valor = estado.confirmacao,
                aoMudar = { aoMudar(CampoCadastro.CONFIRMACAO, it) },
                rotulo = stringResource(R.string.acesso_confirmar_senha),
                visivel = senhaVisivel,
                aoAlternarVisibilidade = { senhaVisivel = !senhaVisivel },
                erro = estado.erros[CampoCadastro.CONFIRMACAO],
                habilitado = !bloqueado,
            )
            AceiteTermo(
                aceito = estado.aceiteTermo,
                versao = estado.versaoTermo,
                erro = estado.erros[CampoCadastro.TERMO],
                aoMudar = aoMudarAceite,
                habilitado = !bloqueado,
            )
            BotaoPrincipal(
                texto = stringResource(R.string.acesso_enviar_solicitacao),
                aoClicar = aoEnviar,
                carregando = estado.carregando,
            )
        }
    }
}

@Composable
internal fun AceiteTermo(
    aceito: Boolean,
    versao: String?,
    erro: String?,
    aoMudar: (Boolean) -> Unit,
    habilitado: Boolean,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = aceito,
                    enabled = habilitado,
                    role = Role.Checkbox,
                    onValueChange = aoMudar,
                ),
        ) {
            Checkbox(checked = aceito, onCheckedChange = null, enabled = habilitado)
            Text(
                text = if (versao != null) {
                    stringResource(R.string.acesso_aceite_termo, versao)
                } else {
                    stringResource(R.string.acesso_aceite_termo_carregando)
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        erro?.let { MensagemErro(it) }
    }
}

@Composable
private fun CadastroEnviado(mensagem: String, aoVoltar: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(
            Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
        )
        Text(
            stringResource(R.string.acesso_cadastro_enviado_titulo),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(mensagem, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = aoVoltar) { Text(stringResource(R.string.acesso_voltar_login)) }
    }
}

@Preview(showBackground = true)
@Composable
private fun CadastroTelaPreview() {
    MaterialTheme {
        CadastroTela(
            estado = CadastroEstado(
                nome = "João Produtor",
                versaoTermo = "1.0",
                erros = mapOf(CampoCadastro.EMAIL to "E-mail inválido."),
            ),
            aoMudar = { _, _ -> }, aoMudarAceite = {}, aoEnviar = {}, aoVoltar = {},
        )
    }
}
