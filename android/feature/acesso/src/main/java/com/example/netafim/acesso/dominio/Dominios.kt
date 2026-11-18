package com.example.netafim.acesso.dominio

/**
 * Domínio de e-mail corporativo aceito no cadastro (RNF18). Quem se cadastra com e-mail de um domínio da
 * Netafim ou de um distribuidor pode ser aprovado nessa organização; sem domínio cadastrado a aprovação
 * volta `dominio_nao_autorizado`.
 */
data class Dominio(
    val id: Int,
    val dominio: String,
    /** `netafim` ou `distribuidor`. */
    val tipoOrganizacao: String,
    val idDistribuidor: Int?,
    val ativo: Boolean,
)

data class DadosNovoDominio(
    val dominio: String,
    val tipoOrganizacao: String,
    val idDistribuidor: Int?,
)

/** Domínios de e-mail corporativos (só o Netafim Admin; `GET/POST/PATCH/DELETE dominios`). */
interface RepositorioDominios {
    suspend fun listar(): Resultado<List<Dominio>>

    suspend fun criar(dados: DadosNovoDominio): Resultado<Dominio>

    suspend fun alterarAtivo(idDominio: Int, ativo: Boolean): Resultado<Dominio>

    suspend fun excluir(idDominio: Int): Resultado<Unit>
}

/** Quem pode gerenciar os domínios: o Netafim Admin (a API recusa os demais). */
fun Usuario.podeGerenciarDominios(): Boolean =
    perfil?.let { it.tipoOrganizacao == TiposOrganizacao.NETAFIM && it.ehAdmin } == true

/** Validação do domínio feita no aparelho; a API valida de novo (`validate_dominio`). */
object ValidacaoDominio {
    private val FORMATO = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")

    /** Como a API grava: sem espaços nas pontas, em minúsculas e sem o `@` do começo. */
    fun normalizar(valor: String): String = valor.trim().lowercase().removePrefix("@")

    fun validar(valor: String): String? {
        val texto = normalizar(valor)
        return when {
            texto.isEmpty() -> "Informe o domínio."
            texto.length > 120 -> "Domínio muito longo."
            !FORMATO.matches(texto) -> "Domínio inválido. Use algo como empresa.com.br."
            else -> null
        }
    }
}
