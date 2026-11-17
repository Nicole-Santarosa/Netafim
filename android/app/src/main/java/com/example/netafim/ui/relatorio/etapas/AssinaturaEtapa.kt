package com.example.netafim.ui.relatorio.etapas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.os.Environment
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.netafim.ui.relatorio.RelatorioViewModel
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AssinaturaEtapa(viewModel: RelatorioViewModel) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text("Etapa 4: Assinaturas Digitais", style = MaterialTheme.typography.titleLarge, color = Color(0xFF181F4E))

        // Assinatura do Cliente
        SecaoAssinatura(
            titulo = "Assinatura do Cliente *",
            onSalvar = { linhas -> salvarAssinaturaPng(context, linhas, "CLIENTE") }
        ) { caminho ->
            viewModel.definirAssinaturaCliente(caminho)
        }

        // Assinatura do Especialista / Técnico
        SecaoAssinatura(
            titulo = "Assinatura do Especialista *",
            onSalvar = { linhas -> salvarAssinaturaPng(context, linhas, "ESPECIALISTA") }
        ) { caminho ->
            viewModel.definirAssinaturaEspecialista(caminho)
        }
    }
}

@Composable
fun SecaoAssinatura(
    titulo: String,
    onSalvar: (List<List<Offset>>) -> File?,
    onCaminhoSalvo: (String) -> Unit
) {
    val linhas = remember { mutableStateListOf<List<Offset>>() }
    var linhaAtual = remember { mutableStateListOf<Offset>() }
    var assinado by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(titulo, style = MaterialTheme.typography.titleMedium, color = Color(0xFF181F4E))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(Color(0xFFFFFBEB))
                .pointerInput(true) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            linhaAtual = mutableStateListOf(offset)
                            linhas.add(linhaAtual)
                        },
                        onDrag = { change, _ ->
                            linhaAtual.add(change.position)
                            assinado = true
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                linhas.forEach { linha ->
                    for (i in 0 until linha.size - 1) {
                        drawLine(
                            color = Color(0xFF181F4E),
                            start = linha[i],
                            end = linha[i + 1],
                            strokeWidth = 6f
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    linhas.clear()
                    linhaAtual.clear()
                    assinado = false
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Limpar")
            }

            Button(
                onClick = {
                    if (assinado) {
                        val arquivo = onSalvar(linhas)
                        if (arquivo != null) {
                            onCaminhoSalvo(arquivo.absolutePath)
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF181F4E)),
                enabled = assinado
            ) {
                Text("Salvar Assinatura", color = Color(0xFFFFFBEB))
            }
        }
    }
}

private fun salvarAssinaturaPng(context: Context, linhas: List<List<Offset>>, tipo: String): File? {
    return try {
        val largura = 1000
        val altura = 400
        val bitmap = Bitmap.createBitmap(largura, altura, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)
        canvas.drawColor(android.graphics.Color.parseColor("#FFFBEB"))

        val paint = Paint().apply {
            color = android.graphics.Color.parseColor("#181F4E")
            strokeWidth = 8f
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        linhas.forEach { linha ->
            if (linha.size > 1) {
                val path = AndroidPath().apply {
                    moveTo(linha[0].x, linha[0].y)
                    for (i in 1 until linha.size) {
                        lineTo(linha[i].x, linha[i].y)
                    }
                }
                canvas.drawPath(path, paint)
            }
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val arquivo = File.createTempFile("ASSINATURA_${tipo}_${timeStamp}_", ".png", dir)

        val fos = FileOutputStream(arquivo)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        fos.flush()
        fos.close()

        arquivo
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}