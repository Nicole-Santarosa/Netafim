package com.netafim.posvenda.acesso.dominio

/**
 * Validações feitas no aparelho para dar retorno imediato. A API valida de novo
 * (inclusive as regras de senha do Django), então isto é só conforto de uso.
 */
object Validacao {
    private val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    const val SENHA_MINIMO = 8

    fun email(valor: String): String? = when {
        valor.isBlank() -> "Informe o e-mail."
        !EMAIL.matches(valor.trim()) -> "E-mail inválido."
        else -> null
    }

    fun senhaLogin(valor: String): String? =
        if (valor.isEmpty()) "Informe a senha." else null

    fun novaSenha(valor: String): String? = when {
        valor.length < SENHA_MINIMO -> "A senha precisa ter pelo menos $SENHA_MINIMO caracteres."
        valor.all { it.isDigit() } -> "A senha não pode ter só números."
        else -> null
    }

    fun confirmacao(senha: String, confirmacao: String): String? =
        if (senha != confirmacao) "As senhas não conferem." else null

    fun nome(valor: String): String? = when {
        valor.isBlank() -> "Informe o nome."
        valor.trim().length < 3 -> "Nome muito curto."
        else -> null
    }

    /** Telefone é opcional; quando informado, DDD + número (10 ou 11 dígitos). */
    fun telefone(valor: String): String? {
        if (valor.isBlank()) return null
        val digitos = valor.filter { it.isDigit() }
        return if (digitos.length in 10..11) null else "Informe DDD e número."
    }

    fun somenteDigitos(valor: String): String = valor.filter { it.isDigit() }
}
