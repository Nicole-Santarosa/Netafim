package com.netafim.posvenda.acesso.dominio

import kotlinx.coroutines.flow.StateFlow

/** Contrato usado pelas telas; a implementação conversa com a API e guarda a sessão. */
interface RepositorioAcesso {
    /** Sessão atual (null quando ninguém está logado). Funciona sem internet. */
    val sessao: StateFlow<Sessao?>

    suspend fun entrar(email: String, senha: String): Resultado<ResultadoEntrada>

    /** Segundo passo do login com MFA: código do aplicativo (6 dígitos) ou de recuperação. */
    suspend fun concluirLoginMfa(desafio: String, codigo: String): Resultado<Usuario>

    suspend fun estadoMfa(): Resultado<EstadoMfa>

    /** Gera um segredo novo. O MFA só liga depois de [confirmarMfa]. */
    suspend fun iniciarMfa(): Resultado<ConfiguracaoMfa>

    /** Liga o MFA e devolve os códigos de recuperação, que a API mostra uma única vez. */
    suspend fun confirmarMfa(codigo: String): Resultado<List<String>>

    suspend fun desativarMfa(senha: String, codigo: String): Resultado<Unit>

    suspend fun sair()

    /** Atualiza os dados do usuário a partir de GET /me. */
    suspend fun atualizarPerfil(): Resultado<Usuario>

    suspend fun versaoTermoVigente(): Resultado<String>

    suspend fun solicitarCadastro(dados: DadosCadastro): Resultado<String>

    suspend fun esqueciSenha(email: String): Resultado<String>

    /** Define a senha de um convite (link do e-mail) e registra o aceite do aviso de privacidade. */
    suspend fun aceitarConvite(token: String, senha: String, versaoTermo: String): Resultado<String>

    /** Conclui a redefinição de senha pelo link do e-mail. */
    suspend fun redefinirSenha(token: String, novaSenha: String): Resultado<String>
}

data class DadosCadastro(
    val nome: String,
    val email: String,
    val telefone: String?,
    val senha: String,
    val versaoTermo: String,
)
