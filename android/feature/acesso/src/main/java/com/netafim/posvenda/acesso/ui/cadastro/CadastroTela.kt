package com.netafim.posvenda.acesso.ui.cadastro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netafim.posvenda.acesso.R
import com.netafim.posvenda.acesso.ui.comum.BotaoCheio
import com.netafim.posvenda.acesso.ui.comum.CabecalhoTela
import com.netafim.posvenda.acesso.ui.comum.CampoDoPrototipo
import com.netafim.posvenda.acesso.ui.comum.CampoSenhaDoPrototipo
import com.netafim.posvenda.acesso.ui.comum.MensagemErro
import com.netafim.posvenda.acesso.ui.comum.PaginaComWallpaper
import com.netafim.posvenda.acesso.ui.comum.TituloDaFolha
import com.netafim.posvenda.acesso.ui.comum.TituloDeGrupo
import com.netafim.posvenda.acesso.ui.comum.fundoDeCampoNoVidro
import com.netafim.posvenda.acesso.ui.comum.margensDaFolha
import com.netafim.posvenda.acesso.ui.comum.textoSuaveNoVidro
import com.netafim.posvenda.acesso.ui.comum.TelaResponsiva
import com.netafim.posvenda.acesso.ui.comum.esc
import com.netafim.posvenda.acesso.ui.comum.larguraConteudo

@Composable
fun CadastroRota(viewModel: CadastroViewModel, aoVoltar: () -> Unit) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    CadastroTela(
        estado = estado,
        aoMudar = viewModel::aoMudar,
        aoMudarAceite = viewModel::aoMudarAceite,
        aoEnviar = viewModel::enviar,
        aoVoltar = aoVoltar,
    )
}

/**
 * Cadastro sobre o wallpaper da tela (gota com usuário e +): o ícone fica livre no alto e o
 * formulário sobe numa folha de vidro que rola com a página. Os campos ficam em três grupos (seus
 * dados, sua propriedade, acesso), para o formulário longo não parecer uma lista só.
 *
 * Propriedade e distribuidor ainda não são enviados: a API de cadastro não os recebe.
 */
@Composable
fun CadastroTela(
    estado: CadastroEstado,
    aoMudar: (CampoCadastro, String) -> Unit,
    aoMudarAceite: (Boolean) -> Unit,
    aoEnviar: () -> Unit,
    aoVoltar: () -> Unit,
) {
    if (estado.enviado != null) {
        AguardandoValidacaoTela(aoVoltar)
        return
    }
    PaginaComWallpaper(
        wallpaper = R.drawable.fundo_cadastro,
        descricaoDoWallpaper = stringResource(R.string.acesso_marca),
        alturaDoHero = 0.44f,
        aoVoltar = aoVoltar,
    ) {
        Column(Modifier.margensDaFolha()) {
            Formulario(estado, aoMudar, aoMudarAceite, aoEnviar)
        }
    }
}

