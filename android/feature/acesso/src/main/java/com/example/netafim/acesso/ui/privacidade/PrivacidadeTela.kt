package com.example.netafim.acesso.ui.privacidade

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.netafim.acesso.R
import com.example.netafim.acesso.dominio.PedidoTitular
import com.example.netafim.acesso.dominio.StatusPedido
import com.example.netafim.acesso.dominio.TipoPedido
import com.example.netafim.acesso.ui.comum.BotaoCheio
import com.example.netafim.acesso.ui.comum.BotaoContorno
import com.example.netafim.acesso.ui.comum.MensagemErro
import com.example.netafim.acesso.ui.comum.PaginaComCabecalho
import com.example.netafim.acesso.ui.comum.TextoApoio
import com.example.netafim.acesso.ui.comum.TituloDeGrupo
import com.example.netafim.acesso.ui.comum.esc
import com.example.netafim.acesso.ui.comum.estilo

@Composable
fun PrivacidadeRota(viewModel: PrivacidadeViewModel, aoFazerPedido: () -> Unit) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val assunto = stringResource(R.string.privacidade_compartilhar_assunto)
    val titulo = stringResource(R.string.privacidade_compartilhar_titulo)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.aoRetomar() }
    // A cópia sai pela folha de compartilhar do sistema (a pessoa escolhe onde guardar) e não fica no app.
    LaunchedEffect(estado.copia) {
        val copia = estado.copia ?: return@LaunchedEffect
        // Arquivo, e não texto da Intent: uma exportação grande estouraria o limite do sistema.
        try {
            CopiaDosDados.compartilhar(contexto, copia, assunto, titulo)
        } finally {
            viewModel.copiaEntregue()
        }
    }
    PrivacidadeTela(
        estado = estado,
        aoBaixar = viewModel::baixarDados,
        aoFazerPedido = aoFazerPedido,
        aoRecarregar = viewModel::carregar,
    )
}

/**
 * Privacidade (LGPD, direitos do titular). Não existe no Figma; usa a base do protótipo (barra azul com
 * o título, botões do Cadastro e cartões de bordas C9C9C2 e raio 8, como a Gestão de usuários).
 */
@Composable
fun PrivacidadeTela(
    estado: PrivacidadeEstado,
    aoBaixar: () -> Unit,
    aoFazerPedido: () -> Unit,
    aoRecarregar: () -> Unit,
) {
    PaginaComCabecalho(stringResource(R.string.privacidade_titulo)) {
        TextoApoio(stringResource(R.string.privacidade_explicacao))
        Spacer(Modifier.height(esc(20)))
        estado.erroCopia?.let {
            MensagemErro(it)
            Spacer(Modifier.height(esc(12)))
        }
        BotaoCheio(stringResource(R.string.privacidade_baixar), aoBaixar, carregando = estado.baixando)
        Spacer(Modifier.height(esc(8)))
        TextoApoio(stringResource(R.string.privacidade_baixar_aviso))
        Spacer(Modifier.height(esc(20)))
        BotaoContorno(stringResource(R.string.privacidade_fazer_pedido), aoFazerPedido)
        Spacer(Modifier.height(esc(28)))
        TituloDeGrupo(stringResource(R.string.privacidade_meus_pedidos))
        Spacer(Modifier.height(esc(8)))
        when {
            estado.carregando && estado.pedidos.isEmpty() -> Box(Modifier.fillMaxWidth().padding(top = esc(16)), Alignment.Center) {
                CircularProgressIndicator()
            }
            estado.erro != null && estado.pedidos.isEmpty() -> {
                MensagemErro(estado.erro)
                Spacer(Modifier.height(esc(16)))
                BotaoContorno(stringResource(R.string.acesso_tentar_novamente), aoRecarregar)
            }
            estado.pedidos.isEmpty() -> TextoApoio(stringResource(R.string.privacidade_sem_pedidos))
            else -> Column(verticalArrangement = Arrangement.spacedBy(esc(8))) {
                estado.pedidos.forEach { CartaoDoPedido(it) }
            }
        }
    }
}

@Composable
private fun CartaoDoPedido(pedido: PedidoTitular) {
    val cores = MaterialTheme.colorScheme
    val forma = RoundedCornerShape(esc(8))
    val tipo = TipoPedido.deCodigo(pedido.tipo)
    val statusTexto = textoDoStatus(pedido.status)?.let { stringResource(it) } ?: pedido.status
    Column(
        verticalArrangement = Arrangement.spacedBy(esc(2)),
        modifier = Modifier
            .fillMaxWidth()
            .background(cores.background, forma)
            .border(1.dp, cores.outlineVariant, forma)
            .padding(horizontal = esc(12), vertical = esc(10)),
    ) {
        Text(
            tipo?.let { stringResource(textoDoTipo(it)) } ?: pedido.tipo,
            color = cores.onSurface,
            style = estilo(13, FontWeight.Medium),
        )
        Text(
            statusTexto,
            color = if (pedido.status == StatusPedido.NEGADA) cores.error else cores.primary,
            style = estilo(12, FontWeight.Medium),
        )
        Text(
            stringResource(R.string.privacidade_pedido_em, formatarDataDoPedido(pedido.dataSolicitacao)),
            color = cores.onSurfaceVariant,
            style = estilo(11),
        )
        if (pedido.dataConclusao != null) {
            Text(
                stringResource(R.string.privacidade_concluido_em, formatarDataDoPedido(pedido.dataConclusao)),
                color = cores.onSurfaceVariant,
                style = estilo(11),
            )
        } else {
            Text(
                stringResource(R.string.privacidade_prazo, formatarDataDoPedido(pedido.dataPrazo)),
                color = cores.onSurfaceVariant,
                style = estilo(11),
            )
        }
        // Quando negado, a pessoa precisa ver o motivo para entender a resposta.
        pedido.justificativaNegativa?.takeIf { it.isNotBlank() }?.let {
            Text(stringResource(R.string.privacidade_motivo_negativa, it), color = cores.onSurface, style = estilo(11))
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 828)
@Composable
private fun PrivacidadeTelaPreview() {
    MaterialTheme {
        PrivacidadeTela(
            estado = PrivacidadeEstado(
                carregando = false,
                pedidos = listOf(
                    PedidoTitular(3, "acesso", "em_analise", null, "2026-11-10T12:00:00Z", "2026-11-25T12:00:00Z", null, null),
                    PedidoTitular(
                        2, "correcao", "negada", null, "2026-10-01T12:00:00Z", "2026-10-16T12:00:00Z",
                        "2026-10-05T12:00:00Z", "Não encontramos o dado informado.",
                    ),
                ),
            ),
            aoBaixar = {}, aoFazerPedido = {}, aoRecarregar = {},
        )
    }
}
