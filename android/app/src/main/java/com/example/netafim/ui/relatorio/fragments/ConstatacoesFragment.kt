package com.example.netafim.ui.relatorio.fragments

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.netafim.ui.relatorio.RelatorioViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ConstatacoesScreen(viewModel: RelatorioViewModel) {
    val context = LocalContext.current
    val constatacoes by viewModel.constatacoes.collectAsState()
    val fotos by viewModel.fotosConstatacao.collectAsState()

    // Armazena temporariamente o Uri onde a foto de alta resolução será gravada pela câmera
    var fotoUriTemp by remember { mutableStateOf<Uri?>(null) }

    // Launcher utilizando o contrato TakePicture para alta resolução (Regra RF16 / RN12)
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            fotoUriTemp?.let { uri ->
                // Salva o caminho/URI do arquivo de alta resolução no ViewModel
                viewModel.adicionarFotoConstatacao(uri.toString())
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Título da etapa seguindo a identidade visual da Netafim
        Text(
            text = "Etapa 1: Principais Constatações",
            style = MaterialTheme.typography.titleLarge,
            color = Color(0xFF181F4E) // Azul Meia-Noite Netafim
        )

        // Campo de texto para as constatações descritivas
        OutlinedTextField(
            value = constatacoes,
            onValueChange = { viewModel.atualizarConstatacoes(it) },
            label = { Text("Descreva as constatações de campo *") },
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            maxLines = 5
        )

        // Botão para disparar a câmera nativa salvando em alta resolução
        Button(
            onClick = {
                // 1. Cria o arquivo temporário no diretório seguro do app
                val fotoFile = criarArquivoTemporario(context)

                // 2. Converte o arquivo em um Uri seguro através do FileProvider configurado
                val authority = "${context.packageName}.fileprovider"
                val uri = FileProvider.getUriForFile(context, authority, fotoFile)
                fotoUriTemp = uri

                // 3. Executa a intent da câmera passando o Uri de destino (alta resolução)
                takePictureLauncher.launch(uri)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF3698FF) // Azul Irrigação Netafim
            )
        ) {
            Text("Tirar Foto em Alta Resolução (Câmera)", color = Color(0xFFFFFBEB)) // Creme
        }

        // Contador de evidências fotográficas capturadas
        Text(
            text = "Evidências fotográficas capturadas: ${fotos.size}",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
    }
}

/**
 * Função auxiliar responsável por criar um arquivo JPEG temporário exclusivo
 * na pasta de imagens externas privadas do aplicativo.
 */
private fun criarArquivoTemporario(context: Context): File {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    return File.createTempFile(
        "JPEG_${timeStamp}_",
        ".jpg",
        storageDir
    )
}