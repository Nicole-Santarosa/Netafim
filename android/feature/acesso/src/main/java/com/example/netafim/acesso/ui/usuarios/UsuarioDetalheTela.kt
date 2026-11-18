package com.example.netafim.acesso.ui.usuarios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.netafim.acesso.R
import com.example.netafim.acesso.dominio.AcoesDoUsuario
import com.example.netafim.acesso.dominio.Aparelho
import com.example.netafim.acesso.dominio.Organizacao
import com.example.netafim.acesso.dominio.Perfil
import com.example.netafim.acesso.dominio.SituacoesUsuario
import com.example.netafim.acesso.dominio.Usuario
import com.example.netafim.acesso.ui.comum.BotaoAcao
import com.example.netafim.acesso.ui.comum.BotaoCheio
import com.example.netafim.acesso.ui.comum.BotaoContorno
import com.example.netafim.acesso.ui.comum.CartaoDoPrototipo
import com.example.netafim.acesso.ui.comum.DetalheDeEstado
import com.example.netafim.acesso.ui.comum.MensagemErro
import com.example.netafim.acesso.ui.comum.OpcaoSelecionavel
import com.example.netafim.acesso.ui.comum.PaginaComCabecalho
import com.example.netafim.acesso.ui.comum.esc
import com.example.netafim.acesso.ui.comum.estilo
import com.example.netafim.acesso.ui.comum.larguraConteudo
import com.example.netafim.acesso.ui.perfil.formatarTelefone
import com.example.netafim.acesso.ui.privacidade.formatarDataDoPedido

@Composable
fun UsuarioDetalheRota(viewModel: UsuarioDetalheViewModel, aoVoltar: () -> Unit, aoEditar: () -> Unit) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.aoRetomar() }
    LaunchedEffect(estado.excluido) {
        if (estado.excluido) aoVoltar()
    }
    UsuarioDetalheTela(
        estado = estado,
        aoRecarregar = viewModel::carregar,
        aoEditar = aoEditar,
        aoAtivar = viewModel::ativar,
        aoDesativar = viewModel::pedirDesativar,
        aoExcluir = viewModel::pedirExcluir,
        aoReenviarConvite = viewModel::reenviarConvite,
        aoDefinirPerfil = viewModel::abrirPerfis,
        aoConfirmar = viewModel::confirmar,
        aoCancelar = viewModel::cancelarConfirmacao,
        aoEscolherPerfil = viewModel::escolherPerfil,
        aoFecharPerfis = viewModel::fecharPerfis,
        aoRecarregarPerfis = viewModel::abrirPerfis,
        aoRedefinirMfa = viewModel::pedirRedefinirMfa,
        aoVerAparelhos = viewModel::abrirAparelhos,
        aoFecharAparelhos = viewModel::fecharAparelhos,
        aoRecarregarAparelhos = viewModel::abrirAparelhos,
        aoRevogarAparelho = viewModel::pedirRevogarAparelho,
        aoConfirmarRevogar = viewModel::confirmarRevogarAparelho,
        aoCancelarRevogar = viewModel::cancelarRevogarAparelho,
    )
}

/**
 * Detalhe do usuário para quem administra. Não existe no Figma; usa a base do protótipo (barra azul
 * com o título, cartão de dados como o de Aprovação de cadastro e botões de 48 dp). Só aparecem as ações
 * que o perfil de quem está logado pode fazer; ativar e desativar, excluir e definir perfil pedem confirmação.
 */
