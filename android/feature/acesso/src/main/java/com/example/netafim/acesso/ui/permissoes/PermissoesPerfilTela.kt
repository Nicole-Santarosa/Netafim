package com.example.netafim.acesso.ui.permissoes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.netafim.acesso.R
import com.example.netafim.acesso.dominio.Perfil
import com.example.netafim.acesso.dominio.PerfilComPermissoes
import com.example.netafim.acesso.dominio.PermissaoDoSistema
import com.example.netafim.acesso.dominio.Permissoes
import com.example.netafim.acesso.ui.comum.BotaoCheio
import com.example.netafim.acesso.ui.comum.BotaoContorno
import com.example.netafim.acesso.ui.comum.MensagemErro
import com.example.netafim.acesso.ui.comum.PaginaComCabecalho
import com.example.netafim.acesso.ui.comum.TextoApoio
import com.example.netafim.acesso.ui.comum.esc
import com.example.netafim.acesso.ui.comum.estilo
import com.example.netafim.acesso.ui.perfil.DescricaoPermissoes

@Composable
fun PermissoesPerfilRota(viewModel: PermissoesPerfilViewModel, aoConcluir: () -> Unit) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    LaunchedEffect(estado.salvo) {
        if (estado.salvo) aoConcluir()
    }
    PermissoesPerfilTela(
        estado = estado,
        aoAlternar = viewModel::alternar,
        aoSalvar = viewModel::salvar,
        aoRecarregar = viewModel::carregar,
    )
}

/**
 * Edição das permissões de um perfil (RF20): uma linha por permissão com caixa de seleção, as essenciais do
 * Netafim Admin travadas (marcadas e desabilitadas, com aviso) e o botão Salvar só liga quando algo mudou.
 * Base do protótipo (barra azul com o nome do perfil). O voltar do sistema descarta as mudanças.
 */
@Composable
fun PermissoesPerfilTela(
    estado: PermissoesPerfilEstado,
    aoAlternar: (String) -> Unit,
    aoSalvar: () -> Unit,
    aoRecarregar: () -> Unit,
) {
    PaginaComCabecalho(estado.perfil?.perfil?.nome ?: stringResource(R.string.permissoes_titulo)) {
        when {
            estado.carregando -> Box(Modifier.fillMaxWidth().padding(top = esc(40)), Alignment.Center) { CircularProgressIndicator() }
            estado.perfil == null -> {
                MensagemErro(estado.erro ?: stringResource(R.string.usuario_erro_carregar))
                Spacer(Modifier.height(esc(20)))
                BotaoContorno(stringResource(R.string.acesso_tentar_novamente), aoRecarregar)
            }
            else -> {
                TextoApoio(stringResource(R.string.permissoes_explicacao_edicao))
                Spacer(Modifier.height(esc(16)))
                estado.erroAcao?.let {
                    MensagemErro(it)
                    Spacer(Modifier.height(esc(16)))
                }
                Column(verticalArrangement = Arrangement.spacedBy(esc(4))) {
                    estado.catalogo.forEach { permissao ->
                        LinhaDaPermissao(
                            permissao = permissao,
                            marcada = permissao.codigo in estado.selecionadas,
                            travada = permissao.codigo in estado.essenciais,
                            habilitada = !estado.salvando,
                            aoAlternar = { aoAlternar(permissao.codigo) },
                        )
                    }
                }
                Spacer(Modifier.height(esc(26)))
                BotaoCheio(
                    texto = stringResource(R.string.permissoes_salvar),
                    aoClicar = aoSalvar,
                    carregando = estado.salvando,
                    habilitado = estado.mudou,
                )
            }
        }
    }
}

@Composable
private fun LinhaDaPermissao(
    permissao: PermissaoDoSistema,
    marcada: Boolean,
    travada: Boolean,
    habilitada: Boolean,
    aoAlternar: () -> Unit,
) {
    val cores = MaterialTheme.colorScheme
    val ativa = habilitada && !travada
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = marcada, enabled = ativa, role = Role.Checkbox, onValueChange = { aoAlternar() })
            .padding(vertical = esc(4)),
    ) {
        Checkbox(checked = marcada, onCheckedChange = null, enabled = ativa)
        Column(Modifier.padding(start = esc(8))) {
            Text(
                DescricaoPermissoes.texto(permissao.codigo) ?: permissao.descricao.ifBlank { permissao.codigo },
                color = cores.onSurface,
                style = estilo(14),
            )
            if (travada) {
                Text(stringResource(R.string.permissoes_essencial), color = cores.onSurfaceVariant, style = estilo(11))
            }
        }
    }
}

private fun amostra(vararg codigos: String) = codigos.map { PermissaoDoSistema(it, it) }

@Preview(showBackground = true, widthDp = 360, heightDp = 828)
@Composable
private fun PermissoesPerfilTelaPreview() {
    MaterialTheme {
        PermissoesPerfilTela(
            estado = PermissoesPerfilEstado(
                perfil = PerfilComPermissoes(Perfil(1, "Netafim Admin", "netafim", "admin"), null, setOf(Permissoes.APROVAR_CADASTRO)),
                catalogo = amostra(Permissoes.ABRIR_ATENDIMENTO, Permissoes.APROVAR_CADASTRO, Permissoes.DEFINIR_PERFIL),
                selecionadas = setOf(Permissoes.APROVAR_CADASTRO, Permissoes.DEFINIR_PERFIL, Permissoes.ABRIR_ATENDIMENTO),
                originais = setOf(Permissoes.APROVAR_CADASTRO, Permissoes.DEFINIR_PERFIL),
                essenciais = setOf(Permissoes.APROVAR_CADASTRO, Permissoes.DEFINIR_PERFIL),
                carregando = false,
            ),
            aoAlternar = {}, aoSalvar = {}, aoRecarregar = {},
        )
    }
}
