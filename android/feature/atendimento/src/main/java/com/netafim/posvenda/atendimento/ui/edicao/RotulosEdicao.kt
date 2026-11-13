package com.netafim.posvenda.atendimento.ui.edicao

import androidx.annotation.StringRes
import com.netafim.posvenda.atendimento.R
import com.netafim.posvenda.atendimento.dominio.SituacaoDoProjeto

// Texto de cada opção do seletor da situação do projeto.
@StringRes
fun SituacaoDoProjeto.rotulo(): Int = when (this) {
    SituacaoDoProjeto.GARANTIA -> R.string.atendimento_edicao_situacao_garantia
    SituacaoDoProjeto.FORA_GARANTIA -> R.string.atendimento_edicao_situacao_fora_garantia
    SituacaoDoProjeto.INSTALACAO -> R.string.atendimento_edicao_situacao_instalacao
}
