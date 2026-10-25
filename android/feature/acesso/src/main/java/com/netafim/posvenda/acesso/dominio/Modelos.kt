package com.netafim.posvenda.acesso.dominio

/** Usuário autenticado, como devolvido por GET /me. */
data class Usuario(
    val id: Int,
    val nome: String,
    val email: String,
    val telefone: String?,
    val situacao: String,
    val perfil: Perfil?,
    val organizacao: Organizacao?,
    val permissoes: Set<String>,
    val exigeTrocaSenha: Boolean,
) {
    fun temPermissao(codigo: String): Boolean = codigo in permissoes
}

data class Perfil(
    val id: Int,
    val nome: String,
    val tipoOrganizacao: String,
    val nivel: String,
) {
    val ehAdmin: Boolean get() = nivel == "admin"
}

data class Organizacao(
    val tipo: String,
    val id: Int?,
    val nome: String,
)

/** Sessão local: tokens e o usuário dono deles. */
data class Sessao(
    val accessToken: String,
    val refreshToken: String,
    val usuario: Usuario,
)

/** Códigos de permissão da API (tabela PERMISSAO, Anexo B do MER/DER). */
object Permissoes {
    const val APROVAR_CADASTRO = "aprovar_cadastro"
    const val CRIAR_USUARIO = "criar_usuario"
    const val EDITAR_USUARIO = "editar_usuario"
    const val EXCLUIR_USUARIO = "excluir_usuario"
    const val DEFINIR_PERFIL = "definir_perfil"
    const val ABRIR_ATENDIMENTO = "abrir_atendimento"
    const val VINCULAR_TECNICO = "vincular_tecnico"
    const val RESPONDER_ATENDIMENTO = "responder_atendimento"
    const val ENCERRAR_ATENDIMENTO = "encerrar_atendimento"
    const val CONSULTAR_PROPRIOS_ATENDIMENTOS = "consultar_proprios_atendimentos"
    const val CONSULTAR_ATENDIMENTOS_EMPRESA = "consultar_atendimentos_empresa"
    const val CONSULTAR_ATENDIMENTOS_CLIENTES_VINCULADOS =
        "consultar_atendimentos_clientes_vinculados"
    const val CONSULTAR_TODOS_ATENDIMENTOS = "consultar_todos_atendimentos"
    const val DASHBOARD_GERENCIAL = "dashboard_gerencial"
    const val DESTINAR_ATENDIMENTO = "destinar_atendimento"
    const val RECLASSIFICAR_ATENDIMENTO = "reclassificar_atendimento"
    const val EDITAR_ATENDIMENTO_FINALIZADO = "editar_atendimento_finalizado"
}
