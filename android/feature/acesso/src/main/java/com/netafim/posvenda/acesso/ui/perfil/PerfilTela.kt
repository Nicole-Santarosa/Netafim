package com.netafim.posvenda.acesso.ui.perfil

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netafim.posvenda.acesso.AcessoGrafo
import com.netafim.posvenda.acesso.R
import com.netafim.posvenda.acesso.dominio.Organizacao
import com.netafim.posvenda.acesso.dominio.Perfil
import com.netafim.posvenda.acesso.dominio.Usuario
import com.netafim.posvenda.acesso.ui.comum.AvatarDeIniciais
import com.netafim.posvenda.acesso.ui.comum.BotaoCircularSobreImagem
import com.netafim.posvenda.acesso.ui.comum.BotaoContorno
import com.netafim.posvenda.acesso.ui.comum.CartaoNoVidro
import com.netafim.posvenda.acesso.ui.comum.PaginaComWallpaper
import com.netafim.posvenda.acesso.ui.comum.TituloDeGrupo
import com.netafim.posvenda.acesso.ui.comum.esc
import com.netafim.posvenda.acesso.ui.comum.estilo
import com.netafim.posvenda.acesso.ui.comum.margensDaFolha
import com.netafim.posvenda.acesso.ui.comum.textoSuaveNoVidro
import kotlinx.coroutines.launch

@Composable
fun PerfilRota(viewModel: PerfilViewModel, aoVoltar: (() -> Unit)?, aoSair: () -> Unit) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    LaunchedEffect(estado.saiu) {
        if (estado.saiu) aoSair()
    }

    // A foto fica no aparelho (ver FotosDePerfil). A galeria usa o seletor do sistema, sem pedir permissão.
    val fotos = AcessoGrafo.fotos
    val escopo = rememberCoroutineScope()
    val idUsuario = estado.usuario?.id
    val alteracoes by fotos.alteracoes.collectAsStateWithLifecycle()
    val foto by produceState<ImageBitmap?>(null, idUsuario, alteracoes) {
        value = idUsuario?.let { fotos.carregar(it) }?.asImageBitmap()
    }
    var erroFoto by rememberSaveable { mutableStateOf(false) }
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && idUsuario != null) escopo.launch { erroFoto = !fotos.salvar(idUsuario, uri) }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { imagem ->
        if (imagem != null && idUsuario != null) escopo.launch { erroFoto = !fotos.salvar(idUsuario, imagem) }
    }

    PerfilTela(
        estado = estado,
        foto = foto,
        erroFoto = erroFoto,
        aoAtualizar = viewModel::atualizar,
        aoSair = viewModel::sair,
        aoVoltar = aoVoltar,
        aoEscolherDaGaleria = {
            erroFoto = false
            galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        aoTirarFoto = {
            erroFoto = false
            camera.launch(null)
        },
        aoRemoverFoto = { if (idUsuario != null) escopo.launch { fotos.remover(idUsuario) } },
    )
}

/**
 * Meu perfil sobre o wallpaper da tela (gota com usuário e engrenagem): a foto e o nome abrem a
 * folha de vidro; abaixo vêm os dados da conta, o que a pessoa pode fazer e o botão Sair. A seta de
 * voltar e o atualizar ficam sobre a imagem.
 */
