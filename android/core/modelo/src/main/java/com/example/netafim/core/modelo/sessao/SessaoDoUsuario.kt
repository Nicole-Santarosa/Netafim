package com.example.netafim.core.modelo.sessao

import kotlinx.coroutines.flow.StateFlow

// Tipo da organização a que o usuário pertence. Define de que lado ele está: a própria Netafim, um
// distribuidor ou um cliente final.
enum class TipoDeOrganizacao {
    NETAFIM,
    DISTRIBUIDOR,
    CLIENTE,
}

// Quem está logado, no formato que as telas de atendimento precisam. Não é o modelo do módulo de acesso: é
// só o recorte útil aqui, para este módulo não depender de nada do acesso.
// - idDaOrganizacao é o id do distribuidor ou do cliente. É nulo para a Netafim, que não tem organização
//   dona (ela é a própria empresa).
// - ehAdmin vem do nível do perfil (por exemplo Netafim Admin, Distribuidor Admin, Cliente Admin), e não de
//   uma permissão avulsa.
// - permissoes guarda os códigos de permissão do perfil, como vêm do servidor (por exemplo "abrir_atendimento").
// O adaptador que monta este objeto a partir da sessão do módulo de acesso será escrito depois, fora desta peça.
data class UsuarioDaSessao(
    val id: Int,
    val nome: String,
    val tipoDeOrganizacao: TipoDeOrganizacao,
    val idDaOrganizacao: Int?,
    val ehAdmin: Boolean,
    val permissoes: Set<String>,
) {
    // Diz se o perfil tem a permissão pelo código exato. Códigos que o app não conhece simplesmente não casam.
    fun temPermissao(codigo: String): Boolean = codigo in permissoes

    // Conta de distribuidor ou de cliente que o Netafim Admin ainda não vinculou a uma empresa. A Netafim fica de
    // fora porque para ela o idDaOrganizacao nulo é o normal. Sem empresa a conta não enxerga atendimento nenhum
    // e não pode abrir um (a API recusaria), por isso as listas e a abertura olham esta regra, que fica só aqui.
    val semOrganizacao: Boolean
        get() = tipoDeOrganizacao != TipoDeOrganizacao.NETAFIM && idDaOrganizacao == null
}

// Fonte do usuário logado. O StateFlow guarda sempre o valor atual: nulo quando ninguém está logado, e um
// novo valor quando a pessoa entra, sai ou troca de conta. Quem consome reage a essas mudanças.
interface SessaoDoUsuario {
    val usuario: StateFlow<UsuarioDaSessao?>
}
