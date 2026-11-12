package com.netafim.posvenda.atendimento.dominio

import java.time.Instant

// Dados de um atendimento que a tela de detalhe do cliente mostra.
// Nome da fazenda e cidade são opcionais: o atendimento pode ter sido aberto sem eles.
data class DetalheAtendimento(
    val id: String,
    val protocolo: String,
    val motivo: MotivoAtendimento,
    val status: StatusAtendimento,
    val abertoEm: Instant,
    val nomeFazenda: String?,
    val cidade: String?,
)

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

// Monta a linha do tempo a partir do selo do status (os oito status caem em quatro selos).
// O cancelamento não existe no protótipo do cliente: a regra abaixo (só duas etapas, aberto feito e
// cancelado como etapa atual) é uma decisão do grupo ainda para ser conferida.
fun linhaDoTempo(status: StatusAtendimento): List<EtapaDaLinha> = when (status.selo) {
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
    SeloStatus.CANCELADO -> listOf(
        EtapaDaLinha(TipoEtapa.ABERTO, SituacaoEtapa.FEITA),
        EtapaDaLinha(TipoEtapa.CANCELADO, SituacaoEtapa.ATUAL),
    )
}

// Junta nome da fazenda e cidade com " · ", ignorando o que for nulo ou só espaços.
// Devolve null quando não sobra nada, para a tela esconder a linha de local.
// O " · " é só pontuação de separação, não texto de interface.
fun linhaDeLocal(detalhe: DetalheAtendimento): String? =
    listOfNotNull(detalhe.nomeFazenda, detalhe.cidade)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" · ")