@Composable
fun PerfilTela(
    estado: PerfilEstado,
    foto: ImageBitmap?,
    erroFoto: Boolean,
    aoAtualizar: () -> Unit,
    aoSair: () -> Unit,
    aoVoltar: (() -> Unit)?,
    aoEscolherDaGaleria: () -> Unit,
    aoTirarFoto: () -> Unit,
    aoRemoverFoto: () -> Unit,
) {
    var confirmarSaida by rememberSaveable { mutableStateOf(false) }
    var escolhendoFoto by rememberSaveable { mutableStateOf(false) }
    PaginaComWallpaper(
        wallpaper = R.drawable.fundo_meu_perfil,
        descricaoDoWallpaper = stringResource(R.string.acesso_marca),
        alturaDoHero = 0.26f,
        aoVoltar = aoVoltar,
        acaoNoTopo = {
            BotaoCircularSobreImagem(
                Icons.Outlined.Refresh,
                stringResource(R.string.acesso_atualizar),
                aoAtualizar,
                habilitado = !estado.atualizando,
            )
        },
    ) {
        val usuario = estado.usuario
        Column(Modifier.margensDaFolha()) {
            if (estado.atualizando) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(esc(12)))
            }
            if (usuario == null) return@Column
            estado.aviso?.let {
                Aviso(it)
                Spacer(Modifier.height(esc(12)))
            }
            Cabecalho(usuario, foto, erroFoto, aoTocarNaFoto = { escolhendoFoto = true })
            Spacer(Modifier.height(esc(24)))
            CartaoNoVidro {
                Column(verticalArrangement = Arrangement.spacedBy(esc(12))) {
                    Item(stringResource(R.string.acesso_perfil_organizacao), usuario.organizacao?.nome)
                    Item(stringResource(R.string.acesso_perfil_perfil), usuario.perfil?.nome)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Item(stringResource(R.string.acesso_perfil_email), usuario.email)
                    Item(
                        stringResource(R.string.acesso_perfil_telefone),
                        usuario.telefone?.let(::formatarTelefone) ?: stringResource(R.string.acesso_perfil_sem_telefone),
                    )
                    Item(stringResource(R.string.acesso_perfil_situacao), rotuloSituacao(usuario.situacao))
                }
            }
            Permissoes(usuario.permissoes)
            Spacer(Modifier.height(esc(28)))
            BotaoContorno(stringResource(R.string.acesso_sair), { confirmarSaida = true })
        }
    }
    if (escolhendoFoto) {
        EscolhaDaFoto(
            temFoto = foto != null,
            aoGaleria = {
                escolhendoFoto = false
                aoEscolherDaGaleria()
            },
            aoCamera = {
                escolhendoFoto = false
                aoTirarFoto()
            },
            aoRemover = {
                escolhendoFoto = false
                aoRemoverFoto()
            },
            aoFechar = { escolhendoFoto = false },
        )
    }
    if (confirmarSaida) {
        AlertDialog(
            onDismissRequest = { confirmarSaida = false },
            containerColor = MaterialTheme.colorScheme.background,
            title = { Text(stringResource(R.string.acesso_sair_confirmar_titulo)) },
            text = { Text(stringResource(R.string.acesso_sair_confirmar_texto)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmarSaida = false
                    aoSair()
                }) { Text(stringResource(R.string.acesso_sair)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmarSaida = false }) {
                    Text(stringResource(R.string.acesso_cancelar))
                }
            },
        )
    }
}

