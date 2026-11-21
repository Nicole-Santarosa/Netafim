package com.netafim.posvenda.acesso.dados

import com.netafim.posvenda.acesso.dominio.PedidoTitular
import com.netafim.posvenda.acesso.dominio.RepositorioPrivacidade
import com.netafim.posvenda.acesso.dominio.Resultado
import com.netafim.posvenda.acesso.dominio.TipoPedido
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** Direitos do titular pela API autenticada. */
class RepositorioPrivacidadeRemoto(
    private val api: AcessoApi,
    private val json: Json,
) : RepositorioPrivacidade {

    private val formatado = Json { prettyPrint = true }

    override suspend fun meusPedidos(): Resultado<List<PedidoTitular>> = executarApi(json) {
        api.meusPedidos().map { it.paraDominio() }
    }

    override suspend fun abrirPedido(tipo: TipoPedido, observacao: String): Resultado<PedidoTitular> =
        executarApi(json) {
            api.abrirPedido(PedidoNovoRequisicao(tipo.codigo, observacao.trim().ifEmpty { null })).paraDominio()
        }

    override suspend fun copiaDosDados(): Resultado<String> = executarApi(json) {
        val bruto = api.meusDados().use { it.string() }
        // Se vier algo que não é JSON, entrega o texto como veio em vez de esconder a cópia da pessoa.
        runCatching { formatado.encodeToString(JsonElement.serializer(), json.parseToJsonElement(bruto)) }.getOrDefault(bruto)
    }

    private fun PedidoTitularDto.paraDominio() = PedidoTitular(
        id = idSolicitacao,
        tipo = tipo,
        status = status,
        observacao = observacao,
        dataSolicitacao = dataSolicitacao,
        dataPrazo = dataPrazo,
        dataConclusao = dataConclusao,
        justificativaNegativa = justificativaNegativa,
    )
}
