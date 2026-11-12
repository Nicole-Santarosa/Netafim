package com.netafim.posvenda.acesso.ui.senha

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
import com.netafim.posvenda.acesso.ui.comum.BotaoContorno
import com.netafim.posvenda.acesso.ui.comum.CabecalhoTela
import com.netafim.posvenda.acesso.ui.comum.CampoDoPrototipo
import com.netafim.posvenda.acesso.ui.comum.MensagemErro
import com.netafim.posvenda.acesso.ui.comum.TelaResponsiva
import com.netafim.posvenda.acesso.ui.comum.TextoCorpo
import com.netafim.posvenda.acesso.ui.comum.esc
import com.netafim.posvenda.acesso.ui.comum.larguraConteudo

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
 * Recuperar senha conforme o protótipo (Figma, "05 Recuperar senha"): barra azul com o título,
 * campo de e-mail e o botão "Enviar link de recuperação". O protótipo não tem seta de voltar:
 * o retorno é o gesto/botão voltar do sistema. A confirmação do envio (não desenhada no
 * protótipo) usa os mesmos componentes.
 */
@Composable
fun EsqueciSenhaTela(
    estado: EsqueciSenhaEstado,
    aoMudarEmail: (String) -> Unit,
    aoEnviar: () -> Unit,
    aoVoltar: () -> Unit,
) {
    TelaResponsiva(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .imePadding(),
        ) {
            CabecalhoTela(stringResource(R.string.acesso_esqueci_titulo))
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    modifier = Modifier
                        .widthIn(max = larguraConteudo())
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(start = esc(20), end = esc(20), top = esc(36), bottom = esc(32)),
                ) {
                    val enviado = estado.enviado
                    if (enviado != null) {
                        TextoCorpo(enviado)
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
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 828)
@Preview(showBackground = true, widthDp = 800, heightDp = 1280)
@Composable
private fun EsqueciSenhaTelaPreview() {
    MaterialTheme {
        EsqueciSenhaTela(
            estado = EsqueciSenhaEstado(email = "tecnico@netafim.com"),
            aoMudarEmail = {}, aoEnviar = {}, aoVoltar = {},
        )
    }
}
