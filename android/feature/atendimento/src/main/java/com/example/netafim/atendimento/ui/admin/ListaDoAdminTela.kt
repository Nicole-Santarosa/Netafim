package com.example.netafim.atendimento.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.netafim.atendimento.R
import com.example.netafim.atendimento.dominio.FiltroDeOrigem
import com.example.netafim.atendimento.dominio.comoFiltroDeAtendimentos
import com.example.netafim.atendimento.dominio.selo
import com.example.netafim.atendimento.dominio.textoDaLinha
import com.example.netafim.atendimento.ui.comum.BarraDoAtendimento
import com.example.netafim.atendimento.ui.comum.IconeDeSincronizacao
import com.example.netafim.atendimento.ui.comum.IndicadorDeCarregando
import com.example.netafim.atendimento.ui.comum.LinhaDeFiltros
import com.example.netafim.atendimento.ui.comum.MensagemDeEstadoVazio
import com.example.netafim.atendimento.ui.comum.TemaDePreview
import com.example.netafim.atendimento.ui.comum.itemDePreview
import com.example.netafim.atendimento.ui.comum.itensDeCadaSincronizacao
import com.example.netafim.atendimento.ui.comum.listaDePreview
import com.example.netafim.atendimento.ui.comum.rotulo
import com.example.netafim.atendimento.ui.fila.SeloDaFila
import com.example.netafim.atendimento.ui.lista.EstadoDaLista
import com.example.netafim.core.modelo.ItemDeAtendimento
import com.example.netafim.core.modelo.MotivoAtendimento
import com.example.netafim.core.modelo.OrigemAtendimento
import com.example.netafim.core.modelo.StatusAtendimento

// Lista de atendimentos do Admin da Netafim, com filtro por origem. Tela sem estado: os itens chegam já
// filtrados e ordenados pelo repositório (ver comoFiltroDeAtendimentos) e a tela só desenha; o filtro volta
// ao chamador pela lambda. Não há busca nesta tela.
@Composable
fun ListaDoAdminTela(
    estado: EstadoDaLista,
    filtro: FiltroDeOrigem,
    aoMudarFiltro: (FiltroDeOrigem) -> Unit,
    aoAbrir: (String) -> Unit,
    aoVoltar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Os textos dos chips são resolvidos aqui, pois a lambda de rótulo não é @Composable.
    val rotulosDosFiltros = FiltroDeOrigem.entries.associateWith { stringResource(it.rotuloDoFiltro()) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        BarraDoAtendimento(titulo = stringResource(R.string.atendimento_admin_titulo), aoVoltar = aoVoltar)
        // O protótipo desenha chips cinza preenchidos de 28dp; aqui fica o mesmo estilo de chip da fila do
        // técnico e da região, para o app ter um estilo só. Com quatro chips, em 360dp o último ficava
        // cortado na linha que rola; por isso aqui eles quebram de linha e aparecem todos inteiros.
        LinhaDeFiltros(
            opcoes = FiltroDeOrigem.entries,
            selecionada = filtro,
            rotulo = { rotulosDosFiltros.getValue(it) },
            aoMudar = aoMudarFiltro,
            modifier = Modifier.padding(top = 16.dp),
            quebrarLinha = true,
        )
        when (estado) {
            // Antes da primeira resposta do repositório: progresso, e não a mensagem de lista vazia.
            EstadoDaLista.Carregando -> IndicadorDeCarregando(modifier = Modifier.weight(1f))
            is EstadoDaLista.Pronta -> if (estado.itens.isEmpty()) {
                // Uma mensagem para filtro sem resultado, outra para lista realmente vazia.
                MensagemDeEstadoVazio(
                    stringResource(
                        if (filtro != FiltroDeOrigem.TODAS) {
                            R.string.atendimento_admin_vazio_filtro
                        } else {
                            R.string.atendimento_admin_vazio
                        },
                    ),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp),
                ) {
                    items(items = estado.itens, key = { it.id }) { item ->
                        LinhaDoAdmin(item = item, aoClicar = { aoAbrir(item.id) })
                    }
                }
            }
        }
    }
}

