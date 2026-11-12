package com.netafim.posvenda.acesso.dominio

/** Dados do convite de um novo usuário. A organização e o perfil vêm do administrador que cria. */
data class DadosNovoUsuario(
    val nome: String,
    val email: String,
    val telefone: String?,
)

/** Gestão de usuários da própria organização (Gestão de usuários do protótipo). */
interface RepositorioUsuarios {
    /** Usuários que o administrador logado pode ver (todas as páginas). */
    suspend fun listar(): Resultado<List<Usuario>>

    /**
     * Cria o usuário e envia o convite por e-mail. Admin de distribuidor ou de cliente cria
     * usuários de nível Usuário da própria organização, que ficam pendentes até a Netafim aprovar.
     */
    suspend fun criar(dados: DadosNovoUsuario): Resultado<Usuario>
}
