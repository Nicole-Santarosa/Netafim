package com.example.netafim.ui.relatorio.fragments

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.netafim.ui.relatorio.RelatorioViewModel

@Composable
fun PlanoAcaoScreen(viewModel: RelatorioViewModel) {
    // Coleta o estado atual do plano de ação de forma reativa pelo ViewModel
    val planoAcao by viewModel.planoAcao.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Título da seção aplicando a identidade visual e tom corporativo da Netafim[cite: 56, 59]
        Text(
            text = "Etapa 3: Plano de Ação",
            style = MaterialTheme.typography.titleLarge,
            color = Color(0xFF181F4E) // Azul Meia-Noite (Netafim)[cite: 59, 64]
        )

        // Campo de texto multilinha para detalhar as ações recomendadas, substituição de peças e prazos[cite: 34, 77]
        OutlinedTextField(
            value = planoAcao,
            onValueChange = { viewModel.atualizarPlanoAcao(it) },
            label = { Text("Descreva o plano de ação / recomendações *") },
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            maxLines = 8
        )

        // Mensagem de instrução para orientar o operador em campo
        Text(
            text = "Informe claramente os itens de reposição necessários e as próximas intervenções técnicas da Netafim.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}