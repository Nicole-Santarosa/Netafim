package com.netafim.posvenda.acesso.dados

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/** Endpoints do módulo de acesso (prefixo /api/v1/). */
interface AcessoApi {
    @POST("auth/login")
    suspend fun login(@Body corpo: LoginRequisicao): TokensResposta

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
}
