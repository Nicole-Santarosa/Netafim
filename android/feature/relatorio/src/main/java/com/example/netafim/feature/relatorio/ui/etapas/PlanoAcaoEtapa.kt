package com.example.netafim.feature.relatorio.ui.etapas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.netafim.feature.relatorio.model.EtapaEvidencia
import com.example.netafim.feature.relatorio.model.EvidenciaItem
import com.example.netafim.feature.relatorio.model.RelatorioRascunho

@Composable
fun PlanoAcaoEtapa(
    rascunho: RelatorioRascunho,
    onPlanoAcaoChange: (String) -> Unit,
    onConclusaoVisitaChange: (String) -> Unit,
    onSelecionarPdfAnexo: () -> Unit,
    onRemoverEvidencia: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val anexos = rascunho.evidencias.filter {
        it.etapa == EtapaEvidencia.ANEXO_EXTRA || it.etapa == EtapaEvidencia.GERAL
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = rascunho.planoAcao,
            onValueChange = onPlanoAcaoChange,
            label = { Text("Plano de ação") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = rascunho.conclusaoVisita,
            onValueChange = onConclusaoVisitaChange,
            label = { Text("Conclusão sobre a visita") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(onClick = onSelecionarPdfAnexo) {
            Text("Anexar PDF")
        }
        ListaEvidenciasEtapa(itens = anexos, onRemover = onRemoverEvidencia)
    }
}

@Composable
internal fun ListaEvidenciasEtapa(
    itens: List<EvidenciaItem>,
    onRemover: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        itens.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val estadoArquivo = if (item.disponivel) item.nomeArquivo else "${item.nomeArquivo} (indisponível)"
                Text(text = "[${item.tipo.codigoBanco}] $estadoArquivo")
                OutlinedButton(onClick = { onRemover(item.id) }) {
                    Text("Remover")
                }
            }
        }
    }
}