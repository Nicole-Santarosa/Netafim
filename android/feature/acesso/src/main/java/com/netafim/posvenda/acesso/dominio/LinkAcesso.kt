package com.netafim.posvenda.acesso.dominio

/** O que o link do e-mail pede: definir a senha de um convite ou redefinir uma senha esquecida. */
enum class AcaoLink(val valor: String) {
    CONVITE("convite"),
    REDEFINIR_SENHA("redefinir-senha"),
    ;

    companion object {
        fun de(valor: String?): AcaoLink? = entries.firstOrNull { it.valor == valor }
    }
}

/**
 * Link `netafim://convite?acao=<convite|redefinir-senha>&token=<token>` enviado por e-mail.
 *
 * O token é um segredo de uso único: nunca vai para log nem para tela, e some do estado assim que o
 * link é usado. Aqui só se confere o formato; quem decide se ele vale é a API.
 */
data class LinkAcesso(val acao: AcaoLink, val token: String) {
    // Mesmo alfabeto que o back-end gera (secrets.token_urlsafe); a API aceita até 128 caracteres.
    companion object {
        private val FORMATO_TOKEN = Regex("^[A-Za-z0-9_-]{16,128}$")

        /** Devolve null quando a ação é desconhecida ou o token não tem o formato esperado. */
        fun de(acao: String?, token: String?): LinkAcesso? {
            val acaoLink = AcaoLink.de(acao) ?: return null
            if (token == null || !FORMATO_TOKEN.matches(token)) return null
            return LinkAcesso(acaoLink, token)
        }
    }

    // Evita o token em logs e em mensagens de erro (toString de data class).
    override fun toString(): String = "LinkAcesso(acao=$acao, token=***)"
}
