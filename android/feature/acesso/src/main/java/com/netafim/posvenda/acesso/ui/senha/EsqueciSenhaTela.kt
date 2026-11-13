package com.netafim.posvenda.acesso.ui.senha

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netafim.posvenda.acesso.R
import com.netafim.posvenda.acesso.ui.comum.BotaoCheio
import com.netafim.posvenda.acesso.ui.comum.BotaoContorno
import com.netafim.posvenda.acesso.ui.comum.CampoDoPrototipo
import com.netafim.posvenda.acesso.ui.comum.MensagemErro
import com.netafim.posvenda.acesso.ui.comum.PaginaComCabecalho
import com.netafim.posvenda.acesso.ui.comum.TextoApoio
import com.netafim.posvenda.acesso.ui.comum.esc

@Composable
fun EsqueciSenhaRota(viewModel: EsqueciSenhaViewModel, aoVoltar: () -> Unit) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    EsqueciSenhaTela(
        estado = estado,
        aoMudarEmail = viewModel::aoMudarEmail,
        aoEnviar = viewModel::enviar,
        aoVoltar = aoVoltar,
    )
}

/**
 * Recuperar senha conforme o protótipo (Figma, "05 Recuperar senha"): barra azul com o título, o
 * campo E-mail e o botão "Enviar link de recuperação" (26 dp abaixo do campo). Depois do envio, fora
 * do protótipo, o corpo troca para a confirmação com um botão de contorno para voltar ao login.
 */
@Composable
fun EsqueciSenhaTela(
    estado: EsqueciSenhaEstado,
    aoMudarEmail: (String) -> Unit,
    aoEnviar: () -> Unit,
    aoVoltar: () -> Unit,
) {
    PaginaComCabecalho(stringResource(R.string.acesso_esqueci_titulo)) {
        val enviado = estado.enviado
        if (enviado != null) {
            TextoApoio(enviado)
            Spacer(Modifier.height(esc(26)))
            BotaoContorno(stringResource(R.string.acesso_voltar_login), aoVoltar)
        } else {
            estado.erroGeral?.let {
                MensagemErro(it)
                Spacer(Modifier.height(esc(16)))
            }
            CampoDoPrototipo(
                valor = estado.email,
                aoMudar = aoMudarEmail,
                rotulo = stringResource(R.string.acesso_email),
                erro = estado.erroEmail,
                tipoTeclado = KeyboardType.Email,
                acaoIme = ImeAction.Done,
                aoConcluir = aoEnviar,
                habilitado = !estado.carregando,
            )
            Spacer(Modifier.height(esc(26)))
            BotaoCheio(
                texto = stringResource(R.string.acesso_enviar_link),
                aoClicar = aoEnviar,
                carregando = estado.carregando,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 828)
@Preview(showBackground = true, widthDp = 800, heightDp = 1280)
@Composable
private fun EsqueciSenhaTelaPreview() {
    androidx.compose.material3.MaterialTheme {
        EsqueciSenhaTela(
            estado = EsqueciSenhaEstado(email = "tecnico@netafim.com"),
            aoMudarEmail = {}, aoEnviar = {}, aoVoltar = {},
        )
    }
}
