package com.netafim.posvenda.acesso.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netafim.posvenda.acesso.R
import com.netafim.posvenda.acesso.dominio.TiposOrganizacao
import com.netafim.posvenda.acesso.dominio.Usuario
import androidx.compose.ui.platform.LocalContext
import com.netafim.posvenda.acesso.ui.comum.AvatarDeIniciais
import com.netafim.posvenda.acesso.ui.comum.CartaoNoVidro
import com.netafim.posvenda.acesso.ui.comum.PaginaComWallpaper
import com.netafim.posvenda.acesso.ui.comum.TituloDaFolha
import com.netafim.posvenda.acesso.ui.comum.margensDaFolha
import com.netafim.posvenda.acesso.ui.comum.textoSuaveNoVidro
import com.netafim.posvenda.acesso.ui.comum.BotaoAcao
import com.netafim.posvenda.acesso.ui.comum.BotaoCheio
import com.netafim.posvenda.acesso.ui.comum.BotaoContorno
import com.netafim.posvenda.acesso.ui.comum.CabecalhoTela
import com.netafim.posvenda.acesso.ui.comum.DetalheDeEstado
import com.netafim.posvenda.acesso.ui.comum.MensagemErro
import com.netafim.posvenda.acesso.ui.comum.OpcaoSelecionavel
import com.netafim.posvenda.acesso.ui.comum.TelaResponsiva
import com.netafim.posvenda.acesso.ui.comum.TituloDeEstado
import com.netafim.posvenda.acesso.ui.comum.estilo
import com.netafim.posvenda.acesso.ui.comum.esc
import com.netafim.posvenda.acesso.ui.comum.larguraConteudo
import com.netafim.posvenda.acesso.ui.perfil.formatarTelefone

@Composable
fun AprovacaoRota(viewModel: AprovacaoViewModel, aoVoltar: () -> Unit) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    AprovacaoCadastroTela(
        estado = estado,
        aoVoltar = aoVoltar,
        aoRecarregar = viewModel::carregar,
        aoAprovar = viewModel::aoAprovar,
        aoRecusar = viewModel::aoRecusar,
        aoEscolherTipo = viewModel::escolherTipo,
        aoEscolherOrganizacao = viewModel::escolherOrganizacao,
        aoEscolherPerfil = viewModel::escolherPerfil,
        aoRecarregarOpcoes = viewModel::carregarOpcoes,
        aoConfirmarAprovacao = viewModel::confirmarAprovacao,
        aoCancelarAprovacao = viewModel::cancelarAprovacao,
        aoConfirmarRecusa = viewModel::confirmarRecusa,
        aoCancelarRecusa = viewModel::cancelarRecusa,
    )
}

/**
 * Aprovação de cadastro sobre o wallpaper da tela (gota com documento e check). Com cadastros na
 * fila, só o logo e o alto da gota ficam à mostra e a lista sobe numa folha de vidro; sem nenhum,
 * o wallpaper aparece inteiro e a folha é só a mensagem. Cada pessoa é um cartão com avatar, dados
 * e os dois botões dentro dele (Recusar e Aprovar), para ficar claro a quem a ação se refere.
 */
