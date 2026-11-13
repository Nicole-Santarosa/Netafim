package com.netafim.posvenda.acesso.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netafim.posvenda.acesso.R
import com.netafim.posvenda.acesso.ui.comum.BarraDeStatusSobreImagem
import com.netafim.posvenda.acesso.ui.comum.BotaoCheioHd
import com.netafim.posvenda.acesso.ui.comum.BotaoContorno
import com.netafim.posvenda.acesso.ui.comum.CampoHd
import com.netafim.posvenda.acesso.ui.comum.CampoSenhaHd
import com.netafim.posvenda.acesso.ui.comum.CoresHd
import com.netafim.posvenda.acesso.ui.comum.DivisorComTexto
import com.netafim.posvenda.acesso.ui.comum.MensagemErro
import com.netafim.posvenda.acesso.ui.comum.TelaResponsiva
import com.netafim.posvenda.acesso.ui.comum.esc
import com.netafim.posvenda.acesso.ui.comum.estilo
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
 * Login conforme o quadro "HD 01 — Login" do Figma (arquivo "Netafim", seção Acesso): faixa azul
 * de 81 dp (barra de status incluída) com o logo branco, e abaixo o cartão branco com o título,
 * o subtítulo, os campos de e-mail e senha com ícone, "Esqueci minha senha" à direita, o botão
 * Entrar, o divisor "ou" e o botão de contorno Criar conta. Os números são os do desenho, em dp,
 * na escala da tela (ver [TelaResponsiva]); o fundo atrás do cartão é F6F7F9.
 *
 * Fora do desenho, mas necessário: a mensagem de erro geral (acima dos campos) e o olho de
 * mostrar/ocultar a senha (acessibilidade).
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
    // O azul passa por baixo da barra de status: ícones claros em cima e escuros embaixo (sobre o branco).
    BarraDeStatusSobreImagem(navegacaoSobreImagem = false)
    TelaResponsiva(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(CoresHd.FundoDaTela)
                .imePadding(),
        ) {
            FaixaDoLogo()
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.White),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    Modifier
                        .widthIn(max = larguraConteudo())
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(start = esc(24), end = esc(24), top = esc(36), bottom = esc(32)),
                ) {
                    FormularioLogin(estado, aoMudarEmail, aoMudarSenha, aoAlternarSenha, aoEntrar, aoSolicitarAcesso, aoEsquecerSenha)
                }
            }
        }
    }
}

/**
 * Faixa azul do alto: 28 dp de barra de status + 53 dp com o logo de 90 x 27 dp no meio (13 dp de
 * folga em cima e embaixo), como no desenho. Uma sombra suave de 20 dp cai do cartão sobre ela.
 */
@Composable
private fun FaixaDoLogo() {
    val sombra = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.12f)))
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .statusBarsPadding()
            .height(esc(53))
            .drawWithContent {
                drawContent()
                val altura = 20.dp.toPx()
                drawRect(brush = sombra, topLeft = Offset(0f, size.height - altura), size = Size(size.width, altura))
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.logo_netafim_branco),
            // O leitor de tela anuncia a marca.
            contentDescription = stringResource(R.string.acesso_marca),
            modifier = Modifier.size(width = esc(90), height = esc(27)),
        )
    }
}

@Composable
private fun FormularioLogin(
    estado: LoginEstado,
    aoMudarEmail: (String) -> Unit,
    aoMudarSenha: (String) -> Unit,
    aoAlternarSenha: () -> Unit,
    aoEntrar: () -> Unit,
    aoSolicitarAcesso: () -> Unit,
    aoEsquecerSenha: () -> Unit,
) {
    Text(
        stringResource(R.string.acesso_login_titulo),
        color = CoresHd.TextoForte,
        style = estilo(20, FontWeight.SemiBold),
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(esc(5)))
    Text(stringResource(R.string.acesso_login_subtitulo), color = CoresHd.TextoSuave, style = estilo(12.5f))
    Spacer(Modifier.height(esc(20)))
    estado.erroGeral?.let {
        MensagemErro(it)
        Spacer(Modifier.height(esc(16)))
    }
    CampoHd(
        valor = estado.email,
        aoMudar = aoMudarEmail,
        rotulo = stringResource(R.string.acesso_email_corporativo),
        dica = stringResource(R.string.acesso_email_dica),
        icone = R.drawable.ic_envelope,
        erro = estado.erroEmail,
        tipoTeclado = KeyboardType.Email,
        habilitado = !estado.carregando,
    )
    Spacer(Modifier.height(esc(18)))
    CampoSenhaHd(
        valor = estado.senha,
        aoMudar = aoMudarSenha,
        rotulo = stringResource(R.string.acesso_senha),
        dica = stringResource(R.string.acesso_senha_dica),
        icone = R.drawable.ic_lock,
        visivel = estado.senhaVisivel,
        aoAlternarVisibilidade = aoAlternarSenha,
        descricaoMostrar = stringResource(R.string.acesso_mostrar_senha),
        descricaoOcultar = stringResource(R.string.acesso_ocultar_senha),
        erro = estado.erroSenha,
        acaoIme = ImeAction.Done,
        aoConcluir = aoEntrar,
        habilitado = !estado.carregando,
    )
    // Entre o campo e o botão o desenho deixa 38 dp (10 acima do texto, 12 abaixo): essa faixa inteira
    // é a área de toque do link, que fica com o texto no mesmo lugar do desenho.
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Box(
            Modifier
                .height(esc(38))
                .clickable(role = Role.Button, onClick = aoEsquecerSenha)
                .padding(start = esc(12)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.acesso_esqueci_senha),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End,
                style = estilo(12.5f, FontWeight.Medium),
            )
        }
    }
    BotaoCheioHd(
        texto = stringResource(R.string.acesso_entrar),
        aoClicar = aoEntrar,
        carregando = estado.carregando,
    )
    Spacer(Modifier.height(esc(18)))
    DivisorComTexto(stringResource(R.string.acesso_ou))
    Spacer(Modifier.height(esc(15)))
    // O botão do desenho tem 320 dp de largura (20 dp de margem), mais largo que os campos (312 dp).
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BotaoContorno(
            texto = stringResource(R.string.acesso_criar_conta),
            aoClicar = aoSolicitarAcesso,
            modifier = Modifier.requiredWidth(esc(320)),
            habilitado = !estado.carregando,
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 677)
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
