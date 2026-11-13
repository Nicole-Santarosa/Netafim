package com.netafim.posvenda.acesso.ui.cadastro

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.netafim.posvenda.acesso.R
import com.netafim.posvenda.acesso.ui.comum.BotaoContorno
import com.netafim.posvenda.acesso.ui.comum.CirculoDoPrototipo
import com.netafim.posvenda.acesso.ui.comum.DetalheDeEstado
import com.netafim.posvenda.acesso.ui.comum.PaginaComCabecalho
import com.netafim.posvenda.acesso.ui.comum.TituloDeEstado
import com.netafim.posvenda.acesso.ui.comum.esc

/**
 * Aguardando validação conforme o protótipo (Figma, "04 Aguardando validação"): barra azul com o
 * título, um círculo de 72 dp (a 76 dp da barra), "Seu cadastro está em análise pela Netafim" 28 dp
 * abaixo e o aviso em cinza 21 dp abaixo disso, tudo centralizado.
 *
 * Fora do protótipo: o botão de contorno para voltar ao login (o botão voltar do sistema faz o
 * mesmo), para a pessoa não ficar presa nesta tela.
 */
@Composable
fun AguardandoValidacaoTela(aoVoltar: () -> Unit) {
    BackHandler(onBack = aoVoltar)
    PaginaComCabecalho(stringResource(R.string.acesso_aguardando_titulo)) {
        // O círculo fica 76 dp abaixo da barra; a página já deixa 36 dp.
        Spacer(Modifier.height(esc(40)))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            CirculoDoPrototipo()
            Spacer(Modifier.height(esc(28)))
            TituloDeEstado(stringResource(R.string.acesso_aguardando_mensagem))
            Spacer(Modifier.height(esc(21)))
            DetalheDeEstado(stringResource(R.string.acesso_aguardando_detalhe))
        }
        Spacer(Modifier.height(esc(40)))
        BotaoContorno(stringResource(R.string.acesso_voltar_login), aoVoltar)
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 828)
@Preview(showBackground = true, widthDp = 800, heightDp = 1280)
@Composable
private fun AguardandoValidacaoTelaPreview() {
    MaterialTheme { AguardandoValidacaoTela(aoVoltar = {}) }
}
