package com.netafim.posvenda.acesso.ui.usuarios

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netafim.posvenda.acesso.R
import com.netafim.posvenda.acesso.dominio.Perfil
import com.netafim.posvenda.acesso.dominio.Usuario
import com.netafim.posvenda.acesso.ui.comum.BarraDeStatusSobreImagem
import com.netafim.posvenda.acesso.ui.comum.BotaoCheio
import com.netafim.posvenda.acesso.ui.comum.BotaoContorno
import com.netafim.posvenda.acesso.ui.comum.CirculoDoPrototipo
import com.netafim.posvenda.acesso.ui.comum.DetalheDeEstado
import com.netafim.posvenda.acesso.ui.comum.MensagemErro
import com.netafim.posvenda.acesso.ui.comum.TelaResponsiva
import com.netafim.posvenda.acesso.ui.comum.TituloDeEstado
import com.netafim.posvenda.acesso.ui.comum.esc
import com.netafim.posvenda.acesso.ui.comum.estilo
import com.netafim.posvenda.acesso.ui.comum.larguraConteudo

@Composable
fun GestaoUsuariosRota(viewModel: UsuariosViewModel, aoAdicionar: () -> Unit) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.aoRetomar() }
    GestaoUsuariosTela(estado = estado, aoAdicionar = aoAdicionar, aoRecarregar = viewModel::carregar)
}

/**
 * Gestão de usuários como tela de trabalho: o que importa é a lista, então o pôster da marca vira
 * só uma faixa fina no topo e o resto da tela é de quem usa.
 *
 * - Faixa da marca, título (texto de verdade, para o leitor de tela e para traduzir) e um resumo:
 *   organização, quantos usuários e quantos aguardam aprovação.
 * - Cada usuário num cartão com avatar de iniciais, nome, e-mail, o papel (Admin ou Usuário) e, se
 *   não estiver ativo, a situação (Pendente, Recusado...), sempre com texto e não só com cor.
 * - Busca por nome ou e-mail quando a lista é longa.
 * - "+ Adicionar usuário" fica numa barra fixa embaixo: nunca some no fim de uma lista grande.
 * - Paisagem: o pôster inteiro à esquerda e a lista num cartão sobre o azul da marca à direita.
 */
@Composable
fun GestaoUsuariosTela(
    estado: UsuariosEstado,
    aoAdicionar: () -> Unit,
    aoRecarregar: () -> Unit,
) {
    TelaResponsiva(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (maxWidth >= LARGURA_TABLET && maxWidth > maxHeight) {
                EmDuasColunas(estado, aoAdicionar, aoRecarregar)
            } else {
                BarraDeStatusSobreImagem(navegacaoSobreImagem = false)
                val larguraTela = maxWidth
                Column(Modifier.fillMaxSize()) {
                    Faixa(larguraTela = larguraTela)
                    Corpo(estado, aoRecarregar, titulo = stringResource(R.string.usuarios_titulo), modifier = Modifier.weight(1f))
                    if (estado.podeAdicionar) BarraDeAcao(aoAdicionar, comMargemDoSistema = true)
                }
            }
        }
    }
}

/** Em paisagem, a partir daqui, pôster e lista ficam lado a lado. */
private val LARGURA_TABLET: Dp = 600.dp

/** A faixa (parte de cima do pôster) tem 941 x 300 px. */
private const val ALTURA_DA_FAIXA = 300f / 941f

/** O topo da imagem, para estender o azul por baixo da barra de status sem emenda. */
private val AZUL_DO_TOPO = Brush.horizontalGradient(
    colorStops = arrayOf(0f to Color(0xFF0D4179), 0.3f to Color(0xFF012A5C), 1f to Color(0xFF01285A)),
)

private val SombraCartao = Color(0x1A0F1426)

