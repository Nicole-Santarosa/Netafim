package com.netafim.posvenda.acesso.dados

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Foto de perfil guardada só neste aparelho (pasta privada do app, uma por usuário).
 *
 * A API ainda não tem onde receber a foto; quando tiver, o envio entra em [salvar] e a foto passa a
 * seguir a pessoa entre aparelhos. A imagem escolhida é recortada em quadrado e reduzida para
 * [LADO] px, então o arquivo fica pequeno (algumas dezenas de KB) e cabe em qualquer tela.
 */
class FotosDePerfil(private val contexto: Context) {

    private val contador = MutableStateFlow(0)

    /** Muda a cada foto salva ou removida, para as telas recarregarem. */
    val alteracoes: StateFlow<Int> get() = contador

    private fun arquivo(idUsuario: Int) = File(File(contexto.filesDir, "fotos_perfil"), "usuario_$idUsuario.jpg")

    suspend fun carregar(idUsuario: Int): Bitmap? = withContext(Dispatchers.IO) {
        arquivo(idUsuario).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
    }

    /** Lê a imagem escolhida (galeria), recorta no centro e salva. Devolve false se não deu para ler. */
    suspend fun salvar(idUsuario: Int, origem: Uri): Boolean = withContext(Dispatchers.IO) {
        val lida = runCatching { decodificar(origem) }.getOrNull() ?: return@withContext false
        gravar(idUsuario, lida)
    }

    /** Salva uma imagem já em memória (miniatura da câmera). */
    suspend fun salvar(idUsuario: Int, imagem: Bitmap): Boolean = withContext(Dispatchers.IO) {
        gravar(idUsuario, imagem)
    }

    suspend fun remover(idUsuario: Int) = withContext(Dispatchers.IO) {
        arquivo(idUsuario).delete()
        contador.value++
    }

    private fun gravar(idUsuario: Int, imagem: Bitmap): Boolean = runCatching {
        val quadrada = recortarEReduzir(imagem)
        val destino = arquivo(idUsuario)
        destino.parentFile?.mkdirs()
        val temporario = File(destino.parentFile, destino.name + ".tmp")
        temporario.outputStream().use { quadrada.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        if (!temporario.renameTo(destino)) {
            temporario.copyTo(destino, overwrite = true)
            temporario.delete()
        }
        contador.value++
        true
    }.getOrDefault(false)

    private fun decodificar(origem: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // O ImageDecoder já aplica a rotação do EXIF (foto tirada em pé não vem deitada).
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(contexto.contentResolver, origem)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSampleSize(max(1, min(info.size.width, info.size.height) / (LADO * 2)))
            }
        }
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contexto.contentResolver.openInputStream(origem)?.use { BitmapFactory.decodeStream(it, null, limites) }
        val opcoes = BitmapFactory.Options().apply {
            inSampleSize = max(1, min(limites.outWidth, limites.outHeight) / (LADO * 2))
        }
        return contexto.contentResolver.openInputStream(origem)?.use { BitmapFactory.decodeStream(it, null, opcoes) }
    }

    private fun recortarEReduzir(imagem: Bitmap): Bitmap {
        val lado = min(imagem.width, imagem.height)
        val recorte = Bitmap.createBitmap(imagem, (imagem.width - lado) / 2, (imagem.height - lado) / 2, lado, lado)
        return if (lado > LADO) Bitmap.createScaledBitmap(recorte, LADO, LADO, true) else recorte
    }

    private companion object {
        const val LADO = 512
    }
}