@Composable
fun UsuarioDetalheTela(
    estado: UsuarioDetalheEstado,
    aoRecarregar: () -> Unit,
    aoEditar: () -> Unit,
    aoAtivar: () -> Unit,
    aoDesativar: () -> Unit,
    aoExcluir: () -> Unit,
    aoReenviarConvite: () -> Unit,
    aoDefinirPerfil: () -> Unit,
    aoConfirmar: () -> Unit,
    aoCancelar: () -> Unit,
    aoEscolherPerfil: (Int) -> Unit,
    aoFecharPerfis: () -> Unit,
    aoRecarregarPerfis: () -> Unit,
    aoRedefinirMfa: () -> Unit = {},
    aoVerAparelhos: () -> Unit = {},
    aoFecharAparelhos: () -> Unit = {},
    aoRecarregarAparelhos: () -> Unit = {},
    aoRevogarAparelho: (Aparelho) -> Unit = {},
    aoConfirmarRevogar: () -> Unit = {},
    aoCancelarRevogar: () -> Unit = {},
) {
    PaginaComCabecalho(stringResource(R.string.usuario_detalhe_titulo)) {
        val usuario = estado.usuario
        when {
            estado.carregando && usuario == null -> Box(Modifier.fillMaxWidth().padding(top = esc(40)), Alignment.Center) {
                CircularProgressIndicator()
            }
            usuario == null -> {
                MensagemErro(estado.erro ?: stringResource(R.string.usuario_erro_carregar))
                Spacer(Modifier.height(esc(20)))
                BotaoContorno(stringResource(R.string.acesso_tentar_novamente), aoRecarregar)
            }
            else -> Conteudo(
                estado, usuario, aoEditar, aoAtivar, aoDesativar, aoExcluir, aoReenviarConvite, aoDefinirPerfil,
                aoRedefinirMfa, aoVerAparelhos,
            )
        }
    }
    estado.confirmando?.let { acao ->
        ConfirmarAcao(acao, estado.usuario?.nome.orEmpty(), estado.processando, aoConfirmar, aoCancelar)
    }
    if (estado.escolhendoPerfil) {
        FolhaDePerfil(estado, aoEscolherPerfil, aoFecharPerfis, aoRecarregarPerfis)
    }
    if (estado.aparelhosAbertos) {
        FolhaDeAparelhos(estado, aoFecharAparelhos, aoRecarregarAparelhos, aoRevogarAparelho)
    }
    estado.aparelhoParaRevogar?.let {
        ConfirmarRevogar(estado.revogando, aoConfirmarRevogar, aoCancelarRevogar)
    }
}

