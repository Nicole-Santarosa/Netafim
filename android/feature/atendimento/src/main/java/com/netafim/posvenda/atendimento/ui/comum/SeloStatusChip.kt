package com.netafim.posvenda.atendimento.ui.comum

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.netafim.posvenda.atendimento.dominio.SeloStatus

// Pílula com a bolinha de cor e o rótulo do status. A bolinha é só decorativa (sem descrição de
// acessibilidade): quem lê a informação para o leitor de tela é o texto.
@Composable
fun SeloStatusChip(selo: SeloStatus, modifier: Modifier = Modifier) {
    val cores = coresDoSelo(selo)
    Row(
        modifier = modifier
            .background(Color(cores.fundo), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(Color(cores.bolinha), CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(selo.rotulo()),
            style = MaterialTheme.typography.labelMedium,
            color = Color(cores.texto),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SeloStatusChipPreview() {
    TemaDePreview {
        Row(modifier = Modifier.padding(16.dp)) {
            SeloStatus.entries.forEach { selo ->
                SeloStatusChip(selo = selo, modifier = Modifier.padding(end = 8.dp))
            }
        }
    }
}
