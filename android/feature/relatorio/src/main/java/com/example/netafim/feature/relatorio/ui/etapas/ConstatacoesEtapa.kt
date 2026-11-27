package com.example.netafim.feature.relatorio.ui.etapas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.netafim.feature.relatorio.model.EtapaEvidencia
import com.example.netafim.feature.relatorio.model.MotivoAtendimento
import com.example.netafim.feature.relatorio.model.RelatorioRascunho

@Composable
fun ConstatacoesEtapa(
    rascunho: RelatorioRascunho,
    onTipoAtendimentoChange: (String) -> Unit,
    onDescricaoConstatacoesChange: (String) -> Unit,
    onTipoTreinamentoChange: (String) -> Unit,
    onLocalTreinamentoChange: (String) -> Unit,
    onQtdPessoasTreinadasChange: (String) -> Unit,
    onCapturarFotoConstatacao: () -> Unit,
    onCapturarVideoConstatacao: () -> Unit,
    onRemoverEvidencia: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val evidenciasEtapa = rascunho.evidencias.filter { it.etapa == EtapaEvidencia.CONSTATACAO }

    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Motivo: ${rascunho.motivo.titulo}")

        if (rascunho.motivo == MotivoAtendimento.DIGITAL_FARMING || rascunho.motivo == MotivoAtendimento.DUVIDAS) {
            OutlinedTextField(
                value = rascunho.tipoAtendimento,
                onValueChange = onTipoAtendimentoChange,
                label = { Text("Tipo do Atendimento (Online / Presencial)") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (rascunho.motivo == MotivoAtendimento.TREINAMENTO) {
            OutlinedTextField(
                value = rascunho.tipoTreinamento,
                onValueChange = onTipoTreinamentoChange,
                label = { Text("Tipo de Treinamento") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = rascunho.localTreinamento,
                onValueChange = onLocalTreinamentoChange,
                label = { Text("Local do Treinamento") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = rascunho.qtdPessoasTreinadas,
                onValueChange = onQtdPessoasTreinadasChange,
                label = { Text("Número de pessoas treinadas") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (rascunho.motivo != MotivoAtendimento.ENTREGA_TECNICA && rascunho.motivo != MotivoAtendimento.PROGRAMACAO_FEIRAS) {
            OutlinedTextField(
                value = rascunho.descricaoConstatacoes,
                onValueChange = onDescricaoConstatacoesChange,
                label = { Text("Principais constatações") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCapturarFotoConstatacao) { Text("Foto (Câmera)") }
                OutlinedButton(onClick = onCapturarVideoConstatacao) { Text("Vídeo") }
            }
        }

        ListaEvidenciasEtapa(itens = evidenciasEtapa, onRemover = onRemoverEvidencia)
    }
}