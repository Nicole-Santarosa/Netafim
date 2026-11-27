package com.example.netafim.feature.relatorio.ui.etapas

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.example.netafim.feature.relatorio.model.AssinaturaRegistro
import java.io.File

@Composable
fun AssinaturaEtapa(
    assinaturaEspecialista: AssinaturaRegistro,
    assinaturaCliente: AssinaturaRegistro,
    onExportarAssinaturaEspecialista: (
        tracos: List<List<Pair<Float, Float>>>,
        larguraCanvas: Float,
        alturaCanvas: Float
    ) -> Unit,
    onLimparAssinaturaEspecialista: () -> Unit,
    onExportarAssinaturaCliente: (
        tracos: List<List<Pair<Float, Float>>>,
        larguraCanvas: Float,
        alturaCanvas: Float
    ) -> Unit,
    onLimparAssinaturaCliente: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        BlocoAssinaturaIndependente(
            titulo = "Assinatura do Especialista",
            registro = assinaturaEspecialista,
            onConfirmarTracos = onExportarAssinaturaEspecialista,
            onLimpar = onLimparAssinaturaEspecialista
        )

        BlocoAssinaturaIndependente(
            titulo = "Assinatura do Cliente",
            registro = assinaturaCliente,
            onConfirmarTracos = onExportarAssinaturaCliente,
            onLimpar = onLimparAssinaturaCliente
        )
    }
}

@Composable
private fun BlocoAssinaturaIndependente(
    titulo: String,
    registro: AssinaturaRegistro,
    onConfirmarTracos: (List<List<Pair<Float, Float>>>, Float, Float) -> Unit,
    onLimpar: () -> Unit
) {
    val tracos = remember(registro.caminhoImagemLocal, registro.confirmada) {
        mutableStateListOf<List<Pair<Float, Float>>>()
    }
    var tracoAtual by remember(registro.caminhoImagemLocal, registro.confirmada) {
        mutableStateOf<List<Pair<Float, Float>>>(emptyList())
    }
    var larguraCanvas by remember { mutableFloatStateOf(1f) }
    var alturaCanvas by remember { mutableFloatStateOf(1f) }

    val bitmapSalvo = remember(registro.caminhoImagemLocal) {
        val caminho = registro.caminhoImagemLocal
        if (!caminho.isNullOrBlank()) {
            val file = File(caminho)
            if (file.exists() && file.length() > 0L) {
                BitmapFactory.decodeFile(caminho)?.asImageBitmap()
            } else null
        } else null
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = titulo, style = MaterialTheme.typography.titleSmall)

        if (bitmapSalvo != null && registro.confirmada) {
            Image(
                bitmap = bitmapSalvo,
                contentDescription = "$titulo confirmada",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .background(Color.White, RoundedCornerShape(8.dp))
            )
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .onSizeChanged { size ->
                        larguraCanvas = size.width.toFloat().coerceAtLeast(1f)
                        alturaCanvas = size.height.toFloat().coerceAtLeast(1f)
                    }
                    .pointerInput(titulo) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                tracoAtual = listOf(offset.x to offset.y)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                tracoAtual = tracoAtual + (change.position.x to change.position.y)
                            },
                            onDragEnd = {
                                if (tracoAtual.isNotEmpty()) {
                                    tracos.add(tracoAtual)
                                    tracoAtual = emptyList()
                                }
                            },
                            onDragCancel = {
                                tracoAtual = emptyList()
                            }
                        )
                    }
            ) {
                val todosTracos = tracos + listOfNotNull(tracoAtual.takeIf { it.isNotEmpty() })
                todosTracos.forEach { pontos ->
                    if (pontos.isNotEmpty()) {
                        val path = Path()
                        pontos.forEachIndexed { idx, (x, y) ->
                            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(
                            path = path,
                            color = Color.Black,
                            style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val listaCompleta = tracos.toList() + listOfNotNull(tracoAtual.takeIf { it.isNotEmpty() })
                    onConfirmarTracos(listaCompleta, larguraCanvas, alturaCanvas)
                }
            ) {
                Text("Confirmar assinatura")
            }
            OutlinedButton(
                onClick = {
                    tracos.clear()
                    tracoAtual = emptyList()
                    onLimpar()
                }
            ) {
                Text("Limpar")
            }
        }
    }
}