/** Faixa da marca. Em tela larga a altura é limitada e o corte tira o excesso de cima (a curva fica). */
@Composable
private fun Faixa(larguraTela: Dp) {
    val altura = minOf(larguraTela * ALTURA_DA_FAIXA, esc(124))
    Column(Modifier.fillMaxWidth().background(AZUL_DO_TOPO).statusBarsPadding()) {
        Image(
            painter = painterResource(R.drawable.gestao_usuarios_faixa),
            contentDescription = stringResource(R.string.acesso_marca),
            contentScale = ContentScale.FillWidth,
            alignment = Alignment.BottomCenter,
            modifier = Modifier.fillMaxWidth().height(altura),
        )
    }
}

@Composable
private fun EmDuasColunas(estado: UsuariosEstado, aoAdicionar: () -> Unit, aoRecarregar: () -> Unit) {
    BarraDeStatusSobreImagem(navegacaoSobreImagem = true)
    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary)) {
        Image(
            painter = painterResource(R.drawable.gestao_usuarios_fundo),
            // O pôster traz o título "Gestão de Usuários" desenhado.
            contentDescription = stringResource(R.string.usuarios_titulo),
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        // A escala do cartão vem da largura do painel, não da tela inteira.
        TelaResponsiva(Modifier.weight(1f).fillMaxHeight()) {
            Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(esc(24)), Alignment.Center) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(esc(28)),
                    shadowElevation = 8.dp,
                    modifier = Modifier.widthIn(max = larguraConteudo() + esc(48)).fillMaxSize(),
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Corpo(
                            estado, aoRecarregar,
                            titulo = estado.organizacao ?: stringResource(R.string.usuarios_titulo),
                            modifier = Modifier.weight(1f),
                            comTopo = esc(24),
                            comOrganizacao = false,
                        )
                        if (estado.podeAdicionar) BarraDeAcao(aoAdicionar, comMargemDoSistema = false)
                    }
                }
            }
        }
    }
}

/** Título, resumo, busca e a lista (ou o carregamento, o erro e o estado vazio). */
@Composable
private fun Corpo(
    estado: UsuariosEstado,
    aoRecarregar: () -> Unit,
    titulo: String,
    modifier: Modifier,
    comTopo: Dp = esc(20),
    comOrganizacao: Boolean = true,
) {
    var busca by rememberSaveable { mutableStateOf("") }
    val filtrados = remember(estado.usuarios, busca) { estado.usuarios.filtrarPor(busca) }
    val mostrarBusca = estado.usuarios.size >= LIMITE_PARA_BUSCA

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(top = comTopo, bottom = esc(16)),
        verticalArrangement = Arrangement.spacedBy(esc(8)),
    ) {
        item {
            Centralizado {
                Column {
                    Text(
                        titulo,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = estilo(22, FontWeight.SemiBold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (!estado.carregando && estado.erro == null) {
                        Spacer(Modifier.height(esc(2)))
                        Text(resumoDe(estado, comOrganizacao), color = MaterialTheme.colorScheme.onSurfaceVariant, style = estilo(13))
                    }
                    Spacer(Modifier.height(esc(8)))
                }
            }
        }
        if (mostrarBusca) {
            item {
                Centralizado { CampoBusca(busca, { busca = it }); Spacer(Modifier.height(esc(4))) }
            }
        }
        when {
            estado.carregando && estado.usuarios.isEmpty() -> item {
                Centralizado { Box(Modifier.fillMaxWidth().padding(top = esc(40)), Alignment.Center) { CircularProgressIndicator() } }
            }
            estado.erro != null && estado.usuarios.isEmpty() -> item {
                Centralizado {
                    Column {
                        MensagemErro(estado.erro)
                        Spacer(Modifier.height(esc(20)))
                        BotaoContorno(stringResource(R.string.acesso_tentar_novamente), aoRecarregar)
                    }
                }
            }
            estado.usuarios.isEmpty() -> item {
                Centralizado {
                    Column(Modifier.fillMaxWidth().padding(top = esc(32)), horizontalAlignment = Alignment.CenterHorizontally) {
                        CirculoDoPrototipo()
                        Spacer(Modifier.height(esc(24)))
                        TituloDeEstado(stringResource(R.string.usuarios_vazio_titulo))
                        Spacer(Modifier.height(esc(12)))
                        DetalheDeEstado(stringResource(R.string.usuarios_vazio_detalhe))
                    }
                }
            }
            filtrados.isEmpty() -> item {
                Centralizado {
                    DetalheDeEstado(stringResource(R.string.usuarios_sem_resultado, busca), Modifier.padding(top = esc(24)))
                }
            }
            else -> items(filtrados, key = { it.id }) { Centralizado { CartaoUsuario(it) } }
        }
    }
}

/** A busca só aparece quando a lista é longa o bastante para precisar dela. */
private const val LIMITE_PARA_BUSCA = 6

/** Quantos usuários e quantos pendentes, depois do nome da organização (quando há). */
@Composable
private fun resumoDe(estado: UsuariosEstado, comOrganizacao: Boolean): String {
    val recursos = LocalContext.current.resources
    val total = estado.usuarios.size
    val pendentes = estado.usuarios.count { it.situacao == "pendente" }
    val partes = buildList {
        if (comOrganizacao) estado.organizacao?.let { add(it) }
        add(recursos.getQuantityString(R.plurals.usuarios_quantidade, total, total))
        if (pendentes > 0) add(recursos.getQuantityString(R.plurals.usuarios_pendentes_quantidade, pendentes, pendentes))
    }
    return partes.joinToString(" · ")
}

/** Mantém o conteúdo na largura do protótipo (escalada), centralizado, com a margem lateral dele. */
@Composable
private fun Centralizado(conteudo: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = larguraConteudo()).fillMaxWidth().padding(horizontal = esc(20))) { conteudo() }
    }
}

