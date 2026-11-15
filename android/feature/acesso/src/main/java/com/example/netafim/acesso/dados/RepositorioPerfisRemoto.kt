package com.example.netafim.acesso.dados

import com.example.netafim.acesso.dominio.Perfil
import com.example.netafim.acesso.dominio.PerfilComPermissoes
import com.example.netafim.acesso.dominio.PermissaoDoSistema
import com.example.netafim.acesso.dominio.RepositorioPerfis
import com.example.netafim.acesso.dominio.Resultado
import kotlinx.serialization.json.Json

/** Permissões por perfil pela API autenticada (só o Netafim Admin tem acesso). */
class RepositorioPerfisRemoto(
    private val api: AcessoApi,
    private val json: Json,
) : RepositorioPerfis {

    override suspend fun perfis(): Resultado<List<PerfilComPermissoes>> = executarApi(json) {
        api.perfisComPermissoes().map { it.paraDominio() }
    }

    override suspend fun permissoes(): Resultado<List<PermissaoDoSistema>> = executarApi(json) {
        api.permissoes().map { PermissaoDoSistema(it.codigo, it.descricao) }
    }

    override suspend fun salvarPermissoes(idPerfil: Int, codigos: Set<String>): Resultado<PerfilComPermissoes> =
        executarApi(json) {
            api.salvarPermissoes(idPerfil, PermissoesRequisicao(codigos.sorted())).paraDominio()
        }

    private fun PerfilCompletoDto.paraDominio() = PerfilComPermissoes(
        perfil = Perfil(idPerfil, nomePerfil, tipoOrganizacao, nivel),
        descricao = descricao,
        permissoes = permissoes.toSet(),
    )
}
