package com.example.netafim.atendimento.ui.fila

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
import com.example.netafim.atendimento.R
import com.example.netafim.atendimento.dominio.FiltroDaFila
import com.example.netafim.atendimento.dominio.comoFiltroDeAtendimentos
import com.example.netafim.atendimento.ui.comum.BarraDoAtendimento
import com.example.netafim.atendimento.ui.comum.CampoDeBusca
import com.example.netafim.atendimento.ui.comum.IndicadorDeCarregando
import com.example.netafim.atendimento.ui.comum.LinhaDeFiltros
import com.example.netafim.atendimento.ui.comum.MensagemDeEstadoVazio
import com.example.netafim.atendimento.ui.comum.TemaDePreview
import com.example.netafim.atendimento.ui.comum.itemDePreview
import com.example.netafim.atendimento.ui.comum.itensDeCadaSincronizacao
import com.example.netafim.atendimento.ui.comum.listaDePreview
import com.example.netafim.atendimento.ui.lista.EstadoDaLista
import com.example.netafim.core.modelo.MotivoAtendimento
import com.example.netafim.core.modelo.StatusAtendimento

// Fila de atendimentos do técnico. Tela sem estado: os itens chegam já filtrados, buscados e ordenados pelo
// repositório (ver comoFiltroDeAtendimentos) e a tela só desenha; busca e filtro voltam ao chamador pelas lambdas.
@Composable
fun FilaDoTecnicoTela(
    estado: EstadoDaLista,
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
        when (estado) {
            // Antes da primeira resposta do repositório: progresso, e não a mensagem de lista vazia.
            EstadoDaLista.Carregando -> IndicadorDeCarregando(modifier = Modifier.weight(1f))
            is EstadoDaLista.Pronta -> if (estado.itens.isEmpty()) {
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
                    items(items = estado.itens, key = { it.id }) { item ->
                        CartaoDaFila(item = item, aoClicar = { aoAbrir(item.id) })
                    }
                }
            }
        }
    }
}

// Dados fictícios só para os previews.
private val itensDoPreview = listOf(
    itemDePreview("1", "OS-0506", MotivoAtendimento.DIGITAL_FARMING, StatusAtendimento.AGENDADO, "2026-11-08T15:00:00Z", "Vanderlei Zucchi Rodas", "Arealva, SP"),
    itemDePreview("2", "OS-0477", MotivoAtendimento.DIGITAL_FARMING, StatusAtendimento.EM_ATENDIMENTO, "2026-11-01T15:00:00Z", "Citrosuco", "Boa Esperança do Sul, SP"),
    itemDePreview("3", "OS-0512", MotivoAtendimento.POS_VENDA, StatusAtendimento.DESTINADO, "2026-11-10T15:00:00Z", "Fazenda Santa Rita", "Ribeirão Preto, SP"),
)

@Composable
private fun TelaDePreview(filtro: FiltroDaFila = FiltroDaFila.TODOS, busca: String = "") {
    TemaDePreview {
        FilaDoTecnicoTela(
            estado = EstadoDaLista.Pronta(listaDePreview(itensDoPreview, filtro.comoFiltroDeAtendimentos(busca))),
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
            estado = EstadoDaLista.Pronta(emptyList()),
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

// Antes da primeira resposta do repositório: progresso no lugar da lista.
@Preview(showBackground = true, heightDp = 780)
@Composable
private fun FilaCarregandoPreview() {
    TemaDePreview {
        FilaDoTecnicoTela(
            estado = EstadoDaLista.Carregando,
            filtro = FiltroDaFila.TODOS,
            busca = "",
            aoMudarFiltro = {},
            aoMudarBusca = {},
            aoAbrir = {},
        )
    }
}

// Um cartão de cada situação de sincronização: sem ícone, aguardando envio, conflito e erro.
@Preview(showBackground = true, heightDp = 780)
@Composable
private fun FilaSincronizacaoPreview() {
    TemaDePreview {
        FilaDoTecnicoTela(
            estado = EstadoDaLista.Pronta(itensDeCadaSincronizacao),
            filtro = FiltroDaFila.TODOS,
            busca = "",
            aoMudarFiltro = {},
            aoMudarBusca = {},
            aoAbrir = {},
        )
    }
}
