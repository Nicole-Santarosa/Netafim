package com.netafim.posvenda.acesso

import android.content.Context
import android.os.Build
import com.netafim.posvenda.acesso.dados.AcessoApi
import com.netafim.posvenda.acesso.dados.ArmazenamentoSessaoSeguro
import com.netafim.posvenda.acesso.dados.InfoAparelho
import com.netafim.posvenda.acesso.dados.ProvedorTokens
import com.netafim.posvenda.acesso.dados.Rede
import com.netafim.posvenda.acesso.dados.RepositorioAcessoRemoto
import com.netafim.posvenda.acesso.dominio.RepositorioAcesso
import okhttp3.OkHttpClient
import retrofit2.Retrofit

/**
 * Ponto de entrada do módulo de acesso (injeção de dependências manual).
 *
 * O app chama [inicializar] uma vez no `Application.onCreate`. Os outros módulos usam:
 * - [retrofitAutenticado] / [clienteAutenticado] para chamar a API com o token da
 *   sessão, já com renovação automática;
 * - [repositorio] para ler o usuário logado e as permissões (`sessao.value?.usuario`).
 */
object AcessoGrafo {

    data class Configuracao(
        val urlApi: String,
        val versaoApp: String,
        val depuracao: Boolean,
    )

    private lateinit var rede: Rede
    private lateinit var repositorioRemoto: RepositorioAcessoRemoto

    val repositorio: RepositorioAcesso get() = repositorioRemoto
    val clienteAutenticado: OkHttpClient get() = rede.autenticado
    val retrofitAutenticado: Retrofit by lazy { rede.retrofit(rede.autenticado) }

    fun inicializar(contexto: Context, configuracao: Configuracao) {
        if (::repositorioRemoto.isInitialized) return
        val tokens = ProvedorTokensTardio()
        rede = Rede(configuracao.urlApi, tokens, configuracao.depuracao)
        val publica = rede.retrofit(rede.publico).create(AcessoApi::class.java)
        val autenticada = rede.retrofit(rede.autenticado).create(AcessoApi::class.java)
        repositorioRemoto = RepositorioAcessoRemoto(
            apiPublica = publica,
            apiAutenticada = autenticada,
            armazenamento = ArmazenamentoSessaoSeguro(contexto),
            json = rede.json,
            aparelho = InfoAparelho(
                modelo = "${Build.MANUFACTURER} ${Build.MODEL}".take(80),
                versaoApp = configuracao.versaoApp.take(20),
            ),
        )
        tokens.alvo = repositorioRemoto
    }

    /** Lê a sessão guardada no aparelho (sem internet). */
    suspend fun restaurarSessao() = repositorioRemoto.restaurar()

    /** Rede e repositório dependem um do outro; este adaptador quebra o ciclo. */
    private class ProvedorTokensTardio : ProvedorTokens {
        lateinit var alvo: ProvedorTokens
        override fun accessTokenAtual() = alvo.accessTokenAtual()
        override suspend fun renovar(accessUsado: String?) = alvo.renovar(accessUsado)
    }
}
