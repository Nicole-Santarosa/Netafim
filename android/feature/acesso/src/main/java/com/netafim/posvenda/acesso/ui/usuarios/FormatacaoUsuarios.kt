package com.netafim.posvenda.acesso.ui.usuarios

import com.netafim.posvenda.acesso.dominio.Usuario
import java.text.Normalizer

/** Busca por nome ou e-mail, sem diferenciar maiúsculas nem acentos ("jose" acha "José"). */
internal fun List<Usuario>.filtrarPor(busca: String): List<Usuario> {
    val termo = busca.trim().semAcento()
    if (termo.isEmpty()) return this
    return filter { it.nome.semAcento().contains(termo) || it.email.semAcento().contains(termo) }
}

internal fun String.semAcento(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").lowercase()

/** Primeira letra do primeiro e do último nome ("Ana Paula Reis" vira "AR"). */
internal fun iniciaisDe(nome: String): String {
    val letras = nome.split(Regex("\\s+")).mapNotNull { p -> p.firstOrNull { it.isLetterOrDigit() } }
    return when (letras.size) {
        0 -> "?"
        1 -> letras[0].uppercase()
        else -> "${letras.first()}${letras.last()}".uppercase()
    }
}
