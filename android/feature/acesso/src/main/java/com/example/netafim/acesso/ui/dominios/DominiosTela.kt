package com.example.netafim.acesso.ui.dominios

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.netafim.acesso.R
import com.example.netafim.acesso.dominio.Dominio
import com.example.netafim.acesso.dominio.Opcao
import com.example.netafim.acesso.dominio.TiposOrganizacao
import com.example.netafim.acesso.ui.comum.BotaoAcao
import com.example.netafim.acesso.ui.comum.BotaoCheio
import com.example.netafim.acesso.ui.comum.BotaoContorno
import com.example.netafim.acesso.ui.comum.CampoDoPrototipo
import com.example.netafim.acesso.ui.comum.CartaoDoPrototipo
import com.example.netafim.acesso.ui.comum.DetalheDeEstado
import com.example.netafim.acesso.ui.comum.MensagemErro
import com.example.netafim.acesso.ui.comum.OpcaoSelecionavel
import com.example.netafim.acesso.ui.comum.PaginaComCabecalho
import com.example.netafim.acesso.ui.comum.TextoApoio
import com.example.netafim.acesso.ui.comum.esc
import com.example.netafim.acesso.ui.comum.estilo
import com.example.netafim.acesso.ui.comum.larguraConteudo

@Composable
fun DominiosRota(viewModel: DominiosViewModel) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    DominiosTela(
        estado = estado,
        aoRecarregar = viewModel::carregar,
        aoNovo = viewModel::abrirFormulario,
        aoAlternarAtivo = viewModel::alternarAtivo,
        aoMudarDominio = viewModel::mudarDominio,
        aoEscolherTipo = viewModel::escolherTipo,
        aoEscolherDistribuidor = viewModel::escolherDistribuidor,
        aoSalvar = viewModel::salvar,
        aoFecharFormulario = viewModel::fecharFormulario,
    )
}

/**
 * Domínios de e-mail corporativos (RNF18). Não existe no Figma; usa a base do protótipo (barra azul com o título,
 * cartões e botões de 48 dp). Só o Netafim Admin chega aqui.
 */
@Composable
fun DominiosTela(
    estado: DominiosEstado,
    aoRecarregar: () -> Unit,
    aoNovo: () -> Unit,
    aoAlternarAtivo: (Dominio) -> Unit,
    aoMudarDominio: (String) -> Unit,
    aoEscolherTipo: (String) -> Unit,
    aoEscolherDistribuidor: (Int) -> Unit,
    aoSalvar: () -> Unit,
    aoFecharFormulario: () -> Unit,
) {
    PaginaComCabecalho(stringResource(R.string.dominios_titulo)) {
        when {
            estado.carregando && estado.dominios.isEmpty() -> Box(Modifier.fillMaxWidth().padding(top = esc(40)), Alignment.Center) {
                CircularProgressIndicator()
            }
            estado.erro != null && estado.dominios.isEmpty() -> {
                MensagemErro(estado.erro)
                Spacer(Modifier.height(esc(20)))
                BotaoContorno(stringResource(R.string.acesso_tentar_novamente), aoRecarregar)
            }
            else -> Lista(estado, aoNovo, aoAlternarAtivo)
        }
    }
    if (estado.formularioAberto) {
        Formulario(estado, aoMudarDominio, aoEscolherTipo, aoEscolherDistribuidor, aoSalvar, aoFecharFormulario)
    }
}

@Composable
private fun Lista(
    estado: DominiosEstado,
    aoNovo: () -> Unit,
    aoAlternarAtivo: (Dominio) -> Unit,
) {
    val cores = MaterialTheme.colorScheme
    TextoApoio(stringResource(R.string.dominios_explicacao))
    Spacer(Modifier.height(esc(16)))
    BotaoCheio(stringResource(R.string.dominios_novo), aoNovo, habilitado = estado.processandoId == null)
    Spacer(Modifier.height(esc(16)))
    estado.aviso?.let {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(esc(6))) {
            Icon(Icons.Outlined.Check, contentDescription = null, tint = cores.primary, modifier = Modifier.size(esc(16)))
            Text(stringResource(textoDoAviso(it)), color = cores.primary, style = estilo(13, FontWeight.Medium))
        }
        Spacer(Modifier.height(esc(12)))
    }
    estado.erroAcao?.let {
        MensagemErro(it)
        Spacer(Modifier.height(esc(12)))
    }
    if (estado.dominios.isEmpty()) {
        DetalheDeEstado(stringResource(R.string.dominios_vazio))
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(esc(10))) {
        estado.dominios.forEach { dominio ->
            CartaoDoDominio(dominio, estado.distribuidores, estado.processandoId, aoAlternarAtivo)
        }
    }
}

