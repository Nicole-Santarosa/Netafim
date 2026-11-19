package com.example.netafim.ui.relatorio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.netafim.data.repository.RelatorioRepositorio
import com.example.netafim.data.repository.RelatorioRepositorioImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RelatorioViewModel(
    private val repositorio: RelatorioRepositorio = RelatorioRepositorioImpl()
) : ViewModel() {

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

    private val _fotosConstatacao = MutableStateFlow<List<String>>(emptyList())
    val fotosConstatacao: StateFlow<List<String>> = _fotosConstatacao.asStateFlow()

    private val _videosAtividade = MutableStateFlow<List<String>>(emptyList())
    val videosAtividade: StateFlow<List<String>> = _videosAtividade.asStateFlow()

    private val _anexosExtras = MutableStateFlow<List<String>>(emptyList())
    val anexosExtras: StateFlow<List<String>> = _anexosExtras.asStateFlow()

    private val _caminhoAssinaturaCliente = MutableStateFlow<String?>(null)
    val caminhoAssinaturaCliente: StateFlow<String?> = _caminhoAssinaturaCliente.asStateFlow()

    private val _caminhoAssinaturaEspecialista = MutableStateFlow<String?>(null)
    val caminhoAssinaturaEspecialista: StateFlow<String?> = _caminhoAssinaturaEspecialista.asStateFlow()

    fun setMotivo(motivo: String) {
        _motivoAtendimento.value = motivo
    }

    fun atualizarConstatacoes(v: String) { _constatacoes.value = v }
    fun atualizarAtividades(v: String) { _atividades.value = v }
    fun atualizarPlanoAcao(v: String) { _planoAcao.value = v }
    fun atualizarConclusaoVisita(v: String) { _conclusaoVisita.value = v }

    fun adicionarFotoConstatacao(path: String) {
        _fotosConstatacao.value = _fotosConstatacao.value + path
    }

    fun adicionarVideoAtividade(path: String) {
        _videosAtividade.value = _videosAtividade.value + path
    }

    fun adicionarAnexoExtra(path: String) {
        _anexosExtras.value = _anexosExtras.value + path
    }

    fun definirAssinaturaCliente(path: String) {
        _caminhoAssinaturaCliente.value = path
    }

    fun definirAssinaturaEspecialista(path: String) {
        _caminhoAssinaturaEspecialista.value = path
    }

    // Validação estrita por etapa e regras de negócio baseadas no motivo
    fun validarEtapa(etapa: Int): Pair<Boolean, String> {
        val motivo = _motivoAtendimento.value
        val reqConstatacao = _constatacoes.value.isNotBlank()
        val reqAtividade = _atividades.value.isNotBlank()
        val reqConclusao = _conclusaoVisita.value.isNotBlank()

        return when (etapa) {
            1 -> {
                if (!reqConstatacao) Pair(false, "Constatações de campo são obrigatórias.")
                else Pair(true, "")
            }
            2 -> {
                val exigeAtividade = motivo != "duvidas" && motivo != "programacao_feiras"
                if (exigeAtividade && !reqAtividade) {
                    Pair(false, "Atividades realizadas são obrigatórias para este motivo.")
                } else Pair(true, "")
            }
            3 -> {
                if (!reqConclusao) Pair(false, "A Conclusão da Visita técnica é obrigatória.")
                else Pair(true, "")
            }
            4 -> {
                val exigeAssinaturas = motivo != "programacao_feiras"
                if (exigeAssinaturas) {
                    if (_caminhoAssinaturaCliente.value == null) {
                        Pair(false, "A Assinatura do Cliente é obrigatória.")
                    } else if (_caminhoAssinaturaEspecialista.value == null) {
                        Pair(false, "A Assinatura do Especialista é obrigatória.")
                    } else Pair(true, "")
                } else Pair(true, "")
            }
            else -> Pair(true, "")
        }
    }

    fun salvarRelatorio(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val sucesso = repositorio.salvarRascunho(
                motivo = _motivoAtendimento.value,
                constatacoes = _constatacoes.value,
                atividades = _atividades.value,
                planoAcao = _planoAcao.value,
                conclusao = _conclusaoVisita.value,
                fotos = _fotosConstatacao.value,
                videos = _videosAtividade.value,
                anexos = _anexosExtras.value,
                assinaturaCliente = _caminhoAssinaturaCliente.value,
                assinaturaEspecialista = _caminhoAssinaturaEspecialista.value
            )
            onResult(sucesso)
        }
    }
}