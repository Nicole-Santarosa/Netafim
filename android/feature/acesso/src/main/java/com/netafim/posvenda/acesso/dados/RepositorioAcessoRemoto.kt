package com.netafim.posvenda.acesso.dados

import com.netafim.posvenda.acesso.dominio.DadosCadastro
import com.netafim.posvenda.acesso.dominio.ErroAcesso
import com.netafim.posvenda.acesso.dominio.RepositorioAcesso
import com.netafim.posvenda.acesso.dominio.Resultado
import com.netafim.posvenda.acesso.dominio.Sessao
import com.netafim.posvenda.acesso.dominio.Usuario
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import retrofit2.HttpException

/**
 * Implementação do [RepositorioAcesso]. Guarda a sessão no aparelho e renova o
 * access token quando ele expira (ver [RenovacaoAutenticador]).
 *
 * @param apiPublica cliente sem token (login, refresh, cadastro).
 * @param apiAutenticada cliente com o token da sessão (me, logout).
 */
class RepositorioAcessoRemoto(
    private val apiPublica: AcessoApi,
    private val apiAutenticada: AcessoApi,
    private val armazenamento: ArmazenamentoSessao,
    private val json: Json,
    private val aparelho: InfoAparelho,
) : RepositorioAcesso, ProvedorTokens {

    private val _sessao = MutableStateFlow<Sessao?>(null)
    override val sessao: StateFlow<Sessao?> = _sessao.asStateFlow()

    private val travaRenovacao = Mutex()

    /** Lê a sessão guardada. Chamado na abertura do app; não usa a internet. */
    suspend fun restaurar(): Sessao? {
        val guardada = armazenamento.ler() ?: return null
        val usuario = runCatching {
            json.decodeFromString(UsuarioDto.serializer(), guardada.usuarioJson).paraDominio()
        }.getOrNull()
        if (usuario == null) {
            armazenamento.limpar()
            return null
        }
        return Sessao(guardada.accessToken, guardada.refreshToken, usuario).also { _sessao.value = it }
    }

    override suspend fun entrar(email: String, senha: String): Resultado<Usuario> =
        executar {
            val tokens = try {
                apiPublica.login(requisicaoLogin(email, senha))
            } catch (erro: HttpException) {
                // Outro usuário já registrou este identificador: gera outro e tenta de novo.
                if (ErrosHttp.codigoDe(erro, json) != "dispositivo_de_outro_usuario") throw erro
                armazenamento.novoIdDispositivo()
                apiPublica.login(requisicaoLogin(email, senha))
            }
            guardar(tokens).usuario
        }

    private suspend fun requisicaoLogin(email: String, senha: String) = LoginRequisicao(
        email = email.trim(),
        senha = senha,
        dispositivo = DispositivoDto(
            idDispositivo = armazenamento.idDispositivo(),
            modelo = aparelho.modelo,
            versaoApp = aparelho.versaoApp,
        ),
    )

    override suspend fun sair() {
        val atual = _sessao.value
        if (atual != null) {
            // Revoga no servidor se houver conexão; sem conexão, o refresh expira sozinho.
            runCatching { apiAutenticada.logout(RefreshRequisicao(atual.refreshToken)) }
                .onFailure { if (it is CancellationException) throw it }
        }
        encerrarLocalmente()
    }

    override suspend fun atualizarPerfil(): Resultado<Usuario> {
        if (_sessao.value == null) return Resultado.Falha(ErroAcesso.SessaoExpirada)
        return executar {
            val dto = apiAutenticada.me()
            // A sessão pode ter mudado (renovada ou encerrada) durante a chamada.
            val atual = _sessao.value ?: return Resultado.Falha(ErroAcesso.SessaoExpirada)
            armazenamento.salvar(
                SessaoGuardada(
                    accessToken = atual.accessToken,
                    refreshToken = atual.refreshToken,
                    usuarioJson = json.encodeToString(UsuarioDto.serializer(), dto),
                ),
            )
            dto.paraDominio().also { _sessao.value = atual.copy(usuario = it) }
        }
    }

    override suspend fun versaoTermoVigente(): Resultado<String> =
        executar { apiPublica.termo().versaoVigente }

    override suspend fun solicitarCadastro(dados: DadosCadastro): Resultado<String> = executar {
        apiPublica.solicitarCadastro(
            CadastroRequisicao(
                nome = dados.nome.trim(),
                email = dados.email.trim(),
                telefone = dados.telefone?.takeIf { it.isNotBlank() },
                senha = dados.senha,
                aceiteTermo = true,
                versaoTermo = dados.versaoTermo,
            ),
        ).detail
    }

    override suspend fun esqueciSenha(email: String): Resultado<String> =
        executar { apiPublica.esqueciSenha(EsqueciSenhaRequisicao(email.trim())).detail }

    // ------------------------------------------------------------- ProvedorTokens

    override fun accessTokenAtual(): String? = _sessao.value?.accessToken

    override suspend fun renovar(accessUsado: String?): String? = travaRenovacao.withLock {
        val atual = _sessao.value ?: return null
        // Outra requisição já renovou enquanto esta esperava.
        if (accessUsado != null && atual.accessToken != accessUsado) return atual.accessToken
        try {
            guardar(apiPublica.renovar(RefreshRequisicao(atual.refreshToken))).accessToken
        } catch (erro: HttpException) {
            if (erro.code() == 401) encerrarLocalmente()
            null
        } catch (erro: CancellationException) {
            throw erro
        } catch (erro: Exception) {
            null // sem conexão: mantém a sessão para tentar mais tarde
        }
    }

    // ------------------------------------------------------------- interno

    private suspend fun guardar(tokens: TokensResposta): Sessao {
        armazenamento.salvar(
            SessaoGuardada(
                accessToken = tokens.access,
                refreshToken = tokens.refresh,
                usuarioJson = json.encodeToString(UsuarioDto.serializer(), tokens.usuario),
            ),
        )
        return Sessao(tokens.access, tokens.refresh, tokens.usuario.paraDominio())
            .also { _sessao.value = it }
    }

    private suspend fun encerrarLocalmente() {
        armazenamento.limpar()
        _sessao.value = null
    }

    // inline: permite "return" antecipado dentro do bloco.
    private suspend inline fun <T> executar(bloco: () -> T): Resultado<T> = try {
        Resultado.Sucesso(bloco())
    } catch (erro: CancellationException) {
        throw erro
    } catch (erro: Exception) {
        Resultado.Falha(ErrosHttp.mapear(erro, json))
    }
}

/** Informações do aparelho enviadas no login (tabela DISPOSITIVO). */
data class InfoAparelho(val modelo: String?, val versaoApp: String?)
