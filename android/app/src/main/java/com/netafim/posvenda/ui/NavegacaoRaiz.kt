package com.netafim.posvenda.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.netafim.posvenda.acesso.AcessoGrafo
import com.netafim.posvenda.acesso.ui.RotasAcesso
import com.netafim.posvenda.acesso.ui.grafoAcesso
import com.netafim.posvenda.acesso.ui.telaAprovacao
import com.netafim.posvenda.acesso.ui.telaPerfil
import com.netafim.posvenda.acesso.ui.telasUsuarios
import com.netafim.posvenda.acesso.ui.usuarios.podeGerenciarUsuarios
import com.netafim.posvenda.acesso.dominio.Permissoes

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
        if (sessao == null && RotasAcesso.exigeSessao(rota)) {
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
        telaAprovacao(navController)
        telasUsuarios(navController)
        composable(RotasApp.INICIO) {
            val usuario = sessao?.usuario
            val alteracoesDeFoto by AcessoGrafo.fotos.alteracoes.collectAsStateWithLifecycle()
            val foto by produceState<ImageBitmap?>(null, usuario?.id, alteracoesDeFoto) {
                value = usuario?.id?.let { AcessoGrafo.fotos.carregar(it) }?.asImageBitmap()
            }
            InicioTela(
                nome = usuario?.nome.orEmpty(),
                perfil = usuario?.perfil?.nome,
                organizacao = usuario?.organizacao?.nome,
                foto = foto,
                podeGerenciarUsuarios = usuario?.podeGerenciarUsuarios() == true,
                podeAprovarCadastros = usuario?.temPermissao(Permissoes.APROVAR_CADASTRO) == true,
                aoAbrirPerfil = { navController.navigate(RotasAcesso.PERFIL) },
                aoAbrirUsuarios = { navController.navigate(RotasAcesso.USUARIOS) },
                aoAbrirAprovacao = { navController.navigate(RotasAcesso.APROVACAO) },
            )
        }
    }
}
