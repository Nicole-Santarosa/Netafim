package com.netafim.posvenda

import android.app.Application
import com.netafim.posvenda.acesso.AcessoGrafo

class NetafimApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AcessoGrafo.inicializar(
            contexto = this,
            configuracao = AcessoGrafo.Configuracao(
                urlApi = BuildConfig.URL_API,
                versaoApp = BuildConfig.VERSION_NAME,
                depuracao = BuildConfig.DEBUG,
            ),
        )
    }
}
