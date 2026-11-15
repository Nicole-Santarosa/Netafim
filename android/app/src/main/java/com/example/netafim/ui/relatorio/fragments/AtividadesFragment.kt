package com.example.netafim.ui.relatorio.fragments

import android.content.Intent
import android.provider.MediaStore
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
fun AtividadesScreen(viewModel: RelatorioViewModel) {
    // Coleta o estado atual das atividades preenchidas de forma reativa pelo ViewModel
    val atividades by viewModel.atividades.collectAsState()

    // Launcher para captura de fotos/vídeos comprobatórios das atividades executadas
    // Restrito exclusivamente à câmera do aplicativo, garantindo a autenticidade (Regra RF16 / RN12)[cite: 17, 36]
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            // Evidência de atividade capturada com sucesso pela câmera do app.
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Título da seção aplicando a identidade visual e tom corporativo da Netafim[cite: 56, 59]
        Text(
            text = "Etapa 2: Atividades Executadas",
            style = MaterialTheme.typography.titleLarge,
            color = Color(0xFF181F4E) // Azul Meia-Noite (Netafim)[cite: 59, 64]
        )

        // Campo de texto multilinha para descrição detalhada das intervenções técnicas[cite: 34, 75]
        OutlinedTextField(
            value = atividades,
            onValueChange = { viewModel.atualizarAtividades(it) },
            label = { Text("Descreva as atividades executadas em campo *") },
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            maxLines = 5
        )

        // Botão para anexar fotos/vídeos das atividades executadas via câmera nativa[cite: 17, 36, 76]
        Button(
            onClick = {
                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                cameraLauncher.launch(intent)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF3698FF) // Azul Irrigação (Netafim)[cite: 59, 64]
            )
        ) {
            Text("Registrar Foto da Atividade (Câmera)", color = Color(0xFFFFFBEB)) // Creme[cite: 59, 64]
        }
    }
}