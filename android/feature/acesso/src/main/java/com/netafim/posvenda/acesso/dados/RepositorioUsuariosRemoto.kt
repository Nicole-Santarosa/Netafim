package com.netafim.posvenda.acesso.dados

import com.netafim.posvenda.acesso.dominio.DadosNovoUsuario
import com.netafim.posvenda.acesso.dominio.Perfil
import com.netafim.posvenda.acesso.dominio.RepositorioUsuarios
import com.netafim.posvenda.acesso.dominio.Resultado
import com.netafim.posvenda.acesso.dominio.Usuario
import kotlinx.serialization.json.Json

/** Gestão de usuários pela API autenticada. */
class RepositorioUsuariosRemoto(
    private val api: AcessoApi,
    private val json: Json,
) : RepositorioUsuarios {

    override suspend fun listar(): Resultado<List<Usuario>> = executarApi(json) {
        todasAsPaginas { pagina -> api.usuarios(situacao = null, pagina = pagina) }.map { it.paraDominio() }
    }

    override suspend fun obter(idUsuario: Int): Resultado<Usuario> = executarApi(json) {
        api.usuario(idUsuario).paraDominio()
    }

    override suspend fun alterarSituacao(idUsuario: Int, situacao: String): Resultado<Usuario> = executarApi(json) {
        api.situacaoUsuario(idUsuario, SituacaoRequisicao(situacao)).paraDominio()
    }

    override suspend fun definirPerfil(idUsuario: Int, idPerfil: Int): Resultado<Usuario> = executarApi(json) {
        api.perfilUsuario(idUsuario, DefinirPerfilRequisicao(idPerfil)).paraDominio()
    }

    override suspend fun reenviarConvite(idUsuario: Int): Resultado<Unit> = executarApi(json) {
        api.reenviarConvite(idUsuario)
    }

    override suspend fun excluir(idUsuario: Int): Resultado<Unit> = executarApi(json) {
        api.excluirUsuario(idUsuario)
    }

    override suspend fun perfis(): Resultado<List<Perfil>> = executarApi(json) {
        api.perfis().map { Perfil(it.idPerfil, it.nomePerfil, it.tipoOrganizacao, it.nivel) }
    }

    override suspend fun criar(dados: DadosNovoUsuario): Resultado<Usuario> = executarApi(json) {
        api.criarUsuario(
            CriarUsuarioRequisicao(
                nome = dados.nome.trim(),
                email = dados.email.trim(),
                telefone = dados.telefone?.takeIf { it.isNotBlank() },
            ),
        ).paraDominio()
    }
}
