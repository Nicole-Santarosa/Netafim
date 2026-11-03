package com.netafim.posvenda

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.netafim.posvenda.acesso.AcessoGrafo
import com.netafim.posvenda.ui.NavegacaoRaiz
import com.netafim.posvenda.ui.tema.TemaNetafim

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TemaNetafim {
                Raiz()
            }
        }
    }
}

/** Restaura a sessão salva (funciona sem internet) e decide entre login e início. */
@Composable
private fun Raiz() {
    var restaurada by remember { mutableStateOf(false) }
    var logado by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        logado = AcessoGrafo.restaurarSessao() != null
        restaurada = true
    }
    if (!restaurada) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        NavegacaoRaiz(iniciarLogado = logado)
    }
}
