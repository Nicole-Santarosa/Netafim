package com.netafim.posvenda.acesso.ui.login

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.netafim.posvenda.acesso.R
import com.netafim.posvenda.acesso.ui.comum.BotaoCheio
import com.netafim.posvenda.acesso.ui.comum.CampoDoPrototipo
import com.netafim.posvenda.acesso.ui.comum.LinkTexto
import com.netafim.posvenda.acesso.ui.comum.MensagemErro
import com.netafim.posvenda.acesso.ui.comum.PaginaComWallpaper
import com.netafim.posvenda.acesso.ui.comum.TituloDaFolha
import com.netafim.posvenda.acesso.ui.comum.esc
import com.netafim.posvenda.acesso.ui.comum.fundoDeCampoNoVidro
import com.netafim.posvenda.acesso.ui.comum.margensDaFolha
import com.netafim.posvenda.acesso.ui.comum.textoSuaveNoVidro

/**
 * Segundo passo do login: código do aplicativo autenticador ou de recuperação. Usa o wallpaper do
 * cadeado (o mesmo de Recuperar senha) e a folha de vidro; a seta de voltar leva de volta à senha.
 */
@Composable
fun SegundoFatorTela(
    estado: LoginEstado,
    aoMudarCodigo: (String) -> Unit,
    aoConfirmar: () -> Unit,
    aoVoltar: () -> Unit,
) {
    PaginaComWallpaper(
        wallpaper = R.drawable.fundo_recuperar_senha,
        descricaoDoWallpaper = stringResource(R.string.acesso_marca),
        alturaDoHero = 0.60f,
        aoVoltar = aoVoltar,
    ) {
        Column(Modifier.margensDaFolha()) {
            TituloDaFolha(
                stringResource(R.string.acesso_mfa_login_titulo),
                stringResource(R.string.acesso_mfa_login_explicacao),
            )
            Spacer(Modifier.height(esc(20)))
            estado.erroGeral?.let {
                MensagemErro(it)
                Spacer(Modifier.height(esc(16)))
            }
            CampoDoPrototipo(
                valor = estado.codigo,
                aoMudar = aoMudarCodigo,
                rotulo = stringResource(R.string.acesso_mfa_codigo),
                dica = stringResource(R.string.acesso_mfa_codigo_dica),
                erro = estado.erroCodigo,
                tipoTeclado = KeyboardType.Ascii,
                acaoIme = ImeAction.Done,
                aoConcluir = aoConfirmar,
                habilitado = !estado.carregando,
                fundo = fundoDeCampoNoVidro(),
                corDoRotulo = textoSuaveNoVidro(),
            )
            Spacer(Modifier.height(esc(24)))
            BotaoCheio(
                texto = stringResource(R.string.acesso_mfa_verificar),
                aoClicar = aoConfirmar,
                carregando = estado.carregando,
            )
            Spacer(Modifier.height(esc(8)))
            LinkTexto(stringResource(R.string.acesso_voltar_login), aoVoltar)
        }
    }
}
