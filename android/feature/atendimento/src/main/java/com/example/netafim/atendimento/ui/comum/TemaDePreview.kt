package com.example.netafim.atendimento.ui.comum

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.netafim.core.modelo.ItemDeAtendimento
import com.example.netafim.core.modelo.MotivoAtendimento
import com.example.netafim.core.modelo.OrigemAtendimento
import com.example.netafim.core.modelo.SituacaoDeSincronizacao
import com.example.netafim.core.modelo.StatusAtendimento
import java.time.Instant

// Tema usado só nos previews do módulo. O TemaNetafim fica no :app e este módulo não o enxerga,
// então aqui as cores principais do protótipo são repetidas para o preview parecer com o app.
// Em tempo de execução as telas usam o MaterialTheme que vier do app; este tema não é aplicado lá.
@Composable
internal fun TemaDePreview(conteudo: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF181F4E),
            onPrimary = Color.White,
            background = Color.White,
            surface = Color.White,
            surfaceVariant = Color(0xFFE9E9E4),
            onSurface = Color(0xFF21211F),
            outlineVariant = Color(0xFFC9C9C2),
        ),
    ) {
        // O Surface garante o fundo branco do protótipo nos previews, mesmo quando o preview
        // não preenche a tela inteira com o fundo do tema.
        Surface(color = MaterialTheme.colorScheme.background, content = conteudo)
    }
}

// Monta um item de lista para os previews. As telas de lista ainda não mostram a origem, o técnico nem a
// situação de sincronização, então esses campos recebem um valor fixo e o preview só varia o que a tela desenha.
internal fun itemDePreview(
    id: String,
    protocolo: String?,
    motivo: MotivoAtendimento,
    status: StatusAtendimento,
    abertoEm: String,
    nomeCliente: String? = null,
    local: String? = null,
    origem: OrigemAtendimento = OrigemAtendimento.CLIENTE_FINAL,
) = ItemDeAtendimento(
    id = id,
    protocolo = protocolo,
    motivo = motivo,
    status = status,
    origem = origem,
    abertoEm = Instant.parse(abertoEm),
    nomeCliente = nomeCliente,
    local = local,
    idTecnicoResponsavel = null,
    sincronizacao = SituacaoDeSincronizacao.SINCRONIZADO,
)
