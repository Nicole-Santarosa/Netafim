package com.example.netafim.acesso.ui.privacidade

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.netafim.acesso.R
import com.example.netafim.acesso.dominio.AcoesDoPedido
import com.example.netafim.acesso.dominio.PedidoAdmin
import com.example.netafim.acesso.dominio.StatusPedido
import com.example.netafim.acesso.dominio.TipoPedido
import com.example.netafim.acesso.ui.comum.BotaoAcao
import com.example.netafim.acesso.ui.comum.BotaoCheio
import com.example.netafim.acesso.ui.comum.BotaoContorno
import com.example.netafim.acesso.ui.comum.CampoDoPrototipo
import com.example.netafim.acesso.ui.comum.CartaoDoPrototipo
import com.example.netafim.acesso.ui.comum.DetalheDeEstado
import com.example.netafim.acesso.ui.comum.MensagemErro
import com.example.netafim.acesso.ui.comum.PaginaComCabecalho
import com.example.netafim.acesso.ui.comum.TextoApoio
import com.example.netafim.acesso.ui.comum.esc
import com.example.netafim.acesso.ui.comum.estilo

@Composable
fun PedidoAdminRota(viewModel: PedidoAdminViewModel) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val assunto = stringResource(R.string.privacidade_compartilhar_assunto)
    val titulo = stringResource(R.string.privacidade_compartilhar_titulo)
    // A cópia sai pela folha de compartilhar do sistema e não fica no app.
    LaunchedEffect(estado.copia) {
        val copia = estado.copia ?: return@LaunchedEffect
        val envio = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, assunto)
            putExtra(Intent.EXTRA_TEXT, copia)
        }
        contexto.startActivity(Intent.createChooser(envio, titulo))
        viewModel.copiaEntregue()
    }
    PedidoAdminTela(
        estado = estado,
        aoRecarregar = viewModel::carregar,
        aoAnalisar = viewModel::analisar,
        aoAtender = viewModel::pedirAtender,
        aoNegar = viewModel::pedirNegar,
        aoBaixarDados = viewModel::baixarDados,
        aoConfirmarAtender = viewModel::confirmarAtender,
        aoCancelarAtender = viewModel::cancelarAtender,
        aoMudarJustificativa = viewModel::mudarJustificativa,
        aoConfirmarNegar = viewModel::confirmarNegar,
        aoCancelarNegar = viewModel::cancelarNegar,
    )
}

/**
 * Um pedido de privacidade, para o Netafim Admin. Não existe no Figma; usa a base do protótipo (barra azul com o
 * título, cartão de dados como o de Aprovação de cadastro e botões de 48 dp). Só aparecem as ações que a API
 * aceitaria; atender e negar pedem confirmação, e atender anonimização ou eliminação avisa que não há volta.
 */