@Composable
fun AprovacaoCadastroTela(
    estado: AprovacaoEstado,
    aoVoltar: () -> Unit,
    aoRecarregar: () -> Unit,
    aoAprovar: (Usuario) -> Unit,
    aoRecusar: (Usuario) -> Unit,
    aoEscolherTipo: (String) -> Unit,
    aoEscolherOrganizacao: (Int) -> Unit,
    aoEscolherPerfil: (Int) -> Unit,
    aoRecarregarOpcoes: () -> Unit,
    aoConfirmarAprovacao: () -> Unit,
    aoCancelarAprovacao: () -> Unit,
    aoConfirmarRecusa: () -> Unit,
    aoCancelarRecusa: () -> Unit,
) {
    val vazio = !estado.carregando && estado.erro == null && estado.pendentes.isEmpty()
    val recursos = LocalContext.current.resources
    PaginaComWallpaper(
        wallpaper = R.drawable.fundo_aprovacao,
        descricaoDoWallpaper = stringResource(R.string.acesso_marca),
        alturaDoHero = if (vazio) 0.62f else 0.36f,
        aoVoltar = aoVoltar,
    ) {
        Column(Modifier.margensDaFolha()) {
            val total = estado.pendentes.size
            TituloDaFolha(
                titulo = stringResource(R.string.aprovacao_titulo),
                subtitulo = if (!estado.carregando && estado.erro == null && total > 0) {
                    recursos.getQuantityString(R.plurals.aprovacao_quantidade, total, total)
                } else {
                    null
                },
            )
            Spacer(Modifier.height(esc(20)))
            when {
                estado.carregando -> Box(Modifier.fillMaxWidth().padding(vertical = esc(32)), Alignment.Center) {
                    CircularProgressIndicator()
                }
                estado.erro != null -> Column {
                    MensagemErro(estado.erro)
                    Spacer(Modifier.height(esc(20)))
                    BotaoContorno(stringResource(R.string.acesso_tentar_novamente), aoRecarregar)
                }
                vazio -> Column {
                    Text(
                        stringResource(R.string.aprovacao_vazio_titulo),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = estilo(15, FontWeight.Medium),
                    )
                    Spacer(Modifier.height(esc(6)))
                    Text(stringResource(R.string.aprovacao_vazio_detalhe), color = textoSuaveNoVidro(), style = estilo(13))
                }
                else -> Column(verticalArrangement = Arrangement.spacedBy(esc(12))) {
                    estado.pendentes.forEach { usuario ->
                        CadastroPendente(usuario, aoAprovar = { aoAprovar(usuario) }, aoRecusar = { aoRecusar(usuario) })
                    }
                }
            }
        }
    }

    estado.escolha?.let { escolha ->
        FolhaDeAprovacao(
            escolha = escolha,
            erro = estado.erroAcao,
            processando = estado.processando,
            aoEscolherTipo = aoEscolherTipo,
            aoEscolherOrganizacao = aoEscolherOrganizacao,
            aoEscolherPerfil = aoEscolherPerfil,
            aoRecarregarOpcoes = aoRecarregarOpcoes,
            aoConfirmar = aoConfirmarAprovacao,
            aoCancelar = aoCancelarAprovacao,
        )
    }
    estado.recusando?.let { usuario ->
        ConfirmarRecusa(usuario, estado.erroAcao, estado.processando, aoConfirmarRecusa, aoCancelarRecusa)
    }
}

