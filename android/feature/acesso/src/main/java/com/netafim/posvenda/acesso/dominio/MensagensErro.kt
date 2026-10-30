package com.netafim.posvenda.acesso.dominio

/**
 * Mensagens por código de erro da API. Quando o código é desconhecido, a tela usa o
 * texto "detail" que veio da API, que já é em português.
 */
object MensagensErro {
    private val porCodigo = mapOf(
        "credenciais_invalidas" to
            "E-mail ou senha incorretos. Depois de várias tentativas, o acesso fica " +
            "bloqueado por alguns minutos.",
        "conta_pendente" to
            "Seu cadastro ainda está em análise pela Netafim. Você receberá um e-mail " +
            "quando o acesso for liberado.",
        "conta_recusada" to "Sua solicitação de acesso não foi aprovada.",
        "conta_inativa" to "Sua conta está inativa. Procure o administrador da sua empresa.",
        "conta_bloqueada" to "Sua conta está bloqueada. Procure a Netafim.",
        "dispositivo_revogado" to "Este aparelho teve o acesso revogado. Procure a Netafim.",
        "termo_desatualizado" to
            "O aviso de privacidade foi atualizado. Leia e aceite a nova versão.",
        "token_invalido" to "Sua sessão expirou. Entre novamente.",
    )

    fun para(codigo: String?, detalheApi: String?): String =
        porCodigo[codigo] ?: detalheApi?.takeIf { it.isNotBlank() } ?: ErroAcesso.MENSAGEM_INESPERADA
}
