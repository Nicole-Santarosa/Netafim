package com.netafim.posvenda.acesso.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netafim.posvenda.acesso.dominio.DadosAprovacao
import com.netafim.posvenda.acesso.dominio.Opcao
import com.netafim.posvenda.acesso.dominio.OpcoesAprovacao
import com.netafim.posvenda.acesso.dominio.Perfil
import com.netafim.posvenda.acesso.dominio.RepositorioCadastros
import com.netafim.posvenda.acesso.dominio.Resultado
import com.netafim.posvenda.acesso.dominio.TiposOrganizacao
import com.netafim.posvenda.acesso.dominio.Usuario
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Passo de escolha aberto ao tocar em Aprovar: tipo de organização, organização e perfil. */
data class EscolhaAprovacao(
    val usuario: Usuario,
    val opcoes: OpcoesAprovacao? = null,
    val carregandoOpcoes: Boolean = true,
    val erroOpcoes: String? = null,
    val tipo: String? = null,
    val idOrganizacao: Int? = null,
    val idPerfil: Int? = null,
) {
    val precisaOrganizacao: Boolean
        get() = tipo == TiposOrganizacao.DISTRIBUIDOR || tipo == TiposOrganizacao.CLIENTE

    val organizacoes: List<Opcao>
        get() = when (tipo) {
            TiposOrganizacao.DISTRIBUIDOR -> opcoes?.distribuidores.orEmpty()
            TiposOrganizacao.CLIENTE -> opcoes?.clientes.orEmpty()
            else -> emptyList()
        }

    val perfis: List<Perfil>
        get() = opcoes?.perfis.orEmpty().filter { it.tipoOrganizacao == tipo }

    val completa: Boolean
        get() = tipo != null && idPerfil != null && (!precisaOrganizacao || idOrganizacao != null)
}

data class AprovacaoEstado(
    val carregando: Boolean = true,
    val pendentes: List<Usuario> = emptyList(),
    /** Falha ao carregar a lista. */
    val erro: String? = null,
    /** Falha ao aprovar ou recusar (aparece na própria folha ou no diálogo). */
    val erroAcao: String? = null,
    val processando: Boolean = false,
    val escolha: EscolhaAprovacao? = null,
    val recusando: Usuario? = null,
)

class AprovacaoViewModel(private val repositorio: RepositorioCadastros) : ViewModel() {

    private val _estado = MutableStateFlow(AprovacaoEstado())
    val estado: StateFlow<AprovacaoEstado> = _estado.asStateFlow()

    private var opcoesCarregadas: OpcoesAprovacao? = null

    init {
        carregar()
    }

    fun carregar() {
        _estado.update { it.copy(carregando = true, erro = null) }
        viewModelScope.launch {
            when (val r = repositorio.pendentes()) {
                is Resultado.Sucesso -> _estado.update { it.copy(carregando = false, pendentes = r.valor) }
                is Resultado.Falha -> _estado.update { it.copy(carregando = false, erro = r.erro.mensagem) }
            }
        }
    }

    // ------------------------------------------------------------- aprovar

    fun aoAprovar(usuario: Usuario) {
        val ja = opcoesCarregadas
        _estado.update {
            it.copy(
                erroAcao = null,
                escolha = EscolhaAprovacao(usuario = usuario, opcoes = ja, carregandoOpcoes = ja == null),
            )
        }
        if (ja == null) carregarOpcoes()
    }

    fun carregarOpcoes() {
        _estado.update { e -> e.copy(escolha = e.escolha?.copy(carregandoOpcoes = true, erroOpcoes = null)) }
        viewModelScope.launch {
            when (val r = repositorio.opcoes()) {
                is Resultado.Sucesso -> {
                    opcoesCarregadas = r.valor
                    _estado.update { e ->
                        e.copy(escolha = e.escolha?.copy(opcoes = r.valor, carregandoOpcoes = false))
                    }
                }
                is Resultado.Falha -> _estado.update { e ->
                    e.copy(escolha = e.escolha?.copy(carregandoOpcoes = false, erroOpcoes = r.erro.mensagem))
                }
            }
        }
    }

    /** Trocar o tipo limpa a organização e o perfil, que dependem dele. */
    fun escolherTipo(tipo: String) = _estado.update { e ->
        e.copy(
            erroAcao = null,
            escolha = e.escolha?.copy(tipo = tipo, idOrganizacao = null, idPerfil = null),
        )
    }

    fun escolherOrganizacao(id: Int) = _estado.update { e ->
        e.copy(erroAcao = null, escolha = e.escolha?.copy(idOrganizacao = id))
    }

    fun escolherPerfil(id: Int) = _estado.update { e ->
        e.copy(erroAcao = null, escolha = e.escolha?.copy(idPerfil = id))
    }

    fun cancelarAprovacao() = _estado.update { it.copy(escolha = null, erroAcao = null) }

    fun confirmarAprovacao() {
        val atual = _estado.value
        val escolha = atual.escolha ?: return
        if (atual.processando || !escolha.completa) return
        val dados = DadosAprovacao(
            tipoOrganizacao = escolha.tipo!!,
            idDistribuidor = escolha.idOrganizacao.takeIf { escolha.tipo == TiposOrganizacao.DISTRIBUIDOR },
            idCliente = escolha.idOrganizacao.takeIf { escolha.tipo == TiposOrganizacao.CLIENTE },
            idPerfil = escolha.idPerfil!!,
        )
        _estado.update { it.copy(processando = true, erroAcao = null) }
        viewModelScope.launch {
            when (val r = repositorio.aprovar(escolha.usuario.id, dados)) {
                is Resultado.Sucesso -> concluir(escolha.usuario.id)
                is Resultado.Falha -> _estado.update { it.copy(processando = false, erroAcao = r.erro.mensagem) }
            }
        }
    }

    // ------------------------------------------------------------- recusar

    fun aoRecusar(usuario: Usuario) = _estado.update { it.copy(recusando = usuario, erroAcao = null) }

    fun cancelarRecusa() = _estado.update { it.copy(recusando = null, erroAcao = null) }

    fun confirmarRecusa() {
        val atual = _estado.value
        val usuario = atual.recusando ?: return
        if (atual.processando) return
        _estado.update { it.copy(processando = true, erroAcao = null) }
        viewModelScope.launch {
            when (val r = repositorio.recusar(usuario.id)) {
                is Resultado.Sucesso -> concluir(usuario.id)
                is Resultado.Falha -> _estado.update { it.copy(processando = false, erroAcao = r.erro.mensagem) }
            }
        }
    }

    /** Tira da fila quem acabou de ser aprovado ou recusado e fecha a folha ou o diálogo. */
    private fun concluir(idUsuario: Int) = _estado.update { e ->
        e.copy(
            processando = false,
            pendentes = e.pendentes.filterNot { it.id == idUsuario },
            escolha = null,
            recusando = null,
            erroAcao = null,
        )
    }
}
