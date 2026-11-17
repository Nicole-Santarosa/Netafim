package com.example.netafim.atendimento.ui.comum

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.netafim.atendimento.dominio.comoIndicador
import com.example.netafim.core.modelo.SituacaoDeSincronizacao

// Aviso, nas telas de detalhe, de que o registro ainda não está igual ao do servidor: aguardando envio, em
// conflito ou com erro no envio. Como Sucesso numa escrita quer dizer "salvo no aparelho", quem abre o
// atendimento precisa saber que a mudança ainda não chegou ao servidor. Sincronizado não desenha nada.
//
// Diferente do AvisoDeErro, não tem botão de fechar: aqui não é um evento que aconteceu e já foi lido, é o
// estado do registro, e o aviso some sozinho quando a sincronização muda a situação.
//
// Mesmo desenho e mesmas cores do ícone da lista (ampulheta para pendente, triângulo para conflito e erro).
// Aqui o ícone é decorativo: o texto ao lado já diz tudo e o leitor de tela leria a mesma coisa duas vezes.
// O texto é região viva educada porque a situação pode mudar com a tela aberta, quando a sincronização roda;
// é lido como está, sem prefixo de "Erro:", porque aguardando envio não é erro.
@Composable
fun AvisoDeSincronizacao(situacao: SituacaoDeSincronizacao, modifier: Modifier = Modifier) {
    val indicador = situacao.comoIndicador() ?: return
    val texto = situacao.textoDoAviso() ?: return
    val cor = Color(corDoIndicador(indicador))
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = indicador.icone(),
            contentDescription = null,
            tint = cor,
            // Desce um pouco para alinhar com a primeira linha do texto.
            modifier = Modifier
                .padding(top = 1.dp)
                .size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(texto),
            style = MaterialTheme.typography.bodyMedium,
            color = cor,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

// Um preview por situação que mostra aviso; o sincronizado não desenha nada e por isso não tem preview.
@Composable
private fun AvisoDoPreview(situacao: SituacaoDeSincronizacao) {
    TemaDePreview { AvisoDeSincronizacao(situacao, modifier = Modifier.padding(16.dp)) }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun AvisoDeSincronizacaoPendentePreview() = AvisoDoPreview(SituacaoDeSincronizacao.PENDENTE_DE_ENVIO)

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun AvisoDeSincronizacaoConflitoPreview() = AvisoDoPreview(SituacaoDeSincronizacao.CONFLITO)

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun AvisoDeSincronizacaoErroPreview() = AvisoDoPreview(SituacaoDeSincronizacao.ERRO)

// Fonte no dobro do tamanho, com o texto mais longo (conflito), para conferir que o texto quebra em várias
// linhas, o ícone continua alinhado com a primeira e nada é cortado.
@Preview(showBackground = true, widthDp = 360, fontScale = 2f)
@Composable
private fun AvisoDeSincronizacaoFonteGrandePreview() = AvisoDoPreview(SituacaoDeSincronizacao.CONFLITO)
