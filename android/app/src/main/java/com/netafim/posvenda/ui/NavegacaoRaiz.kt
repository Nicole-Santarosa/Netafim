package com.netafim.posvenda.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.netafim.posvenda.R
import com.netafim.posvenda.acesso.AcessoGrafo
import com.netafim.posvenda.acesso.ui.RotasAcesso
import com.netafim.posvenda.acesso.ui.grafoAcesso
import com.netafim.posvenda.acesso.ui.telaPerfil

/** Rotas do app. PROVISÓRIO: "inicio" deve ser substituída pela tela do módulo responsável. */
object RotasApp {
    const val INICIO = "inicio"
}

@Composable
fun NavegacaoRaiz(iniciarLogado: Boolean, navController: NavHostController = rememberNavController()) {
    val sessao by AcessoGrafo.repositorio.sessao.collectAsStateWithLifecycle()
    val entrada by navController.currentBackStackEntryAsState()

    // Sessão expirou ou foi revogada com o app aberto: volta para o login.
    LaunchedEffect(sessao, entrada) {
        val rota = entrada?.destination?.route ?: return@LaunchedEffect
        val emTelaDeAcesso = rota.startsWith(RotasAcesso.GRAFO + "/") && rota != RotasAcesso.PERFIL
        if (sessao == null && !emTelaDeAcesso) {
            navController.navigate(RotasAcesso.GRAFO) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (iniciarLogado) RotasApp.INICIO else RotasAcesso.GRAFO,
    ) {
        grafoAcesso(navController) {
            navController.navigate(RotasApp.INICIO) {
                popUpTo(RotasAcesso.GRAFO) { inclusive = true }
            }
        }
        telaPerfil(navController)
        composable(RotasApp.INICIO) {
            InicioProvisorio(
                nome = sessao?.usuario?.nome.orEmpty(),
                aoAbrirPerfil = { navController.navigate(RotasAcesso.PERFIL) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InicioProvisorio(nome: String, aoAbrirPerfil: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.inicio_titulo)) },
                actions = {
                    IconButton(onClick = aoAbrirPerfil) {
                        Icon(Icons.Outlined.AccountCircle, stringResource(R.string.inicio_meu_perfil))
                    }
                },
            )
        },
    ) { margens ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(margens)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.inicio_ola, nome), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.inicio_provisorio), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
