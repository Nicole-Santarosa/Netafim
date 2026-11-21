package com.example.netafim.acesso.dados

import com.example.netafim.acesso.dominio.Resultado
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** Lê uma lista paginada do DRF (PageNumberPagination) até a última página. */
internal suspend inline fun <T> todasAsPaginas(buscar: (Int) -> PaginaDto<T>): List<T> {
    val itens = mutableListOf<T>()
    var pagina = 1
    while (true) {
        val resposta = buscar(pagina)
        itens += resposta.results
        if (resposta.next == null) return itens
        pagina++
    }
}

/** Roda a chamada e devolve [Resultado], com os erros de rede e de HTTP já traduzidos. */
internal suspend inline fun <T> executarApi(json: Json, bloco: () -> T): Resultado<T> = try {
    Resultado.Sucesso(bloco())
} catch (erro: CancellationException) {
    throw erro
} catch (erro: Exception) {
    Resultado.Falha(ErrosHttp.mapear(erro, json))
}

private val JSON_LEGIVEL = Json { prettyPrint = true }

/** Reformata um JSON para leitura; se o texto não for JSON, devolve como veio (não esconde a cópia da pessoa). */
internal fun jsonLegivel(bruto: String, json: Json): String =
    runCatching { JSON_LEGIVEL.encodeToString(JsonElement.serializer(), json.parseToJsonElement(bruto)) }.getOrDefault(bruto)

