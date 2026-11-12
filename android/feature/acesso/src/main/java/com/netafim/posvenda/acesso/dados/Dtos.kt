package com.netafim.posvenda.acesso.dados

import com.netafim.posvenda.acesso.dominio.Organizacao
import com.netafim.posvenda.acesso.dominio.Perfil
import com.netafim.posvenda.acesso.dominio.Usuario
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Contrato: docs/api/acesso-openapi.yaml (gerado pelo back-end).

@Serializable
data class LoginRequisicao(
    val email: String,
    val senha: String,
    val dispositivo: DispositivoDto? = null,
)

@Serializable
data class DispositivoDto(
    @SerialName("id_dispositivo") val idDispositivo: String,
    val modelo: String? = null,
    @SerialName("versao_app") val versaoApp: String? = null,
)

@Serializable
data class RefreshRequisicao(val refresh: String)

@Serializable
data class TokensResposta(
    val access: String,
    val refresh: String,
    @SerialName("access_expira_em") val accessExpiraEm: String,
    @SerialName("refresh_expira_em") val refreshExpiraEm: String,
    val usuario: UsuarioDto,
)

@Serializable
data class UsuarioDto(
    @SerialName("id_usuario") val idUsuario: Int,
    val nome: String,
    val email: String,
    val telefone: String? = null,
    val situacao: String,
    @SerialName("tipo_organizacao") val tipoOrganizacao: String? = null,
    val organizacao: OrganizacaoDto? = null,
    val perfil: PerfilDto? = null,
    val permissoes: List<String> = emptyList(),
    @SerialName("exige_troca_senha") val exigeTrocaSenha: Boolean = false,
) {
    fun paraDominio() = Usuario(
        id = idUsuario,
        nome = nome,
        email = email,
        telefone = telefone,
        situacao = situacao,
        perfil = perfil?.let { Perfil(it.idPerfil, it.nomePerfil, it.tipoOrganizacao, it.nivel) },
        organizacao = organizacao?.let { Organizacao(it.tipo, it.id, it.nome) },
        permissoes = permissoes.toSet(),
        exigeTrocaSenha = exigeTrocaSenha,
    )
}

@Serializable
data class PerfilDto(
    @SerialName("id_perfil") val idPerfil: Int,
    @SerialName("nome_perfil") val nomePerfil: String,
    @SerialName("tipo_organizacao") val tipoOrganizacao: String,
    val nivel: String,
)

@Serializable
data class OrganizacaoDto(
    val tipo: String,
    val id: Int? = null,
    val nome: String,
)

@Serializable
data class CadastroRequisicao(
    val nome: String,
    val email: String,
    val telefone: String? = null,
    val senha: String,
    @SerialName("aceite_termo") val aceiteTermo: Boolean,
    @SerialName("versao_termo") val versaoTermo: String,
)

@Serializable
data class EsqueciSenhaRequisicao(val email: String)

@Serializable
data class AceitarConviteRequisicao(
    val token: String,
    val senha: String,
    @SerialName("aceite_termo") val aceiteTermo: Boolean,
    @SerialName("versao_termo") val versaoTermo: String,
)

@Serializable
data class RedefinirSenhaRequisicao(
    val token: String,
    @SerialName("nova_senha") val novaSenha: String,
)

@Serializable
data class TermoResposta(@SerialName("versao_vigente") val versaoVigente: String)

@Serializable
data class MensagemResposta(val detail: String)

/** Lista paginada do DRF (PageNumberPagination): [next] é nulo na última página. */
@Serializable
data class PaginaDto<T>(
    val next: String? = null,
    val results: List<T> = emptyList(),
)

@Serializable
data class DistribuidorDto(
    @SerialName("id_distribuidor") val idDistribuidor: Int,
    val nome: String,
    val status: String = "ativo",
)

@Serializable
data class ClienteDto(
    @SerialName("id_cliente") val idCliente: Int,
    val nome: String,
    val status: String = "ativo",
)

/** Corpo de POST /usuarios/{id}/aprovar. */
@Serializable
data class AprovarRequisicao(
    @SerialName("tipo_organizacao") val tipoOrganizacao: String,
    @SerialName("id_distribuidor") val idDistribuidor: Int? = null,
    @SerialName("id_cliente") val idCliente: Int? = null,
    @SerialName("id_perfil") val idPerfil: Int,
)

/** Corpo de POST /usuarios feito por admin de distribuidor ou cliente: organização e perfil vêm da sessão. */
@Serializable
data class CriarUsuarioRequisicao(
    val nome: String,
    val email: String,
    val telefone: String? = null,
)
