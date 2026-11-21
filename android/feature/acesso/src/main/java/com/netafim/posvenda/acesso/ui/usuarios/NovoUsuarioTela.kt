package com.netafim.posvenda.acesso.ui.usuarios

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netafim.posvenda.acesso.R
import com.netafim.posvenda.acesso.ui.comum.BotaoCheio
import com.netafim.posvenda.acesso.ui.comum.CampoDoPrototipo
import com.netafim.posvenda.acesso.ui.comum.MensagemErro
import com.netafim.posvenda.acesso.ui.comum.PaginaComWallpaper
import com.netafim.posvenda.acesso.ui.comum.TituloDaFolha
import com.netafim.posvenda.acesso.ui.comum.esc
import com.netafim.posvenda.acesso.ui.comum.fundoDeCampoNoVidro
import com.netafim.posvenda.acesso.ui.comum.margensDaFolha
import com.netafim.posvenda.acesso.ui.comum.textoSuaveNoVidro

@Composable
fun NovoUsuarioRota(viewModel: NovoUsuarioViewModel, aoCriar: () -> Unit, aoVoltar: () -> Unit) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    LaunchedEffect(estado.criado) {
        if (estado.criado) aoCriar()
    }
    NovoUsuarioTela(estado = estado, aoMudar = viewModel::aoMudar, aoEnviar = viewModel::enviar, aoVoltar = aoVoltar)
}

/**
 * Novo usuário sobre o wallpaper da tela. O protótipo só desenha o botão "+ Adicionar usuário", não o
 * formulário: este usa o mesmo vidro das outras telas e pede só o que a API recebe. O texto explica o
 * que acontece depois (convite por e-mail e aprovação da Netafim).
 */
@Composable
fun NovoUsuarioTela(
    estado: NovoUsuarioEstado,
    aoMudar: (CampoNovoUsuario, String) -> Unit,
    aoEnviar: () -> Unit,
    aoVoltar: () -> Unit,
) {
    PaginaComWallpaper(
        wallpaper = R.drawable.fundo_novo_usuario,
        descricaoDoWallpaper = stringResource(R.string.acesso_marca),
        alturaDoHero = 0.30f,
        aoVoltar = aoVoltar,
    ) {
        Column(Modifier.margensDaFolha()) {
            TituloDaFolha(stringResource(R.string.novo_usuario_titulo), stringResource(R.string.novo_usuario_explicacao))
            Spacer(Modifier.height(esc(20)))
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
                fundo = fundoDeCampoNoVidro(),
                corDoRotulo = textoSuaveNoVidro(),
            )
            Spacer(Modifier.height(esc(16)))
            CampoDoPrototipo(
                valor = estado.email,
                aoMudar = { aoMudar(CampoNovoUsuario.EMAIL, it) },
                rotulo = stringResource(R.string.acesso_email),
                erro = estado.erros[CampoNovoUsuario.EMAIL],
                tipoTeclado = KeyboardType.Email,
                habilitado = !estado.carregando,
                fundo = fundoDeCampoNoVidro(),
                corDoRotulo = textoSuaveNoVidro(),
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
                fundo = fundoDeCampoNoVidro(),
                corDoRotulo = textoSuaveNoVidro(),
            )
            Spacer(Modifier.height(esc(24)))
            BotaoCheio(
                texto = stringResource(R.string.novo_usuario_enviar),
                aoClicar = aoEnviar,
                carregando = estado.carregando,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Preview(showBackground = true, widthDp = 800, heightDp = 1280)
@Preview(showBackground = true, widthDp = 1280, heightDp = 800)
@Composable
private fun NovoUsuarioTelaPreview() {
    MaterialTheme {
        NovoUsuarioTela(
            estado = NovoUsuarioEstado(nome = "Ana Paula Reis"),
            aoMudar = { _, _ -> }, aoEnviar = {}, aoVoltar = {},
        )
    }
}
