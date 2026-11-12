package com.netafim.posvenda.atendimento.ui.detalhe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.netafim.posvenda.atendimento.dominio.EtapaDaLinha
import com.netafim.posvenda.atendimento.dominio.SituacaoEtapa
import com.netafim.posvenda.atendimento.dominio.StatusAtendimento
import com.netafim.posvenda.atendimento.dominio.TipoEtapa
import com.netafim.posvenda.atendimento.dominio.linhaDoTempo
import com.netafim.posvenda.atendimento.ui.comum.TemaDePreview

// Linha do tempo vertical do atendimento: um círculo por etapa, ligado ao próximo por uma linha de 2dp.
@Composable
fun LinhaDoTempo(etapas: List<EtapaDaLinha>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        etapas.forEachIndexed { indice, etapa ->
            LinhaDaEtapa(etapa = etapa, ultima = indice == etapas.lastIndex)
        }
    }
}

// Uma etapa: círculo e linha de ligação à esquerda, título e descrição à direita. A etapa inteira é um
// único item para o leitor de tela (título, descrição e estado), e os desenhos são decorativos.
@Composable
private fun LinhaDaEtapa(etapa: EtapaDaLinha, ultima: Boolean) {
    val estado = stringResource(etapa.situacao.descricaoDeEstado())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .semantics(mergeDescendants = true) { stateDescription = estado },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CirculoDaEtapa(etapa)
            if (!ultima) {
                // A linha ocupa o resto da altura da etapa, até o círculo seguinte.
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .heightIn(min = 16.dp)
                        .background(Color(corDaLinhaDeLigacao(etapa))),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.padding(bottom = if (ultima) 0.dp else 16.dp)) {
            Text(
                text = stringResource(etapa.tipo.titulo()),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(etapa.tipo.descricao()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// Círculo de 24dp: check na etapa feita, ponto na atual, vazio com contorno na pendente e X no cancelamento.
@Composable
private fun CirculoDaEtapa(etapa: EtapaDaLinha) {
    val cores = coresDaEtapa(etapa)
    val base = Modifier
        .size(24.dp)
        .clip(CircleShape)
        .background(Color(cores.fundo))
    val comContorno = if (cores.contorno != null) base.border(2.dp, Color(cores.contorno), CircleShape) else base
    Box(modifier = comContorno, contentAlignment = Alignment.Center) {
        when {
            etapa.tipo == TipoEtapa.CANCELADO -> Icone(Icons.Filled.Close, cores.simbolo)
            etapa.situacao == SituacaoEtapa.FEITA -> Icone(Icons.Filled.Check, cores.simbolo)
            etapa.situacao == SituacaoEtapa.ATUAL -> Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(Color(cores.simbolo), CircleShape),
            )
            else -> Unit
        }
    }
}

// Ícone decorativo (sem descrição): quem informa o estado ao leitor de tela é o texto da etapa.
@Composable
private fun Icone(imagem: ImageVector, cor: Long) {
    Icon(imageVector = imagem, contentDescription = null, tint = Color(cor), modifier = Modifier.size(16.dp))
}

@Preview(showBackground = true)
@Composable
private fun LinhaDoTempoPreview() {
    TemaDePreview {
        Column(modifier = Modifier.padding(16.dp)) {
            LinhaDoTempo(linhaDoTempo(StatusAtendimento.EM_ATENDIMENTO))
            Spacer(Modifier.height(24.dp))
            LinhaDoTempo(linhaDoTempo(StatusAtendimento.CANCELADO))
        }
    }
}
