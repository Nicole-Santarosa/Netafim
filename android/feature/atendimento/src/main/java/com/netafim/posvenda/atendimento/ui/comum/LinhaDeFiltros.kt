package com.netafim.posvenda.atendimento.ui.comum

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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

// Chips de filtro. Por padrão ficam numa linha que rola na horizontal (fila do técnico e região do
// distribuidor, que têm poucos chips). Com quebrarLinha ligado os chips passam para a linha de baixo
// quando não cabem (lista do Admin, que tem quatro e em 360dp cortava o último), e nada precisa rolar.
// Marcado: fundo primary e texto onPrimary. Solto: fundo surface com borda outlineVariant. É genérica
// no tipo da opção; o texto de cada chip vem de rotulo.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> LinhaDeFiltros(
    opcoes: List<T>,
    selecionada: T,
    rotulo: (T) -> String,
    aoMudar: (T) -> Unit,
    modifier: Modifier = Modifier,
    quebrarLinha: Boolean = false,
) {
    // Os chips são os mesmos nos dois modos; só muda o contêiner.
    val chips: @Composable () -> Unit = {
        opcoes.forEach { opcao ->
            ChipDoFiltro(
                texto = rotulo(opcao),
                selecionado = opcao == selecionada,
                aoClicar = { aoMudar(opcao) },
            )
        }
    }
    if (quebrarLinha) {
        // O chip do Material já reserva 48dp de altura de toque, então as linhas quebradas ficam com
        // folga entre si sem espaçamento vertical extra.
        FlowRow(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) { chips() }
    } else {
        Row(
            modifier = modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) { chips() }
    }
}

// Um chip de filtro, com as cores e a borda do app.
@Composable
private fun ChipDoFiltro(texto: String, selecionado: Boolean, aoClicar: () -> Unit) {
    FilterChip(
        selected = selecionado,
        onClick = aoClicar,
        label = { Text(texto) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selecionado,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = Color.Transparent,
        ),
    )
}
