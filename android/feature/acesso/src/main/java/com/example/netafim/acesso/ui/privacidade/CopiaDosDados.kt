package com.example.netafim.acesso.ui.privacidade

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Entrega a cópia dos dados do titular como **arquivo** pela folha de compartilhar do sistema. O texto da Intent
 * (`EXTRA_TEXT`) passa pelo binder e tem limite de tamanho; uma exportação grande, com os atendimentos, poderia
 * estourá-lo. O arquivo fica no cache do app, em uma pasta própria, e é apagado a cada nova cópia e na próxima
 * abertura do app: são dados pessoais e não devem ficar guardados.
 */
object CopiaDosDados {
    private const val PASTA = "compartilhar"

    fun pastaDe(contexto: Context): File = File(contexto.cacheDir, PASTA)

    /** Apaga as cópias anteriores, cria a pasta se preciso e grava o texto em UTF-8. */
    fun gravar(pasta: File, texto: String, nome: String): File {
        pasta.mkdirs()
        limpar(pasta)
        return File(pasta, nome).also { it.writeText(texto, Charsets.UTF_8) }
    }

    fun limpar(pasta: File) {
        pasta.listFiles()?.forEach { it.delete() }
    }

    suspend fun compartilhar(contexto: Context, texto: String, assunto: String, titulo: String) {
        val carimbo = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        val arquivo = withContext(Dispatchers.IO) {
            gravar(pastaDe(contexto), texto, "copia-dos-dados-$carimbo.json")
        }
        val uri = FileProvider.getUriForFile(contexto, "${contexto.packageName}.acesso.fileprovider", arquivo)
        val envio = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, assunto)
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        contexto.startActivity(Intent.createChooser(envio, titulo))
    }
}
