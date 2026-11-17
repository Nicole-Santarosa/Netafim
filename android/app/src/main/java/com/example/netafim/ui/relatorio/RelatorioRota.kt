package com.example.netafim.ui.relatorio

import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.example.netafim.ui.relatorio.etapas.AssinaturaEtapa
import com.example.netafim.ui.relatorio.etapas.AtividadesEtapa
import com.example.netafim.ui.relatorio.etapas.ConstatacoesEtapa
import com.example.netafim.ui.relatorio.etapas.PlanoAcaoEtapa

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelatorioRota(
    motivoInicial: String = "pos_venda",
    onVoltar: () -> Unit
) {
    val context = LocalContext.current

    // Instancia o ViewModel com segurança utilizando a Activity e o ViewModelProvider nativo,
    // eliminando a necessidade da dependência externa de compose do lifecycle.
    val viewModel = remember {
        val activity = context.findActivity()
        if (activity != null) {
            ViewModelProvider(activity)[RelatorioViewModel::class.java]
        } else {
            RelatorioViewModel()
        }
    }

    var etapaAtual by remember { mutableStateOf(1) }

    LaunchedEffect(motivoInicial) {
        viewModel.setMotivo(motivoInicial)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Relatório Técnico - Etapa $etapaAtual de 4", color = Color(0xFFFFFBEB)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF181F4E))
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (etapaAtual > 1) {
                        OutlinedButton(
                            onClick = { etapaAtual -= 1 },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Voltar")
                        }
                    }

                    Button(
                        onClick = {
                            if (etapaAtual < 4) {
                                etapaAtual += 1
                            } else {
                                val valido = viewModel.validarRelatorio()
                                if (valido) {
                                    Toast.makeText(context, "Relatório concluído com sucesso!", Toast.LENGTH_LONG).show()
                                    onVoltar()
                                } else {
                                    Toast.makeText(context, "Preencha todos os campos e ambas as assinaturas obrigatórias.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF181F4E))
                    ) {
                        Text(if (etapaAtual == 4) "Concluir Relatório" else "Avançar", color = Color(0xFFFFFBEB))
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (etapaAtual) {
                1 -> ConstatacoesEtapa(viewModel = viewModel)
                2 -> AtividadesEtapa(viewModel = viewModel)
                3 -> PlanoAcaoEtapa(viewModel = viewModel)
                4 -> AssinaturaEtapa(viewModel = viewModel)
            }
        }
    }
}

/**
 * Função utilitária para extrair a ComponentActivity a partir de um Context do Compose.
 */
private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}