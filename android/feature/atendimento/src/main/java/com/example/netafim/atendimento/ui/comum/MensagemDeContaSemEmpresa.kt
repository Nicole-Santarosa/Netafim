package com.example.netafim.atendimento.ui.comum

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.netafim.atendimento.R

// Mensagem das listas quando a conta ainda não foi vinculada a uma empresa (EstadoDaLista.SemOrganizacao).
// Mesmo lugar e mesmo alinhamento centralizado da mensagem de lista vazia, mas com título e explicação, porque
// aqui a pessoa precisa saber o motivo e o que fazer.
// - Sem botão: vincular a conta é tarefa do Netafim Admin, e o app não tem o que oferecer.
// - O ícone é decorativo; o título é cabeçalho para o leitor de tela poder pular direto até ele.
// - Título e texto usam a cor padrão do texto do tema (onSurface), e não o cinza de apoio, para não depender
//   do contraste de um tom que o tema do :app ainda pode mudar.
@Composable
fun MensagemDeContaSemEmpresa(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Icon(
            imageVector = Icons.Filled.Business,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = stringResource(R.string.atendimento_conta_sem_empresa_titulo),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.atendimento_conta_sem_empresa_texto),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 400)
@Composable
private fun MensagemDeContaSemEmpresaPreview() {
    TemaDePreview { MensagemDeContaSemEmpresa() }
}
