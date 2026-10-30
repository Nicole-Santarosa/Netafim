package com.netafim.posvenda.acesso.dados

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/** O que a camada de rede precisa da sessão para autenticar as requisições. */
interface ProvedorTokens {
    fun accessTokenAtual(): String?

    /** Renova a sessão. Devolve o novo access token, ou null se não for possível. */
    suspend fun renovar(accessUsado: String?): String?
}

private const val CABECALHO = "Authorization"
private const val PREFIXO = "Bearer "

/** Anexa o access token da sessão em todas as requisições do cliente autenticado. */
class AutenticacaoInterceptor(private val tokens: ProvedorTokens) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokens.accessTokenAtual()
        val requisicao = if (token == null || chain.request().header(CABECALHO) != null) {
            chain.request()
        } else {
            chain.request().newBuilder().header(CABECALHO, PREFIXO + token).build()
        }
        return chain.proceed(requisicao)
    }
}

/**
 * Ao receber 401, renova a sessão uma vez e repete a requisição. Requisições
 * simultâneas compartilham a mesma renovação (ver RepositorioAcessoRemoto.renovar).
 */
class RenovacaoAutenticador(private val tokens: ProvedorTokens) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.priorResponse != null) return null // já tentou renovar
        val usado = response.request.header(CABECALHO)?.removePrefix(PREFIXO) ?: return null
        // OkHttp chama o Authenticator fora da main thread, então bloquear aqui é seguro.
        val novo = runBlocking { tokens.renovar(usado) } ?: return null
        return response.request.newBuilder().header(CABECALHO, PREFIXO + novo).build()
    }
}

/** Monta os clientes HTTP do app. O [autenticado] serve para os demais módulos também. */
class Rede(urlBase: String, tokens: ProvedorTokens, depuracao: Boolean) {

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    val publico: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .apply {
            if (depuracao) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                        redactHeader(CABECALHO)
                    },
                )
            }
        }
        .build()

    val autenticado: OkHttpClient = publico.newBuilder()
        .addInterceptor(AutenticacaoInterceptor(tokens))
        .authenticator(RenovacaoAutenticador(tokens))
        .build()

    private val urlNormalizada = if (urlBase.endsWith("/")) urlBase else "$urlBase/"

    fun retrofit(cliente: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(urlNormalizada)
        .client(cliente)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
}
