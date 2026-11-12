package com.netafim.posvenda.acesso.dominio

/** Valores de `tipo_organizacao`, como na API e no banco (ver `apps/contas/constantes.py`). */
object TiposOrganizacao {
    const val NETAFIM = "netafim"
    const val DISTRIBUIDOR = "distribuidor"
    const val CLIENTE = "cliente"
}

/** Um item de lista de escolha (distribuidor ou cliente). */
data class Opcao(val id: Int, val nome: String)

/** O que o Netafim Admin pode escolher ao aprovar um cadastro. */
data class OpcoesAprovacao(
    val perfis: List<Perfil>,
    val distribuidores: List<Opcao>,
    val clientes: List<Opcao>,
)

/**
 * Escolhas feitas na aprovação. A API exige a organização (conforme o tipo) e o perfil:
 * cadastro feito pelo aplicativo nasce pendente, sem perfil nem organização.
 */
data class DadosAprovacao(
    val tipoOrganizacao: String,
    val idDistribuidor: Int?,
    val idCliente: Int?,
    val idPerfil: Int,
)

/** Fila de cadastros pendentes, para quem tem a permissão `aprovar_cadastro`. */
interface RepositorioCadastros {
    /** Cadastros aguardando aprovação (todas as páginas). */
    suspend fun pendentes(): Resultado<List<Usuario>>

    /** Perfis, distribuidores e clientes ativos para a escolha da aprovação. */
    suspend fun opcoes(): Resultado<OpcoesAprovacao>

    suspend fun aprovar(idUsuario: Int, dados: DadosAprovacao): Resultado<Usuario>

    /** Recusa o cadastro; a pessoa é avisada pela API. */
    suspend fun recusar(idUsuario: Int): Resultado<Usuario>
}
