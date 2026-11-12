package com.netafim.posvenda.atendimento.ui.fila

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.netafim.posvenda.atendimento.R
import com.netafim.posvenda.atendimento.dominio.FiltroDaFila
import com.netafim.posvenda.atendimento.dominio.ItemDaFila
import com.netafim.posvenda.atendimento.dominio.MotivoAtendimento
import com.netafim.posvenda.atendimento.dominio.StatusAtendimento
import com.netafim.posvenda.atendimento.dominio.filtrarFila
import com.netafim.posvenda.atendimento.ui.comum.BarraDoAtendimento
import com.netafim.posvenda.atendimento.ui.comum.CampoDeBusca
import com.netafim.posvenda.atendimento.ui.comum.LinhaDeFiltros
import com.netafim.posvenda.atendimento.ui.comum.MensagemDeEstadoVazio
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
    // Os textos dos chips são resolvidos aqui, pois a lambda de rótulo não é @Composable.
    val rotulosDosFiltros = FiltroDaFila.entries.associateWith { stringResource(it.rotuloDoFiltro()) }
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
            placeholder = stringResource(R.string.atendimento_fila_busca),
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp),
        )
        LinhaDeFiltros(
            opcoes = FiltroDaFila.entries,
            selecionada = filtro,
            rotulo = { rotulosDosFiltros.getValue(it) },
            aoMudar = aoMudarFiltro,
            modifier = Modifier.padding(top = 12.dp),
        )
        if (itens.isEmpty()) {
            // Uma mensagem para busca ou filtro sem resultado, outra para fila vazia.
            val filtrando = busca.isNotBlank() || filtro != FiltroDaFila.TODOS
            MensagemDeEstadoVazio(
                stringResource(
                    if (filtrando) R.string.atendimento_fila_vazio_busca else R.string.atendimento_fila_vazio,
                ),
            )
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
