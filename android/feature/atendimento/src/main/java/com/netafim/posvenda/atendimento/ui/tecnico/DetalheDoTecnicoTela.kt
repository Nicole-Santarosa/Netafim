package com.netafim.posvenda.atendimento.ui.tecnico

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.netafim.posvenda.atendimento.R
import com.netafim.posvenda.atendimento.dominio.DetalheDoTecnico
import com.netafim.posvenda.atendimento.dominio.MotivoAtendimento
import com.netafim.posvenda.atendimento.dominio.StatusAtendimento
import com.netafim.posvenda.atendimento.dominio.culturasEmTexto
import com.netafim.posvenda.atendimento.dominio.podeIniciar
import com.netafim.posvenda.atendimento.ui.comum.BarraDoAtendimento
import com.netafim.posvenda.atendimento.ui.comum.CampoDeLeitura
import com.netafim.posvenda.atendimento.ui.comum.TemaDePreview
import com.netafim.posvenda.atendimento.ui.comum.rotulo

// Detalhe do atendimento para o técnico. Tela sem estado: mostra os dados que receber e só chama
// aoIniciar quando o técnico toca no botão. O que acontece depois do clique (chamada ao servidor e
// os passos seguintes do técnico) não é desta tela. O botão só aparece quando o status permite
// iniciar; nos outros status a tela é só de leitura. "iniciando" desabilita o botão e mostra o
// indicador de progresso enquanto o pedido está a caminho.
@Composable
fun DetalheDoTecnicoTela(
    detalhe: DetalheDoTecnico,
    iniciando: Boolean,
    aoIniciar: () -> Unit,
    aoVoltar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val naoInformado = stringResource(R.string.atendimento_tecnico_nao_informado)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        BarraDoAtendimento(titulo = stringResource(R.string.atendimento_tecnico_titulo), aoVoltar = aoVoltar)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(26.dp))

            // Campos de leitura, na ordem do protótipo.
            CampoDeLeitura(stringResource(R.string.atendimento_tecnico_numero), detalhe.protocolo)
            CampoDeLeitura(stringResource(R.string.atendimento_tecnico_produtor), detalhe.produtor)
            CampoDeLeitura(stringResource(R.string.atendimento_tecnico_local), detalhe.local ?: naoInformado)
            CampoDeLeitura(
                stringResource(R.string.atendimento_tecnico_cultura),
                culturasEmTexto(detalhe.culturas) ?: naoInformado,
            )
            CampoDeLeitura(
                stringResource(R.string.atendimento_tecnico_motivo),
                stringResource(detalhe.motivo.rotulo()),
            )

            // Ação principal, só no status em que o back-end aceita iniciar.
            if (podeIniciar(detalhe.status)) {
                Spacer(Modifier.height(34.dp))
                BotaoIniciar(iniciando = iniciando, aoIniciar = aoIniciar)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// Mesmo padrão do botão de enviar da abertura: cor primária, versão mais clara quando desabilitado e
// indicador de progresso no lugar do texto enquanto o pedido está a caminho.
@Composable
private fun BotaoIniciar(iniciando: Boolean, aoIniciar: () -> Unit) {
    Button(
        onClick = aoIniciar,
        enabled = !iniciando,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            disabledContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        if (iniciando) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(
                text = stringResource(R.string.atendimento_tecnico_iniciar),
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// Dados fictícios dos previews.
private val detalheDoPreview = DetalheDoTecnico(
    id = "1",
    protocolo = "OS-0506",
    produtor = "Vanderlei Zucchi Rodas",
    local = "Arealva, SP",
    culturas = listOf("Citrus"),
    motivo = MotivoAtendimento.DIGITAL_FARMING,
    status = StatusAtendimento.AGENDADO,
)

@Composable
private fun TelaDePreview(detalhe: DetalheDoTecnico, iniciando: Boolean = false) {
    TemaDePreview {
        DetalheDoTecnicoTela(detalhe = detalhe, iniciando = iniciando, aoIniciar = {}, aoVoltar = {})
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 828)
@Composable
private fun DetalheDoTecnicoAgendadoPreview() {
    TelaDePreview(detalheDoPreview)
}

@Preview(showBackground = true, widthDp = 360, heightDp = 828)
@Composable
private fun DetalheDoTecnicoIniciandoPreview() {
    TelaDePreview(detalheDoPreview, iniciando = true)
}

@Preview(showBackground = true, widthDp = 360, heightDp = 828)
@Composable
private fun DetalheDoTecnicoEmAtendimentoPreview() {
    TelaDePreview(detalheDoPreview.copy(status = StatusAtendimento.EM_ATENDIMENTO))
}

@Preview(showBackground = true, widthDp = 360, heightDp = 828)
@Composable
private fun DetalheDoTecnicoDestinadoPreview() {
    TelaDePreview(detalheDoPreview.copy(status = StatusAtendimento.DESTINADO))
}

@Preview(showBackground = true, widthDp = 360, heightDp = 828)
@Composable
private fun DetalheDoTecnicoSemLocalNemCulturasPreview() {
    TelaDePreview(detalheDoPreview.copy(local = null, culturas = emptyList()))
}

@Preview(showBackground = true, widthDp = 360, heightDp = 828)
@Composable
private fun DetalheDoTecnicoVariasCulturasPreview() {
    TelaDePreview(detalheDoPreview.copy(culturas = listOf("Citrus", "Cana-de-açúcar", "Soja", "Milho")))
}
