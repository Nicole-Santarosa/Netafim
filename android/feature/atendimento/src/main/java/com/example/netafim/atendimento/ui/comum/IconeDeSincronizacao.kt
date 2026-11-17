package com.example.netafim.atendimento.ui.comum

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.netafim.atendimento.R
import com.example.netafim.atendimento.dominio.IndicadorDeSincronizacao
import com.example.netafim.atendimento.dominio.comoIndicador
import com.example.netafim.core.modelo.SituacaoDeSincronizacao

// Ícone pequeno (16dp) que diz, no cartão da lista, se o registro ainda espera envio ou se deu problema.
// Não desenha nada quando o registro está sincronizado.
// A informação não depende só da cor: pendente é uma ampulheta e atenção é um triângulo de alerta. A
// ampulheta foi escolhida no lugar do relógio porque o relógio já é o ícone do status aberto na fila.
// Leitor de tela: o ícone tem a própria descrição e fica dentro do cartão, que junta tudo num item só
// (mergeDescendants). Assim a frase entra uma vez na leitura do cartão, depois do protocolo, sem ser repetida.
@Composable
fun IconeDeSincronizacao(situacao: SituacaoDeSincronizacao, modifier: Modifier = Modifier) {
    val indicador = situacao.comoIndicador() ?: return
    Icon(
        imageVector = when (indicador) {
            IndicadorDeSincronizacao.PENDENTE -> Icons.Filled.HourglassTop
            IndicadorDeSincronizacao.ATENCAO -> Icons.Filled.Warning
        },
        contentDescription = stringResource(descricaoDaSituacao(situacao)),
        tint = Color(corDoIndicador(indicador)),
        modifier = modifier.size(16.dp),
    )
}

// Texto que o leitor de tela fala. O desenho de conflito e erro é o mesmo, mas a frase diz qual dos dois é.
// SINCRONIZADO nunca chega aqui (não tem ícone), e fica com o texto de pendente só para o when ser completo.
@StringRes
private fun descricaoDaSituacao(situacao: SituacaoDeSincronizacao): Int = when (situacao) {
    SituacaoDeSincronizacao.SINCRONIZADO,
    SituacaoDeSincronizacao.PENDENTE_DE_ENVIO,
    -> R.string.atendimento_sincronizacao_pendente
    SituacaoDeSincronizacao.CONFLITO -> R.string.atendimento_sincronizacao_conflito
    SituacaoDeSincronizacao.ERRO -> R.string.atendimento_sincronizacao_erro
}

@Preview(showBackground = true)
@Composable
private fun IconeDeSincronizacaoPreview() {
    TemaDePreview {
        Row {
            SituacaoDeSincronizacao.entries.forEach { IconeDeSincronizacao(it) }
        }
    }
}
