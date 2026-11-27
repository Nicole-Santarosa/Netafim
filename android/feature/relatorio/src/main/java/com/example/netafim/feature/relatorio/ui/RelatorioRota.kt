package com.example.netafim.feature.relatorio.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.netafim.feature.relatorio.model.EtapaRelatorio
import com.example.netafim.feature.relatorio.ui.etapas.AssinaturaEtapa
import com.example.netafim.feature.relatorio.ui.etapas.AtividadesEtapa
import com.example.netafim.feature.relatorio.ui.etapas.ConstatacoesEtapa
import com.example.netafim.feature.relatorio.ui.etapas.PlanoAcaoEtapa

@Composable
fun RelatorioRota(
    viewModel: RelatorioViewModel,
    onCapturarFotoConstatacao: () -> Unit,
    onCapturarVideoConstatacao: () -> Unit,
    onCapturarFotoAtividade: () -> Unit,
    onCapturarVideoAtividade: () -> Unit,
    onSelecionarPdfAnexo: () -> Unit,
    onExportarAssinaturaEspecialista: (List<List<Pair<Float, Float>>>, Float, Float) -> Unit,
    onExportarAssinaturaCliente: (List<List<Pair<Float, Float>>>, Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val rascunho = state.rascunho

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Etapa ${state.etapaAtual.ordem}/4: ${state.etapaAtual.titulo}",
                style = MaterialTheme.typography.titleMedium
            )

            state.mensagemErro?.let { erro ->
                Text(
                    text = erro,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (state.salvoComSucesso) {
                Text(
                    text = "Relatório gravado com sucesso.",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            when (state.etapaAtual) {
                EtapaRelatorio.CONSTATACOES -> ConstatacoesEtapa(
                    rascunho = rascunho,
                    onTipoAtendimentoChange = viewModel::atualizarTipoAtendimento,
                    onDescricaoConstatacoesChange = viewModel::atualizarDescricaoConstatacoes,
                    onTipoTreinamentoChange = viewModel::atualizarTipoTreinamento,
                    onLocalTreinamentoChange = viewModel::atualizarLocalTreinamento,
                    onQtdPessoasTreinadasChange = viewModel::atualizarQtdPessoasTreinadas,
                    onCapturarFotoConstatacao = onCapturarFotoConstatacao,
                    onCapturarVideoConstatacao = onCapturarVideoConstatacao,
                    onRemoverEvidencia = viewModel::removerEvidencia
                )

                EtapaRelatorio.ATIVIDADES -> AtividadesEtapa(
                    rascunho = rascunho,
                    onAtividadesExecutadasChange = viewModel::atualizarAtividadesExecutadas,
                    onCapturarFotoAtividade = onCapturarFotoAtividade,
                    onCapturarVideoAtividade = onCapturarVideoAtividade,
                    onRemoverEvidencia = viewModel::removerEvidencia
                )

                EtapaRelatorio.PLANO_ACAO -> PlanoAcaoEtapa(
                    rascunho = rascunho,
                    onPlanoAcaoChange = viewModel::atualizarPlanoAcao,
                    onConclusaoVisitaChange = viewModel::atualizarConclusaoVisita,
                    onSelecionarPdfAnexo = onSelecionarPdfAnexo,
                    onRemoverEvidencia = viewModel::removerEvidencia
                )

                EtapaRelatorio.ASSINATURA -> AssinaturaEtapa(
                    assinaturaEspecialista = rascunho.assinaturaEspecialista,
                    assinaturaCliente = rascunho.assinaturaCliente,
                    onExportarAssinaturaEspecialista = onExportarAssinaturaEspecialista,
                    onLimparAssinaturaEspecialista = viewModel::limparAssinaturaEspecialista,
                    onExportarAssinaturaCliente = onExportarAssinaturaCliente,
                    onLimparAssinaturaCliente = viewModel::limparAssinaturaCliente
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.etapaAtual.anterior() != null) {
                OutlinedButton(
                    onClick = viewModel::voltarEtapa,
                    enabled = !state.salvando
                ) {
                    Text("Voltar")
                }
            }

            if (state.etapaAtual != EtapaRelatorio.ASSINATURA) {
                Button(
                    onClick = { viewModel.avancarEtapa() },
                    enabled = !state.salvando
                ) {
                    Text("Avançar")
                }
            } else if (state.salvando) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = viewModel::salvarRelatorio,
                    enabled = !state.salvando
                ) {
                    Text("Salvar relatório")
                }
            }
        }
    }
}