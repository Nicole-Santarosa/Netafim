package com.example.netafim.acesso.ui

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
import com.example.netafim.acesso.AcessoGrafo
import com.example.netafim.acesso.ui.admin.AprovacaoRota
import com.example.netafim.acesso.ui.admin.AprovacaoViewModel
import com.example.netafim.acesso.ui.cadastro.CadastroRota
import com.example.netafim.acesso.ui.cadastro.CadastroViewModel
import com.example.netafim.acesso.ui.link.LinkRota
import com.example.netafim.acesso.ui.link.LinkViewModel
import com.example.netafim.acesso.ui.login.LoginRota
import com.example.netafim.acesso.ui.login.LoginViewModel
import com.example.netafim.acesso.ui.permissoes.PerfisRota
import com.example.netafim.acesso.ui.permissoes.PerfisViewModel
import com.example.netafim.acesso.ui.permissoes.PermissoesPerfilRota
import com.example.netafim.acesso.ui.permissoes.PermissoesPerfilViewModel
import com.example.netafim.acesso.ui.perfil.MfaViewModel
import com.example.netafim.acesso.ui.perfil.PerfilRota
import com.example.netafim.acesso.ui.privacidade.FilaPrivacidadeRota
import com.example.netafim.acesso.ui.privacidade.FilaPrivacidadeViewModel
import com.example.netafim.acesso.ui.privacidade.NovoPedidoRota
import com.example.netafim.acesso.ui.privacidade.NovoPedidoViewModel
import com.example.netafim.acesso.ui.privacidade.PedidoAdminRota
import com.example.netafim.acesso.ui.privacidade.PedidoAdminViewModel
import com.example.netafim.acesso.ui.privacidade.PrivacidadeRota
import com.example.netafim.acesso.ui.privacidade.PrivacidadeViewModel
import com.example.netafim.acesso.ui.perfil.PerfilViewModel
import com.example.netafim.acesso.ui.perfil.TrocarSenhaRota
import com.example.netafim.acesso.ui.perfil.TrocarSenhaViewModel
import com.example.netafim.acesso.ui.senha.EsqueciSenhaRota
import com.example.netafim.acesso.ui.usuarios.EditarUsuarioRota
import com.example.netafim.acesso.ui.usuarios.EditarUsuarioViewModel
import com.example.netafim.acesso.ui.usuarios.GestaoUsuariosRota
import com.example.netafim.acesso.ui.usuarios.NovoUsuarioRota
import com.example.netafim.acesso.ui.usuarios.NovoUsuarioViewModel
import com.example.netafim.acesso.ui.usuarios.UsuarioDetalheRota
import com.example.netafim.acesso.ui.usuarios.UsuarioDetalheViewModel
import com.example.netafim.acesso.ui.usuarios.UsuariosViewModel
import com.example.netafim.acesso.ui.senha.EsqueciSenhaViewModel

/** Rotas do módulo de acesso. O app só precisa conhecer [GRAFO], as telas internas ([PERFIL], [APROVACAO], [USUARIOS]) e [exigeSessao] (o link do e-mail entra sozinho). */
object RotasAcesso {
    const val GRAFO = "acesso"
    const val LOGIN = "acesso/login"
    const val CADASTRO = "acesso/cadastro"
    const val ESQUECI_SENHA = "acesso/esqueci-senha"
    const val PERFIL = "acesso/perfil"
    const val TROCAR_SENHA = "acesso/perfil/senha"
    const val LINK = "acesso/link"
    const val APROVACAO = "acesso/aprovacao"
    const val USUARIOS = "acesso/usuarios"
    const val NOVO_USUARIO = "acesso/usuarios/novo"
    const val USUARIO_DETALHE = "acesso/usuarios/{id}"

    const val USUARIO_EDITAR = "acesso/usuarios/{id}/editar"
    const val PERMISSOES = "acesso/permissoes"
    const val PERMISSOES_PERFIL = "acesso/permissoes/{id}"
    const val PRIVACIDADE = "acesso/privacidade"
    const val NOVO_PEDIDO = "acesso/privacidade/novo"
    const val FILA_PRIVACIDADE = "acesso/privacidade/fila"
    const val PEDIDO_PRIVACIDADE = "acesso/privacidade/fila/{id}"

