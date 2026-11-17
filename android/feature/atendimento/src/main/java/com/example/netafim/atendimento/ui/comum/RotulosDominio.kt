package com.example.netafim.atendimento.ui.comum

import androidx.annotation.StringRes
import com.example.netafim.atendimento.R
import com.example.netafim.atendimento.dominio.ErroAoSalvar
import com.example.netafim.atendimento.dominio.SeloStatus
import com.example.netafim.core.modelo.AreaDoSetor
import com.example.netafim.core.modelo.MotivoAtendimento
import com.example.netafim.core.modelo.SituacaoDeSincronizacao
import com.example.netafim.core.modelo.TipoVenda

// Liga cada valor do domínio ao texto do strings.xml. O domínio só carrega códigos e regras,
// então o rótulo que o usuário lê é resolvido aqui, na camada de interface.
@StringRes
fun SeloStatus.rotulo(): Int = when (this) {
    SeloStatus.ABERTO -> R.string.atendimento_selo_aberto
    SeloStatus.EM_ATENDIMENTO -> R.string.atendimento_selo_em_atendimento
    SeloStatus.CONCLUIDO -> R.string.atendimento_selo_concluido
    SeloStatus.CANCELADO -> R.string.atendimento_selo_cancelado
    SeloStatus.DESCONHECIDO -> R.string.atendimento_desconhecido
}

@StringRes
fun MotivoAtendimento.rotulo(): Int = when (this) {
    MotivoAtendimento.DIGITAL_FARMING -> R.string.atendimento_motivo_digital_farming
    MotivoAtendimento.DUVIDAS -> R.string.atendimento_motivo_duvidas
    MotivoAtendimento.ENTREGA_TECNICA -> R.string.atendimento_motivo_entrega_tecnica
    MotivoAtendimento.POS_VENDA -> R.string.atendimento_motivo_pos_venda
    MotivoAtendimento.PROGRAMACAO_FEIRAS -> R.string.atendimento_motivo_programacao_feiras
    MotivoAtendimento.TREINAMENTO -> R.string.atendimento_motivo_treinamento
    MotivoAtendimento.DESCONHECIDO -> R.string.atendimento_desconhecido
}

// Texto de cada tipo de venda. Reaproveita as strings que os chips da abertura já usavam.
@StringRes
fun TipoVenda.rotulo(): Int = when (this) {
    TipoVenda.PROJETO -> R.string.atendimento_abrir_tipo_venda_projeto
    TipoVenda.VENDA_REGULAR -> R.string.atendimento_abrir_tipo_venda_regular
    TipoVenda.DESCONHECIDO -> R.string.atendimento_desconhecido
}

// Texto de cada área ou setor da Netafim, para o seletor da abertura.
@StringRes
fun AreaDoSetor.rotulo(): Int = when (this) {
    AreaDoSetor.COMERCIAL -> R.string.atendimento_area_comercial
    AreaDoSetor.ENGENHARIA -> R.string.atendimento_area_engenharia
    AreaDoSetor.INSTALACAO -> R.string.atendimento_area_instalacao
    AreaDoSetor.MARKETING -> R.string.atendimento_area_marketing
    AreaDoSetor.POS_VENDA -> R.string.atendimento_area_pos_venda
}

// Texto do aviso de cada erro de escrita, nas três telas de ação. O when não tem else de propósito: se
// ErroAoSalvar ganhar um valor novo, o compilador obriga a escolher o texto dele aqui. Na regra de negócio
// o id é o do texto genérico; a tela mostra no lugar dele a mensagem da API quando ela vier preenchida.
@StringRes
fun ErroAoSalvar.rotulo(): Int = when (this) {
    ErroAoSalvar.ConflitoDeVersao -> R.string.atendimento_erro_conflito
    ErroAoSalvar.SemPermissao -> R.string.atendimento_erro_sem_permissao
    ErroAoSalvar.NaoEncontrado -> R.string.atendimento_erro_nao_encontrado
    ErroAoSalvar.SemConexao -> R.string.atendimento_erro_sem_conexao
    is ErroAoSalvar.RegraDeNegocio -> R.string.atendimento_erro_regra
}

// Texto do aviso de sincronização das telas de detalhe, ou nulo quando o registro está sincronizado (o caso
// normal, que não mostra aviso). Sem else no when: uma situação nova no core obriga a decidir o texto dela.
@StringRes
fun SituacaoDeSincronizacao.textoDoAviso(): Int? = when (this) {
    SituacaoDeSincronizacao.SINCRONIZADO -> null
    SituacaoDeSincronizacao.PENDENTE_DE_ENVIO -> R.string.atendimento_sincronizacao_aviso_pendente
    SituacaoDeSincronizacao.CONFLITO -> R.string.atendimento_sincronizacao_aviso_conflito
    SituacaoDeSincronizacao.ERRO -> R.string.atendimento_sincronizacao_aviso_erro
}