@Composable
private fun Conteudo(
    estado: UsuarioDetalheEstado,
    usuario: Usuario,
    aoEditar: () -> Unit,
    aoAtivar: () -> Unit,
    aoDesativar: () -> Unit,
    aoExcluir: () -> Unit,
    aoReenviarConvite: () -> Unit,
    aoDefinirPerfil: () -> Unit,
    aoRedefinirMfa: () -> Unit,
    aoVerAparelhos: () -> Unit,
) {
    val cores = MaterialTheme.colorScheme
    CartaoDoPrototipo {
        Column(verticalArrangement = Arrangement.spacedBy(esc(10))) {
            Dado(stringResource(R.string.aprovacao_nome), usuario.nome)
            Dado(stringResource(R.string.aprovacao_email), usuario.email)
            Dado(
                stringResource(R.string.aprovacao_telefone),
                usuario.telefone?.takeIf { it.isNotBlank() }?.let(::formatarTelefone)
                    ?: stringResource(R.string.aprovacao_nao_informado),
            )
            Dado(stringResource(R.string.acesso_perfil_organizacao), usuario.organizacao?.nome ?: stringResource(R.string.usuario_sem_organizacao))
            Dado(stringResource(R.string.acesso_perfil_perfil), usuario.perfil?.nome ?: stringResource(R.string.usuario_sem_perfil))
            Dado(stringResource(R.string.acesso_perfil_situacao), rotuloDaSituacao(usuario.situacao))
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
        DetalheDeEstado(stringResource(R.string.usuario_sem_acoes))
        return
    }
    val livre = !estado.processando
    Column(verticalArrangement = Arrangement.spacedBy(esc(12))) {
        if (acoes.editar) BotaoContorno(stringResource(R.string.usuario_acao_editar), aoEditar, habilitado = livre)
        if (acoes.definirPerfil) BotaoContorno(stringResource(R.string.usuario_acao_perfil), aoDefinirPerfil, habilitado = livre)
        if (acoes.reenviarConvite) BotaoContorno(stringResource(R.string.usuario_acao_convite), aoReenviarConvite, habilitado = livre)
        if (acoes.verAparelhos) BotaoContorno(stringResource(R.string.usuario_acao_aparelhos), aoVerAparelhos, habilitado = livre)
        if (acoes.redefinirMfa) BotaoContorno(stringResource(R.string.usuario_acao_mfa), aoRedefinirMfa, habilitado = livre)
        if (acoes.ativar) BotaoCheio(stringResource(R.string.usuario_acao_ativar), aoAtivar, carregando = estado.processando)
        if (acoes.desativar) BotaoContorno(stringResource(R.string.usuario_acao_desativar), aoDesativar, habilitado = livre)
        if (acoes.excluir) {
            BotaoAcao(
                texto = stringResource(R.string.usuario_acao_excluir),
                aoClicar = { if (livre) aoExcluir() },
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

@Composable
private fun rotuloDaSituacao(situacao: String): String = stringResource(
    when (situacao) {
        SituacoesUsuario.ATIVO -> R.string.usuarios_situacao_ativo
        SituacoesUsuario.PENDENTE -> R.string.usuarios_situacao_pendente
        SituacoesUsuario.RECUSADO -> R.string.usuarios_situacao_recusado
        SituacoesUsuario.INATIVO -> R.string.usuarios_situacao_inativo
        SituacoesUsuario.BLOQUEADO -> R.string.usuarios_situacao_bloqueado
        else -> R.string.usuarios_situacao_desconhecida
    },
)

private fun textoDoAviso(aviso: AvisoUsuario): Int = when (aviso) {
    AvisoUsuario.ATIVADO -> R.string.usuario_aviso_ativado
    AvisoUsuario.DESATIVADO -> R.string.usuario_aviso_desativado
    AvisoUsuario.PERFIL_ALTERADO -> R.string.usuario_aviso_perfil
    AvisoUsuario.CONVITE_REENVIADO -> R.string.usuario_aviso_convite
    AvisoUsuario.MFA_REDEFINIDO -> R.string.usuario_aviso_mfa
    AvisoUsuario.APARELHO_REVOGADO -> R.string.usuario_aviso_aparelho
}

@Composable
private fun ConfirmarAcao(
    acao: ConfirmacaoUsuario,
    nome: String,
    processando: Boolean,
    aoConfirmar: () -> Unit,
    aoCancelar: () -> Unit,
) {
    val titulo = when (acao) {
        ConfirmacaoUsuario.EXCLUIR -> R.string.usuario_excluir_titulo
        ConfirmacaoUsuario.DESATIVAR -> R.string.usuario_desativar_titulo
        ConfirmacaoUsuario.REDEFINIR_MFA -> R.string.usuario_mfa_titulo
    }
    val texto = when (acao) {
        ConfirmacaoUsuario.EXCLUIR -> R.string.usuario_excluir_texto
        ConfirmacaoUsuario.DESATIVAR -> R.string.usuario_desativar_texto
        ConfirmacaoUsuario.REDEFINIR_MFA -> R.string.usuario_mfa_texto
    }
    val botao = when (acao) {
        ConfirmacaoUsuario.EXCLUIR -> R.string.usuario_confirmar_excluir
        ConfirmacaoUsuario.DESATIVAR -> R.string.usuario_confirmar_desativar
        ConfirmacaoUsuario.REDEFINIR_MFA -> R.string.usuario_confirmar_mfa
    }
    AlertDialog(
        onDismissRequest = { if (!processando) aoCancelar() },
        containerColor = MaterialTheme.colorScheme.background,
        title = { Text(stringResource(titulo, nome)) },
        text = { Text(stringResource(texto)) },
        confirmButton = {
            TextButton(onClick = aoConfirmar, enabled = !processando) {
                Text(stringResource(botao), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = aoCancelar, enabled = !processando) { Text(stringResource(R.string.acesso_cancelar)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolhaDePerfil(
    estado: UsuarioDetalheEstado,
    aoEscolher: (Int) -> Unit,
    aoFechar: () -> Unit,
    aoRecarregar: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = aoFechar,
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
                verticalArrangement = Arrangement.spacedBy(esc(12)),
            ) {
                Text(
                    stringResource(R.string.usuario_perfil_titulo),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = estilo(18, FontWeight.SemiBold),
                )
                estado.erroPerfis?.let { MensagemErro(it) }
                when {
                    estado.carregandoPerfis -> Box(Modifier.fillMaxWidth().padding(vertical = esc(24)), Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    estado.perfis.isEmpty() && estado.erroPerfis != null ->
                        BotaoContorno(stringResource(R.string.acesso_tentar_novamente), aoRecarregar)
                    estado.perfis.isEmpty() -> DetalheDeEstado(stringResource(R.string.aprovacao_sem_opcoes))
                    else -> estado.perfis.forEach { perfil ->
                        OpcaoSelecionavel(
                            texto = perfil.nome,
                            selecionada = estado.usuario?.perfil?.id == perfil.id,
                            aoClicar = { if (!estado.processando) aoEscolher(perfil.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmarRevogar(revogando: Boolean, aoConfirmar: () -> Unit, aoCancelar: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!revogando) aoCancelar() },
        containerColor = MaterialTheme.colorScheme.background,
        title = { Text(stringResource(R.string.usuario_aparelho_revogar_titulo)) },
        text = { Text(stringResource(R.string.usuario_aparelho_revogar_texto)) },
        confirmButton = {
            TextButton(onClick = aoConfirmar, enabled = !revogando) {
                Text(stringResource(R.string.usuario_confirmar_revogar), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = aoCancelar, enabled = !revogando) { Text(stringResource(R.string.acesso_cancelar)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolhaDeAparelhos(
    estado: UsuarioDetalheEstado,
    aoFechar: () -> Unit,
    aoRecarregar: () -> Unit,
    aoRevogar: (Aparelho) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = aoFechar,
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
                verticalArrangement = Arrangement.spacedBy(esc(12)),
            ) {
                Text(
                    stringResource(R.string.usuario_aparelhos_titulo),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = estilo(18, FontWeight.SemiBold),
                )
                estado.erroAparelhos?.let { MensagemErro(it) }
                when {
                    estado.carregandoAparelhos -> Box(Modifier.fillMaxWidth().padding(vertical = esc(24)), Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    estado.aparelhos.isEmpty() && estado.erroAparelhos != null ->
                        BotaoContorno(stringResource(R.string.acesso_tentar_novamente), aoRecarregar)
                    estado.aparelhos.isEmpty() -> DetalheDeEstado(stringResource(R.string.usuario_aparelhos_vazio))
                    else -> estado.aparelhos.forEach { aparelho ->
                        CartaoDoPrototipo {
                            Column(verticalArrangement = Arrangement.spacedBy(esc(6))) {
                                Text(
                                    aparelho.modelo?.takeIf { it.isNotBlank() } ?: stringResource(R.string.usuario_aparelho_sem_nome),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = estilo(13, FontWeight.Medium),
                                )
                                aparelho.versaoApp?.takeIf { it.isNotBlank() }?.let {
                                    Text(
                                        stringResource(R.string.usuario_aparelho_versao, it),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = estilo(11),
                                    )
                                }
                                Text(
                                    aparelho.ultimaSincronizacao?.let { stringResource(R.string.usuario_aparelho_ultimo, formatarDataDoPedido(it)) }
                                        ?: stringResource(R.string.usuario_aparelho_nunca),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = estilo(11),
                                )
                                if (aparelho.revogado) {
                                    Text(
                                        stringResource(R.string.usuario_aparelho_revogado),
                                        color = MaterialTheme.colorScheme.error,
                                        style = estilo(11, FontWeight.Medium),
                                    )
                                } else if (estado.acoes.revogarAparelhos) {
                                    BotaoAcao(
                                        texto = stringResource(R.string.usuario_aparelho_revogar),
                                        aoClicar = { if (!estado.revogando) aoRevogar(aparelho) },
                                        cor = MaterialTheme.colorScheme.error,
                                        preenchido = false,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun exemplo(situacao: String, admin: Boolean = false) = Usuario(
    id = 2, nome = "Ana Paula Reis", email = "ana@agroirriga.com.br", telefone = "16997389535", situacao = situacao,
    perfil = Perfil(3, if (admin) "Distribuidor Admin" else "Distribuidor Usuário", "distribuidor", if (admin) "admin" else "usuario"),
    organizacao = Organizacao("distribuidor", 1, "Agro Irriga Ltda"), permissoes = emptySet(), exigeTrocaSenha = false,
)

@Preview(showBackground = true, widthDp = 360, heightDp = 828)
@Composable
private fun UsuarioDetalheTelaPreview() {
    MaterialTheme {
        UsuarioDetalheTela(
            estado = UsuarioDetalheEstado(
                usuario = exemplo(SituacoesUsuario.ATIVO),
                acoes = AcoesDoUsuario(editar = true, desativar = true, definirPerfil = true, excluir = true),
                carregando = false,
                aviso = AvisoUsuario.PERFIL_ALTERADO,
            ),
            aoRecarregar = {}, aoEditar = {}, aoAtivar = {}, aoDesativar = {}, aoExcluir = {}, aoReenviarConvite = {},
            aoDefinirPerfil = {}, aoConfirmar = {}, aoCancelar = {}, aoEscolherPerfil = {}, aoFecharPerfis = {},
            aoRecarregarPerfis = {},
        )
    }
}
