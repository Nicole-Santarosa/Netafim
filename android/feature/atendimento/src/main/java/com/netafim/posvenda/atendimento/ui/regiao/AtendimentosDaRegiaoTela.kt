package com.netafim.posvenda.atendimento.ui.regiao

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
import com.netafim.posvenda.atendimento.dominio.FiltroDaRegiao
import com.netafim.posvenda.atendimento.dominio.ItemDaFila
import com.netafim.posvenda.atendimento.dominio.MotivoAtendimento
import com.netafim.posvenda.atendimento.dominio.StatusAtendimento
import com.netafim.posvenda.atendimento.dominio.filtrarRegiao
import com.netafim.posvenda.atendimento.ui.comum.BarraDoAtendimento
import com.netafim.posvenda.atendimento.ui.comum.CampoDeBusca
import com.netafim.posvenda.atendimento.ui.comum.LinhaDeFiltros
import com.netafim.posvenda.atendimento.ui.comum.MensagemDeEstadoVazio
import com.netafim.posvenda.atendimento.ui.comum.TemaDePreview
import com.netafim.posvenda.atendimento.ui.comum.rotulo
import com.netafim.posvenda.atendimento.ui.fila.CartaoDaFila
import java.time.Instant

// Atendimentos da região do distribuidor. Mesmo desenho da fila do técnico, com filtros próprios e
// atendimentos de qualquer status. Tela sem estado: os itens chegam já filtrados e ordenados (ver
// filtrarRegiao) e a tela só desenha; busca e filtro voltam ao chamador pelas lambdas.
@Composable
fun AtendimentosDaRegiaoTela(
    itens: List<ItemDaFila>,
    filtro: FiltroDaRegiao,
    busca: String,
    aoMudarFiltro: (FiltroDaRegiao) -> Unit,
    aoMudarBusca: (String) -> Unit,
    aoAbrir: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Os textos dos chips são resolvidos aqui, pois a lambda de rótulo não é @Composable.
    val rotulosDosFiltros = FiltroDaRegiao.entries.associateWith { stringResource(it.rotuloDoFiltro()) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Sem seta de voltar: é a tela inicial do distribuidor.
        BarraDoAtendimento(titulo = stringResource(R.string.atendimento_regiao_titulo))
        CampoDeBusca(
            busca = busca,
            aoMudar = aoMudarBusca,
            placeholder = stringResource(R.string.atendimento_regiao_busca),
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp),
        )
        LinhaDeFiltros(
            opcoes = FiltroDaRegiao.entries,
            selecionada = filtro,
            rotulo = { rotulosDosFiltros.getValue(it) },
            aoMudar = aoMudarFiltro,
            modifier = Modifier.padding(top = 12.dp),
        )
        if (itens.isEmpty()) {
            // Uma mensagem para busca ou filtro sem resultado, outra para região sem atendimentos.
            val filtrando = busca.isNotBlank() || filtro != FiltroDaRegiao.TODOS
            MensagemDeEstadoVazio(
                stringResource(
                    if (filtrando) R.string.atendimento_regiao_vazio_busca else R.string.atendimento_regiao_vazio,
                ),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(items = itens, key = { it.id }) { item ->
                    // O selo mostra o rótulo normal do status (Aberto, Em atendimento, Concluído,
                    // Cancelado) e não o "Novo" da fila do técnico.
                    CartaoDaFila(
                        item = item,
                        aoClicar = { aoAbrir(item.id) },
                        rotuloDoStatus = { status -> status.selo.rotulo() },
                    )
                }
            }
        }
    }
}

// Dados fictícios só para os previews, com datas fora de ordem para provar a ordenação.
private val itensDoPreview = listOf(
    ItemDaFila("1", "OS-0477", "Citrosuco", "Boa Esperança do Sul, SP", MotivoAtendimento.DIGITAL_FARMING, StatusAtendimento.EM_ATENDIMENTO, Instant.parse("2026-11-01T15:00:00Z")),
    ItemDaFila("2", "OS-0512", "Fazenda Santa Rita", "Ribeirão Preto, SP", MotivoAtendimento.POS_VENDA, StatusAtendimento.DESTINADO, Instant.parse("2026-11-10T15:00:00Z")),
    ItemDaFila("3", "OS-0430", "Agrícola Boa Vista", "Araraquara, SP", MotivoAtendimento.ENTREGA_TECNICA, StatusAtendimento.ENCERRADO, Instant.parse("2026-10-20T15:00:00Z")),
    ItemDaFila("4", "OS-0498", "Sítio Três Irmãos", null, MotivoAtendimento.DUVIDAS, StatusAtendimento.CANCELADO, Instant.parse("2026-10-28T15:00:00Z")),
    ItemDaFila("5", "OS-0506", "Vanderlei Zucchi Rodas", "Arealva, SP", MotivoAtendimento.DIGITAL_FARMING, StatusAtendimento.AGENDADO, Instant.parse("2026-11-08T15:00:00Z")),
)

@Composable
private fun TelaDePreview(
    itens: List<ItemDaFila> = itensDoPreview,
    filtro: FiltroDaRegiao = FiltroDaRegiao.TODOS,
    busca: String = "",
) {
    TemaDePreview {
        AtendimentosDaRegiaoTela(
            itens = filtrarRegiao(itens, filtro, busca),
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
private fun RegiaoCompletaPreview() = TelaDePreview()

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun RegiaoConcluidosPreview() = TelaDePreview(filtro = FiltroDaRegiao.CONCLUIDOS)

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun RegiaoComBuscaPreview() = TelaDePreview(busca = "citro")

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun RegiaoVaziaPreview() = TelaDePreview(itens = emptyList())

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun RegiaoVaziaComBuscaPreview() = TelaDePreview(busca = "xyz")
