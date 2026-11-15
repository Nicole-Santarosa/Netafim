package com.netafim.posvenda.acesso.ui.senha

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Email
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netafim.posvenda.acesso.R
import com.netafim.posvenda.acesso.ui.comum.BotaoPrincipal
import com.netafim.posvenda.acesso.ui.comum.CampoTexto
import com.netafim.posvenda.acesso.ui.comum.MensagemErro

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EsqueciSenhaRota(viewModel: EsqueciSenhaViewModel, aoVoltar: () -> Unit) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.acesso_esqueci_titulo)) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(margens)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val enviado = estado.enviado
            if (enviado != null) {
                Text(enviado, style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = aoVoltar) {
                    Text(stringResource(R.string.acesso_voltar_login))
                }
                return@Column
            }
            Text(
                stringResource(R.string.acesso_esqueci_explicacao),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            estado.erroGeral?.let { MensagemErro(it) }
            CampoTexto(
                valor = estado.email,
                aoMudar = viewModel::aoMudarEmail,
                rotulo = stringResource(R.string.acesso_email),
                erro = estado.erroEmail,
                icone = Icons.Outlined.Email,
                tipoTeclado = KeyboardType.Email,
                acaoIme = ImeAction.Done,
                aoConcluir = viewModel::enviar,
                habilitado = !estado.carregando,
            )
            BotaoPrincipal(
                texto = stringResource(R.string.acesso_enviar_link),
                aoClicar = viewModel::enviar,
                carregando = estado.carregando,
            )
        }
    }
}
