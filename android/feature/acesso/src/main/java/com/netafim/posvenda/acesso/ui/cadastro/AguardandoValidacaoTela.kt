package com.netafim.posvenda.acesso.ui.cadastro

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.netafim.posvenda.acesso.R
import com.netafim.posvenda.acesso.ui.comum.CabecalhoTela
import com.netafim.posvenda.acesso.ui.comum.CirculoDoPrototipo
import com.netafim.posvenda.acesso.ui.comum.DetalheDeEstado
import com.netafim.posvenda.acesso.ui.comum.TelaResponsiva
import com.netafim.posvenda.acesso.ui.comum.TituloDeEstado
import com.netafim.posvenda.acesso.ui.comum.esc
import com.netafim.posvenda.acesso.ui.comum.larguraConteudo

/**
 * Aguardando validação conforme o protótipo (Figma, "04 Aguardando validação"): é o que o
 * solicitante vê depois de enviar o cadastro. O protótipo não tem botão: o gesto/botão voltar do
 * sistema leva ao login ([aoVoltar]), e não de volta ao formulário já enviado.
 */
@Composable
fun AguardandoValidacaoTela(aoVoltar: () -> Unit) {
    BackHandler(onBack = aoVoltar)
    TelaResponsiva(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            CabecalhoTela(stringResource(R.string.acesso_aguardando_titulo))
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .widthIn(max = larguraConteudo())
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        // no quadro o círculo começa 76 dp abaixo do cabeçalho
                        .padding(start = esc(20), end = esc(20), top = esc(76), bottom = esc(32)),
                ) {
                    CirculoDoPrototipo()
                    Spacer(Modifier.height(esc(28)))
                    TituloDeEstado(stringResource(R.string.acesso_aguardando_mensagem))
                    Spacer(Modifier.height(esc(21)))
                    DetalheDeEstado(stringResource(R.string.acesso_aguardando_detalhe))
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 828)
@Preview(showBackground = true, widthDp = 800, heightDp = 1280)
@Composable
private fun AguardandoValidacaoTelaPreview() {
    MaterialTheme { AguardandoValidacaoTela(aoVoltar = {}) }
}
