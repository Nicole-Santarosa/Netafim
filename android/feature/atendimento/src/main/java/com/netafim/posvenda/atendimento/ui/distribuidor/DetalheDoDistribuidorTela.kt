package com.netafim.posvenda.atendimento.ui.distribuidor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.netafim.posvenda.atendimento.R
import com.netafim.posvenda.atendimento.dominio.DetalheDoDistribuidor
import com.netafim.posvenda.atendimento.dominio.MotivoAtendimento
import com.netafim.posvenda.atendimento.dominio.StatusAtendimento
import com.netafim.posvenda.atendimento.ui.comum.BarraDoAtendimento
import com.netafim.posvenda.atendimento.ui.comum.CampoDeLeitura
import com.netafim.posvenda.atendimento.ui.comum.TemaDePreview
import com.netafim.posvenda.atendimento.ui.comum.rotulo

// Detalhe do atendimento para o distribuidor. Tela sem estado e só de leitura: o distribuidor acompanha
// o atendimento da região e não altera nada, então não há nenhum botão nem lambda de ação.
@Composable
fun DetalheDoDistribuidorTela(
    detalhe: DetalheDoDistribuidor,
    aoVoltar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val naoInformado = stringResource(R.string.atendimento_distribuidor_nao_informado)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        BarraDoAtendimento(titulo = stringResource(R.string.atendimento_distribuidor_titulo), aoVoltar = aoVoltar)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            PilulaSomenteLeitura()
            Spacer(Modifier.height(12.dp))

            // Campos de leitura, na ordem do protótipo. Aqui o espaço depois da divisória é de 12dp.
            CampoDeLeitura(
                rotulo = stringResource(R.string.atendimento_distribuidor_produtor),
                valor = detalhe.produtor,
                espacoDepoisDaDivisoria = 12.dp,
            )
            CampoDeLeitura(
                rotulo = stringResource(R.string.atendimento_distribuidor_local),
                valor = detalhe.local ?: naoInformado,
                espacoDepoisDaDivisoria = 12.dp,
            )
            CampoDeLeitura(
                rotulo = stringResource(R.string.atendimento_distribuidor_motivo),
                valor = stringResource(detalhe.motivo.rotulo()),
                espacoDepoisDaDivisoria = 12.dp,
            )
            // O status aparece como texto simples, o mesmo rótulo do selo que o cliente vê, sem chip.
            CampoDeLeitura(
                rotulo = stringResource(R.string.atendimento_distribuidor_status),
                valor = stringResource(detalhe.status.selo.rotulo()),
                espacoDepoisDaDivisoria = 12.dp,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

// Pílula "Somente leitura" alinhada à direita. No protótipo o texto é um cinza claro de baixo
// contraste; aqui usa onSurfaceVariant para ficar legível.
@Composable
private fun PilulaSomenteLeitura() {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Box(
            modifier = Modifier
                .height(22.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = CircleShape,
                )
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.atendimento_distribuidor_somente_leitura),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// Dados fictícios dos previews.
private val detalheDoPreview = DetalheDoDistribuidor(
    id = "1",
    protocolo = "OS-0506",
    produtor = "Vanderlei Zucchi Rodas",
    local = "Arealva, SP",
    motivo = MotivoAtendimento.DIGITAL_FARMING,
    status = StatusAtendimento.EM_ATENDIMENTO,
)

@Composable
private fun TelaDePreview(detalhe: DetalheDoDistribuidor) {
    TemaDePreview {
        DetalheDoDistribuidorTela(detalhe = detalhe, aoVoltar = {})
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 828)
@Composable
private fun DetalheDoDistribuidorEmAtendimentoPreview() {
    TelaDePreview(detalheDoPreview)
}

@Preview(showBackground = true, widthDp = 360, heightDp = 828)
@Composable
private fun DetalheDoDistribuidorDestinadoPreview() {
    TelaDePreview(detalheDoPreview.copy(status = StatusAtendimento.DESTINADO))
}

@Preview(showBackground = true, widthDp = 360, heightDp = 828)
@Composable
private fun DetalheDoDistribuidorEncerradoPreview() {
    TelaDePreview(detalheDoPreview.copy(status = StatusAtendimento.ENCERRADO))
}

@Preview(showBackground = true, widthDp = 360, heightDp = 828)
@Composable
private fun DetalheDoDistribuidorCanceladoPreview() {
    TelaDePreview(detalheDoPreview.copy(status = StatusAtendimento.CANCELADO))
}

@Preview(showBackground = true, widthDp = 360, heightDp = 828)
@Composable
private fun DetalheDoDistribuidorSemLocalPreview() {
    TelaDePreview(detalheDoPreview.copy(local = null))
}
