package com.netafim.posvenda.acesso.ui.senha

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netafim.posvenda.acesso.dominio.RepositorioAcesso
import com.netafim.posvenda.acesso.dominio.Resultado
import com.netafim.posvenda.acesso.dominio.Validacao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EsqueciSenhaEstado(
    val email: String = "",
    val erroEmail: String? = null,
    val erroGeral: String? = null,
    val carregando: Boolean = false,
    val enviado: String? = null,
)

class EsqueciSenhaViewModel(private val repositorio: RepositorioAcesso) : ViewModel() {

    private val _estado = MutableStateFlow(EsqueciSenhaEstado())
    val estado: StateFlow<EsqueciSenhaEstado> = _estado.asStateFlow()

    fun aoMudarEmail(valor: String) =
        _estado.update { it.copy(email = valor, erroEmail = null, erroGeral = null) }

    fun enviar() {
        val atual = _estado.value
        if (atual.carregando) return
        Validacao.email(atual.email)?.let { erro ->
            _estado.update { it.copy(erroEmail = erro) }
            return
        }
        _estado.update { it.copy(carregando = true) }
        viewModelScope.launch {
            when (val r = repositorio.esqueciSenha(atual.email)) {
                is Resultado.Sucesso -> _estado.update { it.copy(carregando = false, enviado = r.valor) }
                is Resultado.Falha -> _estado.update {
                    it.copy(carregando = false, erroGeral = r.erro.mensagem)
                }
            }
        }
    }
}
