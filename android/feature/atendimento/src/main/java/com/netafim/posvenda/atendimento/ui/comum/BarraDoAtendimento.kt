package com.netafim.posvenda.atendimento.ui.comum

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.netafim.posvenda.atendimento.R

// Barra de topo das telas de atendimento: fundo primary, título em negrito na cor onPrimary.
// A seta de voltar só aparece quando a tela recebe aoVoltar; a tela inicial da lista não passa.
// O subtítulo é opcional: quando existe, aparece abaixo do título em fonte menor.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarraDoAtendimento(titulo: String, aoVoltar: (() -> Unit)? = null, subtitulo: String? = null) {
    TopAppBar(
        title = {
            if (subtitulo == null) {
                Text(text = titulo, fontWeight = FontWeight.Bold)
            } else {
                Column {
                    Text(text = titulo, fontWeight = FontWeight.Bold)
                    Text(
                        text = subtitulo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                    )
                }
            }
        },
        navigationIcon = {
            if (aoVoltar != null) {
                IconButton(onClick = aoVoltar) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.atendimento_voltar),
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

@Preview(showBackground = true)
@Composable
private fun BarraDoAtendimentoPreview() {
    TemaDePreview {
        Column {
            BarraDoAtendimento(titulo = "Abrir novo atendimento", aoVoltar = {})
            BarraDoAtendimento(titulo = "Atendimento OS-0498", aoVoltar = {}, subtitulo = "Dúvidas · Concluído")
        }
    }
}
