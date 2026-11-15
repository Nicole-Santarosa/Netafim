package com.netafim.posvenda.acesso.ui.permissoes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netafim.posvenda.acesso.dominio.PerfilComPermissoes
import com.netafim.posvenda.acesso.dominio.PermissaoDoSistema
import com.netafim.posvenda.acesso.dominio.RepositorioAcesso
import com.netafim.posvenda.acesso.dominio.RepositorioPerfis
import com.netafim.posvenda.acesso.dominio.Resultado
import com.netafim.posvenda.acesso.dominio.permissoesEssenciais
import com.netafim.posvenda.acesso.ui.perfil.DescricaoPermissoes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PermissoesPerfilEstado(
    val perfil: PerfilComPermissoes? = null,
    /** Todas as permissões do sistema, na ordem em que o app as mostra. */
    val catalogo: List<PermissaoDoSistema> = emptyList(),
    val selecionadas: Set<String> = emptySet(),
    /** O que o perfil tem hoje no servidor, para saber se algo mudou. */
    val originais: Set<String> = emptySet(),
    /** Não podem ser desmarcadas (o Netafim Admin precisa delas). */
    val essenciais: Set<String> = emptySet(),
    val carregando: Boolean = true,
    val erro: String? = null,
    val salvando: Boolean = false,
    val erroAcao: String? = null,
    /** Salvou: a tela volta para a lista. */
    val salvo: Boolean = false,
) {
    val mudou: Boolean get() = selecionadas != originais
}

/**
 * Edita as permissões de um perfil (RF20): marca e desmarca, e só salva quando algo mudou. As permissões
 * essenciais do Netafim Admin ficam travadas (a API também recusa). Depois de salvar, atualiza o perfil da
 * própria pessoa, porque a mudança pode valer para ela mesma.
 */
class PermissoesPerfilViewModel(
    private val repositorio: RepositorioPerfis,
    private val acesso: RepositorioAcesso,
    private val idPerfil: Int,
) : ViewModel() {

    private val _estado = MutableStateFlow(PermissoesPerfilEstado())
    val estado: StateFlow<PermissoesPerfilEstado> = _estado.asStateFlow()

    init {
        carregar()
    }

    fun carregar() {
        _estado.update { it.copy(carregando = true, erro = null) }
        viewModelScope.launch {
            val perfis = repositorio.perfis()
            val permissoes = repositorio.permissoes()
            when {
                perfis is Resultado.Falha -> _estado.update { it.copy(carregando = false, erro = perfis.erro.mensagem) }
                permissoes is Resultado.Falha -> _estado.update { it.copy(carregando = false, erro = permissoes.erro.mensagem) }
                else -> {
                    val perfil = (perfis as Resultado.Sucesso).valor.firstOrNull { it.perfil.id == idPerfil }
                    if (perfil == null) {
                        _estado.update { it.copy(carregando = false, erro = "Perfil não encontrado.") }
                        return@launch
                    }
                    val catalogo = (permissoes as Resultado.Sucesso).valor.sortedBy { DescricaoPermissoes.posicao(it.codigo) }
                    _estado.update {
                        it.copy(
                            perfil = perfil,
                            catalogo = catalogo,
                            selecionadas = perfil.permissoes,
                            originais = perfil.permissoes,
                            essenciais = permissoesEssenciais(perfil.perfil),
                            carregando = false,
                        )
                    }
                }
            }
        }
    }

    fun alternar(codigo: String) = _estado.update {
        if (codigo in it.essenciais || it.salvando) {
            it
        } else {
            val novas = if (codigo in it.selecionadas) it.selecionadas - codigo else it.selecionadas + codigo
            it.copy(selecionadas = novas, erroAcao = null)
        }
    }

    fun salvar() {
        val atual = _estado.value
        if (atual.salvando || !atual.mudou || atual.perfil == null) return
        _estado.update { it.copy(salvando = true, erroAcao = null) }
        viewModelScope.launch {
            when (val r = repositorio.salvarPermissoes(idPerfil, atual.selecionadas)) {
                is Resultado.Sucesso -> {
                    acesso.atualizarPerfil() // a mudança pode valer para quem está logado
                    _estado.update { it.copy(salvando = false, salvo = true) }
                }
                is Resultado.Falha -> _estado.update { it.copy(salvando = false, erroAcao = r.erro.mensagem) }
            }
        }
    }
}
