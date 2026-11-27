package com.example.netafim.ui.relatorio.etapas

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
fun ConstatacoesEtapa(viewModel: RelatorioViewModel) {
    val context = LocalContext.current
    val constatacoes by viewModel.constatacoes.collectAsState()
    val fotos by viewModel.fotosConstatacao.collectAsState()
    val videos by viewModel.videosAtividade.collectAsState()

    var uriFotoTemp by remember { mutableStateOf<Uri?>(null) }
    var uriVideoTemp by remember { mutableStateOf<Uri?>(null) }

    val fotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) uriFotoTemp?.let { viewModel.adicionarFotoConstatacao(it.toString()) }
    }

    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
        if (success) uriVideoTemp?.let { viewModel.adicionarVideoAtividade(it.toString()) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Etapa 1: Constatações e Evidências", style = MaterialTheme.typography.titleLarge, color = Color(0xFF181F4E))

        OutlinedTextField(
            value = constatacoes,
            onValueChange = { viewModel.atualizarConstatacoes(it) },
            label = { Text("Descreva as constatações de campo *") },
            modifier = Modifier.fillMaxWidth().height(150.dp)
        )

        Button(
            onClick = {
                val file = criarArquivo(context, "jpg")
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                uriFotoTemp = uri
                fotoLauncher.launch(uri)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3698FF))
        ) {
            Text("Capturar Foto HD", color = Color(0xFFFFFBEB))
        }

        Button(
            onClick = {
                val file = criarArquivo(context, "mp4")
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                uriVideoTemp = uri
                videoLauncher.launch(uri)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3698FF))
        ) {
            Text("Gravar Vídeo de Vistoria", color = Color(0xFFFFFBEB))
        }

        Text("Fotos: ${fotos.size} | Vídeos: ${videos.size}", style = MaterialTheme.typography.bodyMedium)
    }
}

private fun criarArquivo(context: Context, ext: String): File {
    val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    return File.createTempFile("MEDIA_${time}_", ".$ext", dir)
}