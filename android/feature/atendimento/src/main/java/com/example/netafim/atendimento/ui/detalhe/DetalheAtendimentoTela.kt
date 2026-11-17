package com.example.netafim.atendimento.ui.detalhe

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
import com.example.netafim.atendimento.R
import com.example.netafim.atendimento.dominio.linhaDeLocal
import com.example.netafim.atendimento.dominio.linhaDoTempo
import com.example.netafim.atendimento.dominio.selo
import com.example.netafim.atendimento.dominio.statusAntesDoCancelamento
import com.example.netafim.atendimento.ui.comum.BarraDoAtendimento
import com.example.netafim.atendimento.ui.comum.TemaDePreview
import com.example.netafim.atendimento.ui.comum.atendimentoDePreview
import com.example.netafim.atendimento.ui.comum.rotulo
import com.example.netafim.core.modelo.Atendimento
import com.example.netafim.core.modelo.EventoDoHistorico
import com.example.netafim.core.modelo.StatusAtendimento
import java.time.Instant

// Detalhe de um atendimento para o cliente: linha do tempo e informações. Tela sem estado.
// O slot "acoes" fica no fim da coluna e existe para o relatório em PDF e a avaliação, que são de outra
// parte do grupo: quem monta a tela por cima encaixa esses botões sem precisar mudar esta tela.
@Composable
fun DetalheAtendimentoTela(
    atendimento: Atendimento,
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
            // Sem protocolo (atendimento criado sem conexão) o servidor ainda não o gerou.
            titulo = stringResource(
                R.string.atendimento_detalhe_titulo,
                atendimento.item.protocolo ?: stringResource(R.string.atendimento_protocolo_pendente),
            ),
            aoVoltar = aoVoltar,
            subtitulo = stringResource(
                R.string.atendimento_detalhe_subtitulo,
                stringResource(atendimento.item.motivo.rotulo()),
                stringResource(atendimento.item.status.selo.rotulo()),
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
                // O status anterior ao cancelamento vem do histórico do próprio atendimento.
                LinhaDoTempo(
                    etapas = linhaDoTempo(atendimento.item.status, statusAntesDoCancelamento(atendimento.historico)),
                )
            }
            CartaoDeSecao {
                CartaoDeInformacoes(atendimento)
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
private fun CartaoDeInformacoes(atendimento: Atendimento) {
    val local = linhaDeLocal(atendimento)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (local != null) {
            LinhaDeInformacao(icone = Icons.Filled.LocationOn, texto = local)
        }
        LinhaDeInformacao(
            icone = Icons.AutoMirrored.Filled.Assignment,
            texto = stringResource(R.string.atendimento_detalhe_motivo, stringResource(atendimento.item.motivo.rotulo())),
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

// Dados fictícios só para os previews. O cancelamento vem do histórico, como no uso real.
private fun atendimentoDoPreview(
    status: StatusAtendimento,
    nomeFazenda: String? = "Fazenda Santa Rita",
    cidade: String? = "Ribeirão Preto, SP",
    historico: List<EventoDoHistorico> = emptyList(),
) = atendimentoDePreview(
    status = status,
    nomeFazenda = nomeFazenda,
    cidade = cidade,
    historico = historico,
)

// Evento de mudança de status, como o banco grava: os dois valores são códigos de status.
private fun mudancaDeStatus(id: Long, anterior: StatusAtendimento, novo: StatusAtendimento) = EventoDoHistorico(
    id = id,
    tipo = "status_alterado",
    valorAnterior = anterior.codigo,
    valorNovo = novo.codigo,
    em = Instant.parse("2026-10-25T15:00:00Z"),
)

@Composable
private fun TelaDePreview(atendimento: Atendimento) {
    TemaDePreview { DetalheAtendimentoTela(atendimento = atendimento, aoVoltar = {}) }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun DetalheConcluidoPreview() = TelaDePreview(atendimentoDoPreview(StatusAtendimento.ENCERRADO))

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun DetalheEmAtendimentoPreview() = TelaDePreview(atendimentoDoPreview(StatusAtendimento.EM_ATENDIMENTO))

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun DetalheAbertoSoComCidadePreview() =
    TelaDePreview(atendimentoDoPreview(StatusAtendimento.ABERTO, nomeFazenda = null, cidade = "Arealva, SP"))

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun DetalheCanceladoPreview() = TelaDePreview(atendimentoDoPreview(StatusAtendimento.CANCELADO))

// Cancelado durante o atendimento: a etapa Em atendimento continua como feita, porque o histórico mostra
// que o status anterior ao cancelamento era em atendimento.
@Preview(showBackground = true, heightDp = 700)
@Composable
private fun DetalheCanceladoAposEmAtendimentoPreview() = TelaDePreview(
    atendimentoDoPreview(
        StatusAtendimento.CANCELADO,
        historico = listOf(
            mudancaDeStatus(1, StatusAtendimento.AGENDADO, StatusAtendimento.EM_ATENDIMENTO),
            mudancaDeStatus(2, StatusAtendimento.EM_ATENDIMENTO, StatusAtendimento.CANCELADO),
        ),
    ),
)

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun DetalheSemLocalPreview() =
    TelaDePreview(atendimentoDoPreview(StatusAtendimento.AGENDADO, nomeFazenda = null, cidade = null))
