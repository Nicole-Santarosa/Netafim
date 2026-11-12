package com.netafim.posvenda.atendimento.ui.detalhe

import androidx.annotation.StringRes
import com.netafim.posvenda.atendimento.R
import com.netafim.posvenda.atendimento.dominio.SituacaoEtapa
import com.netafim.posvenda.atendimento.dominio.TipoEtapa

// Ligam os tipos e situações da linha do tempo aos textos do strings.xml.
@StringRes
fun TipoEtapa.titulo(): Int = when (this) {
    TipoEtapa.ABERTO -> R.string.atendimento_etapa_aberto_titulo
    TipoEtapa.EM_ATENDIMENTO -> R.string.atendimento_etapa_em_atendimento_titulo
    TipoEtapa.CONCLUIDO -> R.string.atendimento_etapa_concluido_titulo
    TipoEtapa.CANCELADO -> R.string.atendimento_etapa_cancelado_titulo
}

@StringRes
fun TipoEtapa.descricao(): Int = when (this) {
    TipoEtapa.ABERTO -> R.string.atendimento_etapa_aberto_descricao
    TipoEtapa.EM_ATENDIMENTO -> R.string.atendimento_etapa_em_atendimento_descricao
    TipoEtapa.CONCLUIDO -> R.string.atendimento_etapa_concluido_descricao
    TipoEtapa.CANCELADO -> R.string.atendimento_etapa_cancelado_descricao
}

// Texto do estado da etapa, lido pelo leitor de tela junto com o título e a descrição.
@StringRes
fun SituacaoEtapa.descricaoDeEstado(): Int = when (this) {
    SituacaoEtapa.FEITA -> R.string.atendimento_etapa_estado_feita
    SituacaoEtapa.ATUAL -> R.string.atendimento_etapa_estado_atual
    SituacaoEtapa.PENDENTE -> R.string.atendimento_etapa_estado_pendente
}
