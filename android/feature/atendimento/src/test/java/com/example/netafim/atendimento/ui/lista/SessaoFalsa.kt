package com.example.netafim.atendimento.ui.lista

import com.example.netafim.core.modelo.sessao.SessaoDoUsuario
import com.example.netafim.core.modelo.sessao.TipoDeOrganizacao
import com.example.netafim.core.modelo.sessao.UsuarioDaSessao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Dublê de teste da sessão: guarda o usuário num MutableStateFlow para o teste logar, deslogar e trocar de
// conta na hora que quiser.
class SessaoFalsa(inicial: UsuarioDaSessao? = umUsuario()) : SessaoDoUsuario {
    private val atual = MutableStateFlow(inicial)
    override val usuario: StateFlow<UsuarioDaSessao?> = atual.asStateFlow()

    fun entrarComo(novo: UsuarioDaSessao?) {
        atual.value = novo
    }

    companion object {
        // Usuário comum de teste. Cada teste troca só o que importa para ele.
        fun umUsuario(id: Int = 10, tipo: TipoDeOrganizacao = TipoDeOrganizacao.DISTRIBUIDOR) = UsuarioDaSessao(
            id = id,
            nome = "Usuário $id",
            tipoDeOrganizacao = tipo,
            idDaOrganizacao = 1,
            ehAdmin = false,
            permissoes = emptySet(),
        )
    }
}
