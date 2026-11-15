package com.example.netafim.ui.relatorio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.netafim.ui.theme.NetafimTheme

class RelatorioActivity : ComponentActivity() {

    private val viewModel: RelatorioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val motivoRecebido = intent.getStringExtra("EXTRA_MOTIVO") ?: "pos_venda"
        viewModel.setMotivo(motivoRecebido)

        setContent {
            NetafimTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RelatorioScreen(viewModel = viewModel, onVoltar = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelatorioScreen(viewModel: RelatorioViewModel, onVoltar: () -> Unit) {
    val motivo by viewModel.motivoAtendimento.collectAsState()
    val constatacoes by viewModel.constatacoes.collectAsState()
    val atividades by viewModel.atividades.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Relatório Técnico - $motivo") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF181F4E), // Azul Meia-Noite (Netafim)[cite: 59, 64]
                    titleContentColor = Color(0xFFFFFBEB) // Creme (Netafim)[cite: 59, 64]
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "Preenchimento de Campo (Pessoa 3)", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = constatacoes,
                onValueChange = { viewModel.atualizarConstatacoes(it) },
                label = { Text("Principais Constatações *") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = atividades,
                onValueChange = { viewModel.atualizarAtividades(it) },
                label = { Text("Atividades Executadas *") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    // Validação de teste do relatório
                    val valido = viewModel.validarRelatorio(temAssinatura = true)
                    // Aqui faremos a chamada para os fragments de evidência/câmera e assinatura
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF181F4E))
            ) {
                Text("Avançar para Evidências e Assinatura", color = Color(0xFFFFFBEB))
            }
        }
    }
}