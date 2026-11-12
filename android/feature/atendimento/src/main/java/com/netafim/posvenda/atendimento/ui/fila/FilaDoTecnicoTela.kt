package com.netafim.posvenda.atendimento.ui.fila

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.netafim.posvenda.atendimento.R
import com.netafim.posvenda.atendimento.dominio.FiltroDaFila
import com.netafim.posvenda.atendimento.dominio.ItemDaFila
import com.netafim.posvenda.atendimento.dominio.MotivoAtendimento
import com.netafim.posvenda.atendimento.dominio.StatusAtendimento
import com.netafim.posvenda.atendimento.dominio.filtrarFila
import com.netafim.posvenda.atendimento.ui.comum.BarraDoAtendimento
import com.netafim.posvenda.atendimento.ui.comum.TemaDePreview
import java.time.Instant

// Fila de atendimentos do técnico. Tela sem estado: os itens chegam já filtrados e ordenados (ver
// filtrarFila) e a tela só desenha; busca e filtro voltam ao chamador pelas lambdas.
@Composable
fun FilaDoTecnicoTela(
    itens: List<ItemDaFila>,
    filtro: FiltroDaFila,
    busca: String,
    aoMudarFiltro: (FiltroDaFila) -> Unit,
    aoMudarBusca: (String) -> Unit,
    aoAbrir: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Sem seta de voltar: é a tela inicial do técnico. Logo e sino são de outra parte do grupo.
        BarraDoAtendimento(titulo = stringResource(R.string.atendimento_fila_titulo))
        CampoDeBusca(
            busca = busca,
            aoMudar = aoMudarBusca,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp),
        )
        LinhaDeFiltros(
            filtroAtual = filtro,
            aoMudar = aoMudarFiltro,
            modifier = Modifier.padding(top = 12.dp),
        )
        if (itens.isEmpty()) {
            EstadoVazio(filtrando = busca.isNotBlank() || filtro != FiltroDaFila.TODOS)
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(items = itens, key = { it.id }) { item ->
                    CartaoDaFila(item = item, aoClicar = { aoAbrir(item.id) })
                }
            }
        }
    }
}

// Campo de busca de 48dp: lupa à esquerda e placeholder. O placeholder também vira o rótulo lido pelo
// leitor de tela. Sem botão de funil: o filtro fica nos chips.
@Composable
private fun CampoDeBusca(busca: String, aoMudar: (String) -> Unit, modifier: Modifier = Modifier) {
    val placeholder = stringResource(R.string.atendimento_fila_busca)
    val forma = RoundedCornerShape(12.dp)
    BasicTextField(
        value = busca,
        onValueChange = aoMudar,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .semantics { contentDescription = placeholder },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        decorationBox = { campo ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(forma)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, forma)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (busca.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    campo()
                }
            }
        },
    )
}

// Chips de filtro numa linha que rola na horizontal. Marcado: fundo primary e texto onPrimary.
// Solto: fundo surface com borda outlineVariant.
@Composable
private fun LinhaDeFiltros(filtroAtual: FiltroDaFila, aoMudar: (FiltroDaFila) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FiltroDaFila.entries.forEach { filtro ->
            FilterChip(
                selected = filtro == filtroAtual,
                onClick = { aoMudar(filtro) },
                label = { Text(stringResource(filtro.rotuloDoFiltro())) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.onSurface,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = filtro == filtroAtual,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedBorderColor = Color.Transparent,
                ),
            )
        }
    }
}

// Mensagem centralizada quando não há itens: uma para busca ou filtro sem resultado, outra para fila vazia.
@Composable
private fun EstadoVazio(filtrando: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(
                if (filtrando) R.string.atendimento_fila_vazio_busca else R.string.atendimento_fila_vazio,
            ),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
}

// Dados fictícios só para os previews.
private val itensDoPreview = listOf(
    ItemDaFila("1", "OS-0506", "Vanderlei Zucchi Rodas", "Arealva, SP", MotivoAtendimento.DIGITAL_FARMING, StatusAtendimento.AGENDADO, Instant.parse("2026-11-08T15:00:00Z")),
    ItemDaFila("2", "OS-0477", "Citrosuco", "Boa Esperança do Sul, SP", MotivoAtendimento.DIGITAL_FARMING, StatusAtendimento.EM_ATENDIMENTO, Instant.parse("2026-11-01T15:00:00Z")),
    ItemDaFila("3", "OS-0512", "Fazenda Santa Rita", "Ribeirão Preto, SP", MotivoAtendimento.POS_VENDA, StatusAtendimento.DESTINADO, Instant.parse("2026-11-10T15:00:00Z")),
)

@Composable
private fun TelaDePreview(filtro: FiltroDaFila = FiltroDaFila.TODOS, busca: String = "") {
    TemaDePreview {
        FilaDoTecnicoTela(
            itens = filtrarFila(itensDoPreview, filtro, busca),
            filtro = filtro,
            busca = busca,
            aoMudarFiltro = {},
            aoMudarBusca = {},
            aoAbrir = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun FilaCompletaPreview() = TelaDePreview()

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun FilaEmAtendimentoPreview() = TelaDePreview(filtro = FiltroDaFila.EM_ATENDIMENTO)

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun FilaComBuscaPreview() = TelaDePreview(busca = "citro")

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun FilaVaziaPreview() {
    TemaDePreview {
        FilaDoTecnicoTela(
            itens = emptyList(),
            filtro = FiltroDaFila.TODOS,
            busca = "",
            aoMudarFiltro = {},
            aoMudarBusca = {},
            aoAbrir = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun FilaVaziaComBuscaPreview() = TelaDePreview(busca = "xyz")
