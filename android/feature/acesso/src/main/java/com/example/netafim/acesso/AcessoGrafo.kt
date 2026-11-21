package com.example.netafim.acesso

import android.content.Context
import android.os.Build
import com.example.netafim.acesso.dados.AcessoApi
import com.example.netafim.acesso.dados.ArmazenamentoSessaoSeguro
import com.example.netafim.acesso.dados.FotosDePerfil
import com.example.netafim.acesso.dados.InfoAparelho
import com.example.netafim.acesso.dados.ProvedorTokens
import com.example.netafim.acesso.dados.Rede
import com.example.netafim.acesso.dados.RepositorioAcessoRemoto
import com.example.netafim.acesso.dados.RepositorioCadastrosRemoto
import com.example.netafim.acesso.dados.RepositorioFilaPrivacidadeRemoto
import com.example.netafim.acesso.dados.RepositorioPerfisRemoto
import com.example.netafim.acesso.dados.RepositorioPrivacidadeRemoto
import com.example.netafim.acesso.dados.RepositorioUsuariosRemoto
import com.example.netafim.acesso.dominio.RepositorioAcesso
import com.example.netafim.acesso.dominio.RepositorioCadastros
import com.example.netafim.acesso.dominio.RepositorioFilaPrivacidade
import com.example.netafim.acesso.dominio.RepositorioPerfis
import com.example.netafim.acesso.dominio.RepositorioPrivacidade
import com.example.netafim.acesso.dominio.RepositorioUsuarios
import com.example.netafim.acesso.dominio.padroesDeLink
import com.example.netafim.acesso.ui.privacidade.CopiaDosDados
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
        /** Domínio do App Link dos e-mails (ex.: `app.netafim.com.br`); vazio = sem link https. */
        val hostLink: String = "",
        /** Aceita também `netafim://convite`; só para debug (qualquer app pode registrar o esquema). */
        val esquemaProprio: Boolean = false,
    )

    private lateinit var rede: Rede
    private lateinit var repositorioRemoto: RepositorioAcessoRemoto
    private lateinit var cadastrosRemoto: RepositorioCadastrosRemoto
    private lateinit var usuariosRemoto: RepositorioUsuariosRemoto
    private lateinit var perfisRemoto: RepositorioPerfisRemoto
    private lateinit var privacidadeRemoto: RepositorioPrivacidadeRemoto
    private lateinit var filaPrivacidadeRemoto: RepositorioFilaPrivacidadeRemoto

    private lateinit var fotosDePerfil: FotosDePerfil

    val repositorio: RepositorioAcesso get() = repositorioRemoto

    /** Foto de perfil de cada usuário, guardada no aparelho. */
    val fotos: FotosDePerfil get() = fotosDePerfil

    /** Fila de cadastros pendentes (aprovar e recusar), para quem tem a permissão aprovar_cadastro. */
    val repositorioCadastros: RepositorioCadastros get() = cadastrosRemoto

    /** Gestão de usuários da própria organização (listar e convidar). */
    val repositorioUsuarios: RepositorioUsuarios get() = usuariosRemoto

    /** Permissões por perfil (RF20), para o Netafim Admin. */
    val repositorioPerfis: RepositorioPerfis get() = perfisRemoto

    /** Direitos do titular (LGPD): pedidos e cópia dos próprios dados, para qualquer usuário logado. */
    val repositorioPrivacidade: RepositorioPrivacidade get() = privacidadeRemoto

    /** Fila de pedidos de titulares (LGPD), para o Netafim Admin. */
    val repositorioFilaPrivacidade: RepositorioFilaPrivacidade get() = filaPrivacidadeRemoto

    /** Padrões de link aceitos pela tela do convite e da redefinição de senha (ver [padroesDeLink]). */
    var padroesDeLink: List<String> = emptyList()
        private set
    val clienteAutenticado: OkHttpClient get() = rede.autenticado
    val retrofitAutenticado: Retrofit by lazy { rede.retrofit(rede.autenticado) }

    fun inicializar(contexto: Context, configuracao: Configuracao) {
        if (::repositorioRemoto.isInitialized) return
        padroesDeLink = padroesDeLink(configuracao.hostLink, configuracao.esquemaProprio)
        fotosDePerfil = FotosDePerfil(contexto.applicationContext)
        // Cópias de dados pessoais que sobraram da última vez: apaga fora da thread principal.
        Thread { CopiaDosDados.limpar(CopiaDosDados.pastaDe(contexto.applicationContext)) }.start()
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
        cadastrosRemoto = RepositorioCadastrosRemoto(api = autenticada, json = rede.json)
        usuariosRemoto = RepositorioUsuariosRemoto(api = autenticada, json = rede.json)
        perfisRemoto = RepositorioPerfisRemoto(api = autenticada, json = rede.json)
        privacidadeRemoto = RepositorioPrivacidadeRemoto(api = autenticada, json = rede.json)
        filaPrivacidadeRemoto = RepositorioFilaPrivacidadeRemoto(api = autenticada, json = rede.json)
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
