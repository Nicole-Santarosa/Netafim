package com.netafim.posvenda.acesso.ui.senha

import androidx.compose.foundation.layout.Column
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
import com.netafim.posvenda.acesso.ui.comum.PaginaComWallpaper
import com.netafim.posvenda.acesso.ui.comum.TextoApoio
import com.netafim.posvenda.acesso.ui.comum.TituloDaFolha
import com.netafim.posvenda.acesso.ui.comum.esc
import com.netafim.posvenda.acesso.ui.comum.fundoDeCampoNoVidro
import com.netafim.posvenda.acesso.ui.comum.margensDaFolha
import com.netafim.posvenda.acesso.ui.comum.textoSuaveNoVidro

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
 * Recuperar senha sobre o wallpaper da tela (gota com cadeado): o ícone fica livre no alto e o
 * formulário, curto, sobe numa folha de vidro. Depois do envio a folha troca para a confirmação,
 * com um botão para voltar ao login.
 */
@Composable
fun EsqueciSenhaTela(
    estado: EsqueciSenhaEstado,
    aoMudarEmail: (String) -> Unit,
    aoEnviar: () -> Unit,
    aoVoltar: () -> Unit,
) {
    PaginaComWallpaper(
        wallpaper = R.drawable.fundo_recuperar_senha,
        descricaoDoWallpaper = stringResource(R.string.acesso_marca),
        alturaDoHero = 0.60f,
        aoVoltar = aoVoltar,
    ) {
        Column(Modifier.margensDaFolha()) {
            val enviado = estado.enviado
            if (enviado != null) {
                TituloDaFolha(stringResource(R.string.esqueci_enviado_titulo))
                Spacer(Modifier.height(esc(12)))
                TextoApoio(enviado, cor = textoSuaveNoVidro())
                Spacer(Modifier.height(esc(24)))
                BotaoContorno(stringResource(R.string.acesso_voltar_login), aoVoltar)
            } else {
                TituloDaFolha(stringResource(R.string.acesso_esqueci_titulo), stringResource(R.string.esqueci_subtitulo))
                Spacer(Modifier.height(esc(20)))
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
                    fundo = fundoDeCampoNoVidro(),
                    corDoRotulo = textoSuaveNoVidro(),
                )
                Spacer(Modifier.height(esc(24)))
                BotaoCheio(
                    texto = stringResource(R.string.acesso_enviar_link),
                    aoClicar = aoEnviar,
                    carregando = estado.carregando,
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Preview(showBackground = true, widthDp = 800, heightDp = 1280)
@Preview(showBackground = true, widthDp = 1280, heightDp = 800)
@Composable
private fun EsqueciSenhaTelaPreview() {
    androidx.compose.material3.MaterialTheme {
        EsqueciSenhaTela(
            estado = EsqueciSenhaEstado(email = "tecnico@netafim.com"),
            aoMudarEmail = {}, aoEnviar = {}, aoVoltar = {},
        )
    }
}
