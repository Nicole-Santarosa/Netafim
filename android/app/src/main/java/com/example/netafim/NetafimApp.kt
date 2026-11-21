package com.example.netafim

import android.app.Application
import com.example.netafim.acesso.AcessoGrafo

/**
 * Inicializa os componentes principais do aplicativo.
 */
class NetafimApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Inicializa autenticação, sessão e serviços do módulo de acesso.
        AcessoGrafo.inicializar(
            contexto = this,
            configuracao = AcessoGrafo.Configuracao(
                urlApi = BuildConfig.URL_API,
                versaoApp = BuildConfig.VERSION_NAME,
                depuracao = BuildConfig.DEBUG,
            ),
        )

        // Inicializa Room e os repositórios de persistência local.
        PersistenciaGrafo.inicializar(this)
    }
}