/** Foto grande no centro, com o selo de câmera que mostra que dá para trocar; nome e perfil embaixo. */
@Composable
private fun Cabecalho(usuario: Usuario, foto: ImageBitmap?, erroFoto: Boolean, aoTocarNaFoto: () -> Unit) {
    val cores = MaterialTheme.colorScheme
    val descricao = stringResource(R.string.perfil_foto_descricao, usuario.nome)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Box(
                Modifier
                    .clip(CircleShape)
                    .semantics { contentDescription = descricao }
                    .clickable(role = Role.Button, onClick = aoTocarNaFoto),
            ) {
                AvatarDeIniciais(usuario.nome, tamanho = 104, foto = foto)
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(esc(36))
                    .clip(CircleShape)
                    .background(cores.primary)
                    .border(2.dp, Color.White, CircleShape)
                    .clearAndSetSemantics { }
                    .clickable(onClick = aoTocarNaFoto),
            ) {
                Icon(
                    Icons.Outlined.CameraAlt,
                    contentDescription = null,
                    tint = cores.onPrimary,
                    modifier = Modifier.size(esc(18)),
                )
            }
        }
        if (erroFoto) {
            Spacer(Modifier.height(esc(8)))
            Text(
                stringResource(R.string.perfil_foto_erro),
                color = cores.error,
                style = estilo(12),
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(esc(12)))
        Text(
            usuario.nome,
            color = cores.onSurface,
            style = estilo(22, FontWeight.SemiBold),
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        usuario.perfil?.let {
            Text(it.nome, color = textoSuaveNoVidro(), style = estilo(13), textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EscolhaDaFoto(
    temFoto: Boolean,
    aoGaleria: () -> Unit,
    aoCamera: () -> Unit,
    aoRemover: () -> Unit,
    aoFechar: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = aoFechar,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(start = esc(20), end = esc(20), bottom = esc(16)),
        ) {
            Text(
                stringResource(R.string.perfil_foto_titulo),
                color = MaterialTheme.colorScheme.onSurface,
                style = estilo(18, FontWeight.SemiBold),
            )
            Text(
                stringResource(R.string.perfil_foto_so_aparelho),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = estilo(12),
            )
            Spacer(Modifier.height(esc(8)))
            OpcaoDaFoto(Icons.Outlined.PhotoLibrary, stringResource(R.string.perfil_foto_galeria), aoGaleria)
            OpcaoDaFoto(Icons.Outlined.CameraAlt, stringResource(R.string.perfil_foto_camera), aoCamera)
            if (temFoto) {
                OpcaoDaFoto(
                    Icons.Outlined.DeleteOutline,
                    stringResource(R.string.perfil_foto_remover),
                    aoRemover,
                    destrutiva = true,
                )
            }
        }
    }
}

@Composable
private fun OpcaoDaFoto(icone: ImageVector, texto: String, aoClicar: () -> Unit, destrutiva: Boolean = false) {
    val cor = if (destrutiva) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(esc(16)),
        modifier = Modifier
            .fillMaxWidth()
            .height(esc(56))
            .clickable(role = Role.Button, onClick = aoClicar),
    ) {
        Icon(icone, contentDescription = null, tint = cor, modifier = Modifier.size(esc(24)))
        Text(texto, color = cor, style = estilo(15, FontWeight.Medium))
    }
}

@Composable
private fun Item(rotulo: String, valor: String?) {
    Column {
        Text(rotulo, color = textoSuaveNoVidro(), style = estilo(12))
        Text(valor ?: "—", color = MaterialTheme.colorScheme.onSurface, style = estilo(15, FontWeight.Medium))
    }
}

@Composable
private fun Permissoes(codigos: Set<String>) {
    val itens = DescricaoPermissoes.para(codigos)
    if (itens.isEmpty()) return
    Spacer(Modifier.height(esc(24)))
    TituloDeGrupo(stringResource(R.string.acesso_perfil_permissoes))
    Spacer(Modifier.height(esc(8)))
    Column(verticalArrangement = Arrangement.spacedBy(esc(8))) {
        itens.forEach { descricao ->
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(esc(10))) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(esc(18)),
                )
                Text(descricao, color = MaterialTheme.colorScheme.onSurface, style = estilo(13))
            }
        }
    }
}

@Composable
private fun Aviso(texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(esc(8))) {
        Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = textoSuaveNoVidro(), modifier = Modifier.size(esc(18)))
        Text(texto, color = textoSuaveNoVidro(), style = estilo(12))
    }
}

private fun rotuloSituacao(situacao: String) = when (situacao) {
    "ativo" -> "Ativo"
    "pendente" -> "Aguardando aprovação"
    "inativo" -> "Inativo"
    "bloqueado" -> "Bloqueado"
    "recusado" -> "Recusado"
    else -> situacao
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Preview(showBackground = true, widthDp = 800, heightDp = 1280)
@Preview(showBackground = true, widthDp = 1280, heightDp = 800)
@Composable
private fun PerfilTelaPreview() {
    MaterialTheme {
        PerfilTela(
            estado = PerfilEstado(
                usuario = Usuario(
                    id = 1, nome = "Ana Souza", email = "ana@agroirriga.com.br",
                    telefone = "16999998888", situacao = "ativo",
                    perfil = Perfil(3, "Distribuidor Admin", "distribuidor", "admin"),
                    organizacao = Organizacao("distribuidor", 1, "Agro Irriga Ltda"),
                    permissoes = setOf("abrir_atendimento", "vincular_tecnico", "criar_usuarios"),
                    exigeTrocaSenha = false,
                ),
                aviso = "Sem conexão: mostrando os dados salvos no aparelho.",
            ),
            foto = null,
            erroFoto = false,
            aoAtualizar = {},
            aoSair = {},
            aoVoltar = {},
            aoEscolherDaGaleria = {},
            aoTirarFoto = {},
            aoRemoverFoto = {},
        )
    }
}
