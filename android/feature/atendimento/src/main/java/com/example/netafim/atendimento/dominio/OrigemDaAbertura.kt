package com.example.netafim.atendimento.dominio

import com.example.netafim.core.modelo.OrigemAtendimento
import com.example.netafim.core.modelo.sessao.TipoDeOrganizacao
import com.example.netafim.core.modelo.sessao.UsuarioDaSessao

// Os dados da abertura que dependem de quem está logado e não do formulário: de que canal o atendimento nasce
// (origem), de qual empresa ele é quando vem de um cliente final (idCliente) e de qual distribuidor ele sai
// quando é aberto por um distribuidor (idDistribuidorOrigem).
data class OrigemDaAbertura(
    val origem: OrigemAtendimento,
    val idCliente: Int?,
    val idDistribuidorOrigem: Int?,
)

// Decide a origem da abertura pelo tipo de organização do usuário da sessão:
// - Netafim abre como origem NETAFIM, sem cliente nem distribuidor;
// - distribuidor abre como origem DISTRIBUIDOR e o distribuidor de origem é a própria organização dele
//   (idDaOrganizacao), por isso o formulário do distribuidor não pede essa escolha;
// - cliente abre como CLIENTE_FINAL e o idCliente é a própria organização dele. O distribuidor de origem do
//   cliente não sai daqui: ele escolhe no formulário, na lista de distribuidores da região.
// Devolve nulo quando o usuário de cliente ou de distribuidor vem sem organização: sem ela não há como montar
// a abertura (a API recusaria o cliente sem empresa com 403), então o app nem tenta.
fun UsuarioDaSessao.comoOrigemDaAbertura(): OrigemDaAbertura? = when (tipoDeOrganizacao) {
    TipoDeOrganizacao.NETAFIM ->
        OrigemDaAbertura(OrigemAtendimento.NETAFIM, idCliente = null, idDistribuidorOrigem = null)
    TipoDeOrganizacao.DISTRIBUIDOR -> idDaOrganizacao?.let {
        OrigemDaAbertura(OrigemAtendimento.DISTRIBUIDOR, idCliente = null, idDistribuidorOrigem = it)
    }
    TipoDeOrganizacao.CLIENTE -> idDaOrganizacao?.let {
        OrigemDaAbertura(OrigemAtendimento.CLIENTE_FINAL, idCliente = it, idDistribuidorOrigem = null)
    }
}