@Composable
fun PedidoAdminTela(
    estado: PedidoAdminEstado,
    aoRecarregar: () -> Unit,
    aoAnalisar: () -> Unit,
    aoAtender: () -> Unit,
    aoNegar: () -> Unit,
    aoBaixarDados: () -> Unit,
    aoConfirmarAtender: () -> Unit,
    aoCancelarAtender: () -> Unit,
    aoMudarJustificativa: (String) -> Unit,
    aoConfirmarNegar: () -> Unit,
    aoCancelarNegar: () -> Unit,
) {
    PaginaComCabecalho(stringResource(R.string.fila_detalhe_titulo)) {
        val pedido = estado.pedido
        when {
            estado.carregando && pedido == null -> Box(Modifier.fillMaxWidth().padding(top = esc(40)), Alignment.Center) {
                CircularProgressIndicator()
            }
            pedido == null -> {
                MensagemErro(estado.erro ?: stringResource(R.string.fila_erro_carregar))
                Spacer(Modifier.height(esc(20)))
                BotaoContorno(stringResource(R.string.acesso_tentar_novamente), aoRecarregar)
            }
            else -> Conteudo(estado, pedido, aoAnalisar, aoAtender, aoNegar, aoBaixarDados)
        }
    }
    if (estado.confirmandoAtender) {
        AlertDialog(
            onDismissRequest = { if (!estado.processando) aoCancelarAtender() },
            containerColor = MaterialTheme.colorScheme.background,
            title = { Text(stringResource(R.string.fila_atender_titulo)) },
            text = {
                Text(
                    stringResource(
                        if (estado.atenderAnonimiza) R.string.fila_atender_texto_anonimiza else R.string.fila_atender_texto,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = aoConfirmarAtender, enabled = !estado.processando) {
                    Text(
                        stringResource(R.string.fila_atender_confirmar),
                        color = if (estado.atenderAnonimiza) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = aoCancelarAtender, enabled = !estado.processando) {
                    Text(stringResource(R.string.acesso_cancelar))
                }
            },
        )
    }
    if (estado.negando) {
        AlertDialog(
            onDismissRequest = { if (!estado.processando) aoCancelarNegar() },
            containerColor = MaterialTheme.colorScheme.background,
            title = { Text(stringResource(R.string.fila_negar_titulo)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(esc(12))) {
                    Text(stringResource(R.string.fila_negar_texto))
                    CampoDoPrototipo(
                        valor = estado.justificativa,
                        aoMudar = aoMudarJustificativa,
                        rotulo = stringResource(R.string.fila_negar_motivo),
                        erro = estado.erroJustificativa,
                        habilitado = !estado.processando,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = aoConfirmarNegar, enabled = !estado.processando) {
                    Text(stringResource(R.string.fila_negar_confirmar), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = aoCancelarNegar, enabled = !estado.processando) {
                    Text(stringResource(R.string.acesso_cancelar))
                }
            },
        )
    }
}

@Composable
private fun Conteudo(
    estado: PedidoAdminEstado,
    pedido: PedidoAdmin,
    aoAnalisar: () -> Unit,
    aoAtender: () -> Unit,
    aoNegar: () -> Unit,
    aoBaixarDados: () -> Unit,
) {
    val cores = MaterialTheme.colorScheme
    val tipo = TipoPedido.deCodigo(pedido.tipo)
    CartaoDoPrototipo {
        Column(verticalArrangement = Arrangement.spacedBy(esc(10))) {
            Dado(
                stringResource(R.string.fila_dado_titular),
                pedido.nomeTitular?.takeIf { it.isNotBlank() } ?: stringResource(R.string.fila_titular_anonimizado),
            )
            Dado(stringResource(R.string.fila_dado_email), pedido.emailContato)
            Dado(stringResource(R.string.fila_dado_pedido), tipo?.let { stringResource(textoDoTipoParaAdmin(it)) } ?: pedido.tipo)
            Dado(stringResource(R.string.fila_dado_situacao), textoDoStatus(pedido.status)?.let { stringResource(it) } ?: pedido.status)
            Dado(stringResource(R.string.fila_dado_pedido_em), formatarDataDoPedido(pedido.dataSolicitacao))
            if (pedido.dataConclusao != null) {
                Dado(stringResource(R.string.fila_dado_concluido_em), formatarDataDoPedido(pedido.dataConclusao))
            } else {
                Dado(
                    stringResource(R.string.fila_dado_prazo),
                    formatarDataDoPedido(pedido.dataPrazo) + if (pedido.atrasada) " · " + stringResource(R.string.fila_atrasado) else "",
                )
            }
            pedido.observacao?.takeIf { it.isNotBlank() }?.let { Dado(stringResource(R.string.fila_dado_observacao), it) }
            pedido.justificativaNegativa?.takeIf { it.isNotBlank() }?.let { Dado(stringResource(R.string.fila_dado_motivo), it) }
        }
    }
    Spacer(Modifier.height(esc(20)))
    estado.aviso?.let {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(esc(6))) {
            Icon(Icons.Outlined.Check, contentDescription = null, tint = cores.primary, modifier = Modifier.size(esc(16)))
            Text(stringResource(textoDoAviso(it)), color = cores.primary, style = estilo(13, FontWeight.Medium))
        }
        Spacer(Modifier.height(esc(16)))
    }
    estado.erroAcao?.let {
        MensagemErro(it)
        Spacer(Modifier.height(esc(16)))
    }
    val acoes = estado.acoes
    if (!acoes.alguma) {
        DetalheDeEstado(stringResource(R.string.fila_sem_acoes))
        return
    }
    val livre = !estado.processando
    Column(verticalArrangement = Arrangement.spacedBy(esc(12))) {
        if (acoes.verDados) {
            BotaoContorno(stringResource(R.string.fila_acao_dados), aoBaixarDados, habilitado = livre && !estado.baixando)
            TextoApoio(stringResource(R.string.fila_dados_aviso))
        }
        if (acoes.analisar) BotaoContorno(stringResource(R.string.fila_acao_analisar), aoAnalisar, habilitado = livre)
        if (acoes.atender) BotaoCheio(stringResource(R.string.fila_acao_atender), aoAtender, carregando = estado.processando)
        if (acoes.negar) {
            BotaoAcao(
                texto = stringResource(R.string.fila_acao_negar),
                aoClicar = { if (livre) aoNegar() },
                cor = cores.error,
                preenchido = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Dado(rotulo: String, valor: String) {
    Column {
        Text(rotulo, color = MaterialTheme.colorScheme.onSurfaceVariant, style = estilo(10))
        Text(valor, color = MaterialTheme.colorScheme.onSurface, style = estilo(13, FontWeight.Medium))
    }
}

private fun textoDoAviso(aviso: AvisoPedido): Int = when (aviso) {
    AvisoPedido.EM_ANALISE -> R.string.fila_aviso_analise
    AvisoPedido.ATENDIDO -> R.string.fila_aviso_atendido
    AvisoPedido.NEGADO -> R.string.fila_aviso_negado
}

@Preview(showBackground = true, widthDp = 360, heightDp = 828)
@Composable
private fun PedidoAdminTelaPreview() {
    MaterialTheme {
        PedidoAdminTela(
            estado = PedidoAdminEstado(
                carregando = false,
                pedido = PedidoAdmin(
                    id = 1, idUsuario = 7, nomeTitular = "Ana Souza", emailContato = "ana@agroirriga.com.br",
                    tipo = "anonimizacao", status = StatusPedido.RECEBIDA, observacao = "Quero sair do sistema",
                    dataSolicitacao = "2026-11-01T12:00:00Z", dataPrazo = "2026-11-16T12:00:00Z",
                    dataConclusao = null, idResponsavel = null, justificativaNegativa = null, atrasada = true,
                ),
                acoes = AcoesDoPedido(analisar = true, atender = true, negar = true),
            ),
            aoRecarregar = {}, aoAnalisar = {}, aoAtender = {}, aoNegar = {}, aoBaixarDados = {},
            aoConfirmarAtender = {}, aoCancelarAtender = {}, aoMudarJustificativa = {}, aoConfirmarNegar = {}, aoCancelarNegar = {},
        )
    }
}
