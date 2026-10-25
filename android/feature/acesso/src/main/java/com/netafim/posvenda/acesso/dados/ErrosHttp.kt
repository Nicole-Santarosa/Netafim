package com.netafim.posvenda.acesso.dados

import com.netafim.posvenda.acesso.dominio.ErroAcesso
import com.netafim.posvenda.acesso.dominio.MensagensErro
import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException

/**
 * Converte falhas de rede/HTTP no [ErroAcesso] que a tela mostra.
 *
 * Formatos da API:
 * - regra de negócio: {"detail": "...", "codigo": "conta_pendente"}
 * - validação do DRF: {"campo": ["mensagem", ...], ...}
 */
internal object ErrosHttp {

    fun mapear(erro: Throwable, json: Json): ErroAcesso = when (erro) {
        is HttpException -> mapearHttp(erro.code(), erro.response()?.errorBody()?.string(), json)
        is IOException -> ErroAcesso.SemConexao
        else -> ErroAcesso.Inesperado()
    }

    fun codigoDe(erro: Throwable, json: Json): String? =
        (mapear(erro, json) as? ErroAcesso.Api)?.codigo

    fun mapearHttp(status: Int, corpo: String?, json: Json): ErroAcesso {
        val objeto = corpo?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() }
            as? JsonObject
        if (objeto == null) {
            return if (status >= 500) ErroAcesso.Inesperado() else ErroAcesso.Api(
                codigo = null, mensagem = MensagensErro.para(null, null), status = status,
            )
        }

        val codigo = objeto["codigo"]?.textoOuNull()
        val detalhe = objeto["detail"]?.textoOuNull()
        if (codigo != null || detalhe != null) {
            return ErroAcesso.Api(codigo, MensagensErro.para(codigo, detalhe), status)
        }

        val campos = objeto.mapNotNull { (campo, valor) -> primeiraMensagem(valor)?.let { campo to it } }
            .toMap()
        if (campos.isNotEmpty()) {
            return ErroAcesso.Campos(campos, campos.values.first())
        }
        return ErroAcesso.Api(null, MensagensErro.para(null, null), status)
    }

    private fun primeiraMensagem(valor: JsonElement): String? = when (valor) {
        is JsonArray -> valor.firstOrNull()?.let { primeiraMensagem(it) }
        is JsonPrimitive -> valor.contentOrNull
        is JsonObject -> valor.values.firstOrNull()?.let { primeiraMensagem(it) }
    }

    private fun JsonElement.textoOuNull(): String? = runCatching { jsonPrimitive.contentOrNull }.getOrNull()
}
