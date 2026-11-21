package com.example.netafim.acesso.dados

import com.example.netafim.acesso.dominio.DadosNovoDominio
import com.example.netafim.acesso.dominio.Dominio
import com.example.netafim.acesso.dominio.RepositorioDominios
import com.example.netafim.acesso.dominio.Resultado
import kotlinx.serialization.json.Json

/** Domínios de e-mail corporativos pela API autenticada. */
class RepositorioDominiosRemoto(
    private val api: AcessoApi,
    private val json: Json,
) : RepositorioDominios {

    override suspend fun listar(): Resultado<List<Dominio>> = executarApi(json) {
        todasAsPaginas { pagina -> api.dominios(pagina) }.map { it.paraDominio() }
    }

    override suspend fun criar(dados: DadosNovoDominio): Resultado<Dominio> = executarApi(json) {
        api.criarDominio(DominioRequisicao(dados.dominio, dados.tipoOrganizacao, dados.idDistribuidor)).paraDominio()
    }

    override suspend fun alterarAtivo(idDominio: Int, ativo: Boolean): Resultado<Dominio> = executarApi(json) {
        api.alterarDominio(idDominio, DominioAtivoRequisicao(ativo)).paraDominio()
    }
}
