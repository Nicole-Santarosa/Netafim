package com.example.netafim.core.modelo

import java.text.Normalizer

// Minúsculas, sem acentos e sem espaços nas pontas: "Ribeirão" e "ribeirao" ficam iguais.
// A forma NFD separa a letra do acento, e o replace tira só as marcas de acento.
fun normalizarParaBusca(texto: String): String =
    Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
