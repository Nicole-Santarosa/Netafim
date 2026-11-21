package com.netafim.posvenda.acesso.ui.permissoes

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netafim.posvenda.acesso.R
import com.netafim.posvenda.acesso.dominio.Perfil
import com.netafim.posvenda.acesso.dominio.PerfilComPermissoes
import com.netafim.posvenda.acesso.dominio.TiposOrganizacao
import com.netafim.posvenda.acesso.ui.comum.BotaoContorno
import com.netafim.posvenda.acesso.ui.comum.DetalheDeEstado
import com.netafim.posvenda.acesso.ui.comum.MensagemErro
import com.netafim.posvenda.acesso.ui.comum.PaginaComCabecalho
import com.netafim.posvenda.acesso.ui.comum.TextoApoio
import com.netafim.posvenda.acesso.ui.comum.esc
import com.netafim.posvenda.acesso.ui.comum.estilo

@Composable
fun PerfisRota(viewModel: PerfisViewModel, aoAbrirPerfil: (Int) -> Unit) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.aoRetomar() }
    PerfisTela(estado = estado, aoRecarregar = viewModel::carregar, aoAbrir = aoAbrirPerfil)
}

/**
 * Permissões por perfil (RF20). Não existe no Figma; usa a base do protótipo (barra azul com o título e
 * cartões de 56 dp com borda C9C9C2 e raio 8, como a Gestão de usuários). Só o Netafim Admin chega aqui.
 */
@Composable
fun PerfisTela(estado: PerfisEstado, aoRecarregar: () -> Unit, aoAbrir: (Int) -> Unit) {
    PaginaComCabecalho(stringResource(R.string.permissoes_titulo)) {
        when {
            estado.carregando && estado.perfis.isEmpty() -> Box(Modifier.fillMaxWidth().padding(top = esc(40)), Alignment.Center) {
                CircularProgressIndicator()
            }
            estado.erro != null && estado.perfis.isEmpty() -> {
                MensagemErro(estado.erro)
                Spacer(Modifier.height(esc(20)))
                BotaoContorno(stringResource(R.string.acesso_tentar_novamente), aoRecarregar)
            }
            estado.perfis.isEmpty() -> DetalheDeEstado(stringResource(R.string.permissoes_vazio))
            else -> {
                TextoApoio(stringResource(R.string.permissoes_explicacao_lista))
                Spacer(Modifier.height(esc(16)))
                Column(verticalArrangement = Arrangement.spacedBy(esc(8))) {
                    estado.perfis.forEach { CartaoDoPerfil(it) { aoAbrir(it.perfil.id) } }
                }
            }
        }
    }
}

@Composable
private fun CartaoDoPerfil(item: PerfilComPermissoes, aoClicar: () -> Unit) {
    val cores = MaterialTheme.colorScheme
    val forma = RoundedCornerShape(esc(8))
    val recursos = LocalContext.current.resources
    val quantidade = item.permissoes.size
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(cores.background, forma)
            .border(1.dp, cores.outlineVariant, forma)
            .clickable(role = Role.Button, onClick = aoClicar)
            .padding(horizontal = esc(12), vertical = esc(10)),
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.perfil.nome, color = cores.onSurface, style = estilo(13, FontWeight.Medium))
            Text(
                stringResource(R.string.permissoes_resumo, rotuloDoTipo(item.perfil.tipoOrganizacao),
                    recursos.getQuantityString(R.plurals.permissoes_quantidade, quantidade, quantidade)),
                color = cores.onSurfaceVariant,
                style = estilo(11),
            )
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = cores.onSurfaceVariant)
    }
}

@Composable
internal fun rotuloDoTipo(tipo: String): String = stringResource(
    when (tipo) {
        TiposOrganizacao.NETAFIM -> R.string.permissoes_tipo_netafim
        TiposOrganizacao.DISTRIBUIDOR -> R.string.permissoes_tipo_distribuidor
        else -> R.string.permissoes_tipo_cliente
    },
)

private fun exemplo(id: Int, nome: String, tipo: String, nivel: String, n: Int) =
    PerfilComPermissoes(Perfil(id, nome, tipo, nivel), null, List(n) { "p$it" }.toSet())

@Preview(showBackground = true, widthDp = 360, heightDp = 828)
@Composable
private fun PerfisTelaPreview() {
    MaterialTheme {
        PerfisTela(
            estado = PerfisEstado(
                carregando = false,
                perfis = listOf(
                    exemplo(1, "Netafim Admin", "netafim", "admin", 17),
                    exemplo(3, "Distribuidor Admin", "distribuidor", "admin", 9),
                    exemplo(4, "Distribuidor Usuário", "distribuidor", "usuario", 4),
                ),
            ),
            aoRecarregar = {}, aoAbrir = {},
        )
    }
}
