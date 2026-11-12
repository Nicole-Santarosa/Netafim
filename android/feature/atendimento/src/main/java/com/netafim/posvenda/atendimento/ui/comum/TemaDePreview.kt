package com.netafim.posvenda.atendimento.ui.comum

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Tema usado só nos previews do módulo. O TemaNetafim fica no :app e este módulo não o enxerga,
// então aqui as cores principais do protótipo são repetidas para o preview parecer com o app.
// Em tempo de execução as telas usam o MaterialTheme que vier do app; este tema não é aplicado lá.
@Composable
internal fun TemaDePreview(conteudo: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF181F4E),
            onPrimary = Color.White,
            surface = Color.White,
            surfaceVariant = Color(0xFFE9E9E4),
            onSurface = Color(0xFF21211F),
            outlineVariant = Color(0xFFC9C9C2),
        ),
        content = conteudo,
    )
}
