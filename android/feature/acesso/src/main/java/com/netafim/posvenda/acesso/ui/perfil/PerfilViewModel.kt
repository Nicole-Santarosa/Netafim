package com.netafim.posvenda.acesso.ui.perfil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netafim.posvenda.acesso.dominio.ErroAcesso
import com.netafim.posvenda.acesso.dominio.RepositorioAcesso
import com.netafim.posvenda.acesso.dominio.Resultado
import com.netafim.posvenda.acesso.dominio.Usuario
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PerfilEstado(
    val usuario: Usuario? = null,
    val atualizando: Boolean = false,
    /** Aviso discreto: o perfil exibido é o último salvo no aparelho. */
    val aviso: String? = null,
    val saiu: Boolean = false,
)

class PerfilViewModel(private val repositorio: RepositorioAcesso) : ViewModel() {

    private val atualizando = MutableStateFlow(false)
    private val aviso = MutableStateFlow<String?>(null)
    private val saiu = MutableStateFlow(false)

    val estado: StateFlow<PerfilEstado> =
        combine(repositorio.sessao, atualizando, aviso, saiu) { sessao, carregando, msg, fora ->
            PerfilEstado(sessao?.usuario, carregando, msg, fora || sessao == null)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PerfilEstado(
            usuario = repositorio.sessao.value?.usuario,
        ))

    init {
        atualizar()
    }

    /** Busca a versão mais recente em /me. Sem internet, mantém a que está salva. */
    fun atualizar() {
        if (atualizando.value) return
        atualizando.value = true
        viewModelScope.launch {
            val resultado = repositorio.atualizarPerfil()
            aviso.value = when {
                resultado is Resultado.Falha && resultado.erro is ErroAcesso.SemConexao ->
                    "Sem conexão: mostrando os dados salvos no aparelho."
                resultado is Resultado.Falha && resultado.erro !is ErroAcesso.SessaoExpirada ->
                    resultado.erro.mensagem
                else -> null
            }
            atualizando.value = false
        }
    }

    fun sair() {
        viewModelScope.launch {
            repositorio.sair()
            saiu.value = true
        }
    }
}
