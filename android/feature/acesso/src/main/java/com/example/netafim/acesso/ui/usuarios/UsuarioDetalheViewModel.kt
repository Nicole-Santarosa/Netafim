package com.example.netafim.acesso.ui.usuarios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.netafim.acesso.dominio.AcoesDoUsuario
import com.example.netafim.acesso.dominio.Aparelho
import com.example.netafim.acesso.dominio.ErroAcesso
import com.example.netafim.acesso.dominio.Perfil
import com.example.netafim.acesso.dominio.RepositorioAcesso
import com.example.netafim.acesso.dominio.RepositorioUsuarios
import com.example.netafim.acesso.dominio.Resultado
import com.example.netafim.acesso.dominio.SituacoesUsuario
import com.example.netafim.acesso.dominio.Usuario
import com.example.netafim.acesso.dominio.acoesPermitidas
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Ação que pede confirmação antes de ir para a API. */
enum class ConfirmacaoUsuario { DESATIVAR, EXCLUIR, REDEFINIR_MFA }

/** Mensagem de sucesso que a tela mostra uma vez. */
enum class AvisoUsuario { ATIVADO, DESATIVADO, PERFIL_ALTERADO, CONVITE_REENVIADO, MFA_REDEFINIDO, APARELHO_REVOGADO }

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
    /** Folha com os aparelhos da pessoa aberta. */
    val aparelhosAbertos: Boolean = false,
    val aparelhos: List<Aparelho> = emptyList(),
    val carregandoAparelhos: Boolean = false,
    val erroAparelhos: String? = null,
    /** Aparelho cuja revogação está esperando a confirmação. */
    val aparelhoParaRevogar: Aparelho? = null,
    val revogando: Boolean = false,
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

    fun pedirRedefinirMfa() = _estado.update { it.copy(confirmando = ConfirmacaoUsuario.REDEFINIR_MFA, erroAcao = null) }

    fun cancelarConfirmacao() = _estado.update { if (it.processando) it else it.copy(confirmando = null) }

    fun confirmar() {
        when (_estado.value.confirmando) {
            ConfirmacaoUsuario.DESATIVAR -> alterarSituacao(SituacoesUsuario.INATIVO, AvisoUsuario.DESATIVADO)
            ConfirmacaoUsuario.EXCLUIR -> excluir()
            ConfirmacaoUsuario.REDEFINIR_MFA -> redefinirMfa()
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

    private fun redefinirMfa() {
        if (_estado.value.processando) return
        _estado.update { it.copy(processando = true, erroAcao = null, aviso = null) }
        viewModelScope.launch {
            when (val r = repositorio.redefinirMfa(idUsuario)) {
                is Resultado.Sucesso -> _estado.update {
                    it.copy(processando = false, confirmando = null, aviso = AvisoUsuario.MFA_REDEFINIDO)
                }
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

    // ------------------------------------------------------------------ aparelhos

    fun abrirAparelhos() {
        _estado.update { it.copy(aparelhosAbertos = true, carregandoAparelhos = true, erroAparelhos = null, erroAcao = null) }
        viewModelScope.launch {
            when (val r = repositorio.aparelhos(idUsuario)) {
                is Resultado.Sucesso -> _estado.update { it.copy(carregandoAparelhos = false, aparelhos = r.valor) }
                is Resultado.Falha -> _estado.update { it.copy(carregandoAparelhos = false, erroAparelhos = r.erro.mensagem) }
            }
        }
    }

    fun fecharAparelhos() = _estado.update {
        if (it.revogando) it else it.copy(aparelhosAbertos = false, aparelhoParaRevogar = null)
    }

    fun pedirRevogarAparelho(aparelho: Aparelho) {
        if (aparelho.revogado) return
        _estado.update { it.copy(aparelhoParaRevogar = aparelho, erroAparelhos = null) }
    }

    fun cancelarRevogarAparelho() = _estado.update { if (it.revogando) it else it.copy(aparelhoParaRevogar = null) }

    fun confirmarRevogarAparelho() {
        val aparelho = _estado.value.aparelhoParaRevogar ?: return
        if (_estado.value.revogando) return
        _estado.update { it.copy(revogando = true, erroAparelhos = null) }
        viewModelScope.launch {
            when (val r = repositorio.revogarAparelho(aparelho.id)) {
                is Resultado.Sucesso -> _estado.update { atual ->
                    atual.copy(
                        revogando = false,
                        aparelhoParaRevogar = null,
                        aparelhos = atual.aparelhos.map { if (it.id == aparelho.id) r.valor else it },
                        aviso = AvisoUsuario.APARELHO_REVOGADO,
                    )
                }
                // A recusa da API aparece dentro da folha aberta.
                is Resultado.Falha -> _estado.update { it.copy(revogando = false, aparelhoParaRevogar = null, erroAparelhos = r.erro.mensagem) }
            }
        }
    }

    fun avisoMostrado() = _estado.update { it.copy(aviso = null) }

    private fun falhaDeAcao(erro: ErroAcesso) =
        _estado.update { it.copy(processando = false, confirmando = null, erroAcao = erro.mensagem) }
}
