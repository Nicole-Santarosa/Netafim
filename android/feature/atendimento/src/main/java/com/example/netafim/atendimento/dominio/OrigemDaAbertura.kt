package com.example.netafim.atendimento.dominio

import com.example.netafim.core.modelo.OrigemAtendimento
import com.example.netafim.core.modelo.sessao.TipoDeOrganizacao
import com.example.netafim.core.modelo.sessao.UsuarioDaSessao

// Os dois dados da abertura que dependem de quem está logado e não do formulário: de que canal o atendimento
// nasce (origem) e, quando é de um cliente final, de qual empresa ele é (idCliente).
data class OrigemDaAbertura(
    val origem: OrigemAtendimento,
    val idCliente: Int?,
)

// Decide a origem da abertura pelo tipo de organização do usuário da sessão:
// - Netafim abre como origem NETAFIM, sem cliente;
// - distribuidor abre como origem DISTRIBUIDOR, sem cliente;
// - cliente abre como CLIENTE_FINAL e o idCliente é a própria organização dele (idDaOrganizacao).
// Devolve nulo para um usuário de cliente sem organização informada: a API exige o id do cliente na origem
// cliente final e recusaria a abertura com 403, então o app nem tenta.
// O distribuidor de origem não sai daqui: quem escolhe é o formulário (id do distribuidor da lista).
fun UsuarioDaSessao.comoOrigemDaAbertura(): OrigemDaAbertura? = when (tipoDeOrganizacao) {
    TipoDeOrganizacao.NETAFIM -> OrigemDaAbertura(OrigemAtendimento.NETAFIM, idCliente = null)
    TipoDeOrganizacao.DISTRIBUIDOR -> OrigemDaAbertura(OrigemAtendimento.DISTRIBUIDOR, idCliente = null)
    TipoDeOrganizacao.CLIENTE ->
        idDaOrganizacao?.let { OrigemDaAbertura(OrigemAtendimento.CLIENTE_FINAL, idCliente = it) }
}
