package com.example.netafim.atendimento.ui.distribuidor

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
import com.example.netafim.atendimento.R
import com.example.netafim.atendimento.dominio.selo
import com.example.netafim.atendimento.ui.comum.BarraDoAtendimento
import com.example.netafim.atendimento.ui.comum.CampoDeLeitura
import com.example.netafim.atendimento.ui.comum.TemaDePreview
import com.example.netafim.atendimento.ui.comum.atendimentoDePreview
import com.example.netafim.atendimento.ui.comum.rotulo
import com.example.netafim.core.modelo.Atendimento
import com.example.netafim.core.modelo.MotivoAtendimento
import com.example.netafim.core.modelo.StatusAtendimento

// Detalhe do atendimento para o distribuidor. Tela sem estado e só de leitura: o distribuidor acompanha
// o atendimento da região e não altera nada, então não há nenhum botão nem lambda de ação.
@Composable
fun DetalheDoDistribuidorTela(
    atendimento: Atendimento,
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
                valor = atendimento.produtor,
                espacoDepoisDaDivisoria = 12.dp,
            )
            CampoDeLeitura(
                rotulo = stringResource(R.string.atendimento_distribuidor_local),
                valor = atendimento.item.local ?: naoInformado,
                espacoDepoisDaDivisoria = 12.dp,
            )
            CampoDeLeitura(
                rotulo = stringResource(R.string.atendimento_distribuidor_motivo),
                valor = stringResource(atendimento.item.motivo.rotulo()),
                espacoDepoisDaDivisoria = 12.dp,
            )
            // O status aparece como texto simples, o mesmo rótulo do selo que o cliente vê, sem chip.
            CampoDeLeitura(
                rotulo = stringResource(R.string.atendimento_distribuidor_status),
                valor = stringResource(atendimento.item.status.selo.rotulo()),
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
private fun atendimentoDoPreview(
    status: StatusAtendimento = StatusAtendimento.EM_ATENDIMENTO,
    cidade: String? = "Arealva, SP",
) = atendimentoDePreview(
    status = status,
    protocolo = "OS-0506",
    motivo = MotivoAtendimento.DIGITAL_FARMING,
    cidade = cidade,
)

@Composable
private fun TelaDePreview(atendimento: Atendimento) {
    TemaDePreview {
        DetalheDoDistribuidorTela(atendimento = atendimento, aoVoltar = {})
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 828)
@Composable
private fun DetalheDoDistribuidorEmAtendimentoPreview() {
    TelaDePreview(atendimentoDoPreview())
}

@Preview(showBackground = true, widthDp = 360, heightDp = 828)
@Composable
private fun DetalheDoDistribuidorDestinadoPreview() {
    TelaDePreview(atendimentoDoPreview(status = StatusAtendimento.DESTINADO))
}

@Preview(showBackground = true, widthDp = 360, heightDp = 828)
@Composable
private fun DetalheDoDistribuidorEncerradoPreview() {
    TelaDePreview(atendimentoDoPreview(status = StatusAtendimento.ENCERRADO))
}

@Preview(showBackground = true, widthDp = 360, heightDp = 828)
@Composable
private fun DetalheDoDistribuidorCanceladoPreview() {
    TelaDePreview(atendimentoDoPreview(status = StatusAtendimento.CANCELADO))
}

@Preview(showBackground = true, widthDp = 360, heightDp = 828)
@Composable
private fun DetalheDoDistribuidorSemLocalPreview() {
    TelaDePreview(atendimentoDoPreview(cidade = null))
}
