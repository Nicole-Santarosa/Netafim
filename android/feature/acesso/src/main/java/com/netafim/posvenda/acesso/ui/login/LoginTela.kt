package com.netafim.posvenda.acesso.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netafim.posvenda.acesso.R
import com.netafim.posvenda.acesso.ui.comum.BotaoPrincipal
import com.netafim.posvenda.acesso.ui.comum.CabecalhoMarca
import com.netafim.posvenda.acesso.ui.comum.CampoSenha
import com.netafim.posvenda.acesso.ui.comum.CampoTexto
import com.netafim.posvenda.acesso.ui.comum.MensagemErro

@Composable
fun LoginRota(
    viewModel: LoginViewModel,
    aoEntrar: () -> Unit,
    aoSolicitarAcesso: () -> Unit,
    aoEsquecerSenha: () -> Unit,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    LaunchedEffect(estado.entrou) {
        if (estado.entrou) aoEntrar()
    }
    LoginTela(
        estado = estado,
        aoMudarEmail = viewModel::aoMudarEmail,
        aoMudarSenha = viewModel::aoMudarSenha,
        aoAlternarSenha = viewModel::alternarVisibilidadeSenha,
        aoEntrar = viewModel::entrar,
        aoSolicitarAcesso = aoSolicitarAcesso,
        aoEsquecerSenha = aoEsquecerSenha,
    )
}

@Composable
fun LoginTela(
    estado: LoginEstado,
    aoMudarEmail: (String) -> Unit,
    aoMudarSenha: (String) -> Unit,
    aoAlternarSenha: () -> Unit,
    aoEntrar: () -> Unit,
    aoSolicitarAcesso: () -> Unit,
    aoEsquecerSenha: () -> Unit,
) {
    Scaffold { margens ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(margens)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            CabecalhoMarca()
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.acesso_login_titulo),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.align(Alignment.Start),
            )
            Text(
                stringResource(R.string.acesso_login_subtitulo),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Start),
            )
            estado.erroGeral?.let { MensagemErro(it) }
            CampoTexto(
                valor = estado.email,
                aoMudar = aoMudarEmail,
                rotulo = stringResource(R.string.acesso_email),
                erro = estado.erroEmail,
                icone = Icons.Outlined.Email,
                tipoTeclado = KeyboardType.Email,
                habilitado = !estado.carregando,
            )
            CampoSenha(
                valor = estado.senha,
                aoMudar = aoMudarSenha,
                rotulo = stringResource(R.string.acesso_senha),
                visivel = estado.senhaVisivel,
                aoAlternarVisibilidade = aoAlternarSenha,
                erro = estado.erroSenha,
                acaoIme = ImeAction.Done,
                aoConcluir = aoEntrar,
                habilitado = !estado.carregando,
            )
            TextButton(onClick = aoEsquecerSenha, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.acesso_esqueci_senha))
            }
            BotaoPrincipal(
                texto = stringResource(R.string.acesso_entrar),
                aoClicar = aoEntrar,
                carregando = estado.carregando,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.acesso_sem_conta), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = aoSolicitarAcesso) {
                    Text(stringResource(R.string.acesso_solicitar_acesso))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginTelaPreview() {
    MaterialTheme {
        LoginTela(
            estado = LoginEstado(email = "tecnico@netafim.com", erroGeral = "E-mail ou senha incorretos."),
            aoMudarEmail = {}, aoMudarSenha = {}, aoAlternarSenha = {}, aoEntrar = {},
            aoSolicitarAcesso = {}, aoEsquecerSenha = {},
        )
    }
}