@Composable
private fun CampoBusca(valor: String, aoMudar: (String) -> Unit) {
    val cores = MaterialTheme.colorScheme
    val forma = RoundedCornerShape(esc(8))
    val dica = stringResource(R.string.usuarios_buscar)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(esc(44))
            .background(cores.surfaceVariant, forma)
            .border(1.dp, cores.outlineVariant, forma)
            .padding(horizontal = esc(12)),
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = cores.onSurfaceVariant, modifier = Modifier.size(esc(20)))
        Spacer(Modifier.width(esc(8)))
        Box(Modifier.weight(1f), Alignment.CenterStart) {
            if (valor.isEmpty()) Text(dica, color = cores.onSurfaceVariant, style = estilo(14))
            BasicTextField(
                value = valor,
                onValueChange = aoMudar,
                singleLine = true,
                textStyle = estilo(14).copy(color = cores.onSurface),
                cursorBrush = SolidColor(cores.primary),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = dica },
            )
        }
    }
}

/** Barra fixa com a ação principal da tela. */
@Composable
private fun BarraDeAcao(aoAdicionar: () -> Unit, comMargemDoSistema: Boolean) {
    Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 12.dp) {
        Box(
            Modifier
                .fillMaxWidth()
                .then(if (comMargemDoSistema) Modifier.navigationBarsPadding() else Modifier)
                .padding(vertical = esc(12)),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(Modifier.widthIn(max = larguraConteudo()).fillMaxWidth().padding(horizontal = esc(20))) {
                BotaoCheio(stringResource(R.string.usuarios_adicionar), aoAdicionar)
            }
        }
    }
}

