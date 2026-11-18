package com.example.netafim.acesso

import com.example.netafim.acesso.dados.ProvedorTokens
import com.example.netafim.acesso.dados.Rede
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.resume

/**
 * Várias requisições autenticadas com o access token vencido ao mesmo tempo. O autenticador bloqueia
 * uma thread do despachante do OkHttp enquanto a renovação roda; se a renovação usasse o mesmo despachante
 * (limite de 5 chamadas por host), as chamadas bloqueadas ocupariam todas as vagas e a renovação nunca
 * começaria. O cliente autenticado precisa ter despachante próprio.
 */
class RedeConcorrenciaTest {

    @Test
    fun `varias requisicoes com token vencido renovam uma vez e nao travam`() {
        val servidor = MockWebServer()
        val renovacoes = AtomicInteger()
        servidor.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/renovar" -> {
                    renovacoes.incrementAndGet()
                    Thread.sleep(200)
                    MockResponse().setBody("novo")
                }
                request.getHeader("Authorization") == "Bearer novo" -> MockResponse().setBody("ok")
                else -> MockResponse().setResponseCode(401)
            }
        }
        servidor.start()
        try {
            lateinit var rede: Rede
            val tokens = object : ProvedorTokens {
                @Volatile var access = "velho"
                private val trava = Mutex()

                override fun accessTokenAtual(): String = access

                override suspend fun renovar(accessUsado: String?): String? = trava.withLock {
                    if (access != accessUsado) return@withLock access // outra requisição já renovou
                    // Como o Retrofit com suspend: a chamada de renovação sai pelo cliente público, em fila.
                    val resposta = suspendCancellableCoroutine<String?> { continuacao ->
                        val chamada = rede.publico.newCall(
                            Request.Builder().url(servidor.url("/renovar")).post("{}".toRequestBody()).build(),
                        )
                        chamada.enqueue(object : Callback {
                            override fun onFailure(call: Call, e: IOException) = continuacao.resume(null)
                            override fun onResponse(call: Call, response: Response) {
                                response.use { continuacao.resume(it.body?.string()) }
                            }
                        })
                    }
                    if (resposta != null) access = resposta
                    resposta
                }
            }
            rede = Rede(servidor.url("/").toString(), tokens, depuracao = false)

            val quantas = 8
            val terminou = CountDownLatch(quantas)
            val codigos = java.util.Collections.synchronizedList(mutableListOf<Int>())
            repeat(quantas) {
                rede.autenticado.newCall(Request.Builder().url(servidor.url("/protegido")).build())
                    .enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            codigos.add(-1)
                            terminou.countDown()
                        }

                        override fun onResponse(call: Call, response: Response) {
                            codigos.add(response.code)
                            response.close()
                            terminou.countDown()
                        }
                    })
            }

            assertTrue("as requisições travaram (renovação sem vaga no despachante)", terminou.await(20, TimeUnit.SECONDS))
            assertEquals(List(quantas) { 200 }, codigos.toList())
            assertEquals("uma única renovação para todas", 1, renovacoes.get())
        } finally {
            servidor.shutdown()
        }
    }
}
