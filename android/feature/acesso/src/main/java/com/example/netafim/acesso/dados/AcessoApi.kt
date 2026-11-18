package com.example.netafim.acesso.dados

import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.PUT
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Endpoints do módulo de acesso (prefixo /api/v1/). */
interface AcessoApi {
    @POST("auth/login")
    suspend fun login(@Body corpo: LoginRequisicao): LoginResposta

    @POST("auth/mfa/verificar")
    suspend fun mfaVerificar(@Body corpo: MfaVerificarRequisicao): TokensResposta

    @GET("me/mfa")
    suspend fun mfaEstado(): MfaEstadoResposta

    @POST("me/mfa/iniciar")
    suspend fun mfaIniciar(): MfaIniciadoResposta

    @POST("me/mfa/confirmar")
    suspend fun mfaConfirmar(@Body corpo: MfaCodigoRequisicao): MfaConfirmadoResposta

    @POST("me/senha")
    suspend fun alterarSenha(@Body corpo: AlterarSenhaRequisicao)

    @POST("me/mfa/desativar")
    suspend fun mfaDesativar(@Body corpo: MfaDesativarRequisicao)

    @POST("auth/refresh")
    suspend fun renovar(@Body corpo: RefreshRequisicao): TokensResposta

    @POST("auth/logout")
    suspend fun logout(@Body corpo: RefreshRequisicao)

    @GET("auth/termo")
    suspend fun termo(): TermoResposta

    @POST("auth/cadastro")
    suspend fun solicitarCadastro(@Body corpo: CadastroRequisicao): MensagemResposta

    @POST("auth/senha/esqueci")
    suspend fun esqueciSenha(@Body corpo: EsqueciSenhaRequisicao): MensagemResposta

    @POST("auth/convite/aceitar")
    suspend fun aceitarConvite(@Body corpo: AceitarConviteRequisicao): MensagemResposta

    @POST("auth/senha/redefinir")
    suspend fun redefinirSenha(@Body corpo: RedefinirSenhaRequisicao): MensagemResposta

    @GET("me")
    suspend fun me(): UsuarioDto

    // ---- Direitos do titular (LGPD)

    @GET("me/solicitacoes-lgpd")
    suspend fun meusPedidos(): List<PedidoTitularDto>

    @POST("me/solicitacoes-lgpd")
    suspend fun abrirPedido(@Body corpo: PedidoNovoRequisicao): PedidoTitularDto

    /** Corpo cru: a cópia é entregue como JSON para a pessoa, sem o app interpretar cada campo. */
    @GET("me/dados")
    suspend fun meusDados(): ResponseBody

    // ---- Fila de pedidos de titulares (Netafim Admin)

    @GET("solicitacoes-lgpd")
    suspend fun filaLgpd(): List<PedidoAdminDto>

    @POST("solicitacoes-lgpd/{id}/analisar")
    suspend fun analisarPedido(@Path("id") id: Int): PedidoAdminDto

    @POST("solicitacoes-lgpd/{id}/atender")
    suspend fun atenderPedido(@Path("id") id: Int): PedidoAdminDto

    @POST("solicitacoes-lgpd/{id}/negar")
    suspend fun negarPedido(@Path("id") id: Int, @Body corpo: NegarPedidoRequisicao): PedidoAdminDto

    @GET("solicitacoes-lgpd/{id}/dados")
    suspend fun dadosDoTitular(@Path("id") id: Int): ResponseBody

    // ---- Cadastros pendentes (Netafim Admin; permissão aprovar_cadastro)

    @GET("usuarios")
    suspend fun usuarios(
        @Query("situacao") situacao: String?,
        @Query("page") pagina: Int,
    ): PaginaDto<UsuarioDto>

    @POST("usuarios")
    suspend fun criarUsuario(@Body corpo: CriarUsuarioRequisicao): UsuarioDto

    @GET("usuarios/{id}")
    suspend fun usuario(@Path("id") id: Int): UsuarioDto

    @PATCH("usuarios/{id}")
    suspend fun editarUsuario(@Path("id") id: Int, @Body corpo: EditarUsuarioRequisicao): UsuarioDto

    @POST("usuarios/{id}/situacao")
    suspend fun situacaoUsuario(@Path("id") id: Int, @Body corpo: SituacaoRequisicao): UsuarioDto

    @POST("usuarios/{id}/perfil")
    suspend fun perfilUsuario(@Path("id") id: Int, @Body corpo: DefinirPerfilRequisicao): UsuarioDto

    @POST("usuarios/{id}/reenviar-convite")
    suspend fun reenviarConvite(@Path("id") id: Int)

    @DELETE("usuarios/{id}")
    suspend fun excluirUsuario(@Path("id") id: Int)

    /** Netafim Admin remove o MFA de quem perdeu o aparelho e os códigos (204); encerra as sessões da pessoa. */
    @POST("usuarios/{id}/mfa/resetar")
    suspend fun resetarMfa(@Path("id") id: Int)

    @GET("usuarios/{id}/dispositivos")
    suspend fun aparelhosDoUsuario(@Path("id") id: Int): List<AparelhoDto>

    /** Aparelho perdido ou roubado: revoga o acesso e os tokens dele (só o Netafim Admin). */
    @POST("dispositivos/{id}/revogar")
    suspend fun revogarAparelho(@Path("id") id: String): AparelhoDto

    @POST("usuarios/{id}/aprovar")
    suspend fun aprovarUsuario(@Path("id") id: Int, @Body corpo: AprovarRequisicao): UsuarioDto

    @POST("usuarios/{id}/recusar")
    suspend fun recusarUsuario(@Path("id") id: Int): UsuarioDto

    /** Sem paginação na API. */
    @GET("perfis")
    suspend fun perfis(): List<PerfilDto>

    /** Os mesmos perfis de `GET perfis`, com a descrição e os códigos das permissões de cada um. */
    @GET("perfis")
    suspend fun perfisComPermissoes(): List<PerfilCompletoDto>

    @GET("permissoes")
    suspend fun permissoes(): List<PermissaoDto>

    @PUT("perfis/{id}/permissoes")
    suspend fun salvarPermissoes(@Path("id") id: Int, @Body corpo: PermissoesRequisicao): PerfilCompletoDto

    @GET("distribuidores")
    suspend fun distribuidores(@Query("page") pagina: Int): PaginaDto<DistribuidorDto>

    @GET("clientes")
    suspend fun clientes(@Query("page") pagina: Int): PaginaDto<ClienteDto>
}
