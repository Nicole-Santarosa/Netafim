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
    val viewModel = remember {
        val activity = context.findActivity()
        if (activity != null) ViewModelProvider(activity)[RelatorioViewModel::class.java]
        else RelatorioViewModel()
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
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (etapaAtual > 1) {
                        OutlinedButton(onClick = { etapaAtual -= 1 }, modifier = Modifier.weight(1f)) {
                            Text("Voltar")
                        }
                    }

                    Button(
                        onClick = {
                            val (valido, msgErro) = viewModel.validarEtapa(etapaAtual)
                            if (!valido) {
                                Toast.makeText(context, msgErro, Toast.LENGTH_SHORT).show()
                            } else {
                                if (etapaAtual < 4) {
                                    etapaAtual += 1
                                } else {
                                    viewModel.salvarRelatorio { sucesso ->
                                        if (sucesso) {
                                            Toast.makeText(context, "Relatório salvo com sucesso!", Toast.LENGTH_LONG).show()
                                            onVoltar()
                                        } else {
                                            Toast.makeText(context, "Erro ao salvar relatório no banco.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF181F4E))
                    ) {
                        Text(if (etapaAtual == 4) "Salvar e Concluir" else "Avançar", color = Color(0xFFFFFBEB))
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (etapaAtual) {
                1 -> ConstatacoesEtapa(viewModel = viewModel)
                2 -> AtividadesEtapa(viewModel = viewModel)
                3 -> PlanoAcaoEtapa(viewModel = viewModel)
                4 -> AssinaturaEtapa(viewModel = viewModel)
            }
        }
    }
}

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}