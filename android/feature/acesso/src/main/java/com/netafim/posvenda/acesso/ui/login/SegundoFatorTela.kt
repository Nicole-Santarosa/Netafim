package com.netafim.posvenda.acesso.ui.login

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.netafim.posvenda.acesso.R
import com.netafim.posvenda.acesso.ui.comum.BotaoCheio
import com.netafim.posvenda.acesso.ui.comum.CampoDoPrototipo
import com.netafim.posvenda.acesso.ui.comum.LinkTexto
import com.netafim.posvenda.acesso.ui.comum.MensagemErro
import com.netafim.posvenda.acesso.ui.comum.PaginaComCabecalho
import com.netafim.posvenda.acesso.ui.comum.TextoApoio
import com.netafim.posvenda.acesso.ui.comum.esc

/**
 * Segundo passo do login: código do aplicativo autenticador ou de recuperação. Não existe no Figma;
 * usa a mesma base das telas de formulário do protótipo (barra azul com o título, campo e botão
 * iguais ao Recuperar senha). O botão voltar do sistema leva de volta à senha.
 */
@Composable
fun SegundoFatorTela(
    estado: LoginEstado,
    aoMudarCodigo: (String) -> Unit,
    aoConfirmar: () -> Unit,
    aoVoltar: () -> Unit,
) {
    BackHandler(onBack = aoVoltar)
    PaginaComCabecalho(stringResource(R.string.acesso_mfa_login_titulo)) {
        TextoApoio(stringResource(R.string.acesso_mfa_login_explicacao))
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

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun SegundoFatorTelaPreview() {
    MaterialTheme {
        SegundoFatorTela(
            estado = LoginEstado(desafio = "desafio", codigo = "123456"),
            aoMudarCodigo = {}, aoConfirmar = {}, aoVoltar = {},
        )
    }
}
