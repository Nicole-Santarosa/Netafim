package com.netafim.posvenda.atendimento.ui.comum

import androidx.annotation.StringRes
import com.netafim.posvenda.atendimento.R
import com.netafim.posvenda.atendimento.dominio.MotivoAtendimento
import com.netafim.posvenda.atendimento.dominio.SeloStatus

// Liga cada valor do domínio ao texto do strings.xml. O domínio só carrega códigos e regras,
// então o rótulo que o usuário lê é resolvido aqui, na camada de interface.
@StringRes
fun SeloStatus.rotulo(): Int = when (this) {
    SeloStatus.ABERTO -> R.string.atendimento_selo_aberto
    SeloStatus.EM_ATENDIMENTO -> R.string.atendimento_selo_em_atendimento
    SeloStatus.CONCLUIDO -> R.string.atendimento_selo_concluido
    SeloStatus.CANCELADO -> R.string.atendimento_selo_cancelado
}

@StringRes
fun MotivoAtendimento.rotulo(): Int = when (this) {
    MotivoAtendimento.DIGITAL_FARMING -> R.string.atendimento_motivo_digital_farming
    MotivoAtendimento.DUVIDAS -> R.string.atendimento_motivo_duvidas
    MotivoAtendimento.ENTREGA_TECNICA -> R.string.atendimento_motivo_entrega_tecnica
    MotivoAtendimento.POS_VENDA -> R.string.atendimento_motivo_pos_venda
    MotivoAtendimento.PROGRAMACAO_FEIRAS -> R.string.atendimento_motivo_programacao_feiras
    MotivoAtendimento.TREINAMENTO -> R.string.atendimento_motivo_treinamento
}
