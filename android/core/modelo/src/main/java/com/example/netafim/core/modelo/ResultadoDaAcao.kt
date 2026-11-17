package com.example.netafim.core.modelo

// Resultado de uma ação de escrita no repositório.
sealed interface ResultadoDaAcao {
    // A ação foi salva no aparelho. Não quer dizer que o servidor aceitou: a confirmação definitiva só
    // existe quando a sincronização do registro for SINCRONIZADO.
    data object Sucesso : ResultadoDaAcao

    // A versão que o aparelho tinha não é a do servidor.
    data object ConflitoDeVersao : ResultadoDaAcao

    // O servidor recusou por regra de negócio; a mensagem é a que ele devolveu.
    data class RegraRecusada(val mensagem: String) : ResultadoDaAcao

    // Dados inválidos, com a mensagem de cada campo (chave é o nome do campo).
    data class DadosInvalidos(val erros: Map<String, String>) : ResultadoDaAcao

    data object SemPermissao : ResultadoDaAcao

    // O atendimento não existe ou o usuário não o enxerga.
    data object NaoEncontrado : ResultadoDaAcao

    data object FalhaDeRede : ResultadoDaAcao
}
