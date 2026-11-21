package com.example.netafim.acesso.ui.dominios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.netafim.acesso.dominio.DadosNovoDominio
import com.example.netafim.acesso.dominio.Dominio
import com.example.netafim.acesso.dominio.ErroAcesso
import com.example.netafim.acesso.dominio.Opcao
import com.example.netafim.acesso.dominio.RepositorioCadastros
import com.example.netafim.acesso.dominio.RepositorioDominios
import com.example.netafim.acesso.dominio.Resultado
import com.example.netafim.acesso.dominio.TiposOrganizacao
import com.example.netafim.acesso.dominio.ValidacaoDominio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Mensagem de sucesso que a tela mostra uma vez. */
enum class AvisoDominio { CRIADO, ATIVADO, DESATIVADO }

data class DominiosEstado(
    val dominios: List<Dominio> = emptyList(),
    val carregando: Boolean = true,
    val erro: String? = null,
    /** Distribuidores ativos: dão o nome na lista e a escolha no formulário. */
    val distribuidores: List<Opcao> = emptyList(),
    val formularioAberto: Boolean = false,
    val textoDominio: String = "",
    val tipo: String = TiposOrganizacao.NETAFIM,
    val idDistribuidor: Int? = null,
    val erroDominio: String? = null,
    val erroDistribuidor: String? = null,
    val erroFormulario: String? = null,
    val salvando: Boolean = false,
    val aviso: AvisoDominio? = null,
    val erroAcao: String? = null,
    /** Domínio que está sendo ativado ou desativado: os botões ficam desabilitados. */
    val processandoId: Int? = null,
)

/**
 * Domínios de e-mail corporativos (RNF18), só para o Netafim Admin: listar, cadastrar, ativar e desativar.
 * Não há exclusão: o `app_django` só tem select, insert e update na tabela. A API continua sendo quem decide; as recusas aparecem como mensagem.
 */
class DominiosViewModel(
    private val repositorio: RepositorioDominios,
    private val cadastros: RepositorioCadastros,
) : ViewModel() {

    private val _estado = MutableStateFlow(DominiosEstado())
    val estado: StateFlow<DominiosEstado> = _estado.asStateFlow()

    init {
        carregar()
    }

    fun carregar() {
        _estado.update { it.copy(carregando = true, erro = null) }
        viewModelScope.launch {
            when (val r = repositorio.listar()) {
                is Resultado.Sucesso -> _estado.update { it.copy(dominios = ordenar(r.valor), carregando = false) }
                is Resultado.Falha -> _estado.update { it.copy(carregando = false, erro = r.erro.mensagem) }
            }
            // Os nomes dos distribuidores são um complemento: se falhar, a lista aparece do mesmo jeito.
            val opcoes = cadastros.opcoes()
            if (opcoes is Resultado.Sucesso) _estado.update { it.copy(distribuidores = opcoes.valor.distribuidores) }
        }
    }

    private fun ordenar(lista: List<Dominio>) = lista.sortedBy { it.dominio }

    // ------------------------------------------------------------------ formulário

    fun abrirFormulario() = _estado.update {
        it.copy(
            formularioAberto = true, textoDominio = "", tipo = TiposOrganizacao.NETAFIM, idDistribuidor = null,
            erroDominio = null, erroDistribuidor = null, erroFormulario = null, erroAcao = null,
        )
    }

    fun fecharFormulario() = _estado.update { if (it.salvando) it else it.copy(formularioAberto = false) }

    fun mudarDominio(valor: String) = _estado.update { it.copy(textoDominio = valor, erroDominio = null, erroFormulario = null) }

    fun escolherTipo(tipo: String) = _estado.update {
        it.copy(
            tipo = tipo,
            idDistribuidor = if (tipo == TiposOrganizacao.DISTRIBUIDOR) it.idDistribuidor else null,
            erroDistribuidor = null,
            erroFormulario = null,
        )
    }

    fun escolherDistribuidor(id: Int) = _estado.update { it.copy(idDistribuidor = id, erroDistribuidor = null, erroFormulario = null) }

    fun salvar() {
        val atual = _estado.value
        if (atual.salvando) return
        val erroDominio = ValidacaoDominio.validar(atual.textoDominio)
        val erroDistribuidor = if (atual.tipo == TiposOrganizacao.DISTRIBUIDOR && atual.idDistribuidor == null) {
            "Escolha o distribuidor."
        } else {
            null
        }
        if (erroDominio != null || erroDistribuidor != null) {
            _estado.update { it.copy(erroDominio = erroDominio, erroDistribuidor = erroDistribuidor) }
            return
        }
        _estado.update { it.copy(salvando = true, erroDominio = null, erroDistribuidor = null, erroFormulario = null) }
        val dados = DadosNovoDominio(
            dominio = ValidacaoDominio.normalizar(atual.textoDominio),
            tipoOrganizacao = atual.tipo,
            idDistribuidor = atual.idDistribuidor.takeIf { atual.tipo == TiposOrganizacao.DISTRIBUIDOR },
        )
        viewModelScope.launch {
            when (val r = repositorio.criar(dados)) {
                is Resultado.Sucesso -> _estado.update {
                    it.copy(
                        salvando = false,
                        formularioAberto = false,
                        dominios = ordenar(it.dominios + r.valor),
                        aviso = AvisoDominio.CRIADO,
                    )
                }
                is Resultado.Falha -> _estado.update { aplicarErroDoFormulario(it, r.erro) }
            }
        }
    }

    private fun aplicarErroDoFormulario(e: DominiosEstado, erro: ErroAcesso): DominiosEstado {
        val base = e.copy(salvando = false)
        if (erro is ErroAcesso.Campos) {
            val dominio = erro.campos["dominio"]
            val distribuidor = erro.campos["id_distribuidor"]
            if (dominio != null || distribuidor != null) {
                return base.copy(erroDominio = dominio, erroDistribuidor = distribuidor)
            }
        }
        return base.copy(erroFormulario = erro.mensagem)
    }

    // ------------------------------------------------------------------ ações da lista

    fun alternarAtivo(dominio: Dominio) {
        if (_estado.value.processandoId != null) return
        _estado.update { it.copy(processandoId = dominio.id, erroAcao = null, aviso = null) }
        viewModelScope.launch {
            when (val r = repositorio.alterarAtivo(dominio.id, !dominio.ativo)) {
                is Resultado.Sucesso -> _estado.update { atual ->
                    atual.copy(
                        processandoId = null,
                        dominios = atual.dominios.map { if (it.id == dominio.id) r.valor else it },
                        aviso = if (r.valor.ativo) AvisoDominio.ATIVADO else AvisoDominio.DESATIVADO,
                    )
                }
                is Resultado.Falha -> _estado.update { it.copy(processandoId = null, erroAcao = r.erro.mensagem) }
            }
        }
    }

    fun avisoMostrado() = _estado.update { it.copy(aviso = null) }
}
