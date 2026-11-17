package com.example.netafim.ui.relatorio.etapas

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.netafim.ui.relatorio.RelatorioViewModel

@Composable
fun PlanoAcaoEtapa(viewModel: RelatorioViewModel) {
    val planoAcao by viewModel.planoAcao.collectAsState()
    val conclusao by viewModel.conclusaoVisita.collectAsState()
    val anexos by viewModel.anexosExtras.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Etapa 3: Plano de Ação e Conclusão", style = MaterialTheme.typography.titleLarge, color = Color(0xFF181F4E))

        OutlinedTextField(
            value = planoAcao,
            onValueChange = { viewModel.atualizarPlanoAcao(it) },
            label = { Text("Recomendações / Plano de Ação") },
            modifier = Modifier.fillMaxWidth().height(120.dp)
        )

        OutlinedTextField(
            value = conclusao,
            onValueChange = { viewModel.atualizarConclusaoVisita(it) },
            label = { Text("Conclusão sobre a visita técnica *") },
            modifier = Modifier.fillMaxWidth().height(120.dp)
        )

        Button(
            onClick = { viewModel.adicionarAnexoExtra("documento_exemplo.pdf") },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3698FF))
        ) {
            Text("Adicionar Anexo PDF", color = Color(0xFFFFFBEB))
        }

        Text("Anexos PDF adicionados: ${anexos.size}", style = MaterialTheme.typography.bodyMedium)
    }
}