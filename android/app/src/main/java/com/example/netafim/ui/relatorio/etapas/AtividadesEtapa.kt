package com.example.netafim.ui.relatorio.etapas

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.netafim.ui.relatorio.RelatorioViewModel

@Composable
fun AtividadesEtapa(viewModel: RelatorioViewModel) {
    val atividades by viewModel.atividades.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Etapa 2: Atividades Realizadas", style = MaterialTheme.typography.titleLarge, color = Color(0xFF181F4E))

        OutlinedTextField(
            value = atividades,
            onValueChange = { viewModel.atualizarAtividades(it) },
            label = { Text("Descreva os serviços e intervenções executados *") },
            modifier = Modifier.fillMaxWidth().height(200.dp),
            maxLines = 8
        )
    }
}