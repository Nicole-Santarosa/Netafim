package com.netafim.posvenda.acesso.dominio

import kotlinx.coroutines.flow.StateFlow

/** Contrato usado pelas telas; a implementação conversa com a API e guarda a sessão. */
interface RepositorioAcesso {
    /** Sessão atual (null quando ninguém está logado). Funciona sem internet. */
    val sessao: StateFlow<Sessao?>

    suspend fun entrar(email: String, senha: String): Resultado<Usuario>

    suspend fun sair()

    /** Atualiza os dados do usuário a partir de GET /me. */
    suspend fun atualizarPerfil(): Resultado<Usuario>

    suspend fun versaoTermoVigente(): Resultado<String>

    suspend fun solicitarCadastro(dados: DadosCadastro): Resultado<String>

    suspend fun esqueciSenha(email: String): Resultado<String>
}

data class DadosCadastro(
    val nome: String,
    val email: String,
    val telefone: String?,
    val senha: String,
    val versaoTermo: String,
)
