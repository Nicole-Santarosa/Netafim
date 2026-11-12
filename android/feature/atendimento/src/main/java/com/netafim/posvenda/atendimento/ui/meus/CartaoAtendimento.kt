package com.netafim.posvenda.atendimento.ui.meus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.netafim.posvenda.atendimento.R
import com.netafim.posvenda.atendimento.dominio.ApoioDoCartao
import com.netafim.posvenda.atendimento.dominio.MotivoAtendimento
import com.netafim.posvenda.atendimento.dominio.ResumoAtendimento
import com.netafim.posvenda.atendimento.dominio.StatusAtendimento
import com.netafim.posvenda.atendimento.dominio.apoioDoCartao
import com.netafim.posvenda.atendimento.ui.comum.SeloStatusChip
import com.netafim.posvenda.atendimento.ui.comum.TemaDePreview
import com.netafim.posvenda.atendimento.ui.comum.rotulo
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Formato da data de abertura mostrada nos atendimentos já concluídos ou cancelados.
private val formatoData: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

// Cartão de um atendimento na lista: protocolo, linha de apoio (há quanto tempo ou data de abertura),
// motivo à esquerda e selo de status à direita. O cartão inteiro é clicável.
@Composable
fun CartaoAtendimento(
    resumo: ResumoAtendimento,
    agora: Instant,
    zona: ZoneId,
    aoClicar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = aoClicar,
        // Alvo de toque mínimo de 48dp e leitura do cartão como um único item pelo leitor de tela.
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = resumo.protocolo,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = textoDeApoio(apoioDoCartao(resumo, agora, zona)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChipDoMotivo(resumo.motivo)
                SeloStatusChip(selo = resumo.status.selo)
            }
        }
    }
}

// Converte o que o domínio decidiu mostrar no texto do strings.xml.
@Composable
private fun textoDeApoio(apoio: ApoioDoCartao): String = when (apoio) {
    ApoioDoCartao.AbertoHoje -> stringResource(R.string.atendimento_aberto_hoje)
    is ApoioDoCartao.AbertoHaDias ->
        pluralStringResource(R.plurals.atendimento_aberto_ha_dias, apoio.dias, apoio.dias)
    is ApoioDoCartao.AbertoEm ->
        stringResource(R.string.atendimento_aberto_em, apoio.data.format(formatoData))
}

// Chip neutro com o motivo do atendimento (fundo surfaceVariant, texto onSurface).
@Composable
private fun ChipDoMotivo(motivo: MotivoAtendimento) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            text = stringResource(motivo.rotulo()),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CartaoAtendimentoPreview() {
    TemaDePreview {
        CartaoAtendimento(
            resumo = ResumoAtendimento(
                id = "1",
                protocolo = "OS-0506",
                motivo = MotivoAtendimento.POS_VENDA,
                status = StatusAtendimento.ABERTO,
                abertoEm = Instant.parse("2026-11-08T15:00:00Z"),
            ),
            agora = Instant.parse("2026-11-11T15:00:00Z"),
            zona = ZoneId.of("America/Sao_Paulo"),
            aoClicar = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
