package com.netafim.posvenda.ui.tema

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.netafim.posvenda.R

// PROVISÓRIO: cores aproximadas da marca. Substituir pelos valores do manual de
// identidade visual da Netafim (RNF24/RNF25) assim que o grupo tiver o arquivo.
private val AzulNetafim = Color(0xFF181F4E)
private val AzulClaro = Color(0xFFB7CCF2)
private val AzulDestaque = Color(0xFF3B98FF)
private val VerdeCampo = Color(0xFF96CC2F)
private val VerdeClaro = Color(0xFF9ED8A6)

private val Claro = lightColorScheme(
    primary = AzulNetafim,
    onPrimary = Color.White,
    primaryContainer = AzulClaro,
    secondary = VerdeCampo,
    onSecondary = Color(0xFF21211F),
    secondaryContainer = VerdeClaro,
    tertiary = AzulDestaque,
    onTertiary = Color(0xFF21211F),
    background = Color.White,
    surface = Color.White,
    onSurface = Color(0xFF21211F),
    onSurfaceVariant = Color(0xFF666661),
    surfaceVariant = Color(0xFFE9E9E4),
    outlineVariant = Color(0xFFC9C9C2),
)

private val Escuro = darkColorScheme(
    primary = AzulClaro,
    onPrimary = Color(0xFF00205B),
    secondary = VerdeClaro,
)

// Outfit (SIL OFL, ver android/licencas): a fonte do protótipo. Uma única fonte variável
// cobre os pesos 400, 500 e 600.
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun outfit(peso: FontWeight) = Font(
    resId = R.font.outfit_variavel,
    weight = peso,
    variationSettings = FontVariation.Settings(FontVariation.weight(peso.weight)),
)

val FamiliaOutfit = FontFamily(outfit(FontWeight.Normal), outfit(FontWeight.Medium), outfit(FontWeight.SemiBold))

private fun Typography.comFamilia(familia: FontFamily): Typography {
    fun TextStyle.f() = copy(fontFamily = familia)
    return copy(
        displayLarge = displayLarge.f(), displayMedium = displayMedium.f(), displaySmall = displaySmall.f(),
        headlineLarge = headlineLarge.f(), headlineMedium = headlineMedium.f(), headlineSmall = headlineSmall.f(),
        titleLarge = titleLarge.f(), titleMedium = titleMedium.f(), titleSmall = titleSmall.f(),
        bodyLarge = bodyLarge.f(), bodyMedium = bodyMedium.f(), bodySmall = bodySmall.f(),
        labelLarge = labelLarge.f(), labelMedium = labelMedium.f(), labelSmall = labelSmall.f(),
    )
}

@Composable
fun TemaNetafim(escuro: Boolean = isSystemInDarkTheme(), conteudo: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (escuro) Escuro else Claro,
        typography = Typography().comFamilia(FamiliaOutfit),
        content = conteudo,
    )
}
