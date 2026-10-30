package com.netafim.posvenda.acesso.ui

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.netafim.posvenda.acesso.AcessoGrafo
import com.netafim.posvenda.acesso.ui.cadastro.CadastroRota
import com.netafim.posvenda.acesso.ui.cadastro.CadastroViewModel
import com.netafim.posvenda.acesso.ui.link.LinkRota
import com.netafim.posvenda.acesso.ui.link.LinkViewModel
import com.netafim.posvenda.acesso.ui.login.LoginRota
import com.netafim.posvenda.acesso.ui.login.LoginViewModel
import com.netafim.posvenda.acesso.ui.perfil.PerfilRota
import com.netafim.posvenda.acesso.ui.perfil.PerfilViewModel
import com.netafim.posvenda.acesso.ui.senha.EsqueciSenhaRota
import com.netafim.posvenda.acesso.ui.senha.EsqueciSenhaViewModel

/** Rotas do módulo de acesso. O app só precisa conhecer [GRAFO] e [PERFIL] (o link do e-mail entra sozinho). */
object RotasAcesso {
    const val GRAFO = "acesso"
    const val LOGIN = "acesso/login"
    const val CADASTRO = "acesso/cadastro"
    const val ESQUECI_SENHA = "acesso/esqueci-senha"
    const val PERFIL = "acesso/perfil"
    const val LINK = "acesso/link"
}

private val fabrica = viewModelFactory {
    initializer { LoginViewModel(AcessoGrafo.repositorio) }
    initializer { CadastroViewModel(AcessoGrafo.repositorio) }
    initializer { PerfilViewModel(AcessoGrafo.repositorio) }
    initializer { EsqueciSenhaViewModel(AcessoGrafo.repositorio) }
    initializer { LinkViewModel(AcessoGrafo.repositorio) }
}

/**
 * Telas de entrada (login, cadastro, esqueci a senha). [aoEntrar] leva para a tela
 * inicial do app, que pertence a outro módulo.
 */
fun NavGraphBuilder.grafoAcesso(navController: NavHostController, aoEntrar: () -> Unit) {
    navigation(startDestination = RotasAcesso.LOGIN, route = RotasAcesso.GRAFO) {
        composable(RotasAcesso.LOGIN) {
            LoginRota(
                viewModel = viewModel(factory = fabrica),
                aoEntrar = aoEntrar,
                aoSolicitarAcesso = { navController.navigate(RotasAcesso.CADASTRO) },
                aoEsquecerSenha = { navController.navigate(RotasAcesso.ESQUECI_SENHA) },
            )
        }
        composable(RotasAcesso.CADASTRO) {
            CadastroRota(viewModel = viewModel(factory = fabrica), aoVoltar = { navController.popBackStack() })
        }
        composable(RotasAcesso.ESQUECI_SENHA) {
            EsqueciSenhaRota(viewModel = viewModel(factory = fabrica), aoVoltar = { navController.popBackStack() })
        }
        // Link do e-mail (convite ou redefinição de senha). O token vem como argumento da rota.
        composable(
            route = "${RotasAcesso.LINK}?acao={acao}&token={token}",
            arguments = listOf(
                navArgument("acao") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("token") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
            deepLinks = listOf(navDeepLink { uriPattern = "netafim://convite?acao={acao}&token={token}" }),
        ) { entrada ->
            LinkRota(
                viewModel = viewModel(factory = fabrica),
                acao = entrada.arguments?.getString("acao"),
                token = entrada.arguments?.getString("token"),
                aoIrParaLogin = {
                    navController.navigate(RotasAcesso.GRAFO) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}

/** Tela "Meu perfil". Ao sair, volta para o login limpando a pilha de navegação. */
fun NavGraphBuilder.telaPerfil(navController: NavHostController) {
    composable(RotasAcesso.PERFIL) {
        PerfilRota(
            viewModel = viewModel(factory = fabrica),
            aoVoltar = { navController.popBackStack() },
            aoSair = {
                navController.navigate(RotasAcesso.GRAFO) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            },
        )
    }
}
