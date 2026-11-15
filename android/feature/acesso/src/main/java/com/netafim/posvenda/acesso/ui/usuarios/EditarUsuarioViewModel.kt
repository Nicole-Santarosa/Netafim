package com.netafim.posvenda.acesso.ui.usuarios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netafim.posvenda.acesso.dominio.ErroAcesso
import com.netafim.posvenda.acesso.dominio.RepositorioUsuarios
import com.netafim.posvenda.acesso.dominio.Resultado
import com.netafim.posvenda.acesso.dominio.Usuario
import com.netafim.posvenda.acesso.dominio.Validacao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CampoEditarUsuario { NOME, TELEFONE }

data class EditarUsuarioEstado(
    val email: String = "",
    val nome: String = "",
    val telefone: String = "",
    val erros: Map<CampoEditarUsuario, String> = emptyMap(),
    val erroGeral: String? = null,
    /** Carregando os dados atuais para preencher os campos. */
    val carregando: Boolean = true,
    /** Só vale com os dados carregados: sem eles não há o que editar. */
    val carregado: Boolean = false,
    val salvando: Boolean = false,
    /** Alterações salvas: a tela volta para o detalhe. */
    val salvo: Boolean = false,
)

/**
 * Corrige nome e telefone de um usuário (`PATCH usuarios/{id}`). Preenche os campos com os dados
 * atuais, valida no aparelho e só envia quando algo mudou. O e-mail aparece só para leitura.
 */
class EditarUsuarioViewModel(
    private val repositorio: RepositorioUsuarios,
    private val idUsuario: Int,
) : ViewModel() {

    private val _estado = MutableStateFlow(EditarUsuarioEstado())
    val estado: StateFlow<EditarUsuarioEstado> = _estado.asStateFlow()

    private var original: Usuario? = null

    init {
        carregar()
    }

    fun carregar() {
        _estado.update { it.copy(carregando = true, erroGeral = null) }
        viewModelScope.launch {
            when (val r = repositorio.obter(idUsuario)) {
                is Resultado.Sucesso -> {
                    original = r.valor
                    _estado.update {
                        it.copy(
                            email = r.valor.email,
                            nome = r.valor.nome,
                            telefone = r.valor.telefone.orEmpty(),
                            carregando = false,
                            carregado = true,
                        )
                    }
                }
                is Resultado.Falha -> _estado.update { it.copy(carregando = false, erroGeral = r.erro.mensagem) }
            }
        }
    }

    fun aoMudar(campo: CampoEditarUsuario, valor: String) = _estado.update {
        val novo = when (campo) {
            CampoEditarUsuario.NOME -> it.copy(nome = valor)
            CampoEditarUsuario.TELEFONE -> it.copy(telefone = valor)
        }
        novo.copy(erros = it.erros - campo, erroGeral = null)
    }

    fun salvar() {
        val atual = _estado.value
        if (atual.salvando || !atual.carregado) return
        val erros = buildMap {
            Validacao.nome(atual.nome)?.let { put(CampoEditarUsuario.NOME, it) }
            Validacao.telefone(atual.telefone)?.let { put(CampoEditarUsuario.TELEFONE, it) }
        }
        if (erros.isNotEmpty()) {
            _estado.update { it.copy(erros = erros) }
            return
        }
        val nome = atual.nome.trim()
        val telefone = Validacao.somenteDigitos(atual.telefone)
        val base = original
        // Nada mudou: não há o que enviar, a tela só volta.
        if (base != null && nome == base.nome && telefone == Validacao.somenteDigitos(base.telefone.orEmpty())) {
            _estado.update { it.copy(salvo = true) }
            return
        }
        _estado.update { it.copy(salvando = true, erroGeral = null) }
        viewModelScope.launch {
            when (val r = repositorio.editar(idUsuario, nome, telefone)) {
                is Resultado.Sucesso -> _estado.update { it.copy(salvando = false, salvo = true) }
                is Resultado.Falha -> _estado.update { aplicarErro(it, r.erro) }
            }
        }
    }

    private fun aplicarErro(e: EditarUsuarioEstado, erro: ErroAcesso): EditarUsuarioEstado {
        val base = e.copy(salvando = false)
        if (erro is ErroAcesso.Campos) {
            val porCampo = erro.campos.mapNotNull { (campo, msg) -> CAMPOS_API[campo]?.let { it to msg } }.toMap()
            return base.copy(erros = porCampo, erroGeral = if (porCampo.isEmpty()) erro.mensagem else null)
        }
        return base.copy(erroGeral = erro.mensagem)
    }

    private companion object {
        val CAMPOS_API = mapOf("nome" to CampoEditarUsuario.NOME, "telefone" to CampoEditarUsuario.TELEFONE)
    }
}
