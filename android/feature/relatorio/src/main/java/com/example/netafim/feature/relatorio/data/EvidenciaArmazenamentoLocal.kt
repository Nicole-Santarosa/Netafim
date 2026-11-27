package com.example.netafim.feature.relatorio.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlin.math.min

class EvidenciaArmazenamentoLocal(private val context: Context) {

    fun diretorioEvidencias(): File =
        File(context.filesDir, "relatorio/evidencias").apply { mkdirs() }

    fun diretorioAssinaturas(): File =
        File(context.filesDir, "relatorio/assinaturas").apply { mkdirs() }

    fun importarPdfParaArmazenamentoPrivado(uri: Uri): Result<File> {
        return try {
            val resolver = context.contentResolver
            val mime = resolver.getType(uri)
            if (mime != null && mime != "application/pdf" && !uri.toString().endsWith(".pdf", ignoreCase = true)) {
                return Result.failure(IllegalArgumentException("Selecione um arquivo PDF válido."))
            }

            val nomeOriginal = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }?.replace(Regex("[^a-zA-Z0-9._-]"), "_") ?: "anexo_${UUID.randomUUID()}.pdf"

            val nomeFinal = if (nomeOriginal.endsWith(".pdf", ignoreCase = true)) nomeOriginal else "$nomeOriginal.pdf"
            val destino = File(diretorioEvidencias(), "${UUID.randomUUID()}_$nomeFinal")

            val input = resolver.openInputStream(uri)
                ?: return Result.failure(IOException("Não foi possível abrir o arquivo PDF selecionado."))

            input.use { entrada ->
                FileOutputStream(destino).use { saida ->
                    val copiados = entrada.copyTo(saida)
                    if (copiados <= 0L) {
                        destino.delete()
                        return Result.failure(IOException("O arquivo PDF selecionado está vazio ou ilegível."))
                    }
                }
            }

            if (!destino.exists() || destino.length() <= 0L) {
                return Result.failure(IOException("Falha ao armazenar o PDF no diretório privado do aplicativo."))
            }

            Result.success(destino)
        } catch (e: Exception) {
            Result.failure(IOException("Erro ao importar anexo PDF: ${e.message ?: "falha de leitura"}", e))
        }
    }

    fun arquivoExisteEValido(caminho: String?): Boolean {
        if (caminho.isNullOrBlank()) return false
        val f = File(caminho)
        return f.exists() && f.isFile && f.length() > 0L
    }

    fun exportarAssinaturaPngProporcional(
        tracos: List<List<Pair<Float, Float>>>,
        larguraCanvas: Float,
        alturaCanvas: Float,
        identificadorAssinante: String,
        larguraBitmap: Int = 900,
        alturaBitmap: Int = 360
    ): Result<String> {
        if (tracos.isEmpty() || tracos.all { it.isEmpty() } || larguraCanvas <= 0f || alturaCanvas <= 0f) {
            return Result.failure(IllegalArgumentException("Assinatura vazia ou dimensões inválidas."))
        }

        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY

        for (traco in tracos) {
            for ((x, y) in traco) {
                if (x < minX) minX = x
                if (y < minY) minY = y
                if (x > maxX) maxX = x
                if (y > maxY) maxY = y
            }
        }

        if (!minX.isFinite() || !minY.isFinite() || !maxX.isFinite() || !maxY.isFinite()) {
            return Result.failure(IllegalArgumentException("Coordenadas da assinatura inválidas."))
        }

        val margemPx = 28f
        val areaUtilLargura = (larguraBitmap - 2f * margemPx).coerceAtLeast(1f)
        val areaUtilAltura = (alturaBitmap - 2f * margemPx).coerceAtLeast(1f)

        val larguraDesenho = (maxX - minX).coerceAtLeast(1f)
        val alturaDesenho = (maxY - minY).coerceAtLeast(1f)

        val escalaUniforme = min(areaUtilLargura / larguraDesenho, areaUtilAltura / alturaDesenho)
        val larguraEscalado = larguraDesenho * escalaUniforme
        val alturaEscalado = alturaDesenho * escalaUniforme

        val offsetX = margemPx + (areaUtilLargura - larguraEscalado) / 2f
        val offsetY = margemPx + (areaUtilAltura - alturaEscalado) / 2f

        return try {
            val bitmap = Bitmap.createBitmap(larguraBitmap, alturaBitmap, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeWidth = (5f * escalaUniforme).coerceIn(3f, 10f)
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            for (traco in tracos) {
                if (traco.isEmpty()) continue
                val path = Path()
                traco.forEachIndexed { index, (px, py) ->
                    val tx = offsetX + (px - minX) * escalaUniforme
                    val ty = offsetY + (py - minY) * escalaUniforme
                    if (index == 0) {
                        path.moveTo(tx, ty)
                    } else {
                        path.lineTo(tx, ty)
                    }
                }
                canvas.drawPath(path, paint)
            }

            val arquivoSaida = File(diretorioAssinaturas(), "assinatura_${identificadorAssinante}_${UUID.randomUUID()}.png")
            FileOutputStream(arquivoSaida).use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    throw IOException("Falha ao comprimir PNG da assinatura.")
                }
            }
            bitmap.recycle()

            if (!arquivoSaida.exists() || arquivoSaida.length() <= 0L) {
                return Result.failure(IOException("Arquivo PNG de assinatura não foi gerado."))
            }

            Result.success(arquivoSaida.absolutePath)
        } catch (e: Exception) {
            Result.failure(IOException("Erro ao salvar assinatura PNG: ${e.message ?: "erro desconhecido"}", e))
        }
    }
}