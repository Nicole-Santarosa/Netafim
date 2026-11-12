package com.netafim.posvenda.atendimento.ui.fila

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import com.netafim.posvenda.atendimento.dominio.StatusAtendimento
import com.netafim.posvenda.atendimento.ui.comum.coresDoSelo

// Selo de status da fila: pílula de 22dp com ponto de 5dp e o texto. É próprio da fila porque o texto
// vem de rotuloNaFila ("Novo" para destinado e agendado) e as medidas são menores que as do
// SeloStatusChip. As cores são as do selo do status. O ponto é decorativo; quem informa o estado é o texto.
// rotuloDoStatus permite que outra lista (a da região do distribuidor) use o rótulo normal do selo.
@Composable
fun SeloDaFila(
    status: StatusAtendimento,
    modifier: Modifier = Modifier,
    rotuloDoStatus: (StatusAtendimento) -> Int = ::rotuloNaFila,
) {
    val cores = coresDoSelo(status.selo)
    Row(
        modifier = modifier
            .height(22.dp)
            .background(Color(cores.fundo), CircleShape)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .background(Color(cores.bolinha), CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(rotuloDoStatus(status)),
            style = MaterialTheme.typography.labelMedium,
            color = Color(cores.texto),
        )
    }
}
