package com.example.netafim.atendimento.ui.fila

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
import androidx.compose.material.icons.automirrored.filled.Help
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
import com.example.netafim.atendimento.R
import com.example.netafim.atendimento.dominio.SeloStatus
import com.example.netafim.atendimento.dominio.selo
import com.example.netafim.atendimento.ui.comum.IconeDeSincronizacao
import com.example.netafim.atendimento.ui.comum.TemaDePreview
import com.example.netafim.atendimento.ui.comum.coresDoSelo
import com.example.netafim.atendimento.ui.comum.itemDePreview
import com.example.netafim.atendimento.ui.comum.rotulo
import com.example.netafim.core.modelo.ItemDeAtendimento
import com.example.netafim.core.modelo.MotivoAtendimento
import com.example.netafim.core.modelo.StatusAtendimento

// Cartão de um atendimento na fila do técnico. O cartão inteiro é clicável e é lido como um item só
// pelo leitor de tela; ícones, pino e seta são decorativos, menos o indicador de sincronização, que tem
// descrição própria e entra uma vez na leitura do cartão.
// O parâmetro "selo" fica para selos de outras partes do grupo: quando informado, o conteúdo passado aparece
// no lugar do selo de status do atendimento. A sincronização não usa mais esse encaixe: tem o próprio
// ícone ao lado do protocolo.
// rotuloDoStatus escolhe o texto do selo: a fila do técnico usa o padrão ("Novo" para destinado e
// agendado) e a lista da região do distribuidor passa o rótulo normal do selo.
@Composable
fun CartaoDaFila(
    item: ItemDeAtendimento,
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
                    // O indicador de sincronização vai na mesma linha do protocolo: é menor que o texto, então
                    // não aumenta a altura do cartão nem mexe no selo de status lá embaixo.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            // Sem protocolo (atendimento criado sem conexão) o servidor ainda não o gerou.
                            text = item.protocolo ?: stringResource(R.string.atendimento_protocolo_pendente),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        IconeDeSincronizacao(item.sincronizacao, modifier = Modifier.padding(start = 6.dp))
                    }
                    // O item vem de outro módulo, então o compilador não faz smart cast: let entrega o valor não nulo.
                    item.nomeCliente?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
                    item.local?.let { LinhaDeLocal(it) }
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
// para concluído, X para cancelado e interrogação para o status que o app não conhece. Fundo e ícone usam
// as cores do selo.
@Composable
private fun CirculoDoStatus(status: StatusAtendimento) {
    val cores = coresDoSelo(status.selo)
    val icone = when (status.selo) {
        SeloStatus.ABERTO -> Icons.Filled.Schedule
        SeloStatus.EM_ATENDIMENTO -> Icons.Filled.Build
        SeloStatus.CONCLUIDO -> Icons.Filled.Check
        SeloStatus.CANCELADO -> Icons.Filled.Close
        SeloStatus.DESCONHECIDO -> Icons.AutoMirrored.Filled.Help
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

private val itemDoPreview = itemDePreview(
    id = "1",
    protocolo = "OS-0506",
    nomeCliente = "Vanderlei Zucchi Rodas",
    local = "Arealva, SP",
    motivo = MotivoAtendimento.DIGITAL_FARMING,
    status = StatusAtendimento.AGENDADO,
    abertoEm = "2026-11-08T15:00:00Z",
)

@Preview(showBackground = true)
@Composable
private fun CartaoDaFilaPreview() {
    TemaDePreview {
        CartaoDaFila(item = itemDoPreview, aoClicar = {}, modifier = Modifier.padding(20.dp))
    }
}

// Prova o encaixe do parâmetro selo: o texto passado aparece no lugar do selo de status.
@Preview(showBackground = true)
@Composable
private fun CartaoDaFilaComSeloPreview() {
    TemaDePreview {
        CartaoDaFila(
            item = itemDoPreview,
            aoClicar = {},
            modifier = Modifier.padding(20.dp),
            selo = { Text(text = "Selo de exemplo", style = MaterialTheme.typography.labelMedium) },
        )
    }
}