    fun pedidoDePrivacidade(id: Int) = "acesso/privacidade/fila/$id"

    fun permissoesDoPerfil(id: Int) = "acesso/permissoes/$id"

    fun detalheDoUsuario(id: Int) = "acesso/usuarios/$id"
    fun editarUsuario(id: Int) = "acesso/usuarios/$id/editar"

    /** Telas de dentro do app: sem sessão, a navegação raiz leva ao login. */
    fun exigeSessao(rota: String): Boolean =
        !rota.startsWith("$GRAFO/") || rota in setOf(PERFIL, TROCAR_SENHA, APROVACAO, USUARIOS, NOVO_USUARIO, USUARIO_DETALHE, USUARIO_EDITAR, PERMISSOES, PERMISSOES_PERFIL, PRIVACIDADE, NOVO_PEDIDO, FILA_PRIVACIDADE, PEDIDO_PRIVACIDADE)
}

private val fabrica = viewModelFactory {
    initializer { LoginViewModel(AcessoGrafo.repositorio) }
    initializer { CadastroViewModel(AcessoGrafo.repositorio) }
    initializer { PerfilViewModel(AcessoGrafo.repositorio) }
    initializer { MfaViewModel(AcessoGrafo.repositorio) }
    initializer { TrocarSenhaViewModel(AcessoGrafo.repositorio) }
    initializer { EsqueciSenhaViewModel(AcessoGrafo.repositorio) }
    initializer { LinkViewModel(AcessoGrafo.repositorio) }
    initializer { AprovacaoViewModel(AcessoGrafo.repositorioCadastros) }
    initializer { UsuariosViewModel(AcessoGrafo.repositorioUsuarios, AcessoGrafo.repositorio) }
    initializer { PerfisViewModel(AcessoGrafo.repositorioPerfis) }
    initializer { NovoUsuarioViewModel(AcessoGrafo.repositorioUsuarios) }
    initializer { PrivacidadeViewModel(AcessoGrafo.repositorioPrivacidade) }
    initializer { NovoPedidoViewModel(AcessoGrafo.repositorioPrivacidade) }
    initializer { FilaPrivacidadeViewModel(AcessoGrafo.repositorioFilaPrivacidade) }
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
            deepLinks = AcessoGrafo.padroesDeLink.map { padrao -> navDeepLink { uriPattern = padrao } },
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
    // Sair, ou trocar a senha, encerra a sessão: volta para o login limpando a pilha.
    val paraOLogin: () -> Unit = {
        navController.navigate(RotasAcesso.GRAFO) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }
    composable(RotasAcesso.PERFIL) {
        PerfilRota(
            viewModel = viewModel(factory = fabrica),
            mfaViewModel = viewModel(factory = fabrica),
            aoSair = paraOLogin,
            aoTrocarSenha = { navController.navigate(RotasAcesso.TROCAR_SENHA) },
            aoPrivacidade = { navController.navigate(RotasAcesso.PRIVACIDADE) },
        )
    }
    composable(RotasAcesso.TROCAR_SENHA) {
        TrocarSenhaRota(viewModel = viewModel(factory = fabrica), aoConcluir = paraOLogin)
    }
}

/** Permissões por perfil (RF20): a lista dos perfis e a edição de um deles, só para o Netafim Admin. */
fun NavGraphBuilder.telasPermissoes(navController: NavHostController) {
    composable(RotasAcesso.PERMISSOES) {
        PerfisRota(
            viewModel = viewModel(factory = fabrica),
            aoAbrirPerfil = { navController.navigate(RotasAcesso.permissoesDoPerfil(it)) },
        )
    }
    composable(
        RotasAcesso.PERMISSOES_PERFIL,
        arguments = listOf(navArgument("id") { type = NavType.IntType }),
    ) { entrada ->
        val id = entrada.arguments?.getInt("id") ?: return@composable
        val viewModel: PermissoesPerfilViewModel = viewModel(
            key = "permissoes-perfil-$id",
            factory = viewModelFactory {
                initializer { PermissoesPerfilViewModel(AcessoGrafo.repositorioPerfis, AcessoGrafo.repositorio, id) }
            },
        )
        PermissoesPerfilRota(viewModel = viewModel, aoConcluir = { navController.popBackStack() })
    }
}

