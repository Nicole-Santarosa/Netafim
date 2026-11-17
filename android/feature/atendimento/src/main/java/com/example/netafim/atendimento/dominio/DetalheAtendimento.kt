package com.example.netafim.atendimento.dominio

import com.example.netafim.core.modelo.Atendimento
import com.example.netafim.core.modelo.EventoDoHistorico
import com.example.netafim.core.modelo.StatusAtendimento

// Tipo de cada etapa da linha do tempo. O texto de cada uma fica no strings.xml.
enum class TipoEtapa {
    ABERTO,
    EM_ATENDIMENTO,
    CONCLUIDO,
    CANCELADO,
}

// Onde o atendimento está em relação à etapa: já passou (FEITA), é a de agora (ATUAL) ou ainda não chegou.
enum class SituacaoEtapa {
    FEITA,
    ATUAL,
    PENDENTE,
}

data class EtapaDaLinha(val tipo: TipoEtapa, val situacao: SituacaoEtapa)

// Monta a linha do tempo a partir do selo do status (os oito status reais caem em quatro selos).
// Status desconhecido: as três etapas ficam pendentes, sem nenhuma atual nem feita, porque o app não sabe
// em que ponto o atendimento está e não deve afirmar progresso.
// O cancelamento não existe no protótipo do cliente: a regra do cancelado abaixo é uma decisão do grupo
// ainda para ser conferida. Quem foi cancelado durante o atendimento (statusAntesDoCancelamento igual a
// em atendimento) mantém essa etapa como feita; a partir de aberto, destinado ou agendado, ou sem saber o
// status anterior (nulo), a linha tem só Aberto feito e Cancelado atual. O parâmetro vem de
// statusAntesDoCancelamento(historico) e é ignorado quando o status não é cancelado.
fun linhaDoTempo(
    status: StatusAtendimento,
    statusAntesDoCancelamento: StatusAtendimento? = null,
): List<EtapaDaLinha> = when (status.selo) {
    SeloStatus.ABERTO -> listOf(
        EtapaDaLinha(TipoEtapa.ABERTO, SituacaoEtapa.ATUAL),
        EtapaDaLinha(TipoEtapa.EM_ATENDIMENTO, SituacaoEtapa.PENDENTE),
        EtapaDaLinha(TipoEtapa.CONCLUIDO, SituacaoEtapa.PENDENTE),
    )
    SeloStatus.EM_ATENDIMENTO -> listOf(
        EtapaDaLinha(TipoEtapa.ABERTO, SituacaoEtapa.FEITA),
        EtapaDaLinha(TipoEtapa.EM_ATENDIMENTO, SituacaoEtapa.ATUAL),
        EtapaDaLinha(TipoEtapa.CONCLUIDO, SituacaoEtapa.PENDENTE),
    )
    SeloStatus.CONCLUIDO -> listOf(
        EtapaDaLinha(TipoEtapa.ABERTO, SituacaoEtapa.FEITA),
        EtapaDaLinha(TipoEtapa.EM_ATENDIMENTO, SituacaoEtapa.FEITA),
        EtapaDaLinha(TipoEtapa.CONCLUIDO, SituacaoEtapa.FEITA),
    )
    SeloStatus.DESCONHECIDO -> listOf(
        EtapaDaLinha(TipoEtapa.ABERTO, SituacaoEtapa.PENDENTE),
        EtapaDaLinha(TipoEtapa.EM_ATENDIMENTO, SituacaoEtapa.PENDENTE),
        EtapaDaLinha(TipoEtapa.CONCLUIDO, SituacaoEtapa.PENDENTE),
    )
    SeloStatus.CANCELADO -> if (statusAntesDoCancelamento == StatusAtendimento.EM_ATENDIMENTO) {
        listOf(
            EtapaDaLinha(TipoEtapa.ABERTO, SituacaoEtapa.FEITA),
            EtapaDaLinha(TipoEtapa.EM_ATENDIMENTO, SituacaoEtapa.FEITA),
            EtapaDaLinha(TipoEtapa.CANCELADO, SituacaoEtapa.ATUAL),
        )
    } else {
        listOf(
            EtapaDaLinha(TipoEtapa.ABERTO, SituacaoEtapa.FEITA),
            EtapaDaLinha(TipoEtapa.CANCELADO, SituacaoEtapa.ATUAL),
        )
    }
}

// Tipo do evento que o banco grava quando o status muda (fn_atendimento_depois grava 'status_alterado').
private const val TIPO_EVENTO_STATUS_ALTERADO = "status_alterado"

// Status em que o atendimento estava antes de ser cancelado, tirado do histórico. No banco, o evento
// status_alterado grava em valor_anterior e valor_novo os próprios códigos de status ("em_atendimento",
// "cancelado"), os mesmos que a API e o StatusAtendimento usam. Vale o último evento de status cujo novo
// valor é cancelado; a lista é lida na ordem em que o repositório a entrega (data do evento e depois id),
// sem reordenar. Devolve nulo quando não há esse evento, quando o valor anterior falta ou quando o código
// anterior é de um status que o app não conhece (DESCONHECIDO): nesses casos a linha do tempo faz como
// sempre fez com o cancelado sem informação, mostrando só Aberto e Cancelado.
fun statusAntesDoCancelamento(historico: List<EventoDoHistorico>): StatusAtendimento? {
    val cancelamento = historico.lastOrNull {
        it.tipo == TIPO_EVENTO_STATUS_ALTERADO && it.valorNovo == StatusAtendimento.CANCELADO.codigo
    } ?: return null
    return cancelamento.valorAnterior
        ?.let { StatusAtendimento.deCodigo(it) }
        ?.takeIf { it != StatusAtendimento.DESCONHECIDO }
}

// Junta nome da fazenda e cidade com " · ", ignorando o que for nulo ou só espaços.
// Devolve null quando não sobra nada, para a tela esconder a linha de local.
// O " · " é só pontuação de separação, não texto de interface.
fun linhaDeLocal(atendimento: Atendimento): String? =
    listOfNotNull(atendimento.nomeFazenda, atendimento.cidade)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" · ")
