package com.netafim.posvenda.acesso.dados

import com.netafim.posvenda.acesso.dominio.DadosAprovacao
import com.netafim.posvenda.acesso.dominio.Opcao
import com.netafim.posvenda.acesso.dominio.OpcoesAprovacao
import com.netafim.posvenda.acesso.dominio.Perfil
import com.netafim.posvenda.acesso.dominio.RepositorioCadastros
import com.netafim.posvenda.acesso.dominio.Resultado
import com.netafim.posvenda.acesso.dominio.Usuario
import kotlinx.serialization.json.Json

/** Cadastros pendentes pela API autenticada (o token da sessão e a renovação já vêm no cliente). */
class RepositorioCadastrosRemoto(
    private val api: AcessoApi,
    private val json: Json,
) : RepositorioCadastros {

    override suspend fun pendentes(): Resultado<List<Usuario>> = executarApi(json) {
        todasAsPaginas { pagina -> api.usuarios(SITUACAO_PENDENTE, pagina) }.map { it.paraDominio() }
    }

    override suspend fun opcoes(): Resultado<OpcoesAprovacao> = executarApi(json) {
        OpcoesAprovacao(
            perfis = api.perfis().map { Perfil(it.idPerfil, it.nomePerfil, it.tipoOrganizacao, it.nivel) },
            distribuidores = todasAsPaginas { api.distribuidores(it) }
                .filter { it.status == STATUS_ATIVO }
                .map { Opcao(it.idDistribuidor, it.nome) },
            clientes = todasAsPaginas { api.clientes(it) }
                .filter { it.status == STATUS_ATIVO }
                .map { Opcao(it.idCliente, it.nome) },
        )
    }

    override suspend fun aprovar(idUsuario: Int, dados: DadosAprovacao): Resultado<Usuario> = executarApi(json) {
        api.aprovarUsuario(
            idUsuario,
            AprovarRequisicao(
                tipoOrganizacao = dados.tipoOrganizacao,
                idDistribuidor = dados.idDistribuidor,
                idCliente = dados.idCliente,
                idPerfil = dados.idPerfil,
            ),
        ).paraDominio()
    }

    override suspend fun recusar(idUsuario: Int): Resultado<Usuario> = executarApi(json) {
        api.recusarUsuario(idUsuario).paraDominio()
    }

    private companion object {
        const val SITUACAO_PENDENTE = "pendente"
        const val STATUS_ATIVO = "ativo"
    }
}