/** Tela "Aprovação de cadastro", para quem tem a permissão aprovar_cadastro (a tela inicial só mostra a entrada para elas). */
fun NavGraphBuilder.telaAprovacao(navController: NavHostController) {
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
            aoAbrirUsuario = { navController.navigate(RotasAcesso.detalheDoUsuario(it)) },
        )
    }
    composable(
        RotasAcesso.USUARIO_DETALHE,
        arguments = listOf(navArgument("id") { type = NavType.IntType }),
    ) { entrada ->
        val id = entrada.arguments?.getInt("id") ?: return@composable
        val viewModel: UsuarioDetalheViewModel = viewModel(
            key = "usuario-$id",
            factory = viewModelFactory {
                initializer { UsuarioDetalheViewModel(AcessoGrafo.repositorioUsuarios, AcessoGrafo.repositorio, id) }
            },
        )
        UsuarioDetalheRota(
            viewModel = viewModel,
            aoVoltar = { navController.popBackStack() },
            aoEditar = { navController.navigate(RotasAcesso.editarUsuario(id)) },
        )
    }
    composable(
        RotasAcesso.USUARIO_EDITAR,
        arguments = listOf(navArgument("id") { type = NavType.IntType }),
    ) { entrada ->
        val id = entrada.arguments?.getInt("id") ?: return@composable
        val viewModel: EditarUsuarioViewModel = viewModel(
            key = "editar-usuario-$id",
            factory = viewModelFactory {
                initializer { EditarUsuarioViewModel(AcessoGrafo.repositorioUsuarios, id) }
            },
        )
        EditarUsuarioRota(viewModel = viewModel, aoConcluir = { navController.popBackStack() })
    }
    composable(RotasAcesso.NOVO_USUARIO) {
        NovoUsuarioRota(
            viewModel = viewModel(factory = fabrica),
            aoCriar = { navController.popBackStack() },
            aoVoltar = { navController.popBackStack() },
        )
    }
}

/** Privacidade (LGPD): pedidos do titular e cópia dos próprios dados, para qualquer usuário logado (entrada em Meu perfil). */
fun NavGraphBuilder.telasPrivacidade(navController: NavHostController) {
    composable(RotasAcesso.PRIVACIDADE) {
        PrivacidadeRota(
            viewModel = viewModel(factory = fabrica),
            aoFazerPedido = { navController.navigate(RotasAcesso.NOVO_PEDIDO) },
        )
    }
    composable(RotasAcesso.NOVO_PEDIDO) {
        NovoPedidoRota(viewModel = viewModel(factory = fabrica), aoConcluir = { navController.popBackStack() })
    }
}

/** Fila de pedidos de privacidade dos titulares (LGPD) e o detalhe de um pedido, só para o Netafim Admin. */
fun NavGraphBuilder.telasFilaPrivacidade(navController: NavHostController) {
    composable(RotasAcesso.FILA_PRIVACIDADE) {
        FilaPrivacidadeRota(
            viewModel = viewModel(factory = fabrica),
            aoAbrirPedido = { navController.navigate(RotasAcesso.pedidoDePrivacidade(it)) },
        )
    }
    composable(
        RotasAcesso.PEDIDO_PRIVACIDADE,
        arguments = listOf(navArgument("id") { type = NavType.IntType }),
    ) { entrada ->
        val id = entrada.arguments?.getInt("id") ?: return@composable
        val viewModel: PedidoAdminViewModel = viewModel(
            key = "pedido-privacidade-$id",
            factory = viewModelFactory {
                initializer { PedidoAdminViewModel(AcessoGrafo.repositorioFilaPrivacidade, AcessoGrafo.repositorio, id) }
            },
        )
        PedidoAdminRota(viewModel = viewModel)
    }
}

