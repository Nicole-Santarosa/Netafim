package com.netafim.posvenda.acesso.ui.permissoes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netafim.posvenda.acesso.dominio.PerfilComPermissoes
import com.netafim.posvenda.acesso.dominio.RepositorioPerfis
import com.netafim.posvenda.acesso.dominio.Resultado
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PerfisEstado(
    val perfis: List<PerfilComPermissoes> = emptyList(),
    val carregando: Boolean = true,
    val erro: String? = null,
)

/** Lista dos perfis de acesso com quantas permissões cada um concede (RF20, só para o Netafim Admin). */
class PerfisViewModel(private val repositorio: RepositorioPerfis) : ViewModel() {

    private val _estado = MutableStateFlow(PerfisEstado())
    val estado: StateFlow<PerfisEstado> = _estado.asStateFlow()

    private var jaCarregou = false

    init {
        carregar()
    }

    fun carregar() {
        _estado.update { it.copy(carregando = true, erro = null) }
        viewModelScope.launch {
            when (val r = repositorio.perfis()) {
                is Resultado.Sucesso -> {
                    jaCarregou = true
                    _estado.update { it.copy(perfis = ordenar(r.valor), carregando = false) }
                }
                is Resultado.Falha -> _estado.update { it.copy(carregando = false, erro = r.erro.mensagem) }
            }
        }
    }

    /** Ao voltar da edição, atualiza as contagens sem piscar o carregamento. */
    fun aoRetomar() {
        if (!jaCarregou) return
        viewModelScope.launch {
            val r = repositorio.perfis()
            if (r is Resultado.Sucesso) _estado.update { it.copy(perfis = ordenar(r.valor)) }
        }
    }

    /** Netafim, distribuidor e cliente, e dentro de cada um o admin antes do usuário. */
    private fun ordenar(lista: List<PerfilComPermissoes>) = lista.sortedWith(
        compareBy({ ORDEM_ORGANIZACAO.indexOf(it.perfil.tipoOrganizacao).let { i -> if (i < 0) Int.MAX_VALUE else i } },
            { if (it.perfil.ehAdmin) 0 else 1 }, { it.perfil.nome }),
    )

    private companion object {
        val ORDEM_ORGANIZACAO = listOf("netafim", "distribuidor", "cliente")
    }
}
