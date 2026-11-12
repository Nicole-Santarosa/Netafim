package com.netafim.posvenda.acesso.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netafim.posvenda.acesso.R
import com.netafim.posvenda.acesso.ui.comum.BarraDeStatusSobreImagem
import com.netafim.posvenda.acesso.ui.comum.BotaoCheio
import com.netafim.posvenda.acesso.ui.comum.BotaoContorno
import com.netafim.posvenda.acesso.ui.comum.CampoDoPrototipo
import com.netafim.posvenda.acesso.ui.comum.CampoSenhaDoPrototipo
import com.netafim.posvenda.acesso.ui.comum.LinkTexto
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
    )
}

/**
 * Login com o wallpaper da marca. O wallpaper já traz o logo desenhado (a 22 a 29% da altura), então
 * o formulário fica numa superfície branca que nunca cobre essa faixa:
 * - celular: o wallpaper ocupa a tela e o formulário sobe de baixo, como uma folha;
 * - tablet em retrato: o formulário é um cartão centralizado, na parte de baixo;
 * - tablet em paisagem: wallpaper à esquerda e, à direita, o cartão sobre o azul da marca.
 * O formulário mantém os campos e botões do protótipo (cinza E9E9E4, azul 181F4E); o branco da
 * superfície garante o contraste do texto, que não depende da foto.
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
) {
    val formulario: @Composable () -> Unit = {
        FormularioLogin(
            estado, aoMudarEmail, aoMudarSenha, aoAlternarSenha, aoEntrar, aoSolicitarAcesso, aoEsquecerSenha,
        )
    }
    TelaResponsiva(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary)) {
            if (maxWidth >= LARGURA_TABLET && maxWidth > maxHeight) {
                EmDuasColunas(formulario)
            } else {
                SobreAImagem(compacto = maxWidth < LARGURA_TABLET, formulario = formulario)
            }
        }
    }
}

/** Até aqui é celular em retrato; a partir daqui, tablet ou celular em paisagem. */
/**
 * A partir daqui é tablet (ou celular em paisagem). Em retrato o formulário vira um cartão flutuante
 * em vez de folha de borda a borda; em paisagem, wallpaper e formulário ficam lado a lado.
 */
private val LARGURA_TABLET: Dp = 600.dp

/** O wallpaper, ancorado no topo para o logo desenhado nele ficar sempre na mesma faixa. */
@Composable
private fun FundoLogin(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.login_fundo),
        // O logo faz parte da imagem: o leitor de tela anuncia a marca.
        contentDescription = stringResource(R.string.acesso_marca),
        contentScale = ContentScale.Crop,
        alignment = Alignment.TopCenter,
        modifier = modifier,
    )
}

@Composable
private fun SobreAImagem(compacto: Boolean, formulario: @Composable () -> Unit) {
    BarraDeStatusSobreImagem(navegacaoSobreImagem = false)
    Box(Modifier.fillMaxSize()) {
        FundoLogin(Modifier.fillMaxSize())
        val forma = if (compacto) {
            RoundedCornerShape(topStart = esc(28), topEnd = esc(28))
        } else {
            RoundedCornerShape(esc(28))
        }
        Surface(
            color = MaterialTheme.colorScheme.background,
            shape = forma,
            shadowElevation = 16.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .then(
                    if (compacto) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier
                            .navigationBarsPadding()
                            .padding(horizontal = esc(24), vertical = esc(32))
                            .widthIn(max = larguraConteudo() + esc(48))
                            .fillMaxWidth()
                    },
                )
                .imePadding(),
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .then(if (compacto) Modifier.navigationBarsPadding() else Modifier),
            ) { formulario() }
        }
    }
}

@Composable
private fun EmDuasColunas(formulario: @Composable () -> Unit) {
    BarraDeStatusSobreImagem(navegacaoSobreImagem = true)
    Row(Modifier.fillMaxSize()) {
        FundoLogin(Modifier.weight(1f).fillMaxHeight())
        // A escala do cartão vem da largura do painel, não da tela inteira.
        TelaResponsiva(Modifier.weight(1f).fillMaxHeight()) {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(esc(28)),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(esc(24))
                        .widthIn(max = larguraConteudo() + esc(48))
                        .heightIn(max = maxHeight),
                ) {
                    Column(Modifier.verticalScroll(rememberScrollState())) { formulario() }
                }
            }
        }
    }
}

/**
 * Hierarquia do formulário: título e subtítulo, os dois campos, uma ação principal (Entrar, cheia),
 * uma secundária (Esqueci minha senha, só texto) e, separada por mais espaço, a de quem ainda não
 * tem conta (Criar conta, contornada).
 */
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
    val cores = MaterialTheme.colorScheme
    Column(Modifier.padding(start = esc(24), end = esc(24), top = esc(28), bottom = esc(24))) {
        Text(
            stringResource(R.string.acesso_login_titulo),
            color = cores.onSurface,
            style = estilo(22, FontWeight.SemiBold),
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(esc(4)))
        Text(stringResource(R.string.acesso_login_subtitulo), color = cores.onSurfaceVariant, style = estilo(13))
        Spacer(Modifier.height(esc(20)))
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
        Spacer(Modifier.height(esc(24)))
        BotaoCheio(
            texto = stringResource(R.string.acesso_entrar),
            aoClicar = aoEntrar,
            carregando = estado.carregando,
        )
        LinkTexto(stringResource(R.string.acesso_esqueci_senha), aoEsquecerSenha)
        Spacer(Modifier.height(esc(12)))
        Text(
            stringResource(R.string.acesso_ainda_sem_conta),
            color = cores.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = estilo(12),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(esc(8)))
        BotaoContorno(
            texto = stringResource(R.string.acesso_criar_conta),
            aoClicar = aoSolicitarAcesso,
            habilitado = !estado.carregando,
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Preview(showBackground = true, widthDp = 800, heightDp = 1280)
@Preview(showBackground = true, widthDp = 1280, heightDp = 800)
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
