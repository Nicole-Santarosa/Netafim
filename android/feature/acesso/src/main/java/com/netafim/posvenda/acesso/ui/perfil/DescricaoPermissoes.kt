package com.netafim.posvenda.acesso.ui.perfil

import com.netafim.posvenda.acesso.dominio.Permissoes

/** Texto amigável para cada permissão, na ordem em que aparece no perfil. */
object DescricaoPermissoes {
    private val textos = linkedMapOf(
        Permissoes.ABRIR_ATENDIMENTO to "Abrir atendimentos",
        Permissoes.RESPONDER_ATENDIMENTO to "Responder atendimentos",
        Permissoes.CONSULTAR_PROPRIOS_ATENDIMENTOS to "Consultar seus atendimentos",
        Permissoes.CONSULTAR_ATENDIMENTOS_EMPRESA to "Consultar os atendimentos da empresa",
        Permissoes.CONSULTAR_ATENDIMENTOS_CLIENTES_VINCULADOS to
            "Consultar atendimentos dos clientes vinculados",
        Permissoes.CONSULTAR_TODOS_ATENDIMENTOS to "Consultar todos os atendimentos",
        Permissoes.VINCULAR_TECNICO to "Vincular técnico ao atendimento",
        Permissoes.DESTINAR_ATENDIMENTO to "Destinar atendimentos",
        Permissoes.RECLASSIFICAR_ATENDIMENTO to "Reclassificar atendimentos",
        Permissoes.ENCERRAR_ATENDIMENTO to "Encerrar atendimentos",
        Permissoes.EDITAR_ATENDIMENTO_FINALIZADO to "Editar atendimentos finalizados",
        Permissoes.DASHBOARD_GERENCIAL to "Ver o painel gerencial",
        Permissoes.CRIAR_USUARIO to "Cadastrar usuários",
        Permissoes.EDITAR_USUARIO to "Editar usuários",
        Permissoes.EXCLUIR_USUARIO to "Excluir usuários",
        Permissoes.APROVAR_CADASTRO to "Aprovar cadastros",
        Permissoes.DEFINIR_PERFIL to "Definir perfis de acesso",
    )

    fun para(codigos: Set<String>): List<String> =
        textos.filterKeys { it in codigos }.values.toList()
}
