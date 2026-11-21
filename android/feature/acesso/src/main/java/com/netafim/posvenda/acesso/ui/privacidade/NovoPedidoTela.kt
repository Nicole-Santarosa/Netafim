package com.netafim.posvenda.acesso.ui.privacidade

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netafim.posvenda.acesso.R
import com.netafim.posvenda.acesso.dominio.TipoPedido
import com.netafim.posvenda.acesso.ui.comum.BotaoCheio
import com.netafim.posvenda.acesso.ui.comum.CampoDoPrototipo
import com.netafim.posvenda.acesso.ui.comum.MensagemErro
import com.netafim.posvenda.acesso.ui.comum.OpcaoSelecionavel
import com.netafim.posvenda.acesso.ui.comum.PaginaComCabecalho
import com.netafim.posvenda.acesso.ui.comum.TextoApoio
import com.netafim.posvenda.acesso.ui.comum.esc

@Composable
fun NovoPedidoRota(viewModel: NovoPedidoViewModel, aoConcluir: () -> Unit) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    LaunchedEffect(estado.concluido) {
        if (estado.concluido) aoConcluir()
    }
    NovoPedidoTela(
        estado = estado,
        aoEscolherTipo = viewModel::escolherTipo,
        aoMudarObservacao = viewModel::mudarObservacao,
        aoEnviar = viewModel::enviar,
        aoConfirmar = viewModel::confirmar,
        aoCancelarConfirmacao = viewModel::cancelarConfirmacao,
    )
}

/**
 * Fazer um pedido (LGPD). Não existe no Figma; usa a base das telas de formulário do protótipo, com as
 * opções em lista (como a escolha de perfil) e o botão 26 dp abaixo do último campo. Anonimizar e excluir
 * pedem uma confirmação a mais, porque a conta não volta. O voltar do sistema descarta o pedido.
 */
@Composable
fun NovoPedidoTela(
    estado: NovoPedidoEstado,
    aoEscolherTipo: (TipoPedido) -> Unit,
    aoMudarObservacao: (String) -> Unit,
    aoEnviar: () -> Unit,
    aoConfirmar: () -> Unit,
    aoCancelarConfirmacao: () -> Unit,
) {
    PaginaComCabecalho(stringResource(R.string.privacidade_novo_titulo)) {
        TextoApoio(stringResource(R.string.privacidade_novo_explicacao))
        Spacer(Modifier.height(esc(16)))
        estado.erroGeral?.let {
            MensagemErro(it)
            Spacer(Modifier.height(esc(16)))
        }
        TipoPedido.entries.forEach { tipo ->
            OpcaoSelecionavel(
                texto = stringResource(textoDoTipo(tipo)),
                selecionada = estado.tipo == tipo,
                aoClicar = { aoEscolherTipo(tipo) },
            )
            Spacer(Modifier.height(esc(8)))
        }
        estado.erros[CampoNovoPedido.TIPO]?.let { MensagemErro(it) }
        if (estado.tipo?.irreversivel == true) {
            Spacer(Modifier.height(esc(4)))
            TextoApoio(stringResource(R.string.privacidade_aviso_irreversivel), cor = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(esc(12)))
        CampoDoPrototipo(
            valor = estado.observacao,
            aoMudar = aoMudarObservacao,
            rotulo = stringResource(R.string.privacidade_observacao),
            erro = estado.erros[CampoNovoPedido.OBSERVACAO],
            dica = stringResource(R.string.privacidade_observacao_dica),
            acaoIme = ImeAction.Done,
            aoConcluir = aoEnviar,
            habilitado = !estado.enviando,
        )
        Spacer(Modifier.height(esc(26)))
        BotaoCheio(
            texto = stringResource(R.string.privacidade_enviar),
            aoClicar = aoEnviar,
            carregando = estado.enviando,
        )
    }
    if (estado.confirmando) {
        AlertDialog(
            onDismissRequest = { if (!estado.enviando) aoCancelarConfirmacao() },
            containerColor = MaterialTheme.colorScheme.background,
            title = { Text(stringResource(R.string.privacidade_confirmar_titulo)) },
            text = { Text(stringResource(R.string.privacidade_confirmar_texto)) },
            confirmButton = {
                TextButton(onClick = aoConfirmar, enabled = !estado.enviando) {
                    Text(stringResource(R.string.privacidade_confirmar_sim), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = aoCancelarConfirmacao, enabled = !estado.enviando) {
                    Text(stringResource(R.string.acesso_cancelar))
                }
            },
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 1000)
@Composable
private fun NovoPedidoTelaPreview() {
    MaterialTheme {
        NovoPedidoTela(
            estado = NovoPedidoEstado(tipo = TipoPedido.ANONIMIZACAO),
            aoEscolherTipo = {}, aoMudarObservacao = {}, aoEnviar = {}, aoConfirmar = {}, aoCancelarConfirmacao = {},
        )
    }
}
