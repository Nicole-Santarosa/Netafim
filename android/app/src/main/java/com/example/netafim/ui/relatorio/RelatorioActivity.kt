package com.example.netafim.ui.relatorio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.netafim.ui.theme.NetafimTheme

class RelatorioActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NetafimTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFFFFBEB) // Fundo Creme Netafim
                ) {
                    RelatorioRota(
                        motivoInicial = intent.getStringExtra("EXTRA_MOTIVO") ?: "pos_venda",
                        onVoltar = { finish() }
                    )
                }
            }
        }
    }
}