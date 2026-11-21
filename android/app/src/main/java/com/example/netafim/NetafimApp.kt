package com.example.netafim

import android.app.Application
import com.example.netafim.acesso.AcessoGrafo

class NetafimApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AcessoGrafo.inicializar(
            contexto = this,
            configuracao = AcessoGrafo.Configuracao(
                urlApi = BuildConfig.URL_API,
                versaoApp = BuildConfig.VERSION_NAME,
                depuracao = BuildConfig.DEBUG,
                hostLink = BuildConfig.LINK_HOST,
                esquemaProprio = BuildConfig.DEBUG,
            ),
        )
    }
}
