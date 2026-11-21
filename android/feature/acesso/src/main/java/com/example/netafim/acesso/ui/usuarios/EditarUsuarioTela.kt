package com.example.netafim.acesso.ui.usuarios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.netafim.acesso.R
import com.example.netafim.acesso.ui.comum.BotaoCheio
import com.example.netafim.acesso.ui.comum.BotaoContorno
import com.example.netafim.acesso.ui.comum.CampoDoPrototipo
import com.example.netafim.acesso.ui.comum.MensagemErro
import com.example.netafim.acesso.ui.comum.PaginaComCabecalho
import com.example.netafim.acesso.ui.comum.esc
import com.example.netafim.acesso.ui.comum.estilo

@Composable
fun EditarUsuarioRota(viewModel: EditarUsuarioViewModel, aoConcluir: () -> Unit) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    LaunchedEffect(estado.salvo) {
        if (estado.salvo) aoConcluir()
    }
    EditarUsuarioTela(
        estado = estado,
        aoMudar = viewModel::aoMudar,
        aoSalvar = viewModel::salvar,
        aoRecarregar = viewModel::carregar,
    )
}

/**
 * Editar usuário. Não existe no Figma; usa a base das telas de formulário do protótipo (campos e botão
 * do Cadastro, botão 26 dp abaixo do último campo), como o Novo usuário. O e-mail aparece só para
 * leitura: quem corrige o e-mail precisa de um novo convite. O voltar do sistema descarta as mudanças.
 */
@Composable
fun EditarUsuarioTela(
    estado: EditarUsuarioEstado,
    aoMudar: (CampoEditarUsuario, String) -> Unit,
    aoSalvar: () -> Unit,
    aoRecarregar: () -> Unit,
) {
    PaginaComCabecalho(stringResource(R.string.usuario_editar_titulo)) {
        when {
            estado.carregando -> Box(Modifier.fillMaxWidth().padding(top = esc(40)), Alignment.Center) { CircularProgressIndicator() }
            !estado.carregado -> {
                MensagemErro(estado.erroGeral ?: stringResource(R.string.usuario_erro_carregar))
                Spacer(Modifier.height(esc(20)))
                BotaoContorno(stringResource(R.string.acesso_tentar_novamente), aoRecarregar)
            }
            else -> Formulario(estado, aoMudar, aoSalvar)
        }
    }
}

@Composable
private fun Formulario(
    estado: EditarUsuarioEstado,
    aoMudar: (CampoEditarUsuario, String) -> Unit,
    aoSalvar: () -> Unit,
) {
    estado.erroGeral?.let {
        MensagemErro(it)
        Spacer(Modifier.height(esc(16)))
    }
    Column {
        Text(stringResource(R.string.acesso_email), color = MaterialTheme.colorScheme.onSurfaceVariant, style = estilo(12))
        Text(estado.email, color = MaterialTheme.colorScheme.onSurface, style = estilo(14))
    }
    Spacer(Modifier.height(esc(16)))
    CampoDoPrototipo(
        valor = estado.nome,
        aoMudar = { aoMudar(CampoEditarUsuario.NOME, it) },
        rotulo = stringResource(R.string.acesso_nome),
        erro = estado.erros[CampoEditarUsuario.NOME],
        habilitado = !estado.salvando,
    )
    Spacer(Modifier.height(esc(16)))
    CampoDoPrototipo(
        valor = estado.telefone,
        aoMudar = { aoMudar(CampoEditarUsuario.TELEFONE, it) },
        rotulo = stringResource(R.string.acesso_telefone),
        erro = estado.erros[CampoEditarUsuario.TELEFONE],
        tipoTeclado = KeyboardType.Phone,
        acaoIme = ImeAction.Done,
        aoConcluir = aoSalvar,
        habilitado = !estado.salvando,
    )
    Spacer(Modifier.height(esc(26)))
    BotaoCheio(
        texto = stringResource(R.string.usuario_editar_salvar),
        aoClicar = aoSalvar,
        carregando = estado.salvando,
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 828)
@Composable
private fun EditarUsuarioTelaPreview() {
    MaterialTheme {
        EditarUsuarioTela(
            estado = EditarUsuarioEstado(
                email = "ana@agroirriga.com.br", nome = "Ana Paula Reis", telefone = "16997389535",
                carregando = false, carregado = true,
                erros = mapOf(CampoEditarUsuario.TELEFONE to "Informe DDD e número."),
            ),
            aoMudar = { _, _ -> }, aoSalvar = {}, aoRecarregar = {},
        )
    }
}
