package com.example.netafim.atendimento.ui.comum

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.netafim.atendimento.R
import com.example.netafim.atendimento.dominio.ErroAoSalvar

// Aviso de erro de uma escrita que falhou (iniciar, salvar a edição ou abrir). Segue o padrão que a edição já
// usava: texto no corpo da tela, logo acima do botão da ação, e não um Snackbar. O Snackbar aparece embaixo e
// tamparia o botão; no corpo o aviso entra abaixo dos campos, então o que está acima (inclusive o campo em
// foco) não sai do lugar.
//
// Para o leitor de tela: o texto é uma região viva educada (anuncia quando aparece, sem interromper a fala em
// andamento) e tem descrição própria, com "Erro:" na frente, porque a cor e o ícone não chegam a quem ouve.
// O ícone é decorativo. O botão de fechar tem 48dp de toque e chama aoDispensar.
// "acao" é um encaixe opcional embaixo do texto, usado pela edição para o botão Atualizar do conflito.
@Composable
fun AvisoDeErro(
    erro: ErroAoSalvar,
    aoDispensar: () -> Unit,
    modifier: Modifier = Modifier,
    acao: (@Composable () -> Unit)? = null,
) {
    val cor = Color(COR_DO_AVISO_DE_ERRO)
    // Na regra de negócio vale o texto que a API devolveu; vazio cai no texto genérico.
    val generico = stringResource(erro.rotulo())
    val texto = if (erro is ErroAoSalvar.RegraDeNegocio) erro.mensagem.ifBlank { generico } else generico
    val descricao = stringResource(R.string.atendimento_erro_descricao, texto)
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = Icons.Filled.Error,
            contentDescription = null,
            tint = cor,
            // Desce um pouco para alinhar com a primeira linha do texto, que fica centrada no botão de fechar.
            modifier = Modifier
                .padding(top = 14.dp)
                .size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 12.dp),
        ) {
            Text(
                text = texto,
                style = MaterialTheme.typography.bodyMedium,
                color = cor,
                modifier = Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = descricao
                },
            )
            acao?.invoke()
        }
        IconButton(onClick = aoDispensar) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.atendimento_erro_dispensar),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun AvisoDeErroPreview() {
    TemaDePreview {
        AvisoDeErro(erro = ErroAoSalvar.SemConexao, aoDispensar = {}, modifier = Modifier.padding(16.dp))
    }
}
