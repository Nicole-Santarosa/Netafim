package com.netafim.posvenda.atendimento.ui.fila

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.netafim.posvenda.atendimento.dominio.ItemDaFila
import com.netafim.posvenda.atendimento.dominio.MotivoAtendimento
import com.netafim.posvenda.atendimento.dominio.SeloStatus
import com.netafim.posvenda.atendimento.dominio.StatusAtendimento
import com.netafim.posvenda.atendimento.ui.comum.TemaDePreview
import com.netafim.posvenda.atendimento.ui.comum.coresDoSelo
import com.netafim.posvenda.atendimento.ui.comum.rotulo
import java.time.Instant

// Cartão de um atendimento na fila do técnico. O cartão inteiro é clicável e é lido como um item só
// pelo leitor de tela; ícones, pino e seta são decorativos.
// O parâmetro "selo" existe para o estado de sincronização (offline, sincronizado), que é de outra parte
// do grupo: quando informado, o conteúdo passado aparece no lugar do selo de status do atendimento.
// rotuloDoStatus escolhe o texto do selo: a fila do técnico usa o padrão ("Novo" para destinado e
// agendado) e a lista da região do distribuidor passa o rótulo normal do selo.
@Composable
fun CartaoDaFila(
    item: ItemDaFila,
    aoClicar: () -> Unit,
    modifier: Modifier = Modifier,
    selo: (@Composable () -> Unit)? = null,
    rotuloDoStatus: (StatusAtendimento) -> Int = ::rotuloNaFila,
) {
    Card(
        onClick = aoClicar,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 110.dp)
            .semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CirculoDoStatus(item.status)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.protocolo,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(text = item.nomeCliente, style = MaterialTheme.typography.bodyMedium)
                    if (item.local != null) LinhaDeLocal(item.local)
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EtiquetaDoMotivo(item.motivo)
                if (selo != null) selo() else SeloDaFila(item.status, rotuloDoStatus = rotuloDoStatus)
            }
        }
    }
}

// Círculo de 44dp com o ícone do selo do status: relógio para aberto, chave para em atendimento, visto
// para concluído e X para cancelado. Fundo e ícone usam as cores do selo.
@Composable
private fun CirculoDoStatus(status: StatusAtendimento) {
    val cores = coresDoSelo(status.selo)
    val icone = when (status.selo) {
        SeloStatus.ABERTO -> Icons.Filled.Schedule
        SeloStatus.EM_ATENDIMENTO -> Icons.Filled.Build
        SeloStatus.CONCLUIDO -> Icons.Filled.Check
        SeloStatus.CANCELADO -> Icons.Filled.Close
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color(cores.fundo)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icone,
            contentDescription = null,
            tint = Color(cores.texto),
            modifier = Modifier.size(22.dp),
        )
    }
}

// Pino de 14dp e a localidade, em cor secundária.
@Composable
private fun LinhaDeLocal(local: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = local,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// Etiqueta do motivo: 22dp de altura, cantos totalmente redondos, borda e fundo discretos.
@Composable
private fun EtiquetaDoMotivo(motivo: MotivoAtendimento) {
    Box(
        modifier = Modifier
            .height(22.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = stringResource(motivo.rotulo()), style = MaterialTheme.typography.labelMedium)
    }
}

private val itemDePreview = ItemDaFila(
    id = "1",
    protocolo = "OS-0506",
    nomeCliente = "Vanderlei Zucchi Rodas",
    local = "Arealva, SP",
    motivo = MotivoAtendimento.DIGITAL_FARMING,
    status = StatusAtendimento.AGENDADO,
    abertoEm = Instant.parse("2026-11-08T15:00:00Z"),
)

@Preview(showBackground = true)
@Composable
private fun CartaoDaFilaPreview() {
    TemaDePreview {
        CartaoDaFila(item = itemDePreview, aoClicar = {}, modifier = Modifier.padding(20.dp))
    }
}

// Prova o encaixe do parâmetro selo: o texto passado aparece no lugar do selo de status.
@Preview(showBackground = true)
@Composable
private fun CartaoDaFilaComSeloPreview() {
    TemaDePreview {
        CartaoDaFila(
            item = itemDePreview,
            aoClicar = {},
            modifier = Modifier.padding(20.dp),
            selo = { Text(text = "Selo de exemplo", style = MaterialTheme.typography.labelMedium) },
        )
    }
}
