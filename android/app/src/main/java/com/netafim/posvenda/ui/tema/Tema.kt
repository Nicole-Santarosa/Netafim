package com.netafim.posvenda.ui.tema

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// PROVISÓRIO: cores aproximadas da marca. Substituir pelos valores do manual de
// identidade visual da Netafim (RNF24/RNF25) assim que o grupo tiver o arquivo.
private val AzulNetafim = Color(0xFF0B3D91)
private val AzulClaro = Color(0xFFB7CCF2)
private val VerdeCampo = Color(0xFF2E8B3A)
private val VerdeClaro = Color(0xFF9ED8A6)

private val Claro = lightColorScheme(
    primary = AzulNetafim,
    onPrimary = Color.White,
    primaryContainer = AzulClaro,
    secondary = VerdeCampo,
    onSecondary = Color.White,
    secondaryContainer = VerdeClaro,
)

private val Escuro = darkColorScheme(
    primary = AzulClaro,
    onPrimary = Color(0xFF00205B),
    secondary = VerdeClaro,
)

@Composable
fun TemaNetafim(escuro: Boolean = isSystemInDarkTheme(), conteudo: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (escuro) Escuro else Claro, content = conteudo)
}
