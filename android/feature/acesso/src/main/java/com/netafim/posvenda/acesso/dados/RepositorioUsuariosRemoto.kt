package com.netafim.posvenda.acesso.dados

import com.netafim.posvenda.acesso.dominio.DadosNovoUsuario
import com.netafim.posvenda.acesso.dominio.RepositorioUsuarios
import com.netafim.posvenda.acesso.dominio.Resultado
import com.netafim.posvenda.acesso.dominio.Usuario
import kotlinx.serialization.json.Json

/** Gestão de usuários pela API autenticada. */
class RepositorioUsuariosRemoto(
    private val api: AcessoApi,
    private val json: Json,
) : RepositorioUsuarios {

    override suspend fun listar(): Resultado<List<Usuario>> = executarApi(json) {
        todasAsPaginas { pagina -> api.usuarios(situacao = null, pagina = pagina) }.map { it.paraDominio() }
    }

    override suspend fun criar(dados: DadosNovoUsuario): Resultado<Usuario> = executarApi(json) {
        api.criarUsuario(
            CriarUsuarioRequisicao(
                nome = dados.nome.trim(),
                email = dados.email.trim(),
                telefone = dados.telefone?.takeIf { it.isNotBlank() },
            ),
        ).paraDominio()
    }
}
