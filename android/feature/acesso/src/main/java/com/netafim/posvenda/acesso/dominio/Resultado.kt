package com.netafim.posvenda.acesso.dominio

/** Resultado das operações do módulo de acesso, sem exceções vazando para a UI. */
sealed interface Resultado<out T> {
    data class Sucesso<T>(val valor: T) : Resultado<T>
    data class Falha(val erro: ErroAcesso) : Resultado<Nothing>
}

/** Erro já traduzido para a pessoa usuária. [codigo] vem da API quando existe. */
sealed interface ErroAcesso {
    val mensagem: String

    /** Erro de regra de negócio devolvido pela API (ex.: conta_pendente). */
    data class Api(val codigo: String?, override val mensagem: String, val status: Int) : ErroAcesso

    /** Erros de validação por campo (HTTP 400 do DRF). */
    data class Campos(val campos: Map<String, String>, override val mensagem: String) : ErroAcesso

    data object SemConexao : ErroAcesso {
        override val mensagem = "Sem conexão com a internet. Verifique a rede e tente de novo."
    }

    data object SessaoExpirada : ErroAcesso {
        override val mensagem = "Sua sessão expirou. Entre novamente."
    }

    data class Inesperado(override val mensagem: String = MENSAGEM_INESPERADA) : ErroAcesso

    companion object {
        const val MENSAGEM_INESPERADA =
            "Não foi possível concluir agora. Tente novamente em instantes."
    }
}