@Composable
private fun Formulario(
    estado: CadastroEstado,
    aoMudar: (CampoCadastro, String) -> Unit,
    aoMudarAceite: (Boolean) -> Unit,
    aoEnviar: () -> Unit,
) {
    var senhaVisivel by rememberSaveable { mutableStateOf(false) }
    val bloqueado = estado.carregando
    val descricaoMostrar = stringResource(R.string.acesso_mostrar_senha)
    val descricaoOcultar = stringResource(R.string.acesso_ocultar_senha)
    val fundo = fundoDeCampoNoVidro()
    val rotulo = textoSuaveNoVidro()

    TituloDaFolha(stringResource(R.string.cadastro_titulo), stringResource(R.string.cadastro_subtitulo))
    Spacer(Modifier.height(esc(20)))
    estado.erroGeral?.let {
        MensagemErro(it)
        Spacer(Modifier.height(esc(16)))
    }

    TituloDeGrupo(stringResource(R.string.cadastro_grupo_dados))
    Spacer(Modifier.height(esc(8)))
    CampoDoPrototipo(
        valor = estado.nome,
        aoMudar = { aoMudar(CampoCadastro.NOME, it) },
        rotulo = stringResource(R.string.acesso_nome),
        erro = estado.erros[CampoCadastro.NOME],
        habilitado = !bloqueado,
        fundo = fundo,
        corDoRotulo = rotulo,
    )
    Spacer(Modifier.height(esc(16)))
    CampoDoPrototipo(
        valor = estado.telefone,
        aoMudar = { aoMudar(CampoCadastro.TELEFONE, it) },
        rotulo = stringResource(R.string.acesso_telefone),
        erro = estado.erros[CampoCadastro.TELEFONE],
        tipoTeclado = KeyboardType.Phone,
        habilitado = !bloqueado,
        fundo = fundo,
        corDoRotulo = rotulo,
    )
    Spacer(Modifier.height(esc(16)))
    CampoDoPrototipo(
        valor = estado.email,
        aoMudar = { aoMudar(CampoCadastro.EMAIL, it) },
        rotulo = stringResource(R.string.acesso_email),
        erro = estado.erros[CampoCadastro.EMAIL],
        tipoTeclado = KeyboardType.Email,
        habilitado = !bloqueado,
        fundo = fundo,
        corDoRotulo = rotulo,
    )

    Spacer(Modifier.height(esc(24)))
    TituloDeGrupo(stringResource(R.string.cadastro_grupo_propriedade))
    Spacer(Modifier.height(esc(8)))
    CampoDoPrototipo(
        valor = estado.propriedade,
        aoMudar = { aoMudar(CampoCadastro.PROPRIEDADE, it) },
        rotulo = stringResource(R.string.acesso_propriedade),
        habilitado = !bloqueado,
        fundo = fundo,
        corDoRotulo = rotulo,
    )
    Spacer(Modifier.height(esc(16)))
    CampoDoPrototipo(
        valor = estado.distribuidor,
        aoMudar = { aoMudar(CampoCadastro.DISTRIBUIDOR, it) },
        rotulo = stringResource(R.string.acesso_distribuidor),
        habilitado = !bloqueado,
        fundo = fundo,
        corDoRotulo = rotulo,
    )

    Spacer(Modifier.height(esc(24)))
    TituloDeGrupo(stringResource(R.string.cadastro_grupo_acesso))
    Spacer(Modifier.height(esc(8)))
    CampoSenhaDoPrototipo(
        valor = estado.senha,
        aoMudar = { aoMudar(CampoCadastro.SENHA, it) },
        rotulo = stringResource(R.string.acesso_senha),
        visivel = senhaVisivel,
        aoAlternarVisibilidade = { senhaVisivel = !senhaVisivel },
        descricaoMostrar = descricaoMostrar,
        descricaoOcultar = descricaoOcultar,
        erro = estado.erros[CampoCadastro.SENHA],
        dica = stringResource(R.string.acesso_dica_senha),
        acaoIme = ImeAction.Next,
        habilitado = !bloqueado,
        fundo = fundo,
        corDoRotulo = rotulo,
    )
    Spacer(Modifier.height(esc(16)))
    CampoSenhaDoPrototipo(
        valor = estado.confirmacao,
        aoMudar = { aoMudar(CampoCadastro.CONFIRMACAO, it) },
        rotulo = stringResource(R.string.acesso_confirmar_senha),
        visivel = senhaVisivel,
        aoAlternarVisibilidade = { senhaVisivel = !senhaVisivel },
        descricaoMostrar = descricaoMostrar,
        descricaoOcultar = descricaoOcultar,
        erro = estado.erros[CampoCadastro.CONFIRMACAO],
        habilitado = !bloqueado,
        fundo = fundo,
        corDoRotulo = rotulo,
    )
    Spacer(Modifier.height(esc(12)))
    AceiteTermo(
        aceito = estado.aceiteTermo,
        versao = estado.versaoTermo,
        erro = estado.erros[CampoCadastro.TERMO],
        aoMudar = aoMudarAceite,
        habilitado = !bloqueado,
    )
    Spacer(Modifier.height(esc(24)))
    BotaoCheio(
        texto = stringResource(R.string.acesso_enviar_cadastro),
        aoClicar = aoEnviar,
        carregando = estado.carregando,
    )
}

@Composable
internal fun AceiteTermo(
    aceito: Boolean,
    versao: String?,
    erro: String?,
    aoMudar: (Boolean) -> Unit,
    habilitado: Boolean,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = aceito,
                    enabled = habilitado,
                    role = Role.Checkbox,
                    onValueChange = aoMudar,
                ),
        ) {
            Checkbox(checked = aceito, onCheckedChange = null, enabled = habilitado)
            Text(
                text = if (versao != null) {
                    stringResource(R.string.acesso_aceite_termo, versao)
                } else {
                    stringResource(R.string.acesso_aceite_termo_carregando)
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        erro?.let { MensagemErro(it) }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 828)
@Preview(showBackground = true, widthDp = 800, heightDp = 1280)
@Composable
private fun CadastroTelaPreview() {
    MaterialTheme {
        CadastroTela(
            estado = CadastroEstado(
                nome = "João Produtor",
                versaoTermo = "1.0",
                erros = mapOf(CampoCadastro.EMAIL to "E-mail inválido."),
            ),
            aoMudar = { _, _ -> }, aoMudarAceite = {}, aoEnviar = {}, aoVoltar = {},
        )
    }
}
