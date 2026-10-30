package com.netafim.posvenda.acesso.ui.perfil

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netafim.posvenda.acesso.R
import com.netafim.posvenda.acesso.dominio.Organizacao
import com.netafim.posvenda.acesso.dominio.Perfil
import com.netafim.posvenda.acesso.dominio.Usuario

@Composable
fun PerfilRota(viewModel: PerfilViewModel, aoVoltar: (() -> Unit)?, aoSair: () -> Unit) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    LaunchedEffect(estado.saiu) {
        if (estado.saiu) aoSair()
    }
    PerfilTela(
        estado = estado,
        aoAtualizar = viewModel::atualizar,
        aoSair = viewModel::sair,
        aoVoltar = aoVoltar,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerfilTela(
    estado: PerfilEstado,
    aoAtualizar: () -> Unit,
    aoSair: () -> Unit,
    aoVoltar: (() -> Unit)?,
) {
    var confirmarSaida by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.acesso_perfil_titulo)) },
                navigationIcon = {
                    if (aoVoltar != null) {
                        IconButton(onClick = aoVoltar) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.acesso_voltar),
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = aoAtualizar, enabled = !estado.atualizando) {
                        Icon(Icons.Outlined.Refresh, stringResource(R.string.acesso_atualizar))
                    }
                },
            )
        },
    ) { margens ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(margens)
                .verticalScroll(rememberScrollState()),
        ) {
            if (estado.atualizando) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            val usuario = estado.usuario ?: return@Column
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                estado.aviso?.let { Aviso(it) }
                Cabecalho(usuario)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Item(stringResource(R.string.acesso_perfil_organizacao), usuario.organizacao?.nome)
                        Item(stringResource(R.string.acesso_perfil_perfil), usuario.perfil?.nome)
                        HorizontalDivider()
                        Item(stringResource(R.string.acesso_perfil_email), usuario.email)
                        Item(
                            stringResource(R.string.acesso_perfil_telefone),
                            usuario.telefone?.let(::formatarTelefone)
                                ?: stringResource(R.string.acesso_perfil_sem_telefone),
                        )
                        Item(stringResource(R.string.acesso_perfil_situacao), rotuloSituacao(usuario.situacao))
                    }
                }
                Permissoes(usuario.permissoes)
                Spacer(Modifier.size(8.dp))
                OutlinedButton(onClick = { confirmarSaida = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.acesso_sair))
                }
            }
        }
    }
    if (confirmarSaida) {
        AlertDialog(
            onDismissRequest = { confirmarSaida = false },
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

@Composable
private fun Cabecalho(usuario: Usuario) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.AccountCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.size(12.dp))
        Column {
            Text(
                usuario.nome,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            usuario.perfil?.let {
                Text(it.nome, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Item(rotulo: String, valor: String?) {
    Column {
        Text(rotulo, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor ?: "—", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Permissoes(codigos: Set<String>) {
    val itens = DescricaoPermissoes.para(codigos)
    if (itens.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.acesso_perfil_permissoes),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        itens.forEach { descricao ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Check, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(descricao, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Aviso(texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.CloudOff, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(texto, style = MaterialTheme.typography.bodySmall)
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

@Preview(showBackground = true)
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
            aoAtualizar = {}, aoSair = {}, aoVoltar = {},
        )
    }
}
