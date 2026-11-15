package com.netafim.posvenda.acesso.dados

import com.netafim.posvenda.acesso.dominio.PedidoAdmin
import com.netafim.posvenda.acesso.dominio.RepositorioFilaPrivacidade
import com.netafim.posvenda.acesso.dominio.Resultado
import kotlinx.serialization.json.Json

/** Fila de pedidos de titulares pela API autenticada (só o Netafim Admin tem acesso). */
class RepositorioFilaPrivacidadeRemoto(
    private val api: AcessoApi,
    private val json: Json,
) : RepositorioFilaPrivacidade {

    override suspend fun fila(): Resultado<List<PedidoAdmin>> = executarApi(json) {
        api.filaLgpd().map { it.paraDominio() }
    }

    override suspend fun analisar(idPedido: Int): Resultado<PedidoAdmin> = executarApi(json) {
        api.analisarPedido(idPedido).paraDominio()
    }

    override suspend fun atender(idPedido: Int): Resultado<PedidoAdmin> = executarApi(json) {
        api.atenderPedido(idPedido).paraDominio()
    }

    override suspend fun negar(idPedido: Int, justificativa: String): Resultado<PedidoAdmin> = executarApi(json) {
        api.negarPedido(idPedido, NegarPedidoRequisicao(justificativa.trim())).paraDominio()
    }

    override suspend fun dadosDoTitular(idPedido: Int): Resultado<String> = executarApi(json) {
        jsonLegivel(api.dadosDoTitular(idPedido).use { it.string() }, json)
    }

    private fun PedidoAdminDto.paraDominio() = PedidoAdmin(
        id = idSolicitacao,
        idUsuario = idUsuario,
        nomeTitular = nomeTitular,
        emailContato = emailContato,
        tipo = tipo,
        status = status,
        observacao = observacao,
        dataSolicitacao = dataSolicitacao,
        dataPrazo = dataPrazo,
        dataConclusao = dataConclusao,
        idResponsavel = idResponsavel,
        justificativaNegativa = justificativaNegativa,
        atrasada = atrasada,
    )
}
