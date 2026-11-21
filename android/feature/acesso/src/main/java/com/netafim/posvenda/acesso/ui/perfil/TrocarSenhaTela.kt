package com.netafim.posvenda.acesso.ui.perfil

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netafim.posvenda.acesso.R
import com.netafim.posvenda.acesso.ui.comum.BotaoCheio
import com.netafim.posvenda.acesso.ui.comum.CampoSenhaDoPrototipo
import com.netafim.posvenda.acesso.ui.comum.MensagemErro
import com.netafim.posvenda.acesso.ui.comum.PaginaComCabecalho
import com.netafim.posvenda.acesso.ui.comum.TextoApoio
import com.netafim.posvenda.acesso.ui.comum.esc

@Composable
fun TrocarSenhaRota(viewModel: TrocarSenhaViewModel, aoConcluir: () -> Unit) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    LaunchedEffect(estado.concluida) {
        if (estado.concluida) aoConcluir()
    }
    TrocarSenhaTela(
        estado = estado,
        aoMudar = viewModel::aoMudar,
        aoAlternarSenha = viewModel::alternarVisibilidade,
        aoSalvar = viewModel::salvar,
    )
}

/**
 * Trocar a própria senha. Não existe no Figma; usa a base das telas de formulário do protótipo (barra
 * azul com o título, campos e botão do Cadastro, botão 26 dp abaixo do último campo). O voltar do
 * sistema fecha a tela sem trocar nada.
 */
@Composable
fun TrocarSenhaTela(
    estado: TrocarSenhaEstado,
    aoMudar: (CampoTrocaSenha, String) -> Unit,
    aoAlternarSenha: () -> Unit,
    aoSalvar: () -> Unit,
) {
    val bloqueado = estado.carregando
    val mostrar = stringResource(R.string.acesso_mostrar_senha)
    val ocultar = stringResource(R.string.acesso_ocultar_senha)
    PaginaComCabecalho(stringResource(R.string.acesso_trocar_senha_titulo)) {
        TextoApoio(stringResource(R.string.acesso_trocar_senha_explicacao))
        Spacer(Modifier.height(esc(20)))
        estado.erroGeral?.let {
            MensagemErro(it)
            Spacer(Modifier.height(esc(16)))
        }
        CampoSenhaDoPrototipo(
            valor = estado.senhaAtual,
            aoMudar = { aoMudar(CampoTrocaSenha.ATUAL, it) },
            rotulo = stringResource(R.string.acesso_senha_atual),
            visivel = estado.senhaVisivel,
            aoAlternarVisibilidade = aoAlternarSenha,
            descricaoMostrar = mostrar,
            descricaoOcultar = ocultar,
            erro = estado.erros[CampoTrocaSenha.ATUAL],
            acaoIme = ImeAction.Next,
            habilitado = !bloqueado,
        )
        Spacer(Modifier.height(esc(16)))
        CampoSenhaDoPrototipo(
            valor = estado.novaSenha,
            aoMudar = { aoMudar(CampoTrocaSenha.NOVA, it) },
            rotulo = stringResource(R.string.acesso_nova_senha),
            visivel = estado.senhaVisivel,
            aoAlternarVisibilidade = aoAlternarSenha,
            descricaoMostrar = mostrar,
            descricaoOcultar = ocultar,
            erro = estado.erros[CampoTrocaSenha.NOVA],
            dica = stringResource(R.string.acesso_dica_senha),
            acaoIme = ImeAction.Next,
            habilitado = !bloqueado,
        )
        Spacer(Modifier.height(esc(16)))
        CampoSenhaDoPrototipo(
            valor = estado.confirmacao,
            aoMudar = { aoMudar(CampoTrocaSenha.CONFIRMACAO, it) },
            rotulo = stringResource(R.string.acesso_confirmar_nova_senha),
            visivel = estado.senhaVisivel,
            aoAlternarVisibilidade = aoAlternarSenha,
            descricaoMostrar = mostrar,
            descricaoOcultar = ocultar,
            erro = estado.erros[CampoTrocaSenha.CONFIRMACAO],
            acaoIme = ImeAction.Done,
            aoConcluir = aoSalvar,
            habilitado = !bloqueado,
        )
        Spacer(Modifier.height(esc(26)))
        BotaoCheio(
            texto = stringResource(R.string.acesso_trocar_senha_salvar),
            aoClicar = aoSalvar,
            carregando = estado.carregando,
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 828)
@Composable
private fun TrocarSenhaTelaPreview() {
    MaterialTheme {
        TrocarSenhaTela(
            estado = TrocarSenhaEstado(
                senhaAtual = "SenhaAntiga1",
                erros = mapOf(CampoTrocaSenha.CONFIRMACAO to "As senhas não conferem."),
            ),
            aoMudar = { _, _ -> }, aoAlternarSenha = {}, aoSalvar = {},
        )
    }
}