/** Cartão de um cadastro pendente: avatar, nome, e-mail e telefone, e Recusar e Aprovar embaixo. */
@Composable
private fun CadastroPendente(usuario: Usuario, aoAprovar: () -> Unit, aoRecusar: () -> Unit) {
    val cores = MaterialTheme.colorScheme
    CartaoNoVidro {
        Column(verticalArrangement = Arrangement.spacedBy(esc(14))) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(esc(12))) {
                AvatarDeIniciais(usuario.nome, tamanho = 44)
                Column(Modifier.weight(1f)) {
                    Text(usuario.nome, color = cores.onSurface, style = estilo(15, FontWeight.SemiBold))
                    Text(usuario.email, color = textoSuaveNoVidro(), style = estilo(12))
                    Text(
                        usuario.telefone?.takeIf { it.isNotBlank() }?.let(::formatarTelefone)
                            ?: stringResource(R.string.aprovacao_nao_informado),
                        color = textoSuaveNoVidro(),
                        style = estilo(12),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(esc(12))) {
                BotaoAcao(
                    texto = stringResource(R.string.aprovacao_recusar),
                    aoClicar = aoRecusar,
                    cor = cores.error,
                    preenchido = false,
                    modifier = Modifier.weight(1f),
                )
                // O protótipo usa texto branco sobre o verde; o contraste é baixo (ver o PR).
                BotaoAcao(
                    texto = stringResource(R.string.aprovacao_aprovar),
                    aoClicar = aoAprovar,
                    cor = cores.secondary,
                    preenchido = true,
                    corTexto = Color.White,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Passo extra (fora do protótipo): a API exige organização e perfil para aprovar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolhaDeAprovacao(
    escolha: EscolhaAprovacao,
    erro: String?,
    processando: Boolean,
    aoEscolherTipo: (String) -> Unit,
    aoEscolherOrganizacao: (Int) -> Unit,
    aoEscolherPerfil: (Int) -> Unit,
    aoRecarregarOpcoes: () -> Unit,
    aoConfirmar: () -> Unit,
    aoCancelar: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { if (!processando) aoCancelar() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = larguraConteudo())
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = esc(20), end = esc(20), bottom = esc(24)),
                verticalArrangement = Arrangement.spacedBy(esc(16)),
            ) {
                Column {
                    Text(
                        stringResource(R.string.aprovacao_folha_titulo),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = estilo(18, FontWeight.SemiBold),
                    )
                    Text(
                        escolha.usuario.nome,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = estilo(12),
                    )
                }
                when {
                    escolha.carregandoOpcoes -> Box(Modifier.fillMaxWidth().padding(vertical = esc(24)), Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    escolha.erroOpcoes != null -> {
                        MensagemErro(escolha.erroOpcoes)
                        BotaoContorno(stringResource(R.string.acesso_tentar_novamente), aoRecarregarOpcoes)
                    }
                    else -> Escolhas(escolha, aoEscolherTipo, aoEscolherOrganizacao, aoEscolherPerfil)
                }
                erro?.let { MensagemErro(it) }
                BotaoCheio(
                    texto = stringResource(R.string.aprovacao_confirmar),
                    aoClicar = aoConfirmar,
                    carregando = processando,
                    habilitado = escolha.completa,
                )
            }
        }
    }
}

@Composable
private fun Escolhas(
    escolha: EscolhaAprovacao,
    aoEscolherTipo: (String) -> Unit,
    aoEscolherOrganizacao: (Int) -> Unit,
    aoEscolherPerfil: (Int) -> Unit,
) {
    Secao(stringResource(R.string.aprovacao_tipo)) {
        Row(horizontalArrangement = Arrangement.spacedBy(esc(8))) {
            listOf(
                TiposOrganizacao.NETAFIM to R.string.aprovacao_tipo_netafim,
                TiposOrganizacao.DISTRIBUIDOR to R.string.aprovacao_tipo_distribuidor,
                TiposOrganizacao.CLIENTE to R.string.aprovacao_tipo_cliente,
            ).forEach { (tipo, rotulo) ->
                OpcaoSelecionavel(
                    texto = stringResource(rotulo),
                    selecionada = escolha.tipo == tipo,
                    aoClicar = { aoEscolherTipo(tipo) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    if (escolha.precisaOrganizacao) {
        val titulo = if (escolha.tipo == TiposOrganizacao.DISTRIBUIDOR) {
            R.string.aprovacao_organizacao_distribuidor
        } else {
            R.string.aprovacao_organizacao_cliente
        }
        Secao(stringResource(titulo)) {
            if (escolha.organizacoes.isEmpty()) {
                DetalheDeEstado(stringResource(R.string.aprovacao_sem_opcoes))
            }
            escolha.organizacoes.forEach { opcao ->
                OpcaoSelecionavel(opcao.nome, escolha.idOrganizacao == opcao.id, { aoEscolherOrganizacao(opcao.id) })
            }
        }
    }
    if (escolha.tipo != null) {
        Secao(stringResource(R.string.aprovacao_perfil)) {
            if (escolha.perfis.isEmpty()) {
                DetalheDeEstado(stringResource(R.string.aprovacao_sem_opcoes))
            }
            escolha.perfis.forEach { perfil ->
                OpcaoSelecionavel(perfil.nome, escolha.idPerfil == perfil.id, { aoEscolherPerfil(perfil.id) })
            }
        }
    }
}

@Composable
private fun Secao(titulo: String, conteudo: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(esc(8))) {
        Text(titulo, color = MaterialTheme.colorScheme.onSurfaceVariant, style = estilo(12))
        conteudo()
    }
}

@Composable
private fun ConfirmarRecusa(
    usuario: Usuario,
    erro: String?,
    processando: Boolean,
    aoConfirmar: () -> Unit,
    aoCancelar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!processando) aoCancelar() },
        containerColor = MaterialTheme.colorScheme.background,
        title = { Text(stringResource(R.string.aprovacao_recusar_titulo)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.aprovacao_recusar_texto, usuario.nome))
                erro?.let { MensagemErro(it) }
            }
        },
        confirmButton = {
            TextButton(onClick = aoConfirmar, enabled = !processando) {
                Text(stringResource(R.string.aprovacao_recusar), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = aoCancelar, enabled = !processando) {
                Text(stringResource(R.string.acesso_cancelar))
            }
        },
    )
}

private val PENDENTE_EXEMPLO = Usuario(
    id = 1, nome = "Vanderlei Zucchi Rodas", email = "vanderlei@exemplo.com", telefone = "16997389535",
    situacao = "pendente", perfil = null, organizacao = null, permissoes = emptySet(), exigeTrocaSenha = false,
)

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Preview(showBackground = true, widthDp = 800, heightDp = 1280)
@Preview(showBackground = true, widthDp = 1280, heightDp = 800)
@Composable
private fun AprovacaoCadastroTelaPreview() {
    MaterialTheme {
        AprovacaoCadastroTela(
            estado = AprovacaoEstado(carregando = false, pendentes = listOf(PENDENTE_EXEMPLO)),
            aoVoltar = {}, aoRecarregar = {}, aoAprovar = {}, aoRecusar = {}, aoEscolherTipo = {},
            aoEscolherOrganizacao = {}, aoEscolherPerfil = {}, aoRecarregarOpcoes = {},
            aoConfirmarAprovacao = {}, aoCancelarAprovacao = {}, aoConfirmarRecusa = {}, aoCancelarRecusa = {},
        )
    }
}
