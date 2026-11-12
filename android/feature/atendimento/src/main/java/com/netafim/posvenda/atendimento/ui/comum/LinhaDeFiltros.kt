package com.netafim.posvenda.atendimento.ui.comum

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Chips de filtro numa linha que rola na horizontal. Marcado: fundo primary e texto onPrimary.
// Solto: fundo surface com borda outlineVariant. É genérica no tipo da opção, para servir ao filtro da
// fila do técnico e ao da região do distribuidor; o texto de cada chip vem de rotulo.
@Composable
fun <T> LinhaDeFiltros(
    opcoes: List<T>,
    selecionada: T,
    rotulo: (T) -> String,
    aoMudar: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        opcoes.forEach { opcao ->
            FilterChip(
                selected = opcao == selecionada,
                onClick = { aoMudar(opcao) },
                label = { Text(rotulo(opcao)) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.onSurface,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = opcao == selecionada,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedBorderColor = Color.Transparent,
                ),
            )
        }
    }
}
