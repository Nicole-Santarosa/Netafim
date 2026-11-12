package com.netafim.posvenda.atendimento.ui.meus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.netafim.posvenda.atendimento.R
import com.netafim.posvenda.atendimento.dominio.MotivoAtendimento
import com.netafim.posvenda.atendimento.dominio.ResumoAtendimento
import com.netafim.posvenda.atendimento.dominio.StatusAtendimento
import com.netafim.posvenda.atendimento.ui.comum.BarraDoAtendimento
import com.netafim.posvenda.atendimento.ui.comum.TemaDePreview
import java.time.Instant
import java.time.ZoneId

// Lista dos atendimentos do cliente, com botão para abrir um novo. Tela sem estado: recebe a lista
// pronta e avisa por lambdas o que o usuário fez. "agora" e "zona" entram como parâmetro para o
// texto de apoio dos cartões ser previsível nos testes e nos previews.
@Composable
fun MeusAtendimentosTela(
    atendimentos: List<ResumoAtendimento>,
    agora: Instant,
    zona: ZoneId,
    aoAbrirNovo: () -> Unit,
    aoAbrirDetalhe: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            // Barra comum do módulo, sem seta de voltar: esta é a tela inicial do cliente.
            BarraDoAtendimento(titulo = stringResource(R.string.atendimento_meus_titulo))
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = aoAbrirNovo,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.atendimento_meus_novo),
                )
            }
        },
    ) { espacamentoDoScaffold ->
        if (atendimentos.isEmpty()) {
            EstadoVazio(modifier = Modifier.padding(espacamentoDoScaffold))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(espacamentoDoScaffold),
                // Margem de 16dp; embaixo sobra espaço para o botão flutuante não cobrir o último cartão.
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items = atendimentos, key = { it.id }) { resumo ->
                    CartaoAtendimento(
                        resumo = resumo,
                        agora = agora,
                        zona = zona,
                        aoClicar = { aoAbrirDetalhe(resumo.id) },
                    )
                }
            }
        }
    }
}

// Mensagem mostrada no centro da tela quando o cliente ainda não tem atendimentos.
@Composable
private fun EstadoVazio(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.atendimento_meus_vazio_titulo),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.atendimento_meus_vazio_texto),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

// Data fixa para o preview não mudar de um dia para o outro.
private val agoraDoPreview: Instant = Instant.parse("2026-11-11T15:00:00Z")
private val zonaDoPreview: ZoneId = ZoneId.of("America/Sao_Paulo")

@Preview(showBackground = true)
@Composable
private fun MeusAtendimentosTelaPreview() {
    TemaDePreview {
        MeusAtendimentosTela(
            atendimentos = listOf(
                ResumoAtendimento("1", "OS-0498", MotivoAtendimento.DUVIDAS, StatusAtendimento.ENCERRADO, Instant.parse("2026-10-20T15:00:00Z")),
                ResumoAtendimento("2", "OS-0506", MotivoAtendimento.POS_VENDA, StatusAtendimento.ABERTO, Instant.parse("2026-11-08T15:00:00Z")),
                ResumoAtendimento("3", "OS-0477", MotivoAtendimento.DIGITAL_FARMING, StatusAtendimento.EM_ATENDIMENTO, Instant.parse("2026-11-11T12:00:00Z")),
                ResumoAtendimento("4", "OS-0431", MotivoAtendimento.TREINAMENTO, StatusAtendimento.CANCELADO, Instant.parse("2026-09-30T15:00:00Z")),
            ),
            agora = agoraDoPreview,
            zona = zonaDoPreview,
            aoAbrirNovo = {},
            aoAbrirDetalhe = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MeusAtendimentosTelaVaziaPreview() {
    TemaDePreview {
        MeusAtendimentosTela(
            atendimentos = emptyList(),
            agora = agoraDoPreview,
            zona = zonaDoPreview,
            aoAbrirNovo = {},
            aoAbrirDetalhe = {},
        )
    }
}
