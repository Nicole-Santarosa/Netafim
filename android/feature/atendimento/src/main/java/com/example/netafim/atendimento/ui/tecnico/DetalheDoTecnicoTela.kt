package com.example.netafim.atendimento.ui.tecnico

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
import com.example.netafim.atendimento.R
import com.example.netafim.atendimento.dominio.culturasEmTexto
import com.example.netafim.atendimento.dominio.podeIniciar
import com.example.netafim.atendimento.ui.comum.BarraDoAtendimento
import com.example.netafim.atendimento.ui.comum.CampoDeLeitura
import com.example.netafim.atendimento.ui.comum.TemaDePreview
import com.example.netafim.atendimento.ui.comum.atendimentoDePreview
import com.example.netafim.atendimento.ui.comum.rotulo
import com.example.netafim.core.modelo.Atendimento
import com.example.netafim.core.modelo.Cultura
import com.example.netafim.core.modelo.MotivoAtendimento
import com.example.netafim.core.modelo.StatusAtendimento

// Detalhe do atendimento para o técnico. Tela sem estado: mostra os dados que receber e só chama
// aoIniciar quando o técnico toca no botão. O que acontece depois do clique (chamada ao servidor e
// os passos seguintes do técnico) não é desta tela. O botão só aparece quando o status permite
// iniciar; nos outros status a tela é só de leitura. "iniciando" desabilita o botão e mostra o
// indicador de progresso enquanto o pedido está a caminho.
@Composable
fun DetalheDoTecnicoTela(
    atendimento: Atendimento,
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
            // Sem protocolo (atendimento criado sem conexão) o servidor ainda não o gerou.
            CampoDeLeitura(
                stringResource(R.string.atendimento_tecnico_numero),
                atendimento.item.protocolo ?: stringResource(R.string.atendimento_protocolo_pendente),
            )
            CampoDeLeitura(stringResource(R.string.atendimento_tecnico_produtor), atendimento.produtor)
            CampoDeLeitura(stringResource(R.string.atendimento_tecnico_local), atendimento.item.local ?: naoInformado)
            CampoDeLeitura(
                stringResource(R.string.atendimento_tecnico_cultura),
                culturasEmTexto(atendimento.culturas.map { it.nome }) ?: naoInformado,
            )
            CampoDeLeitura(
                stringResource(R.string.atendimento_tecnico_motivo),
                stringResource(atendimento.item.motivo.rotulo()),
            )

            // Ação principal, só no status em que o back-end aceita iniciar.
            if (podeIniciar(atendimento.item.status)) {
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
private fun atendimentoDoPreview(
    status: StatusAtendimento = StatusAtendimento.AGENDADO,
    cidade: String? = "Arealva, SP",
    culturas: List<Cultura> = listOf(Cultura(1, "Citrus")),
) = atendimentoDePreview(
    status = status,
    protocolo = "OS-0506",
    motivo = MotivoAtendimento.DIGITAL_FARMING,
    cidade = cidade,
    culturas = culturas,
)

@Composable
private fun TelaDePreview(atendimento: Atendimento, iniciando: Boolean = false) {
    TemaDePreview {
        DetalheDoTecnicoTela(atendimento = atendimento, iniciando = iniciando, aoIniciar = {}, aoVoltar = {})
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 828)
@Composable
private fun DetalheDoTecnicoAgendadoPreview() {
    TelaDePreview(atendimentoDoPreview())
}

@Preview(showBackground = true, widthDp = 360, heightDp = 828)
@Composable
private fun DetalheDoTecnicoIniciandoPreview() {
    TelaDePreview(atendimentoDoPreview(), iniciando = true)
}

@Preview(showBackground = true, widthDp = 360, heightDp = 828)
@Composable
private fun DetalheDoTecnicoEmAtendimentoPreview() {
    TelaDePreview(atendimentoDoPreview(status = StatusAtendimento.EM_ATENDIMENTO))
}

@Preview(showBackground = true, widthDp = 360, heightDp = 828)
@Composable
private fun DetalheDoTecnicoDestinadoPreview() {
    TelaDePreview(atendimentoDoPreview(status = StatusAtendimento.DESTINADO))
}

@Preview(showBackground = true, widthDp = 360, heightDp = 828)
@Composable
private fun DetalheDoTecnicoSemLocalNemCulturasPreview() {
    TelaDePreview(atendimentoDoPreview(cidade = null, culturas = emptyList()))
}

@Preview(showBackground = true, widthDp = 360, heightDp = 828)
@Composable
private fun DetalheDoTecnicoVariasCulturasPreview() {
    TelaDePreview(
        atendimentoDoPreview(
            culturas = listOf(Cultura(1, "Citrus"), Cultura(2, "Cana-de-açúcar"), Cultura(3, "Soja"), Cultura(4, "Milho")),
        ),
    )
}
