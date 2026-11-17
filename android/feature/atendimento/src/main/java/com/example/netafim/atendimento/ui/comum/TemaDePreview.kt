package com.example.netafim.atendimento.ui.comum

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.netafim.core.modelo.Atendimento
import com.example.netafim.core.modelo.Cultura
import com.example.netafim.core.modelo.EventoDoHistorico
import com.example.netafim.core.modelo.FiltroDeAtendimentos
import com.example.netafim.core.modelo.ItemDeAtendimento
import com.example.netafim.core.modelo.MotivoAtendimento
import com.example.netafim.core.modelo.OrigemAtendimento
import com.example.netafim.core.modelo.SituacaoDeSincronizacao
import com.example.netafim.core.modelo.SituacaoDoProjeto
import com.example.netafim.core.modelo.StatusAtendimento
import com.example.netafim.core.modelo.buscarNosItens
import com.example.netafim.core.modelo.ordenar
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

// Faz nos previews o que o repositório faz de verdade: aplica status, origem, busca e ordem do pedido sobre
// os itens fictícios, para o preview mostrar a lista já pronta como a tela a receberá. Só olha os campos que
// as telas de lista usam (somenteMeusComoTecnico e origemDoRegistro dependem do usuário e do banco local).
internal fun listaDePreview(itens: List<ItemDeAtendimento>, filtro: FiltroDeAtendimentos): List<ItemDeAtendimento> {
    val filtrados = itens.filter {
        (filtro.status.isEmpty() || it.status in filtro.status) &&
            (filtro.origem.isEmpty() || it.origem in filtro.origem)
    }
    return ordenar(buscarNosItens(filtrados, filtro.busca), filtro.ordem)
}

// Monta um atendimento completo para os previews dos detalhes, em cima do itemDePreview. O local do item é
// o nome da fazenda e a cidade juntos, como o repositório entrega. O que o detalhe não desenha fica vazio
// (tipo de venda, conflito e erro de envio), e a situação do projeto só entra quando o preview a informa.
internal fun atendimentoDePreview(
    status: StatusAtendimento,
    protocolo: String? = "OS-0498",
    motivo: MotivoAtendimento = MotivoAtendimento.DUVIDAS,
    produtor: String = "Vanderlei Zucchi Rodas",
    nomeFazenda: String? = null,
    cidade: String? = null,
    culturas: List<Cultura> = emptyList(),
    historico: List<EventoDoHistorico> = emptyList(),
    situacaoProjeto: SituacaoDoProjeto? = null,
) = Atendimento(
    item = itemDePreview(
        id = "1",
        protocolo = protocolo,
        motivo = motivo,
        status = status,
        abertoEm = "2026-10-20T15:00:00Z",
        nomeCliente = produtor,
        local = listOfNotNull(nomeFazenda, cidade).joinToString(" · ").ifEmpty { null },
    ),
    produtor = produtor,
    nomeFazenda = nomeFazenda,
    cidade = cidade,
    culturas = culturas,
    situacaoProjeto = situacaoProjeto,
    tipoVenda = null,
    historico = historico,
    conflito = null,
    erroDeEnvio = null,
)
