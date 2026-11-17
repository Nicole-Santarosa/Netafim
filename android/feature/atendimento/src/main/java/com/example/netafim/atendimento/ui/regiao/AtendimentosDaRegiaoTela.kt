package com.example.netafim.atendimento.ui.regiao

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
import com.example.netafim.atendimento.dominio.FiltroDaRegiao
import com.example.netafim.atendimento.dominio.comoFiltroDeAtendimentos
import com.example.netafim.atendimento.dominio.selo
import com.example.netafim.atendimento.ui.comum.BarraDoAtendimento
import com.example.netafim.atendimento.ui.comum.CampoDeBusca
import com.example.netafim.atendimento.ui.comum.IndicadorDeCarregando
import com.example.netafim.atendimento.ui.comum.LinhaDeFiltros
import com.example.netafim.atendimento.ui.comum.MensagemDeContaSemEmpresa
import com.example.netafim.atendimento.ui.comum.MensagemDeEstadoVazio
import com.example.netafim.atendimento.ui.comum.TemaDePreview
import com.example.netafim.atendimento.ui.comum.itemDePreview
import com.example.netafim.atendimento.ui.comum.itensDeCadaSincronizacao
import com.example.netafim.atendimento.ui.comum.listaDePreview
import com.example.netafim.atendimento.ui.comum.rotulo
import com.example.netafim.atendimento.ui.fila.CartaoDaFila
import com.example.netafim.atendimento.ui.lista.EstadoDaLista
import com.example.netafim.core.modelo.ItemDeAtendimento
import com.example.netafim.core.modelo.MotivoAtendimento
import com.example.netafim.core.modelo.StatusAtendimento

// Atendimentos da região do distribuidor. Mesmo desenho da fila do técnico, com filtros próprios e
// atendimentos de qualquer status. Tela sem estado: os itens chegam já filtrados, buscados e ordenados pelo
// repositório (ver comoFiltroDeAtendimentos) e a tela só desenha; busca e filtro voltam ao chamador pelas lambdas.
@Composable
fun AtendimentosDaRegiaoTela(
    estado: EstadoDaLista,
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
        when (estado) {
            // Antes da primeira resposta do repositório: progresso, e não a mensagem de lista vazia.
            EstadoDaLista.Carregando -> IndicadorDeCarregando(modifier = Modifier.weight(1f))
            // Conta sem empresa: no lugar da lista, a explicação. A busca e os chips continuam desenhados, mas
            // não mudam nada, porque o repositório nem é consultado nesse estado.
            EstadoDaLista.SemOrganizacao -> MensagemDeContaSemEmpresa(modifier = Modifier.weight(1f))
            is EstadoDaLista.Pronta -> if (estado.itens.isEmpty()) {
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
                    items(items = estado.itens, key = { it.id }) { item ->
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
}

// Dados fictícios só para os previews, com datas fora de ordem para provar a ordenação.
private val itensDoPreview = listOf(
    itemDePreview(
        "1",
        "OS-0477",
        MotivoAtendimento.DIGITAL_FARMING,
        StatusAtendimento.EM_ATENDIMENTO,
        "2026-11-01T15:00:00Z",
        "Citrosuco",
        "Boa Esperança do Sul, SP",
    ),
    itemDePreview(
        "2",
        "OS-0512",
        MotivoAtendimento.POS_VENDA,
        StatusAtendimento.DESTINADO,
        "2026-11-10T15:00:00Z",
        "Fazenda Santa Rita",
        "Ribeirão Preto, SP",
    ),
    itemDePreview(
        "3",
        "OS-0430",
        MotivoAtendimento.ENTREGA_TECNICA,
        StatusAtendimento.ENCERRADO,
        "2026-10-20T15:00:00Z",
        "Agrícola Boa Vista",
        "Araraquara, SP",
    ),
    itemDePreview(
        "4",
        "OS-0498",
        MotivoAtendimento.DUVIDAS,
        StatusAtendimento.CANCELADO,
        "2026-10-28T15:00:00Z",
        "Sítio Três Irmãos",
    ),
    itemDePreview(
        "5",
        "OS-0506",
        MotivoAtendimento.DIGITAL_FARMING,
        StatusAtendimento.AGENDADO,
        "2026-11-08T15:00:00Z",
        "Vanderlei Zucchi Rodas",
        "Arealva, SP",
    ),
)

@Composable
private fun TelaDePreview(
    itens: List<ItemDeAtendimento>? = itensDoPreview,
    filtro: FiltroDaRegiao = FiltroDaRegiao.TODOS,
    busca: String = "",
) {
    TemaDePreview {
        AtendimentosDaRegiaoTela(
            // Lista nula no preview representa o estado de carregando.
            estado = itens?.let { EstadoDaLista.Pronta(listaDePreview(it, filtro.comoFiltroDeAtendimentos(busca))) }
                ?: EstadoDaLista.Carregando,
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

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun RegiaoCarregandoPreview() = TelaDePreview(itens = null)

// Um cartão de cada situação de sincronização: sem ícone, aguardando envio, conflito e erro.
@Preview(showBackground = true, heightDp = 780)
@Composable
private fun RegiaoSincronizacaoPreview() = TelaDePreview(itens = itensDeCadaSincronizacao)

// Distribuidor com a conta ainda sem empresa: a explicação no lugar da lista.
@Preview(showBackground = true, heightDp = 780)
@Composable
private fun RegiaoContaSemEmpresaPreview() {
    TemaDePreview {
        AtendimentosDaRegiaoTela(
            estado = EstadoDaLista.SemOrganizacao,
            filtro = FiltroDaRegiao.TODOS,
            busca = "",
            aoMudarFiltro = {},
            aoMudarBusca = {},
            aoAbrir = {},
        )
    }
}
