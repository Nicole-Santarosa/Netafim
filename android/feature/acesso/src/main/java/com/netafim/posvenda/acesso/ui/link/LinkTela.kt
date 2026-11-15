package com.netafim.posvenda.acesso.ui.link

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netafim.posvenda.acesso.R
import com.netafim.posvenda.acesso.dominio.AcaoLink
import com.netafim.posvenda.acesso.ui.cadastro.AceiteTermo
import com.netafim.posvenda.acesso.ui.comum.BotaoPrincipal
import com.netafim.posvenda.acesso.ui.comum.CampoSenha
import com.netafim.posvenda.acesso.ui.comum.CabecalhoMarca
import com.netafim.posvenda.acesso.ui.comum.MensagemErro

/** Rota aberta pelo link do e-mail (`netafim://convite?acao=...&token=...`). */
@Composable
fun LinkRota(
    viewModel: LinkViewModel,
    acao: String?,
    token: String?,
    aoIrParaLogin: () -> Unit,
) {
    LaunchedEffect(acao, token) { viewModel.definirLink(acao, token) }
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    LinkTela(
        estado = estado,
        aoMudar = viewModel::aoMudar,
        aoMudarAceite = viewModel::aoMudarAceite,
        aoEnviar = viewModel::enviar,
        aoIrParaLogin = aoIrParaLogin,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkTela(
    estado: LinkEstado,
    aoMudar: (CampoLink, String) -> Unit,
    aoMudarAceite: (Boolean) -> Unit,
    aoEnviar: () -> Unit,
    aoIrParaLogin: () -> Unit,
) {
    val convite = estado.acao == AcaoLink.CONVITE
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (convite) R.string.acesso_link_titulo_convite else R.string.acesso_link_titulo_redefinir,
                        ),
                    )
                },
            )
        },
    ) { margens ->
        when {
            estado.concluido != null -> ResultadoLink(
                mensagem = estado.concluido,
                erro = false,
                aoIrParaLogin = aoIrParaLogin,
                modifier = Modifier.padding(margens),
            )
            estado.linkInvalido -> ResultadoLink(
                mensagem = LinkViewModel.MENSAGEM_LINK_INVALIDO,
                erro = true,
                aoIrParaLogin = aoIrParaLogin,
                modifier = Modifier.padding(margens),
            )
            else -> Formulario(estado, convite, aoMudar, aoMudarAceite, aoEnviar, Modifier.padding(margens))
        }
    }
}

@Composable
private fun Formulario(
    estado: LinkEstado,
    convite: Boolean,
    aoMudar: (CampoLink, String) -> Unit,
    aoMudarAceite: (Boolean) -> Unit,
    aoEnviar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var senhaVisivel by rememberSaveable { mutableStateOf(false) }
    val bloqueado = estado.carregando
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CabecalhoMarca(Modifier.align(Alignment.CenterHorizontally))
        Text(
            stringResource(
                if (convite) R.string.acesso_link_explicacao_convite else R.string.acesso_link_explicacao_redefinir,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        estado.erroGeral?.let { MensagemErro(it) }
        CampoSenha(
            valor = estado.senha,
            aoMudar = { aoMudar(CampoLink.SENHA, it) },
            rotulo = stringResource(R.string.acesso_link_nova_senha),
            visivel = senhaVisivel,
            aoAlternarVisibilidade = { senhaVisivel = !senhaVisivel },
            erro = estado.erros[CampoLink.SENHA],
            dica = stringResource(R.string.acesso_dica_senha),
            acaoIme = ImeAction.Next,
            habilitado = !bloqueado,
        )
        CampoSenha(
            valor = estado.confirmacao,
            aoMudar = { aoMudar(CampoLink.CONFIRMACAO, it) },
            rotulo = stringResource(R.string.acesso_confirmar_senha),
            visivel = senhaVisivel,
            aoAlternarVisibilidade = { senhaVisivel = !senhaVisivel },
            erro = estado.erros[CampoLink.CONFIRMACAO],
            aoConcluir = if (convite) null else aoEnviar,
            habilitado = !bloqueado,
        )
        if (convite) {
            AceiteTermo(
                aceito = estado.aceiteTermo,
                versao = estado.versaoTermo,
                erro = estado.erros[CampoLink.TERMO],
                aoMudar = aoMudarAceite,
                habilitado = !bloqueado,
            )
        }
        BotaoPrincipal(
            texto = stringResource(R.string.acesso_link_salvar_senha),
            aoClicar = aoEnviar,
            carregando = estado.carregando,
        )
    }
}

@Composable
private fun ResultadoLink(
    mensagem: String,
    erro: Boolean,
    aoIrParaLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(
            if (erro) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = if (erro) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Text(mensagem, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        OutlinedButton(onClick = aoIrParaLogin) {
            Text(stringResource(R.string.acesso_voltar_login))
        }
    }
}
