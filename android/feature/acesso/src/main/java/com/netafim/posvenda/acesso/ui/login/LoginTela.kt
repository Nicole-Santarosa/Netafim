package com.netafim.posvenda.acesso.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import com.netafim.posvenda.acesso.R
import com.netafim.posvenda.acesso.ui.comum.BotaoCheio
import com.netafim.posvenda.acesso.ui.comum.BotaoContorno
import com.netafim.posvenda.acesso.ui.comum.CabecalhoTela
import com.netafim.posvenda.acesso.ui.comum.CampoDoPrototipo
import com.netafim.posvenda.acesso.ui.comum.CampoSenhaDoPrototipo
import com.netafim.posvenda.acesso.ui.comum.LinkTexto
import com.netafim.posvenda.acesso.ui.comum.MensagemErro
import com.netafim.posvenda.acesso.ui.comum.TelaResponsiva
import com.netafim.posvenda.acesso.ui.comum.esc
import com.netafim.posvenda.acesso.ui.comum.larguraConteudo

@Composable
fun LoginRota(
    viewModel: LoginViewModel,
    aoEntrar: () -> Unit,
    aoSolicitarAcesso: () -> Unit,
    aoEsquecerSenha: () -> Unit,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    LaunchedEffect(estado.entrou) {
        if (estado.entrou) aoEntrar()
    }
    LoginTela(
        estado = estado,
        aoMudarEmail = viewModel::aoMudarEmail,
        aoMudarSenha = viewModel::aoMudarSenha,
        aoAlternarSenha = viewModel::alternarVisibilidadeSenha,
        aoEntrar = viewModel::entrar,
        aoSolicitarAcesso = aoSolicitarAcesso,
        aoEsquecerSenha = aoEsquecerSenha,
        aoMudarCodigo = viewModel::aoMudarCodigo,
        aoConfirmarCodigo = viewModel::confirmarCodigo,
        aoVoltarParaSenha = viewModel::voltarParaSenha,
    )
}

/**
 * Login conforme o protótipo (Figma, "02 Login"): barra azul com o título, dois campos com o
 * rótulo acima, botão "Entrar", link "Esqueci minha senha" e "Criar conta" mais abaixo.
 * As distâncias seguem o quadro de 360 dp; em telas maiores o conteúdo fica centralizado.
 */
@Composable
fun LoginTela(
    estado: LoginEstado,
    aoMudarEmail: (String) -> Unit,
    aoMudarSenha: (String) -> Unit,
    aoAlternarSenha: () -> Unit,
    aoEntrar: () -> Unit,
    aoSolicitarAcesso: () -> Unit,
    aoEsquecerSenha: () -> Unit,
    aoMudarCodigo: (String) -> Unit = {},
    aoConfirmarCodigo: () -> Unit = {},
    aoVoltarParaSenha: () -> Unit = {},
) {
    if (estado.pedindoCodigo) {
        SegundoFatorTela(estado, aoMudarCodigo, aoConfirmarCodigo, aoVoltarParaSenha)
        return
    }
    TelaResponsiva(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .imePadding(),
        ) {
            CabecalhoTela(stringResource(R.string.acesso_login_titulo))
            BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    modifier = Modifier
                        .widthIn(max = larguraConteudo())
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = maxHeight)
                        .navigationBarsPadding()
                        .padding(start = esc(20), end = esc(20), top = esc(36), bottom = esc(32)),
                ) {
                    estado.erroGeral?.let {
                        MensagemErro(it)
                        Spacer(Modifier.height(esc(16)))
                    }
                    CampoDoPrototipo(
                        valor = estado.email,
                        aoMudar = aoMudarEmail,
                        rotulo = stringResource(R.string.acesso_email),
                        erro = estado.erroEmail,
                        tipoTeclado = KeyboardType.Email,
                        habilitado = !estado.carregando,
                    )
                    Spacer(Modifier.height(esc(16)))
                    CampoSenhaDoPrototipo(
                        valor = estado.senha,
                        aoMudar = aoMudarSenha,
                        rotulo = stringResource(R.string.acesso_senha),
                        visivel = estado.senhaVisivel,
                        aoAlternarVisibilidade = aoAlternarSenha,
                        descricaoMostrar = stringResource(R.string.acesso_mostrar_senha),
                        descricaoOcultar = stringResource(R.string.acesso_ocultar_senha),
                        erro = estado.erroSenha,
                        acaoIme = ImeAction.Done,
                        aoConcluir = aoEntrar,
                        habilitado = !estado.carregando,
                    )
                    Spacer(Modifier.height(esc(26)))
                    BotaoCheio(
                        texto = stringResource(R.string.acesso_entrar),
                        aoClicar = aoEntrar,
                        carregando = estado.carregando,
                    )
                    Spacer(Modifier.height(esc(3)))
                    LinkTexto(stringResource(R.string.acesso_esqueci_senha), aoEsquecerSenha)
                    // No quadro o "Criar conta" fica 224 dp abaixo do link. Esse espaço encolhe quando
                    // a altura é curta (tablet em paisagem), para o botão não sair da tela, e nunca passa
                    // do valor do protótipo.
                    Spacer(Modifier.height(esc(24)))
                    Spacer(Modifier.weight(1f, fill = false).height(esc(188)))
                    BotaoContorno(
                        texto = stringResource(R.string.acesso_criar_conta),
                        aoClicar = aoSolicitarAcesso,
                        habilitado = !estado.carregando,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 828)
@Preview(showBackground = true, widthDp = 800, heightDp = 1280)
@Composable
private fun LoginTelaPreview() {
    MaterialTheme {
        LoginTela(
            estado = LoginEstado(email = "tecnico@netafim.com", erroGeral = "E-mail ou senha incorretos."),
            aoMudarEmail = {}, aoMudarSenha = {}, aoAlternarSenha = {}, aoEntrar = {},
            aoSolicitarAcesso = {}, aoEsquecerSenha = {},
        )
    }
}
