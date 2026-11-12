package com.netafim.posvenda.acesso.ui.usuarios

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netafim.posvenda.acesso.R
import com.netafim.posvenda.acesso.dominio.Perfil
import com.netafim.posvenda.acesso.dominio.Usuario
import com.netafim.posvenda.acesso.ui.comum.BotaoCheio
import com.netafim.posvenda.acesso.ui.comum.BotaoContorno
import com.netafim.posvenda.acesso.ui.comum.CabecalhoTela
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
 * Gestão de usuários conforme o protótipo (Figma, "03 Gestão de usuários"): um cartão de 56 dp por
 * pessoa, com a etiqueta Admin ou Usuário, e o botão "+ Adicionar usuário" depois da lista.
 */
@Composable
fun GestaoUsuariosTela(
    estado: UsuariosEstado,
    aoAdicionar: () -> Unit,
    aoRecarregar: () -> Unit,
) {
    TelaResponsiva(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            CabecalhoTela(stringResource(R.string.usuarios_titulo))
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    modifier = Modifier
                        .widthIn(max = larguraConteudo())
                        .fillMaxWidth()
                        .padding(horizontal = esc(20)),
                ) {
                    when {
                        estado.carregando && estado.usuarios.isEmpty() ->
                            Box(Modifier.fillMaxWidth().padding(top = esc(76)), Alignment.Center) { CircularProgressIndicator() }
                        estado.erro != null && estado.usuarios.isEmpty() -> Column(Modifier.padding(top = esc(16))) {
                            MensagemErro(estado.erro)
                            Spacer(Modifier.height(esc(20)))
                            BotaoContorno(stringResource(R.string.acesso_tentar_novamente), aoRecarregar)
                        }
                        else -> LazyColumn(
                            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                            contentPadding = PaddingValues(top = esc(16), bottom = esc(32)),
                            verticalArrangement = Arrangement.spacedBy(esc(8)),
                        ) {
                            if (estado.usuarios.isEmpty()) {
                                item {
                                    Column(Modifier.padding(vertical = esc(60))) {
                                        TituloDeEstado(stringResource(R.string.usuarios_vazio_titulo))
                                        Spacer(Modifier.height(esc(21)))
                                        DetalheDeEstado(stringResource(R.string.usuarios_vazio_detalhe))
                                    }
                                }
                            }
                            items(estado.usuarios, key = { it.id }) { CartaoUsuario(it) }
                            if (estado.podeAdicionar) {
                                item {
                                    Spacer(Modifier.height(esc(6)))
                                    BotaoCheio(stringResource(R.string.usuarios_adicionar), aoAdicionar)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Cartão de 56 dp: nome em 13 sp e a etiqueta Admin/Usuário embaixo. */
@Composable
private fun CartaoUsuario(usuario: Usuario) {
    val cores = MaterialTheme.colorScheme
    val forma = RoundedCornerShape(esc(8))
    val admin = usuario.perfil?.ehAdmin == true
    Column(
        verticalArrangement = Arrangement.spacedBy(esc(3), Alignment.CenterVertically),
        modifier = Modifier
            .fillMaxWidth()
            .height(esc(56))
            .shadow(esc(3), forma, ambientColor = Color.Transparent, spotColor = SombraCartao)
            .background(cores.background, forma)
            .border(1.dp, cores.outlineVariant, forma)
            .padding(horizontal = esc(12)),
    ) {
        Text(usuario.nome, color = cores.onSurface, style = estilo(13, FontWeight.Medium), maxLines = 1)
        Etiqueta(
            texto = stringResource(if (admin) R.string.usuarios_admin else R.string.usuarios_usuario),
            // No protótipo: Admin em azul-marinho; Usuário em cinza claro (C9C9C2).
            cor = if (admin) cores.primary else cores.outlineVariant,
        )
    }
}

/** Etiqueta arredondada: fundo da cor a 18% e texto na cor (11 sp). */
@Composable
private fun Etiqueta(texto: String, cor: Color) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(esc(21))
            .background(cor.copy(alpha = 0.18f), RoundedCornerShape(esc(11)))
            .padding(horizontal = esc(10)),
    ) {
        Text(texto, color = cor, style = estilo(11, FontWeight.Medium))
    }
}

private val SombraCartao = Color(0x1A0F1426)

private fun exemplo(id: Int, nome: String, admin: Boolean) = Usuario(
    id = id, nome = nome, email = "$id@exemplo.com", telefone = null, situacao = "ativo",
    perfil = Perfil(id, if (admin) "Distribuidor Admin" else "Distribuidor Usuário", "distribuidor", if (admin) "admin" else "usuario"),
    organizacao = null, permissoes = emptySet(), exigeTrocaSenha = false,
)

@Preview(showBackground = true, widthDp = 360, heightDp = 828)
@Preview(showBackground = true, widthDp = 800, heightDp = 1280)
@Composable
private fun GestaoUsuariosTelaPreview() {
    MaterialTheme {
        GestaoUsuariosTela(
            estado = UsuariosEstado(
                carregando = false,
                usuarios = listOf(exemplo(1, "Marcos Silva", true), exemplo(2, "Ana Paula Reis", false), exemplo(3, "Fernando Costa", false)),
                podeAdicionar = true,
            ),
            aoAdicionar = {}, aoRecarregar = {},
        )
    }
}
