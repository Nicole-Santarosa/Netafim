"""Autenticação das requisições da API por JWT de curta duração (cabeçalho Bearer)."""

from drf_spectacular.extensions import OpenApiAuthenticationExtension
from rest_framework_simplejwt.authentication import JWTAuthentication
from rest_framework_simplejwt.exceptions import AuthenticationFailed, InvalidToken
from rest_framework_simplejwt.settings import api_settings

from .models import Usuario


class AutenticacaoJWT(JWTAuthentication):
    """
    Igual ao JWTAuthentication do simplejwt, mas carrega o perfil junto e só aceita
    contas ativas e não excluídas (situacao = ativo). Uma conta inativada, bloqueada
    ou excluída perde o acesso na próxima requisição, mesmo com token ainda válido.
    """

    def get_user(self, validated_token):
        try:
            id_usuario = validated_token[api_settings.USER_ID_CLAIM]
        except KeyError as exc:
            raise InvalidToken("Token sem identificação de usuário.") from exc
        try:
            usuario = Usuario.objects.select_related("perfil").get(id_usuario=id_usuario)
        except Usuario.DoesNotExist as exc:
            raise AuthenticationFailed("Usuário não encontrado.", code="user_not_found") from exc
        if not usuario.is_active:
            raise AuthenticationFailed("Conta sem acesso liberado.", code="user_inactive")
        return usuario


class AutenticacaoJWTEsquema(OpenApiAuthenticationExtension):
    target_class = "apps.contas.autenticacao.AutenticacaoJWT"
    name = "jwtAuth"

    def get_security_definition(self, auto_schema):
        return {"type": "http", "scheme": "bearer", "bearerFormat": "JWT"}
