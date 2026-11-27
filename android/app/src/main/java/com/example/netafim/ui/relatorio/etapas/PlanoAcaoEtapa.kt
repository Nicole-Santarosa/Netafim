package com.example.netafim.ui.relatorio.etapas

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.adicionarAnexoExtra(it.toString()) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Etapa 3: Plano de Ação e Conclusão", style = MaterialTheme.typography.titleLarge, color = Color(0xFF181F4E))

        OutlinedTextField(
            value = planoAcao,
            onValueChange = { viewModel.atualizarPlanoAcao(it) },
            label = { Text("Recomendações e Plano de Ação") },
            modifier = Modifier.fillMaxWidth().height(110.dp)
        )

        OutlinedTextField(
            value = conclusao,
            onValueChange = { viewModel.atualizarConclusaoVisita(it) },
            label = { Text("Conclusão sobre a visita técnica *") },
            modifier = Modifier.fillMaxWidth().height(110.dp)
        )

        Button(
            onClick = { pdfLauncher.launch("application/pdf") },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3698FF))
        ) {
            Text("Selecionar Anexo PDF Real", color = Color(0xFFFFFBEB))
        }

        Text("Anexos PDF anexados: ${anexos.size}", style = MaterialTheme.typography.bodyMedium)
    }
}