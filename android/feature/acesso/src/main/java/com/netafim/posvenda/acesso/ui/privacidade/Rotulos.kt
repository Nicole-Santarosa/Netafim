package com.netafim.posvenda.acesso.ui.privacidade

import androidx.annotation.StringRes
import com.netafim.posvenda.acesso.R
import com.netafim.posvenda.acesso.dominio.StatusPedido
import com.netafim.posvenda.acesso.dominio.TipoPedido
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Texto do tipo de pedido em linguagem simples. */
@StringRes
internal fun textoDoTipo(tipo: TipoPedido): Int = when (tipo) {
    TipoPedido.CONFIRMACAO -> R.string.privacidade_tipo_confirmacao
    TipoPedido.ACESSO -> R.string.privacidade_tipo_acesso
    TipoPedido.CORRECAO -> R.string.privacidade_tipo_correcao
    TipoPedido.ANONIMIZACAO -> R.string.privacidade_tipo_anonimizacao
    TipoPedido.ELIMINACAO -> R.string.privacidade_tipo_eliminacao
    TipoPedido.PORTABILIDADE -> R.string.privacidade_tipo_portabilidade
    TipoPedido.COMPARTILHAMENTO -> R.string.privacidade_tipo_compartilhamento
    TipoPedido.REVOGACAO_CONSENTIMENTO -> R.string.privacidade_tipo_revogacao_consentimento
}

/** Nome neutro do tipo, para quem atende o pedido (o texto do titular está em primeira pessoa). */
@StringRes
internal fun textoDoTipoParaAdmin(tipo: TipoPedido): Int = when (tipo) {
    TipoPedido.CONFIRMACAO -> R.string.fila_tipo_confirmacao
    TipoPedido.ACESSO -> R.string.fila_tipo_acesso
    TipoPedido.CORRECAO -> R.string.fila_tipo_correcao
    TipoPedido.ANONIMIZACAO -> R.string.fila_tipo_anonimizacao
    TipoPedido.ELIMINACAO -> R.string.fila_tipo_eliminacao
    TipoPedido.PORTABILIDADE -> R.string.fila_tipo_portabilidade
    TipoPedido.COMPARTILHAMENTO -> R.string.fila_tipo_compartilhamento
    TipoPedido.REVOGACAO_CONSENTIMENTO -> R.string.fila_tipo_revogacao_consentimento
}

/** Texto do andamento do pedido; um status que o app não conhece volta como nulo (a tela mostra o código). */
@StringRes
internal fun textoDoStatus(status: String): Int? = when (status) {
    StatusPedido.RECEBIDA -> R.string.privacidade_status_recebida
    StatusPedido.EM_ANALISE -> R.string.privacidade_status_em_analise
    StatusPedido.ATENDIDA -> R.string.privacidade_status_atendida
    StatusPedido.NEGADA -> R.string.privacidade_status_negada
    else -> null
}

private val FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** Converte 2026-11-23T22:00:00Z em 23/11/2026 no fuso do aparelho; se não der para ler, devolve o texto como veio. */
internal fun formatarDataDoPedido(iso: String, zona: ZoneId = ZoneId.systemDefault()): String =
    runCatching { FORMATO_DATA.format(OffsetDateTime.parse(iso).atZoneSameInstant(zona)) }.getOrDefault(iso)