@Composable
private fun CartaoDoDominio(
    dominio: Dominio,
    distribuidores: List<Opcao>,
    processandoId: Int?,
    aoAlternarAtivo: (Dominio) -> Unit,
) {
    val cores = MaterialTheme.colorScheme
    val livre = processandoId == null
    CartaoDoPrototipo {
        Column(verticalArrangement = Arrangement.spacedBy(esc(6))) {
            Text(dominio.dominio, color = cores.onSurface, style = estilo(14, FontWeight.SemiBold))
            Text(
                rotuloDaOrganizacao(dominio, distribuidores),
                color = cores.onSurfaceVariant,
                style = estilo(11),
            )
            Text(
                stringResource(if (dominio.ativo) R.string.dominios_ativo else R.string.dominios_inativo),
                color = if (dominio.ativo) cores.primary else cores.error,
                style = estilo(11, FontWeight.Medium),
            )
            Spacer(Modifier.height(esc(4)))
            BotaoContorno(
                stringResource(if (dominio.ativo) R.string.dominios_desativar else R.string.dominios_ativar),
                { aoAlternarAtivo(dominio) },
                habilitado = livre,
            )
        }
    }
}

@Composable
private fun rotuloDaOrganizacao(dominio: Dominio, distribuidores: List<Opcao>): String =
    if (dominio.tipoOrganizacao == TiposOrganizacao.NETAFIM) {
        stringResource(R.string.dominios_org_netafim)
    } else {
        val nome = distribuidores.firstOrNull { it.id == dominio.idDistribuidor }?.nome
            ?: dominio.idDistribuidor?.let { stringResource(R.string.dominios_distribuidor_numero, it) }
            ?: stringResource(R.string.dominios_org_distribuidor)
        stringResource(R.string.dominios_org_distribuidor_nome, nome)
    }

private fun textoDoAviso(aviso: AvisoDominio): Int = when (aviso) {
    AvisoDominio.CRIADO -> R.string.dominios_aviso_criado
    AvisoDominio.ATIVADO -> R.string.dominios_aviso_ativado
    AvisoDominio.DESATIVADO -> R.string.dominios_aviso_desativado
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Formulario(
    estado: DominiosEstado,
    aoMudarDominio: (String) -> Unit,
    aoEscolherTipo: (String) -> Unit,
    aoEscolherDistribuidor: (Int) -> Unit,
    aoSalvar: () -> Unit,
    aoFechar: () -> Unit,
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
                    stringResource(R.string.dominios_novo),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = estilo(18, FontWeight.SemiBold),
                )
                estado.erroFormulario?.let { MensagemErro(it) }
                CampoDoPrototipo(
                    valor = estado.textoDominio,
                    aoMudar = aoMudarDominio,
                    rotulo = stringResource(R.string.dominios_campo),
                    dica = stringResource(R.string.dominios_campo_dica),
                    erro = estado.erroDominio,
                    tipoTeclado = KeyboardType.Uri,
                    acaoIme = ImeAction.Done,
                    aoConcluir = aoSalvar,
                    habilitado = !estado.salvando,
                )
                Text(stringResource(R.string.dominios_tipo), color = MaterialTheme.colorScheme.onSurfaceVariant, style = estilo(11))
                OpcaoSelecionavel(
                    texto = stringResource(R.string.dominios_org_netafim),
                    selecionada = estado.tipo == TiposOrganizacao.NETAFIM,
                    aoClicar = { if (!estado.salvando) aoEscolherTipo(TiposOrganizacao.NETAFIM) },
                )
                OpcaoSelecionavel(
                    texto = stringResource(R.string.dominios_org_distribuidor),
                    selecionada = estado.tipo == TiposOrganizacao.DISTRIBUIDOR,
                    aoClicar = { if (!estado.salvando) aoEscolherTipo(TiposOrganizacao.DISTRIBUIDOR) },
                )
                if (estado.tipo == TiposOrganizacao.DISTRIBUIDOR) {
                    Text(stringResource(R.string.dominios_distribuidor), color = MaterialTheme.colorScheme.onSurfaceVariant, style = estilo(11))
                    estado.erroDistribuidor?.let { MensagemErro(it) }
                    if (estado.distribuidores.isEmpty()) {
                        DetalheDeEstado(stringResource(R.string.dominios_sem_distribuidores))
                    } else {
                        estado.distribuidores.forEach { opcao ->
                            OpcaoSelecionavel(
                                texto = opcao.nome,
                                selecionada = estado.idDistribuidor == opcao.id,
                                aoClicar = { if (!estado.salvando) aoEscolherDistribuidor(opcao.id) },
                            )
                        }
                    }
                }
                BotaoCheio(stringResource(R.string.dominios_salvar), aoSalvar, carregando = estado.salvando)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 828)
@Composable
private fun DominiosTelaPreview() {
    MaterialTheme {
        DominiosTela(
            estado = DominiosEstado(
                carregando = false,
                dominios = listOf(
                    Dominio(1, "netafim.com.br", TiposOrganizacao.NETAFIM, null, true),
                    Dominio(2, "agroirriga.com.br", TiposOrganizacao.DISTRIBUIDOR, 3, false),
                ),
                distribuidores = listOf(Opcao(3, "Agro Irriga Ltda")),
            ),
            aoRecarregar = {}, aoNovo = {}, aoAlternarAtivo = {},
            aoMudarDominio = {}, aoEscolherTipo = {}, aoEscolherDistribuidor = {},
            aoSalvar = {}, aoFecharFormulario = {},
        )
    }
}
