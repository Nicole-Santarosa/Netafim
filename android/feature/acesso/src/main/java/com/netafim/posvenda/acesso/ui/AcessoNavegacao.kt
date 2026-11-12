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
import com.netafim.posvenda.acesso.ui.admin.AprovacaoRota
import com.netafim.posvenda.acesso.ui.admin.AprovacaoViewModel
import com.netafim.posvenda.acesso.ui.cadastro.CadastroRota
import com.netafim.posvenda.acesso.ui.cadastro.CadastroViewModel
import com.netafim.posvenda.acesso.ui.link.LinkRota
import com.netafim.posvenda.acesso.ui.link.LinkViewModel
import com.netafim.posvenda.acesso.ui.login.LoginRota
import com.netafim.posvenda.acesso.ui.login.LoginViewModel
import com.netafim.posvenda.acesso.ui.perfil.MfaViewModel
import com.netafim.posvenda.acesso.ui.perfil.PerfilRota
import com.netafim.posvenda.acesso.ui.perfil.PerfilViewModel
import com.netafim.posvenda.acesso.ui.senha.EsqueciSenhaRota
import com.netafim.posvenda.acesso.ui.usuarios.GestaoUsuariosRota
import com.netafim.posvenda.acesso.ui.usuarios.NovoUsuarioRota
import com.netafim.posvenda.acesso.ui.usuarios.NovoUsuarioViewModel
import com.netafim.posvenda.acesso.ui.usuarios.UsuariosViewModel
import com.netafim.posvenda.acesso.ui.senha.EsqueciSenhaViewModel

/** Rotas do módulo de acesso. O app só precisa conhecer [GRAFO], as telas internas ([PERFIL], [APROVACAO], [USUARIOS]) e [exigeSessao] (o link do e-mail entra sozinho). */
object RotasAcesso {
    const val GRAFO = "acesso"
    const val LOGIN = "acesso/login"
    const val CADASTRO = "acesso/cadastro"
    const val ESQUECI_SENHA = "acesso/esqueci-senha"
    const val PERFIL = "acesso/perfil"
    const val LINK = "acesso/link"
    const val APROVACAO = "acesso/aprovacao"
    const val USUARIOS = "acesso/usuarios"
    const val NOVO_USUARIO = "acesso/usuarios/novo"

    /** Telas de dentro do app: sem sessão, a navegação raiz leva ao login. */
    fun exigeSessao(rota: String): Boolean =
        !rota.startsWith("$GRAFO/") || rota in setOf(PERFIL, APROVACAO, USUARIOS, NOVO_USUARIO)
}

private val fabrica = viewModelFactory {
    initializer { LoginViewModel(AcessoGrafo.repositorio) }
    initializer { CadastroViewModel(AcessoGrafo.repositorio) }
    initializer { PerfilViewModel(AcessoGrafo.repositorio) }
    initializer { MfaViewModel(AcessoGrafo.repositorio) }
    initializer { EsqueciSenhaViewModel(AcessoGrafo.repositorio) }
    initializer { LinkViewModel(AcessoGrafo.repositorio) }
    initializer { AprovacaoViewModel(AcessoGrafo.repositorioCadastros) }
    initializer { UsuariosViewModel(AcessoGrafo.repositorioUsuarios, AcessoGrafo.repositorio) }
    initializer { NovoUsuarioViewModel(AcessoGrafo.repositorioUsuarios) }
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
            mfaViewModel = viewModel(factory = fabrica),
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

/** Tela "Aprovação de cadastro", para quem tem a permissão aprovar_cadastro (a tela inicial só mostra a entrada para elas). */
fun NavGraphBuilder.telaAprovacao() {
    composable(RotasAcesso.APROVACAO) {
        AprovacaoRota(viewModel = viewModel(factory = fabrica))
    }
}

/** Gestão de usuários e o formulário de novo usuário, para admins de distribuidor e de cliente. */
fun NavGraphBuilder.telasUsuarios(navController: NavHostController) {
    composable(RotasAcesso.USUARIOS) {
        GestaoUsuariosRota(
            viewModel = viewModel(factory = fabrica),
            aoAdicionar = { navController.navigate(RotasAcesso.NOVO_USUARIO) },
        )
    }
    composable(RotasAcesso.NOVO_USUARIO) {
        NovoUsuarioRota(viewModel = viewModel(factory = fabrica), aoCriar = { navController.popBackStack() })
    }
}
