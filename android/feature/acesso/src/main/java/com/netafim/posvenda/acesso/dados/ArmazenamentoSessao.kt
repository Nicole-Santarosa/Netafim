package com.netafim.posvenda.acesso.dados

/**
 * Onde a sessão fica guardada no aparelho. A implementação de produção
 * ([ArmazenamentoSessaoSeguro]) cifra os tokens com uma chave do Android Keystore;
 * os testes usam uma versão em memória.
 */
interface ArmazenamentoSessao {
    /** Tokens e usuário serializado, ou null se não houver sessão. */
    suspend fun ler(): SessaoGuardada?

    suspend fun salvar(sessao: SessaoGuardada)

    suspend fun limpar()

    /** Identificador deste aparelho para a API (tabela DISPOSITIVO). */
    suspend fun idDispositivo(): String

    /** Gera um identificador novo, ex.: quando outro usuário entra no mesmo aparelho. */
    suspend fun novoIdDispositivo(): String
}

data class SessaoGuardada(
    val accessToken: String,
    val refreshToken: String,
    val usuarioJson: String,
)
