package com.netafim.posvenda.acesso.ui.usuarios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netafim.posvenda.acesso.dominio.AcoesDoUsuario
import com.netafim.posvenda.acesso.dominio.ErroAcesso
import com.netafim.posvenda.acesso.dominio.Perfil
import com.netafim.posvenda.acesso.dominio.RepositorioAcesso
import com.netafim.posvenda.acesso.dominio.RepositorioUsuarios
import com.netafim.posvenda.acesso.dominio.Resultado
import com.netafim.posvenda.acesso.dominio.SituacoesUsuario
import com.netafim.posvenda.acesso.dominio.Usuario
import com.netafim.posvenda.acesso.dominio.acoesPermitidas
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Ação que pede confirmação antes de ir para a API. */
enum class ConfirmacaoUsuario { DESATIVAR, EXCLUIR }

/** Mensagem de sucesso que a tela mostra uma vez. */
enum class AvisoUsuario { ATIVADO, DESATIVADO, PERFIL_ALTERADO, CONVITE_REENVIADO }

data class UsuarioDetalheEstado(
    val usuario: Usuario? = null,
    val acoes: AcoesDoUsuario = AcoesDoUsuario(),
    val carregando: Boolean = true,
    /** Falha ao carregar o usuário (a tela oferece tentar de novo). */
    val erro: String? = null,
    /** Uma ação está em andamento: os botões ficam desabilitados. */
    val processando: Boolean = false,
    /** Falha de uma ação (ativar, excluir...), mostrada acima dos botões. */
    val erroAcao: String? = null,
    val aviso: AvisoUsuario? = null,
    val confirmando: ConfirmacaoUsuario? = null,
    /** Folha de escolha do perfil aberta; [perfis] só traz os do tipo de organização da pessoa. */
    val escolhendoPerfil: Boolean = false,
    val perfis: List<Perfil> = emptyList(),
    val carregandoPerfis: Boolean = false,
    val erroPerfis: String? = null,
    /** O usuário foi excluído: a tela volta para a lista. */
    val excluido: Boolean = false,
)

/**
 * Detalhe de um usuário para quem administra: dados, ativar ou desativar, definir o perfil, reenviar o
 * convite e excluir. Os botões seguem [acoesPermitidas]; a API continua sendo quem decide e as recusas
 * dela (por exemplo, "último Netafim Admin") aparecem como mensagem.
 */
