package com.example.netafim.ui.relatorio

import androidx.lifecycle.ViewModel
import com.example.netafim.data.repository.RelatorioRepositorio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RelatorioViewModel(
    private val repositorio: RelatorioRepositorio? = null
) : ViewModel() {

    // Motivos válidos conforme padrão da API/Banco
    private val _motivoAtendimento = MutableStateFlow("pos_venda")
    val motivoAtendimento: StateFlow<String> = _motivoAtendimento.asStateFlow()

    private val _constatacoes = MutableStateFlow("")
    val constatacoes: StateFlow<String> = _constatacoes.asStateFlow()

    private val _atividades = MutableStateFlow("")
    val atividades: StateFlow<String> = _atividades.asStateFlow()

    private val _planoAcao = MutableStateFlow("")
    val planoAcao: StateFlow<String> = _planoAcao.asStateFlow()

    private val _conclusaoVisita = MutableStateFlow("")
    val conclusaoVisita: StateFlow<String> = _conclusaoVisita.asStateFlow()

    // Evidências
    private val _fotosConstatacao = MutableStateFlow<List<String>>(emptyList())
    val fotosConstatacao: StateFlow<List<String>> = _fotosConstatacao.asStateFlow()

    private val _videosAtividade = MutableStateFlow<List<String>>(emptyList())
    val videosAtividade: StateFlow<List<String>> = _videosAtividade.asStateFlow()

    private val _anexosExtras = MutableStateFlow<List<String>>(emptyList())
    val anexosExtras: StateFlow<List<String>> = _anexosExtras.asStateFlow()

    // Assinaturas independentes (Cliente e Especialista)
    private val _caminhoAssinaturaCliente = MutableStateFlow<String?>(null)
    val caminhoAssinaturaCliente: StateFlow<String?> = _caminhoAssinaturaCliente.asStateFlow()

    private val _caminhoAssinaturaEspecialista = MutableStateFlow<String?>(null)
    val caminhoAssinaturaEspecialista: StateFlow<String?> = _caminhoAssinaturaEspecialista.asStateFlow()

    fun setMotivo(motivo: String) {
        _motivoAtendimento.value = motivo
    }

    fun atualizarConstatacoes(valor: String) { _constatacoes.value = valor }
    fun atualizarAtividades(valor: String) { _atividades.value = valor }
    fun atualizarPlanoAcao(valor: String) { _planoAcao.value = valor }
    fun atualizarConclusaoVisita(valor: String) { _conclusaoVisita.value = valor }

    fun adicionarFotoConstatacao(caminho: String) {
        _fotosConstatacao.value = _fotosConstatacao.value + caminho
    }

    fun adicionarVideoAtividade(caminho: String) {
        _videosAtividade.value = _videosAtividade.value + caminho
    }

    fun adicionarAnexoExtra(caminho: String) {
        _anexosExtras.value = _anexosExtras.value + caminho
    }

    fun definirAssinaturaCliente(caminho: String) {
        _caminhoAssinaturaCliente.value = caminho
    }

    fun definirAssinaturaEspecialista(caminho: String) {
        _caminhoAssinaturaEspecialista.value = caminho
    }

    /**
     * Validação baseada no motivo e regras de negócio:
     * - Programação de Feiras pode ter regras flexíveis de assinatura.
     * - Demais motivos exigem constatações, atividades, conclusão e ambas as assinaturas por padrão.
     */
    fun validarRelatorio(): Boolean {
        val motivo = _motivoAtendimento.value
        val reqConstatacao = _constatacoes.value.isNotBlank()
        val reqAtividade = _atividades.value.isNotBlank()
        val reqConclusao = _conclusaoVisita.value.isNotBlank()

        val assinaturaClienteOk = _caminhoAssinaturaCliente.value != null
        val assinaturaEspecialistaOk = _caminhoAssinaturaEspecialista.value != null

        return if (motivo == "programacao_feiras") {
            reqConstatacao && reqConclusao
        } else {
            reqConstatacao && reqAtividade && reqConclusao && assinaturaClienteOk && assinaturaEspecialistaOk
        }
    }
}