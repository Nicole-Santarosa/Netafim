package com.example.netafim.ui.relatorio

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RelatorioViewModel : ViewModel() {

    private val _motivoAtendimento = MutableStateFlow("pos_venda")
    val motivoAtendimento: StateFlow<String> = _motivoAtendimento.asStateFlow()

    private val _constatacoes = MutableStateFlow("")
    val constatacoes: StateFlow<String> = _constatacoes.asStateFlow()

    private val _atividades = MutableStateFlow("")
    val atividades: StateFlow<String> = _atividades.asStateFlow()

    private val _planoAcao = MutableStateFlow("")
    val planoAcao: StateFlow<String> = _planoAcao.asStateFlow()

    // Armazena os caminhos absolutos das fotos tiradas pela câmera obrigatória
    private val _fotosConstatacao = MutableStateFlow<List<String>>(emptyList())
    val fotosConstatacao: StateFlow<List<String>> = _fotosConstatacao.asStateFlow()

    // Armazena o caminho do arquivo de assinatura digital gerado
    private val _caminhoAssinatura = MutableStateFlow<String?>(null)
    val caminhoAssinatura: StateFlow<String?> = _caminhoAssinatura.asStateFlow()

    fun setMotivo(motivo: String) {
        _motivoAtendimento.value = motivo
    }

    fun atualizarConstatacoes(valor: String) {
        _constatacoes.value = valor
    }

    fun atualizarAtividades(valor: String) {
        _atividades.value = valor
    }

    fun atualizarPlanoAcao(valor: String) {
        _planoAcao.value = valor
    }

    fun adicionarFotoConstatacao(caminho: String) {
        _fotosConstatacao.value = _fotosConstatacao.value + caminho
    }

    fun definirAssinatura(caminho: String) {
        _caminhoAssinatura.value = caminho
    }

    fun validarRelatorio(): Boolean {
        return _constatacoes.value.isNotBlank() &&
                _atividades.value.isNotBlank() &&
                _caminhoAssinatura.value != null
    }
}