class UsuarioDetalheViewModel(
    private val repositorio: RepositorioUsuarios,
    private val acesso: RepositorioAcesso,
    private val idUsuario: Int,
) : ViewModel() {

    private val _estado = MutableStateFlow(UsuarioDetalheEstado())
    val estado: StateFlow<UsuarioDetalheEstado> = _estado.asStateFlow()

    private var jaCarregou = false

    init {
        carregar()
    }

    /** Ao voltar de outra tela (por exemplo, a edição) atualiza os dados sem piscar o carregamento. */
    fun aoRetomar() {
        if (!jaCarregou || _estado.value.processando) return
        // O aviso de uma ação anterior já cumpriu o papel: não deve reaparecer ao voltar de outra tela.
        _estado.update { it.copy(aviso = null) }
        viewModelScope.launch {
            val r = repositorio.obter(idUsuario)
            if (r is Resultado.Sucesso) mostrar(r.valor)
        }
    }

    fun carregar() {
        _estado.update { it.copy(carregando = true, erro = null) }
        viewModelScope.launch {
            when (val r = repositorio.obter(idUsuario)) {
                is Resultado.Sucesso -> mostrar(r.valor)
                is Resultado.Falha -> _estado.update { it.copy(carregando = false, erro = r.erro.mensagem) }
            }
        }
    }

    private fun mostrar(usuario: Usuario) {
        jaCarregou = true
        val ator = acesso.sessao.value?.usuario
        _estado.update {
            it.copy(
                usuario = usuario,
                acoes = if (ator != null) acoesPermitidas(ator, usuario) else AcoesDoUsuario(),
                carregando = false,
                erro = null,
                processando = false,
            )
        }
    }

    fun ativar() = alterarSituacao(SituacoesUsuario.ATIVO, AvisoUsuario.ATIVADO)

    fun pedirDesativar() = _estado.update { it.copy(confirmando = ConfirmacaoUsuario.DESATIVAR, erroAcao = null) }

    fun pedirExcluir() = _estado.update { it.copy(confirmando = ConfirmacaoUsuario.EXCLUIR, erroAcao = null) }

    fun cancelarConfirmacao() = _estado.update { if (it.processando) it else it.copy(confirmando = null) }

    fun confirmar() {
        when (_estado.value.confirmando) {
            ConfirmacaoUsuario.DESATIVAR -> alterarSituacao(SituacoesUsuario.INATIVO, AvisoUsuario.DESATIVADO)
            ConfirmacaoUsuario.EXCLUIR -> excluir()
            null -> Unit
        }
    }

    private fun alterarSituacao(situacao: String, aviso: AvisoUsuario) {
        if (_estado.value.processando) return
        _estado.update { it.copy(processando = true, erroAcao = null, aviso = null) }
        viewModelScope.launch {
            when (val r = repositorio.alterarSituacao(idUsuario, situacao)) {
                is Resultado.Sucesso -> {
                    mostrar(r.valor)
                    _estado.update { it.copy(confirmando = null, aviso = aviso) }
                }
                is Resultado.Falha -> falhaDeAcao(r.erro)
            }
        }
    }

    private fun excluir() {
        if (_estado.value.processando) return
        _estado.update { it.copy(processando = true, erroAcao = null, aviso = null) }
        viewModelScope.launch {
            when (val r = repositorio.excluir(idUsuario)) {
                is Resultado.Sucesso -> _estado.update { it.copy(processando = false, confirmando = null, excluido = true) }
                is Resultado.Falha -> falhaDeAcao(r.erro)
            }
        }
    }

    fun reenviarConvite() {
        if (_estado.value.processando) return
        _estado.update { it.copy(processando = true, erroAcao = null, aviso = null) }
        viewModelScope.launch {
            when (val r = repositorio.reenviarConvite(idUsuario)) {
                is Resultado.Sucesso -> _estado.update { it.copy(processando = false, aviso = AvisoUsuario.CONVITE_REENVIADO) }
                is Resultado.Falha -> falhaDeAcao(r.erro)
            }
        }
    }

    // ------------------------------------------------------------------ perfil

    fun abrirPerfis() {
        val usuario = _estado.value.usuario ?: return
        _estado.update { it.copy(escolhendoPerfil = true, carregandoPerfis = true, erroPerfis = null, erroAcao = null) }
        viewModelScope.launch {
            when (val r = repositorio.perfis()) {
                is Resultado.Sucesso -> {
                    val tipo = usuario.organizacao?.tipo ?: usuario.perfil?.tipoOrganizacao
                    _estado.update {
                        it.copy(carregandoPerfis = false, perfis = r.valor.filter { p -> p.tipoOrganizacao == tipo })
                    }
                }
                is Resultado.Falha -> _estado.update { it.copy(carregandoPerfis = false, erroPerfis = r.erro.mensagem) }
            }
        }
    }

    fun fecharPerfis() = _estado.update { if (it.processando) it else it.copy(escolhendoPerfil = false) }

    fun escolherPerfil(idPerfil: Int) {
        val atual = _estado.value
        if (atual.processando || atual.usuario?.perfil?.id == idPerfil) return
        _estado.update { it.copy(processando = true, erroPerfis = null) }
        viewModelScope.launch {
            when (val r = repositorio.definirPerfil(idUsuario, idPerfil)) {
                is Resultado.Sucesso -> {
                    mostrar(r.valor)
                    _estado.update { it.copy(escolhendoPerfil = false, aviso = AvisoUsuario.PERFIL_ALTERADO) }
                }
                // A recusa (por exemplo, rebaixar o último Netafim Admin) aparece dentro da folha aberta.
                is Resultado.Falha -> _estado.update { it.copy(processando = false, erroPerfis = r.erro.mensagem) }
            }
        }
    }

    fun avisoMostrado() = _estado.update { it.copy(aviso = null) }

    private fun falhaDeAcao(erro: ErroAcesso) =
        _estado.update { it.copy(processando = false, confirmando = null, erroAcao = erro.mensagem) }
}
