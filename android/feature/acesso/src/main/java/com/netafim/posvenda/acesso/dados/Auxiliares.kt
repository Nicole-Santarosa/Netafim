package com.netafim.posvenda.acesso.dados

import com.netafim.posvenda.acesso.dominio.Resultado
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.Json

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
