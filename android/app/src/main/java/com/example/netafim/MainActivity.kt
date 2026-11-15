package com.example.netafim

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.netafim.ui.relatorio.RelatorioActivity
import com.example.netafim.ui.theme.NetafimTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NetafimTheme {
                // Forçamos o fundo Creme/Claro da Netafim para evitar o fundo preto do Dark Mode
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFFFFBEB) // Creme - Identidade Netafim Figma
                ) {
                    MenuTestePessoa3(
                        onAbrirRelatorio = {
                            val intent = Intent(this, RelatorioActivity::class.java).apply {
                                putExtra("EXTRA_MOTIVO", "pos_venda")
                            }
                            startActivity(intent)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MenuTestePessoa3(modifier: Modifier = Modifier, onAbrirRelatorio: () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFFFFBEB)), // Garante o fundo Creme
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp), // Espaçamento mais fiel ao Figma
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Painel de Testes\nPessoa 3",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF181F4E), // Azul Meia-Noite Netafim
                textAlign = TextAlign.Center
            )

            Text(
                text = "Teste o fluxo de relatórios, constatações, câmera obrigatória e assinatura digital.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF4A4A4A), // Cinza chumbo legível para texto de apoio
                textAlign = TextAlign.Center
            )

            Button(
                onClick = onAbrirRelatorio,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp), // Altura padrão de botões no Figma/Material
                shape = RoundedCornerShape(12.dp), // Borda arredondada moderna
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF181F4E) // Azul Meia-Noite Netafim
                )
            ) {
                Text(
                    text = "Abrir Relatório Técnico",
                    color = Color(0xFFFFFBEB), // Texto em Creme
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}