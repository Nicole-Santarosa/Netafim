package com.netafim.posvenda.acesso.ui.usuarios

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netafim.posvenda.acesso.R
import com.netafim.posvenda.acesso.ui.comum.BotaoCheio
import com.netafim.posvenda.acesso.ui.comum.CabecalhoTela
import com.netafim.posvenda.acesso.ui.comum.CampoDoPrototipo
import com.netafim.posvenda.acesso.ui.comum.MensagemErro
import com.netafim.posvenda.acesso.ui.comum.TelaResponsiva
import com.netafim.posvenda.acesso.ui.comum.TextoApoio
import com.netafim.posvenda.acesso.ui.comum.esc
import com.netafim.posvenda.acesso.ui.comum.larguraConteudo

@Composable
fun NovoUsuarioRota(viewModel: NovoUsuarioViewModel, aoCriar: () -> Unit) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    LaunchedEffect(estado.criado) {
        if (estado.criado) aoCriar()
    }
    NovoUsuarioTela(estado = estado, aoMudar = viewModel::aoMudar, aoEnviar = viewModel::enviar)
}

/**
 * Novo usuário: o protótipo só desenha o botão "+ Adicionar usuário", não o formulário. Esta tela
 * segue o mesmo estilo (campos com rótulo em cima, botão cheio) e pede só o que a API recebe.
 */
@Composable
fun NovoUsuarioTela(
    estado: NovoUsuarioEstado,
    aoMudar: (CampoNovoUsuario, String) -> Unit,
    aoEnviar: () -> Unit,
) {
    TelaResponsiva(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .imePadding(),
        ) {
            CabecalhoTela(stringResource(R.string.novo_usuario_titulo))
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    modifier = Modifier
                        .widthIn(max = larguraConteudo())
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(start = esc(20), end = esc(20), top = esc(36), bottom = esc(32)),
                ) {
                    estado.erroGeral?.let {
                        MensagemErro(it)
                        Spacer(Modifier.height(esc(16)))
                    }
                    CampoDoPrototipo(
                        valor = estado.nome,
                        aoMudar = { aoMudar(CampoNovoUsuario.NOME, it) },
                        rotulo = stringResource(R.string.acesso_nome),
                        erro = estado.erros[CampoNovoUsuario.NOME],
                        habilitado = !estado.carregando,
                    )
                    Spacer(Modifier.height(esc(16)))
                    CampoDoPrototipo(
                        valor = estado.email,
                        aoMudar = { aoMudar(CampoNovoUsuario.EMAIL, it) },
                        rotulo = stringResource(R.string.acesso_email),
                        erro = estado.erros[CampoNovoUsuario.EMAIL],
                        tipoTeclado = KeyboardType.Email,
                        habilitado = !estado.carregando,
                    )
                    Spacer(Modifier.height(esc(16)))
                    CampoDoPrototipo(
                        valor = estado.telefone,
                        aoMudar = { aoMudar(CampoNovoUsuario.TELEFONE, it) },
                        rotulo = stringResource(R.string.acesso_telefone),
                        erro = estado.erros[CampoNovoUsuario.TELEFONE],
                        tipoTeclado = KeyboardType.Phone,
                        acaoIme = ImeAction.Done,
                        aoConcluir = aoEnviar,
                        habilitado = !estado.carregando,
                    )
                    Spacer(Modifier.height(esc(16)))
                    TextoApoio(stringResource(R.string.novo_usuario_explicacao))
                    Spacer(Modifier.height(esc(20)))
                    BotaoCheio(
                        texto = stringResource(R.string.novo_usuario_enviar),
                        aoClicar = aoEnviar,
                        carregando = estado.carregando,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 828)
@Preview(showBackground = true, widthDp = 800, heightDp = 1280)
@Composable
private fun NovoUsuarioTelaPreview() {
    MaterialTheme {
        NovoUsuarioTela(estado = NovoUsuarioEstado(nome = "Ana Paula Reis"), aoMudar = { _, _ -> }, aoEnviar = {})
    }
}
