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

    /** Um usuário do escopo do administrador (`GET usuarios/{id}`). */
    suspend fun obter(idUsuario: Int): Resultado<Usuario>

    /** Ativa ou desativa (`ativo` ou `inativo`); bloquear e desbloquear é só do Netafim Admin. */
    suspend fun alterarSituacao(idUsuario: Int, situacao: String): Resultado<Usuario>

    /** Muda o perfil de acesso; vale já na próxima requisição da pessoa. */
    suspend fun definirPerfil(idUsuario: Int, idPerfil: Int): Resultado<Usuario>

    /** Reenvia o link para definir a senha (convite, ou redefinição se já foi aprovado). */
    suspend fun reenviarConvite(idUsuario: Int): Resultado<Unit>

    /** Exclusão lógica: some das listas e libera o e-mail; os registros ligados ficam. */
    suspend fun excluir(idUsuario: Int): Resultado<Unit>

    /** Perfis de acesso existentes (todos os tipos de organização). */
    suspend fun perfis(): Resultado<List<Perfil>>
}

/** Valores de `situacao` do usuário, como na API. */
object SituacoesUsuario {
    const val ATIVO = "ativo"
    const val INATIVO = "inativo"
    const val BLOQUEADO = "bloqueado"
    const val PENDENTE = "pendente"
    const val RECUSADO = "recusado"
}

/** O que o administrador logado pode fazer com um usuário na tela de detalhe. */
data class AcoesDoUsuario(
    val ativar: Boolean = false,
    val desativar: Boolean = false,
    val reenviarConvite: Boolean = false,
    val definirPerfil: Boolean = false,
    val excluir: Boolean = false,
) {
    val alguma: Boolean get() = ativar || desativar || reenviarConvite || definirPerfil || excluir
}

/**
 * Regras que a tela usa para mostrar só os botões que a API aceitaria (a API continua sendo quem decide):
 * ninguém gerencia a si mesmo; o Netafim Admin gerencia todos; admin de distribuidor ou de cliente só
 * gerencia usuários de nível Usuário da própria organização; bloquear e desbloquear são do Netafim Admin.
 */
fun acoesPermitidas(ator: Usuario, alvo: Usuario): AcoesDoUsuario {
    if (ator.id == alvo.id) return AcoesDoUsuario()
    val atorNetafimAdmin = ator.perfil?.let { it.tipoOrganizacao == TiposOrganizacao.NETAFIM && it.ehAdmin } == true
    val mesmaOrganizacao = ator.organizacao != null && ator.organizacao.tipo == alvo.organizacao?.tipo &&
        ator.organizacao.id == alvo.organizacao.id
    val gerencia = atorNetafimAdmin ||
        (ator.perfil?.ehAdmin == true && mesmaOrganizacao && alvo.perfil?.ehAdmin != true)
    val editar = ator.temPermissao(Permissoes.EDITAR_USUARIO) && gerencia
    val criar = ator.temPermissao(Permissoes.CRIAR_USUARIO) && gerencia
    val excluir = ator.temPermissao(Permissoes.EXCLUIR_USUARIO) && gerencia
    return AcoesDoUsuario(
        desativar = editar && alvo.situacao == SituacoesUsuario.ATIVO,
        ativar = editar && (
            alvo.situacao == SituacoesUsuario.INATIVO ||
                (alvo.situacao == SituacoesUsuario.BLOQUEADO && atorNetafimAdmin)
            ),
        reenviarConvite = criar && alvo.situacao == SituacoesUsuario.PENDENTE,
        definirPerfil = ator.temPermissao(Permissoes.DEFINIR_PERFIL) && alvo.organizacao != null,
        excluir = excluir,
    )
}
