package com.netafim.posvenda.atendimento.ui.detalhe

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.netafim.posvenda.atendimento.R
import com.netafim.posvenda.atendimento.dominio.DetalheAtendimento
import com.netafim.posvenda.atendimento.dominio.MotivoAtendimento
import com.netafim.posvenda.atendimento.dominio.StatusAtendimento
import com.netafim.posvenda.atendimento.dominio.linhaDeLocal
import com.netafim.posvenda.atendimento.dominio.linhaDoTempo
import com.netafim.posvenda.atendimento.ui.comum.BarraDoAtendimento
import com.netafim.posvenda.atendimento.ui.comum.TemaDePreview
import com.netafim.posvenda.atendimento.ui.comum.rotulo
import java.time.Instant

// Detalhe de um atendimento para o cliente: linha do tempo e informações. Tela sem estado.
// O slot "acoes" fica no fim da coluna e existe para o relatório em PDF e a avaliação, que são de outra
// parte do grupo: quem monta a tela por cima encaixa esses botões sem precisar mudar esta tela.
@Composable
fun DetalheAtendimentoTela(
    detalhe: DetalheAtendimento,
    aoVoltar: () -> Unit,
    modifier: Modifier = Modifier,
    acoes: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        BarraDoAtendimento(
            titulo = stringResource(R.string.atendimento_detalhe_titulo, detalhe.protocolo),
            aoVoltar = aoVoltar,
            subtitulo = stringResource(
                R.string.atendimento_detalhe_subtitulo,
                stringResource(detalhe.motivo.rotulo()),
                stringResource(detalhe.status.selo.rotulo()),
            ),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CartaoDeSecao {
                LinhaDoTempo(etapas = linhaDoTempo(detalhe.status))
            }
            CartaoDeSecao {
                CartaoDeInformacoes(detalhe)
            }
            acoes()
        }
    }
}

// Cartão sem clique: borda de 1dp em outlineVariant, cantos de 12dp e fundo surface.
@Composable
private fun CartaoDeSecao(conteudo: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) { conteudo() }
    }
}

// Linha de local (só se houver fazenda ou cidade) e linha do motivo.
@Composable
private fun CartaoDeInformacoes(detalhe: DetalheAtendimento) {
    val local = linhaDeLocal(detalhe)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (local != null) {
            LinhaDeInformacao(icone = Icons.Filled.LocationOn, texto = local)
        }
        LinhaDeInformacao(
            icone = Icons.AutoMirrored.Filled.Assignment,
            texto = stringResource(R.string.atendimento_detalhe_motivo, stringResource(detalhe.motivo.rotulo())),
        )
    }
}

// Ícone decorativo à esquerda e o texto; cada linha é lida como um item só.
@Composable
private fun LinhaDeInformacao(icone: ImageVector, texto: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) {},
    ) {
        Icon(
            imageVector = icone,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(text = texto, style = MaterialTheme.typography.bodyMedium)
    }
}

// Dados fictícios só para os previews.
private fun detalheDePreview(
    status: StatusAtendimento,
    nomeFazenda: String? = "Fazenda Santa Rita",
    cidade: String? = "Ribeirão Preto, SP",
    motivo: MotivoAtendimento = MotivoAtendimento.DUVIDAS,
) = DetalheAtendimento(
    id = "1",
    protocolo = "OS-0498",
    motivo = motivo,
    status = status,
    abertoEm = Instant.parse("2026-10-20T15:00:00Z"),
    nomeFazenda = nomeFazenda,
    cidade = cidade,
)

@Composable
private fun TelaDePreview(detalhe: DetalheAtendimento) {
    TemaDePreview { DetalheAtendimentoTela(detalhe = detalhe, aoVoltar = {}) }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun DetalheConcluidoPreview() = TelaDePreview(detalheDePreview(StatusAtendimento.ENCERRADO))

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun DetalheEmAtendimentoPreview() = TelaDePreview(detalheDePreview(StatusAtendimento.EM_ATENDIMENTO))

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun DetalheAbertoSoComCidadePreview() =
    TelaDePreview(detalheDePreview(StatusAtendimento.ABERTO, nomeFazenda = null, cidade = "Arealva, SP"))

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun DetalheCanceladoPreview() = TelaDePreview(detalheDePreview(StatusAtendimento.CANCELADO))

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun DetalheSemLocalPreview() =
    TelaDePreview(detalheDePreview(StatusAtendimento.AGENDADO, nomeFazenda = null, cidade = null))
