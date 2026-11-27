package com.example.netafim.feature.relatorio.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.netafim.feature.relatorio.data.RelatorioRepositorio
import com.example.netafim.feature.relatorio.domain.ValidadorRelatorio
import com.example.netafim.feature.relatorio.model.AssinaturaRegistro
import com.example.netafim.feature.relatorio.model.EtapaRelatorio
import com.example.netafim.feature.relatorio.model.EvidenciaItem
import com.example.netafim.feature.relatorio.model.MotivoAtendimento
import com.example.netafim.feature.relatorio.model.RelatorioUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RelatorioViewModel(
    private val repositorio: RelatorioRepositorio,
    private val escopoAssincrono: CoroutineScope? = null
) : ViewModel() {

    private fun scope(): CoroutineScope = escopoAssincrono ?: viewModelScope

    private val _uiState = MutableStateFlow(RelatorioUiState())
    val uiState: StateFlow<RelatorioUiState> = _uiState.asStateFlow()

    fun iniciarRelatorio(
        idRelatorio: String,
        idAtendimento: String,
        numeroOs: String,
        dataConclusao: String,
        motivoCodigo: String
    ) {
        val motivo = MotivoAtendimento.fromCodigo(motivoCodigo)
        _uiState.update { atual ->
            atual.copy(
                etapaAtual = EtapaRelatorio.CONSTATACOES,
                rascunho = atual.rascunho.copy(
                    idRelatorio = idRelatorio.ifBlank { atual.rascunho.idRelatorio },
                    idAtendimento = idAtendimento.ifBlank { atual.rascunho.idAtendimento },
                    numeroOs = numeroOs.ifBlank { atual.rascunho.numeroOs },
                    dataConclusao = dataConclusao.ifBlank { atual.rascunho.dataConclusao },
                    motivo = motivo
                ),
                salvoComSucesso = false,
                mensagemErro = null,
                mensagemAviso = null
            )
        }
    }

    fun carregarRascunhoExistente(idAtendimento: String) {
        scope().launch {
            repositorio.recuperarRelatorio(idAtendimento)
                .onSuccess { recuperado ->
                    if (recuperado != null) {
                        _uiState.update {
                            it.copy(
                                rascunho = recuperado,
                                mensagemErro = null,
                                salvoComSucesso = false
                            )
                        }
                    }
                }
                .onFailure { erro ->
                    _uiState.update {
                        it.copy(mensagemErro = erro.message ?: "Não foi possível recuperar o relatório.")
                    }
                }
        }
    }

    fun atualizarMotivo(motivo: MotivoAtendimento) {
        _uiState.update {
            it.copy(
                rascunho = it.rascunho.copy(motivo = motivo),
                mensagemErro = null,
                salvoComSucesso = false
            )
        }
    }

    fun atualizarTipoAtendimento(valor: String) {
        _uiState.update { it.copy(rascunho = it.rascunho.copy(tipoAtendimento = valor), mensagemErro = null) }
    }

    fun atualizarTipoTreinamento(valor: String) {
        _uiState.update { it.copy(rascunho = it.rascunho.copy(tipoTreinamento = valor), mensagemErro = null) }
    }

    fun atualizarLocalTreinamento(valor: String) {
        _uiState.update { it.copy(rascunho = it.rascunho.copy(localTreinamento = valor), mensagemErro = null) }
    }

    fun atualizarQtdPessoasTreinadas(valor: String) {
        _uiState.update { it.copy(rascunho = it.rascunho.copy(qtdPessoasTreinadas = valor), mensagemErro = null) }
    }

    fun atualizarDescricaoConstatacoes(valor: String) {
        _uiState.update { it.copy(rascunho = it.rascunho.copy(descricaoConstatacoes = valor), mensagemErro = null) }
    }

    fun atualizarAtividadesExecutadas(valor: String) {
        _uiState.update { it.copy(rascunho = it.rascunho.copy(atividadesExecutadas = valor), mensagemErro = null) }
    }

    fun atualizarPlanoAcao(valor: String) {
        _uiState.update { it.copy(rascunho = it.rascunho.copy(planoAcao = valor), mensagemErro = null) }
    }

    fun atualizarConclusaoVisita(valor: String) {
        _uiState.update { it.copy(rascunho = it.rascunho.copy(conclusaoVisita = valor), mensagemErro = null) }
    }

    fun adicionarEvidencia(item: EvidenciaItem) {
        _uiState.update { atual ->
            val novaLista = atual.rascunho.evidencias.filterNot { it.id == item.id } + item
            atual.copy(
                rascunho = atual.rascunho.copy(evidencias = novaLista),
                mensagemErro = null,
                salvoComSucesso = false
            )
        }
    }

    fun removerEvidencia(idEvidencia: String) {
        _uiState.update { atual ->
            val novaLista = atual.rascunho.evidencias.filterNot { it.id == idEvidencia }
            atual.copy(
                rascunho = atual.rascunho.copy(evidencias = novaLista),
                mensagemErro = null,
                salvoComSucesso = false
            )
        }
    }

    fun marcarEvidenciaIndisponivel(idEvidencia: String, motivoErro: String) {
        _uiState.update { atual ->
            val novaLista = atual.rascunho.evidencias.map { item ->
                if (item.id == idEvidencia) item.copy(disponivel = false) else item
            }
            atual.copy(
                rascunho = atual.rascunho.copy(evidencias = novaLista),
                mensagemErro = motivoErro
            )
        }
    }

    fun confirmarAssinaturaEspecialista(caminhoImagemLocal: String, nomeAssinante: String = "") {
        _uiState.update { atual ->
            atual.copy(
                rascunho = atual.rascunho.copy(
                    assinaturaEspecialista = AssinaturaRegistro(
                        tipoAssinante = "especialista",
                        caminhoImagemLocal = caminhoImagemLocal,
                        nomeAssinante = nomeAssinante,
                        confirmada = caminhoImagemLocal.isNotBlank()
                    )
                ),
                mensagemErro = null,
                salvoComSucesso = false
            )
        }
    }

    fun limparAssinaturaEspecialista() {
        _uiState.update { atual ->
            atual.copy(
                rascunho = atual.rascunho.copy(
                    assinaturaEspecialista = AssinaturaRegistro(
                        tipoAssinante = "especialista",
                        caminhoImagemLocal = null,
                        nomeAssinante = "",
                        confirmada = false
                    )
                ),
                mensagemErro = null,
                salvoComSucesso = false
            )
        }
    }

    fun confirmarAssinaturaCliente(caminhoImagemLocal: String, nomeAssinante: String = "") {
        _uiState.update { atual ->
            atual.copy(
                rascunho = atual.rascunho.copy(
                    assinaturaCliente = AssinaturaRegistro(
                        tipoAssinante = "cliente",
                        caminhoImagemLocal = caminhoImagemLocal,
                        nomeAssinante = nomeAssinante,
                        confirmada = caminhoImagemLocal.isNotBlank()
                    )
                ),
                mensagemErro = null,
                salvoComSucesso = false
            )
        }
    }

    fun limparAssinaturaCliente() {
        _uiState.update { atual ->
            atual.copy(
                rascunho = atual.rascunho.copy(
                    assinaturaCliente = AssinaturaRegistro(
                        tipoAssinante = "cliente",
                        caminhoImagemLocal = null,
                        nomeAssinante = "",
                        confirmada = false
                    )
                ),
                mensagemErro = null,
                salvoComSucesso = false
            )
        }
    }

    fun selecionarEtapa(etapa: EtapaRelatorio) {
        _uiState.update { it.copy(etapaAtual = etapa, mensagemErro = null) }
    }

    fun voltarEtapa() {
        _uiState.update { atual ->
            val anterior = atual.etapaAtual.anterior() ?: return@update atual
            atual.copy(etapaAtual = anterior, mensagemErro = null)
        }
    }

    fun avancarEtapa(): Boolean {
        val atual = _uiState.value
        val erro = ValidadorRelatorio.validarEtapa(atual.etapaAtual, atual.rascunho)
        if (erro != null) {
            _uiState.update { it.copy(mensagemErro = erro, salvoComSucesso = false) }
            return false
        }
        val proxima = atual.etapaAtual.proxima()
        if (proxima != null) {
            _uiState.update { it.copy(etapaAtual = proxima, mensagemErro = null) }
        }
        return true
    }

    fun salvarRelatorio() {
        val snapshot = _uiState.value
        if (snapshot.salvando) return

        val erroValidacao = ValidadorRelatorio.validarFinalizacao(snapshot.rascunho)
        if (erroValidacao != null) {
            _uiState.update {
                it.copy(
                    mensagemErro = erroValidacao,
                    salvoComSucesso = false
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                salvando = true,
                mensagemErro = null,
                salvoComSucesso = false
            )
        }

        scope().launch {
            try {
                val resultado = repositorio.salvarRelatorio(snapshot.rascunho)
                resultado
                    .onSuccess {
                        _uiState.update {
                            it.copy(
                                salvando = false,
                                salvoComSucesso = true,
                                mensagemErro = null
                            )
                        }
                    }
                    .onFailure { erro ->
                        _uiState.update {
                            it.copy(
                                salvando = false,
                                salvoComSucesso = false,
                                mensagemErro = erro.message ?: "Falha ao salvar o relatório."
                            )
                        }
                    }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        salvando = false,
                        salvoComSucesso = false,
                        mensagemErro = e.message ?: "Erro inesperado ao salvar o relatório."
                    )
                }
            }
        }
    }

    fun registrarFalhaEvidenciaOuAssinatura(mensagem: String) {
        _uiState.update {
            it.copy(
                mensagemErro = mensagem,
                salvoComSucesso = false
            )
        }
    }
}