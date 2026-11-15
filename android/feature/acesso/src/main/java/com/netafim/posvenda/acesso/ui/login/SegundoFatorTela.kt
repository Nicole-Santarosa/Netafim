package com.netafim.posvenda.acesso.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.netafim.posvenda.acesso.R
import com.netafim.posvenda.acesso.ui.comum.BotaoPrincipal
import com.netafim.posvenda.acesso.ui.comum.CabecalhoMarca
import com.netafim.posvenda.acesso.ui.comum.CampoTexto
import com.netafim.posvenda.acesso.ui.comum.MensagemErro

/** Segundo passo do login: código do aplicativo autenticador ou de recuperação. */
@Composable
fun SegundoFatorTela(
    estado: LoginEstado,
    aoMudarCodigo: (String) -> Unit,
    aoConfirmar: () -> Unit,
    aoVoltar: () -> Unit,
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
                stringResource(R.string.acesso_mfa_login_titulo),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.align(Alignment.Start),
            )
            Text(
                stringResource(R.string.acesso_mfa_login_explicacao),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Start),
            )
            estado.erroGeral?.let { MensagemErro(it) }
            CampoTexto(
                valor = estado.codigo,
                aoMudar = aoMudarCodigo,
                rotulo = stringResource(R.string.acesso_mfa_codigo),
                dica = stringResource(R.string.acesso_mfa_codigo_dica),
                erro = estado.erroCodigo,
                icone = Icons.Outlined.Lock,
                tipoTeclado = KeyboardType.Ascii,
                acaoIme = ImeAction.Done,
                aoConcluir = aoConfirmar,
                habilitado = !estado.carregando,
            )
            BotaoPrincipal(
                texto = stringResource(R.string.acesso_mfa_verificar),
                aoClicar = aoConfirmar,
                carregando = estado.carregando,
            )
            TextButton(onClick = aoVoltar) {
                Text(stringResource(R.string.acesso_voltar_login))
            }
        }
    }
}
