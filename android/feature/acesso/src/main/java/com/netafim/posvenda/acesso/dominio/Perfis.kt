package com.netafim.posvenda.acesso.dominio

/** Uma permissão do sistema, como na tabela `permissao` do banco. */
data class PermissaoDoSistema(val codigo: String, val descricao: String)

/** Um perfil de acesso com o conjunto de permissões que ele concede hoje. */
data class PerfilComPermissoes(
    val perfil: Perfil,
    val descricao: String?,
    val permissoes: Set<String>,
)

/** Permissões por perfil (RF20): só o Netafim Admin lê e altera (`GET perfis`, `GET permissoes`, `PUT perfis/{id}/permissoes`). */
interface RepositorioPerfis {
    suspend fun perfis(): Resultado<List<PerfilComPermissoes>>

    suspend fun permissoes(): Resultado<List<PermissaoDoSistema>>

    /** Substitui as permissões do perfil pelo conjunto informado; vale já na próxima requisição de cada pessoa. */
    suspend fun salvarPermissoes(idPerfil: Int, codigos: Set<String>): Resultado<PerfilComPermissoes>
}

/** Quem pode gerenciar permissões: o Netafim Admin (RF20). */
fun Usuario.podeGerenciarPermissoes(): Boolean =
    perfil?.let { it.tipoOrganizacao == TiposOrganizacao.NETAFIM && it.ehAdmin } == true

/**
 * Permissões que o perfil não pode perder: o Netafim Admin sem aprovar cadastros e definir perfis ficaria sem
 * como destravar o sistema (a API também recusa com `permissao_essencial`).
 */
fun permissoesEssenciais(perfil: Perfil): Set<String> =
    if (perfil.tipoOrganizacao == TiposOrganizacao.NETAFIM && perfil.ehAdmin) {
        setOf(Permissoes.APROVAR_CADASTRO, Permissoes.DEFINIR_PERFIL)
    } else {
        emptySet()
    }
