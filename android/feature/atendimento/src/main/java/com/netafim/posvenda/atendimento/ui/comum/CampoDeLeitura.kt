package com.netafim.posvenda.atendimento.ui.comum

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Um campo de leitura: rótulo, valor e divisória. Rótulo e valor são lidos juntos, como um item só,
// pelo leitor de tela. A divisória fica fora do agrupamento, pois não tem nada para ser lido.
// espacoDepoisDaDivisoria muda de uma tela para outra (14dp no técnico, 12dp no distribuidor).
@Composable
fun CampoDeLeitura(rotulo: String, valor: String, espacoDepoisDaDivisoria: Dp = 14.dp) {
    Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Text(
            text = rotulo,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = valor,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
    }
    Spacer(Modifier.height(12.dp))
    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(espacoDepoisDaDivisoria))
}
