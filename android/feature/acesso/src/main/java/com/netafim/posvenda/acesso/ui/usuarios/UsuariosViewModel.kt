package com.netafim.posvenda.acesso.ui.usuarios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netafim.posvenda.acesso.dominio.DadosNovoUsuario
import com.netafim.posvenda.acesso.dominio.ErroAcesso
import com.netafim.posvenda.acesso.dominio.Permissoes
import com.netafim.posvenda.acesso.dominio.RepositorioAcesso
import com.netafim.posvenda.acesso.dominio.RepositorioUsuarios
import com.netafim.posvenda.acesso.dominio.Resultado
import com.netafim.posvenda.acesso.dominio.TiposOrganizacao
import com.netafim.posvenda.acesso.dominio.Usuario
import com.netafim.posvenda.acesso.dominio.Validacao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Quem vê a Gestão de usuários: quem tem a permissão de criar usuários. Convidar pelo app é só
 * para admin de distribuidor e de cliente: a API deduz a organização e o perfil deles. O Netafim
 * Admin precisa escolher organização e perfil, e esse passo não existe no protótipo.
 */
fun Usuario.podeGerenciarUsuarios(): Boolean = temPermissao(Permissoes.CRIAR_USUARIO)

fun Usuario.podeConvidarUsuarios(): Boolean =
    podeGerenciarUsuarios() && perfil?.tipoOrganizacao != TiposOrganizacao.NETAFIM

data class UsuariosEstado(
    val carregando: Boolean = true,
    val usuarios: List<Usuario> = emptyList(),
    val erro: String? = null,
    val podeAdicionar: Boolean = false,
    /** Nome da organização de quem está logado (distribuidor ou cliente), para o resumo da tela. */
    val organizacao: String? = null,
)

class UsuariosViewModel(
    private val repositorio: RepositorioUsuarios,
    acesso: RepositorioAcesso,
) : ViewModel() {

    private val _estado = MutableStateFlow(
        UsuariosEstado(
            podeAdicionar = acesso.sessao.value?.usuario?.podeConvidarUsuarios() == true,
            organizacao = acesso.sessao.value?.usuario?.organizacao?.nome?.takeIf { it.isNotBlank() },
        ),
    )
    val estado: StateFlow<UsuariosEstado> = _estado.asStateFlow()

    private var jaRetomou = false

    init {
        carregar()
    }

    fun carregar() {
        _estado.update { it.copy(carregando = true, erro = null) }
        viewModelScope.launch {
            when (val r = repositorio.listar()) {
                is Resultado.Sucesso -> _estado.update { it.copy(carregando = false, usuarios = r.valor) }
                is Resultado.Falha -> _estado.update { it.copy(carregando = false, erro = r.erro.mensagem) }
            }
        }
    }

    /**
     * Chamado quando a tela volta ao primeiro plano (ex.: depois de convidar alguém): atualiza a
     * lista sem piscar. A primeira chamada é a da própria abertura, que já carregou.
     */
    fun aoRetomar() {
        if (!jaRetomou) {
            jaRetomou = true
            return
        }
        viewModelScope.launch {
            val r = repositorio.listar()
            if (r is Resultado.Sucesso) _estado.update { it.copy(usuarios = r.valor, erro = null) }
        }
    }
}

enum class CampoNovoUsuario { NOME, EMAIL, TELEFONE }

data class NovoUsuarioEstado(
    val nome: String = "",
    val email: String = "",
    val telefone: String = "",
    val erros: Map<CampoNovoUsuario, String> = emptyMap(),
    val erroGeral: String? = null,
    val carregando: Boolean = false,
    /** Convite enviado: a tela avisa a navegação para voltar à lista. */
    val criado: Boolean = false,
)

class NovoUsuarioViewModel(private val repositorio: RepositorioUsuarios) : ViewModel() {

    private val _estado = MutableStateFlow(NovoUsuarioEstado())
    val estado: StateFlow<NovoUsuarioEstado> = _estado.asStateFlow()

    fun aoMudar(campo: CampoNovoUsuario, valor: String) = _estado.update {
        val novo = when (campo) {
            CampoNovoUsuario.NOME -> it.copy(nome = valor)
            CampoNovoUsuario.EMAIL -> it.copy(email = valor)
            CampoNovoUsuario.TELEFONE -> it.copy(telefone = valor)
        }
        novo.copy(erros = it.erros - campo, erroGeral = null)
    }

    fun enviar() {
        val atual = _estado.value
        if (atual.carregando) return
        val erros = buildMap {
            Validacao.nome(atual.nome)?.let { put(CampoNovoUsuario.NOME, it) }
            Validacao.email(atual.email)?.let { put(CampoNovoUsuario.EMAIL, it) }
            Validacao.telefone(atual.telefone)?.let { put(CampoNovoUsuario.TELEFONE, it) }
        }
        if (erros.isNotEmpty()) {
            _estado.update { it.copy(erros = erros) }
            return
        }
        _estado.update { it.copy(carregando = true, erroGeral = null) }
        viewModelScope.launch {
            val dados = DadosNovoUsuario(
                nome = atual.nome,
                email = atual.email,
                telefone = Validacao.somenteDigitos(atual.telefone).ifEmpty { null },
            )
            when (val r = repositorio.criar(dados)) {
                is Resultado.Sucesso -> _estado.update { it.copy(carregando = false, criado = true) }
                is Resultado.Falha -> _estado.update { aplicarErro(it, r.erro) }
            }
        }
    }

    private fun aplicarErro(e: NovoUsuarioEstado, erro: ErroAcesso): NovoUsuarioEstado {
        val base = e.copy(carregando = false)
        if (erro is ErroAcesso.Campos) {
            val porCampo = erro.campos.mapNotNull { (campo, msg) -> CAMPOS_API[campo]?.let { it to msg } }.toMap()
            return base.copy(erros = porCampo, erroGeral = if (porCampo.isEmpty()) erro.mensagem else null)
        }
        // "email_em_uso" e as regras de organização chegam como erro de negócio, com a mensagem pronta.
        return base.copy(erroGeral = erro.mensagem)
    }

    private companion object {
        val CAMPOS_API = mapOf(
            "nome" to CampoNovoUsuario.NOME,
            "email" to CampoNovoUsuario.EMAIL,
            "telefone" to CampoNovoUsuario.TELEFONE,
        )
    }
}
