package com.example.netafim.acesso.dominio

/** Dados do convite de um novo usuário. A organização e o perfil vêm do administrador que cria. */
data class DadosNovoUsuario(
    val nome: String,
    val email: String,
    val telefone: String?,
)

/** Aparelho em que a pessoa entrou. [revogado]: o acesso dele foi cortado. */
data class Aparelho(
    val id: String,
    val modelo: String?,
    val versaoApp: String?,
    val ultimaSincronizacao: String?,
    val revogado: Boolean,
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

    /** Corrige nome e telefone (`PATCH usuarios/{id}`); o e-mail não muda. Telefone vazio limpa o campo. */
    suspend fun editar(idUsuario: Int, nome: String, telefone: String): Resultado<Usuario>

    /** Ativa ou desativa (`ativo` ou `inativo`); bloquear e desbloquear é só do Netafim Admin. */
    suspend fun alterarSituacao(idUsuario: Int, situacao: String): Resultado<Usuario>

    /** Muda o perfil de acesso; vale já na próxima requisição da pessoa. */
    suspend fun definirPerfil(idUsuario: Int, idPerfil: Int): Resultado<Usuario>

    /** Reenvia o link para definir a senha (convite, ou redefinição se já foi aprovado). */
    suspend fun reenviarConvite(idUsuario: Int): Resultado<Unit>

    /** Exclusão lógica: some das listas e libera o e-mail; os registros ligados ficam. */
    suspend fun excluir(idUsuario: Int): Resultado<Unit>

    /** Netafim Admin remove o MFA de quem perdeu o aparelho e os códigos; as sessões da pessoa terminam. */
    suspend fun redefinirMfa(idUsuario: Int): Resultado<Unit>

    /** Aparelhos da pessoa (`GET usuarios/{id}/dispositivos`). */
    suspend fun aparelhos(idUsuario: Int): Resultado<List<Aparelho>>

    /** Corta o acesso de um aparelho (perdido ou roubado); só o Netafim Admin. Devolve o aparelho já revogado. */
    suspend fun revogarAparelho(idAparelho: String): Resultado<Aparelho>

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
    val editar: Boolean = false,
    val ativar: Boolean = false,
    val desativar: Boolean = false,
    val reenviarConvite: Boolean = false,
    val definirPerfil: Boolean = false,
    val excluir: Boolean = false,
    /** Só o Netafim Admin (a API recusa os outros). */
    val redefinirMfa: Boolean = false,
    /** Quem gerencia a pessoa vê os aparelhos dela. */
    val verAparelhos: Boolean = false,
    /** Revogar um aparelho é só do Netafim Admin. */
    val revogarAparelhos: Boolean = false,
) {
    val alguma: Boolean
        get() = editar || ativar || desativar || reenviarConvite || definirPerfil || excluir || redefinirMfa || verAparelhos
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
    // As rotas de MFA e de aparelhos ficam em UsuarioViewSet, que exige uma destas permissões (basta uma).
    val gerenciaUsuarios = ator.temPermissao(Permissoes.EDITAR_USUARIO) ||
        ator.temPermissao(Permissoes.CRIAR_USUARIO) ||
        ator.temPermissao(Permissoes.APROVAR_CADASTRO)
    return AcoesDoUsuario(
        editar = editar,
        desativar = editar && alvo.situacao == SituacoesUsuario.ATIVO,
        ativar = editar && (
            alvo.situacao == SituacoesUsuario.INATIVO ||
                (alvo.situacao == SituacoesUsuario.BLOQUEADO && atorNetafimAdmin)
            ),
        reenviarConvite = criar && alvo.situacao == SituacoesUsuario.PENDENTE,
        definirPerfil = ator.temPermissao(Permissoes.DEFINIR_PERFIL) && alvo.organizacao != null,
        excluir = excluir,
        redefinirMfa = atorNetafimAdmin && gerenciaUsuarios,
        verAparelhos = gerencia && gerenciaUsuarios,
        revogarAparelhos = atorNetafimAdmin && gerenciaUsuarios,
    )
}