/** Cartão de usuário: avatar de iniciais, nome e e-mail, e à direita o papel e a situação. */
@Composable
private fun CartaoUsuario(usuario: Usuario) {
    val cores = MaterialTheme.colorScheme
    val forma = RoundedCornerShape(esc(12))
    val admin = usuario.perfil?.ehAdmin == true
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(esc(12)),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(esc(3), forma, ambientColor = Color.Transparent, spotColor = SombraCartao)
            .background(cores.background, forma)
            .border(1.dp, cores.outlineVariant, forma)
            .padding(horizontal = esc(12), vertical = esc(12)),
    ) {
        Avatar(usuario.nome)
        Column(Modifier.weight(1f)) {
            Text(
                usuario.nome, color = cores.onSurface, style = estilo(14, FontWeight.Medium),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                usuario.email, color = cores.onSurfaceVariant, style = estilo(12),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(esc(4))) {
            Etiqueta(
                texto = stringResource(if (admin) R.string.usuarios_admin else R.string.usuarios_usuario),
                fundo = if (admin) cores.primary.copy(alpha = 0.14f) else cores.surfaceVariant,
                corDoTexto = if (admin) cores.primary else cores.onSurfaceVariant,
            )
            situacaoDe(usuario)?.let { (rotulo, cor) ->
                Etiqueta(texto = rotulo, fundo = cor.copy(alpha = 0.16f), corDoTexto = cores.onSurface, ponto = cor)
            }
        }
    }
}

/** Só mostra a situação quando não é a normal (ativo): o texto vem junto da cor do ponto. */
@Composable
private fun situacaoDe(usuario: Usuario): Pair<String, Color>? {
    val cores = MaterialTheme.colorScheme
    return when (usuario.situacao) {
        "pendente" -> stringResource(R.string.usuarios_situacao_pendente) to cores.tertiary
        "recusado" -> stringResource(R.string.usuarios_situacao_recusado) to cores.error
        "inativo" -> stringResource(R.string.usuarios_situacao_inativo) to cores.error
        "bloqueado" -> stringResource(R.string.usuarios_situacao_bloqueado) to cores.error
        else -> null
    }
}

@Composable
private fun Avatar(nome: String) {
    val cores = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(esc(40)).clip(CircleShape).background(cores.primary.copy(alpha = 0.12f)),
    ) {
        Text(iniciaisDe(nome), color = cores.primary, style = estilo(14, FontWeight.SemiBold))
    }
}

/** Etiqueta arredondada, com um ponto de cor opcional na frente (a situação). */
@Composable
private fun Etiqueta(texto: String, fundo: Color, corDoTexto: Color, ponto: Color? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(esc(22))
            .background(fundo, RoundedCornerShape(esc(11)))
            .padding(horizontal = esc(10)),
    ) {
        ponto?.let {
            Box(Modifier.size(esc(6)).background(it, CircleShape))
            Spacer(Modifier.width(esc(5)))
        }
        Text(texto, color = corDoTexto, style = estilo(11, FontWeight.Medium), maxLines = 1)
    }
}

private fun exemplo(id: Int, nome: String, admin: Boolean, situacao: String = "ativo") = Usuario(
    id = id, nome = nome, email = "${nome.substringBefore(' ').lowercase()}@agroirriga.com.br", telefone = null, situacao = situacao,
    perfil = Perfil(id, if (admin) "Distribuidor Admin" else "Distribuidor Usuário", "distribuidor", if (admin) "admin" else "usuario"),
    organizacao = null, permissoes = emptySet(), exigeTrocaSenha = false,
)

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Preview(showBackground = true, widthDp = 800, heightDp = 1280)
@Preview(showBackground = true, widthDp = 1280, heightDp = 800)
@Composable
private fun GestaoUsuariosTelaPreview() {
    MaterialTheme {
        GestaoUsuariosTela(
            estado = UsuariosEstado(
                carregando = false,
                organizacao = "Agro Irriga Ltda",
                usuarios = listOf(
                    exemplo(1, "Marcos Silva", true), exemplo(2, "Ana Paula Reis", false),
                    exemplo(3, "Fernando Costa", false, "pendente"), exemplo(4, "Beatriz Lima", false, "inativo"),
                ),
                podeAdicionar = true,
            ),
            aoAdicionar = {}, aoRecarregar = {},
        )
    }
}
