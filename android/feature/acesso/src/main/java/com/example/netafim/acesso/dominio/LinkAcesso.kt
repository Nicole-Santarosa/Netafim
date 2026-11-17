package com.example.netafim.acesso.dominio

/** O que o link do e-mail pede: definir a senha de um convite ou redefinir uma senha esquecida. */
enum class AcaoLink(val valor: String) {
    CONVITE("convite"),
    REDEFINIR_SENHA("redefinir-senha"),
    ;

    companion object {
        fun de(valor: String?): AcaoLink? = entries.firstOrNull { it.valor == valor }
    }
}

/** Esquema próprio dos links, só para desenvolvimento: qualquer app instalado pode registrá-lo. */
private const val ESQUEMA_PROPRIO = "netafim://convite"

/** Nome de domínio puro (sem esquema, porta nem caminho), como `app.netafim.com.br`. */
private val FORMATO_HOST = Regex("""^(?=.{4,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$""")

/**
 * Padrões de link que o app aceita. O link do e-mail leva um token de uso único; por isso, em produção, só
 * vale o App Link verificado (`https://<hostLink>/convite`), que o sistema entrega apenas a este app depois de
 * conferir o `assetlinks.json` do domínio. O esquema próprio `netafim://` só entra quando [esquemaProprio] é
 * verdadeiro (build de debug).
 *
 * @throws IllegalArgumentException se [hostLink] não estiver vazio e não for um nome de domínio.
 */
fun padroesDeLink(hostLink: String, esquemaProprio: Boolean): List<String> {
    val host = hostLink.trim().lowercase()
    require(host.isEmpty() || FORMATO_HOST.matches(host)) {
        "linkHost precisa ser só o domínio (ex.: app.netafim.com.br), sem https:// nem caminho."
    }
    return buildList {
        if (host.isNotEmpty()) add("https://$host/convite?acao={acao}&token={token}")
        if (esquemaProprio) add("$ESQUEMA_PROPRIO?acao={acao}&token={token}")
    }
}

/**
 * Link `https://<domínio>/convite?acao=<convite|redefinir-senha>&token=<token>` enviado por e-mail
 * (em debug também `netafim://convite?...`).
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
