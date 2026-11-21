package com.netafim.posvenda.acesso.ui.privacidade

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netafim.posvenda.acesso.R
import com.netafim.posvenda.acesso.dominio.PedidoAdmin
import com.netafim.posvenda.acesso.dominio.StatusPedido
import com.netafim.posvenda.acesso.dominio.TipoPedido
import com.netafim.posvenda.acesso.ui.comum.BotaoContorno
import com.netafim.posvenda.acesso.ui.comum.DetalheDeEstado
import com.netafim.posvenda.acesso.ui.comum.MensagemErro
import com.netafim.posvenda.acesso.ui.comum.PaginaComCabecalho
import com.netafim.posvenda.acesso.ui.comum.TextoApoio
import com.netafim.posvenda.acesso.ui.comum.esc
import com.netafim.posvenda.acesso.ui.comum.estilo

@Composable
fun FilaPrivacidadeRota(viewModel: FilaPrivacidadeViewModel, aoAbrirPedido: (Int) -> Unit) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.aoRetomar() }
    FilaPrivacidadeTela(
        estado = estado,
        aoEscolherFiltro = viewModel::escolherFiltro,
        aoRecarregar = viewModel::carregar,
        aoAbrir = aoAbrirPedido,
    )
}

/**
 * Fila de pedidos de privacidade dos titulares (LGPD), só para o Netafim Admin. Não existe no Figma; usa a base
 * do protótipo (barra azul com o título e cartões de bordas C9C9C2 e raio 8, como a Gestão de usuários).
 */
@Composable
fun FilaPrivacidadeTela(
    estado: FilaPrivacidadeEstado,
    aoEscolherFiltro: (FiltroFila) -> Unit,
    aoRecarregar: () -> Unit,
    aoAbrir: (Int) -> Unit,
) {
    PaginaComCabecalho(stringResource(R.string.fila_titulo)) {
        TextoApoio(stringResource(R.string.fila_explicacao))
        Spacer(Modifier.height(esc(16)))
        when {
            estado.carregando && estado.pedidos.isEmpty() -> Box(Modifier.fillMaxWidth().padding(top = esc(24)), Alignment.Center) {
                CircularProgressIndicator()
            }
            estado.erro != null && estado.pedidos.isEmpty() -> {
                MensagemErro(estado.erro)
                Spacer(Modifier.height(esc(20)))
                BotaoContorno(stringResource(R.string.acesso_tentar_novamente), aoRecarregar)
            }
            else -> {
                if (estado.atrasados > 0) {
                    val recursos = LocalContext.current.resources
                    MensagemErro(recursos.getQuantityString(R.plurals.fila_atrasados, estado.atrasados, estado.atrasados))
                    Spacer(Modifier.height(esc(12)))
                }
                Filtros(estado.filtro, aoEscolherFiltro)
                Spacer(Modifier.height(esc(12)))
                if (estado.visiveis.isEmpty()) {
                    DetalheDeEstado(stringResource(R.string.fila_vazio))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(esc(8))) {
                        estado.visiveis.forEach { CartaoDoPedidoAdmin(it) { aoAbrir(it.id) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun Filtros(atual: FiltroFila, aoEscolher: (FiltroFila) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(esc(6)), modifier = Modifier.fillMaxWidth()) {
        FiltroFila.entries.forEach { filtro ->
            val selecionado = filtro == atual
            val cores = MaterialTheme.colorScheme
            val forma = RoundedCornerShape(esc(8))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(esc(36))
                    .background(if (selecionado) cores.primary else cores.surfaceVariant, forma)
                    .border(1.dp, if (selecionado) cores.primary else cores.outlineVariant, forma)
                    .clickable(role = Role.RadioButton) { aoEscolher(filtro) },
            ) {
                Text(
                    stringResource(textoDoFiltro(filtro)),
                    color = if (selecionado) cores.onPrimary else cores.onSurface,
                    textAlign = TextAlign.Center,
                    style = estilo(12, FontWeight.Medium),
                )
            }
        }
    }
}

private fun textoDoFiltro(filtro: FiltroFila): Int = when (filtro) {
    FiltroFila.ABERTOS -> R.string.fila_filtro_abertos
    FiltroFila.ATENDIDOS -> R.string.fila_filtro_atendidos
    FiltroFila.NEGADOS -> R.string.fila_filtro_negados
    FiltroFila.TODOS -> R.string.fila_filtro_todos
}

@Composable
private fun CartaoDoPedidoAdmin(pedido: PedidoAdmin, aoClicar: () -> Unit) {
    val cores = MaterialTheme.colorScheme
    val forma = RoundedCornerShape(esc(8))
    val tipo = TipoPedido.deCodigo(pedido.tipo)
    val statusTexto = textoDoStatus(pedido.status)?.let { stringResource(it) } ?: pedido.status
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(cores.background, forma)
            .border(1.dp, if (pedido.atrasada) cores.error else cores.outlineVariant, forma)
            .clickable(role = Role.Button, onClick = aoClicar)
            .padding(horizontal = esc(12), vertical = esc(10)),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(esc(2))) {
            Text(
                pedido.nomeTitular?.takeIf { it.isNotBlank() } ?: stringResource(R.string.fila_titular_anonimizado),
                color = cores.onSurface,
                style = estilo(13, FontWeight.Medium),
            )
            Text(
                tipo?.let { stringResource(textoDoTipoParaAdmin(it)) } ?: pedido.tipo,
                color = cores.onSurface,
                style = estilo(12),
            )
            Text(
                statusTexto,
                color = if (pedido.status == StatusPedido.NEGADA) cores.error else cores.primary,
                style = estilo(12, FontWeight.Medium),
            )
            // Concluído: o que importa é quando acabou; aberto: o prazo (em vermelho se já passou).
            Text(
                if (pedido.dataConclusao != null) {
                    stringResource(R.string.privacidade_concluido_em, formatarDataDoPedido(pedido.dataConclusao))
                } else {
                    stringResource(R.string.privacidade_prazo, formatarDataDoPedido(pedido.dataPrazo)) +
                        if (pedido.atrasada) " · " + stringResource(R.string.fila_atrasado) else ""
                },
                color = if (pedido.atrasada) cores.error else cores.onSurfaceVariant,
                style = estilo(11),
            )
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = cores.onSurfaceVariant)
    }
}

private fun exemplo(id: Int, nome: String?, tipo: String, status: String, atrasada: Boolean = false) = PedidoAdmin(
    id = id, idUsuario = if (nome == null) null else id, nomeTitular = nome, emailContato = "titular@exemplo.com.br",
    tipo = tipo, status = status, observacao = null, dataSolicitacao = "2026-11-01T12:00:00Z",
    dataPrazo = "2026-11-16T12:00:00Z", dataConclusao = null, idResponsavel = null, justificativaNegativa = null,
    atrasada = atrasada,
)

@Preview(showBackground = true, widthDp = 360, heightDp = 828)
@Composable
private fun FilaPrivacidadeTelaPreview() {
    MaterialTheme {
        FilaPrivacidadeTela(
            estado = FilaPrivacidadeEstado(
                carregando = false,
                pedidos = listOf(
                    exemplo(1, "Ana Souza", "acesso", "recebida", atrasada = true),
                    exemplo(2, "Bruno Lima", "anonimizacao", "em_analise"),
                    exemplo(3, null, "eliminacao", "atendida"),
                ),
            ),
            aoEscolherFiltro = {}, aoRecarregar = {}, aoAbrir = {},
        )
    }
}
