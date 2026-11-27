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
import com.example.netafim.feature.relatorio.model.RelatorioRascunho

@Composable
fun AtividadesEtapa(
    rascunho: RelatorioRascunho,
    onAtividadesExecutadasChange: (String) -> Unit,
    onCapturarFotoAtividade: () -> Unit,
    onCapturarVideoAtividade: () -> Unit,
    onRemoverEvidencia: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val evidenciasEtapa = rascunho.evidencias.filter { it.etapa == EtapaEvidencia.ATIVIDADE }

    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = rascunho.atividadesExecutadas,
            onValueChange = onAtividadesExecutadasChange,
            label = { Text("Atividades executadas") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onCapturarFotoAtividade) { Text("Foto Atividade") }
            OutlinedButton(onClick = onCapturarVideoAtividade) { Text("Vídeo Atividade") }
        }
        ListaEvidenciasEtapa(itens = evidenciasEtapa, onRemover = onRemoverEvidencia)
    }
}