// Uma linha da lista: "protocolo · cliente" à esquerda (até 2 linhas) e o selo de status à direita, com
// divisória embaixo. A linha inteira é clicável e é lida como um item só pelo leitor de tela.
// O parâmetro "selo" existe para os selos de outras partes do grupo (editado, atrasado): quando informado, o
// conteúdo passado aparece no lugar do selo de status do atendimento. A sincronização não passa por ele: tem o
// próprio ícone antes do texto.
@Composable
private fun LinhaDoAdmin(
    item: ItemDeAtendimento,
    aoClicar: () -> Unit,
    selo: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .clickable(onClick = aoClicar),
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Indicador de sincronização antes do "protocolo · cliente". O ícone tem 16dp, menos que a linha
            // de texto, então a altura da linha não muda e o selo de status continua no mesmo lugar.
            IconeDeSincronizacao(item.sincronizacao, modifier = Modifier.padding(end = 6.dp))
            Text(
                text = textoDaLinha(item, stringResource(R.string.atendimento_protocolo_pendente)),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(12.dp))
            if (selo != null) {
                selo()
            } else {
                SeloDaFila(item.status, rotuloDoStatus = { it.selo.rotulo() })
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

// A lista do Admin só desenha protocolo, cliente, origem (no filtro) e status; o motivo não aparece, então
// os previews usam um valor fixo para ele.
private fun itemAdminDePreview(
    id: String,
    protocolo: String,
    nomeCliente: String,
    origem: OrigemAtendimento,
    status: StatusAtendimento,
    abertoEm: String,
) = itemDePreview(id, protocolo, MotivoAtendimento.POS_VENDA, status, abertoEm, nomeCliente, origem = origem)

// Dados fictícios só para os previews, com datas fora de ordem para provar a ordenação.
private val itensDoPreview = listOf(
    itemAdminDePreview("1", "OS-0477", "Citrosuco", OrigemAtendimento.DISTRIBUIDOR, StatusAtendimento.EM_ATENDIMENTO, "2026-11-01T15:00:00Z"),
    itemAdminDePreview("2", "OS-0512", "Fazenda Santa Rita", OrigemAtendimento.CLIENTE_FINAL, StatusAtendimento.DESTINADO, "2026-11-10T15:00:00Z"),
    itemAdminDePreview("3", "OS-0430", "Agropecuária Bom Jesus", OrigemAtendimento.NETAFIM, StatusAtendimento.ENCERRADO, "2026-10-20T15:00:00Z"),
    itemAdminDePreview("4", "OS-0498", "Citrosuco", OrigemAtendimento.DISTRIBUIDOR, StatusAtendimento.CANCELADO, "2026-10-28T15:00:00Z"),
    itemAdminDePreview("5", "OS-0506", "Vanderlei Zucchi Rodas", OrigemAtendimento.CLIENTE_FINAL, StatusAtendimento.AGENDADO, "2026-11-08T15:00:00Z"),
)

@Composable
private fun TelaDePreview(
    itens: List<ItemDeAtendimento>? = itensDoPreview,
    filtro: FiltroDeOrigem = FiltroDeOrigem.TODAS,
) {
    TemaDePreview {
        ListaDoAdminTela(
            // Lista nula no preview representa o estado de carregando.
            estado = itens?.let { EstadoDaLista.Pronta(listaDePreview(it, filtro.comoFiltroDeAtendimentos())) }
                ?: EstadoDaLista.Carregando,
            filtro = filtro,
            aoMudarFiltro = {},
            aoAbrir = {},
            aoVoltar = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun ListaDoAdminCompletaPreview() = TelaDePreview()

// Largura de 360dp: os quatro chips não cabem numa linha, então o último passa para a linha de baixo.
@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun ListaDoAdminChipsEm360dpPreview() = TelaDePreview()

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun ListaDoAdminDistribuidorPreview() = TelaDePreview(filtro = FiltroDeOrigem.DISTRIBUIDOR)

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun ListaDoAdminVaziaPreview() = TelaDePreview(itens = emptyList())

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun ListaDoAdminVaziaComFiltroPreview() = TelaDePreview(filtro = FiltroDeOrigem.NETAFIM, itens = emptyList())

// Nome de cliente muito longo, para ver o texto quebrar em duas linhas ao lado do selo.
@Preview(showBackground = true, heightDp = 780)
@Composable
private fun ListaDoAdminNomeLongoPreview() = TelaDePreview(
    itens = listOf(
        itemAdminDePreview(
            "1",
            "OS-0506",
            "Cooperativa Agropecuária dos Produtores Rurais de Citros do Vale do Rio Grande",
            OrigemAtendimento.DISTRIBUIDOR,
            StatusAtendimento.EM_ATENDIMENTO,
            "2026-11-08T15:00:00Z",
        ),
    ),
)

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun ListaDoAdminCarregandoPreview() = TelaDePreview(itens = null)

// Uma linha de cada situação de sincronização: sem ícone, aguardando envio, conflito e erro.
@Preview(showBackground = true, heightDp = 780)
@Composable
private fun ListaDoAdminSincronizacaoPreview() = TelaDePreview(itens = itensDeCadaSincronizacao)
