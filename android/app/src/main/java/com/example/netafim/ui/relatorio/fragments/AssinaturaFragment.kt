package com.example.netafim.ui.relatorio.fragments

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
fun AssinaturaScreen(viewModel: RelatorioViewModel, onAssinaturaConcluida: (Boolean) -> Unit) {
    val context = LocalContext.current

    // Lista de linhas (cada linha é uma lista de coordenadas Offset)
    val linhas = remember { mutableStateListOf<List<Offset>>() }
    var linhaAtual = remember { mutableStateListOf<Offset>() }
    var temAssinatura by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Título da etapa seguindo a identidade visual da Netafim[cite: 56]
        Text(
            text = "Etapa 4: Assinatura do Relatório",
            style = MaterialTheme.typography.titleLarge,
            color = Color(0xFF181F4E) // Azul Meia-Noite Netafim
        )

        Text(
            text = "Colete a assinatura do técnico responsável e do cliente diretamente na tela abaixo:",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )

        // Área de desenho (Canvas) sensível ao toque[cite: 17, 37]
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .background(Color(0xFFFFFBEB)) // Cor Creme da Netafim
                .pointerInput(true) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            linhaAtual = mutableStateListOf(offset)
                            linhas.add(linhaAtual)
                        },
                        onDrag = { change, _ ->
                            linhaAtual.add(change.position)
                            temAssinatura = true
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                linhas.forEach { linha ->
                    for (i in 0 until linha.size - 1) {
                        drawLine(
                            color = Color(0xFF181F4E), // Traço em Azul Meia-Noite
                            start = linha[i],
                            end = linha[i + 1],
                            strokeWidth = 6f
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Botão para limpar o canvas e refazer a assinatura
            OutlinedButton(
                onClick = {
                    linhas.clear()
                    linhaAtual.clear()
                    temAssinatura = false
                    onAssinaturaConcluida(false)
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Limpar Assinatura")
            }

            // Botão para confirmar, converter em PNG e salvar no ViewModel
            Button(
                onClick = {
                    if (temAssinatura) {
                        // Converte o desenho atual em arquivo PNG físico
                        val arquivoPng = salvarAssinaturaComoPng(context, linhas)
                        if (arquivoPng != null) {
                            // Guarda o caminho absoluto da assinatura no ViewModel
                            viewModel.definirAssinatura(arquivoPng.absolutePath)
                            onAssinaturaConcluida(true)
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF181F4E) // Azul Meia-Noite
                ),
                enabled = temAssinatura
            ) {
                Text("Confirmar Assinatura", color = Color(0xFFFFFBEB))
            }
        }
    }
}

/**
 * Função utilitária que pega os traços da tela, desenha em um Bitmap do Android
 * com o fundo Creme e exporta um arquivo .PNG na pasta Pictures do app.
 */
private fun salvarAssinaturaComoPng(context: Context, linhas: List<List<Offset>>): File? {
    return try {
        val largura = 1000
        val altura = 400
        val bitmap = Bitmap.createBitmap(largura, altura, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)

        // Preenche o fundo do arquivo PNG com a cor Creme (#FFFBEB) da Netafim
        canvas.drawColor(android.graphics.Color.parseColor("#FFFBEB"))

        val paint = Paint().apply {
            color = android.graphics.Color.parseColor("#181F4E") // Azul Meia-Noite
            strokeWidth = 8f
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        // Desenha cada segmento da assinatura no bitmap
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

        // Cria o arquivo físico temporário/permanente no diretório privado do app
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val arquivo = File.createTempFile("ASSINATURA_${timeStamp}_", ".png", storageDir)

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