package com.example.netafim.atendimento.ui.comum

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.netafim.atendimento.R

// Indicador de progresso no centro da área da lista, mostrado enquanto o repositório não respondeu pela
// primeira vez. Fica no lugar da mensagem de lista vazia, que só aparece quando a resposta veio sem itens.
// O círculo não tem texto, então ganha uma descrição para o leitor de tela anunciar o que está acontecendo.
@Composable
fun IndicadorDeCarregando(modifier: Modifier = Modifier) {
    val descricao = stringResource(R.string.atendimento_lista_carregando)
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = descricao })
    }
}
