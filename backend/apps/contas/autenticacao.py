"""Autenticação das requisições da API por JWT de curta duração (cabeçalho Bearer)."""

from drf_spectacular.extensions import OpenApiAuthenticationExtension
from rest_framework_simplejwt.authentication import JWTAuthentication
from rest_framework_simplejwt.exceptions import AuthenticationFailed, InvalidToken
from rest_framework_simplejwt.settings import api_settings

from . import contexto
from .models import Dispositivo, Usuario


class AutenticacaoJWT(JWTAuthentication):
    """
    Igual ao JWTAuthentication do simplejwt, mas informa o ator ao banco (RLS) e só aceita contas
    ativas e não excluídas. Uma conta inativada, bloqueada ou excluída, ou um aparelho revogado,
    perde o acesso na próxima requisição, mesmo com token ainda válido.

    O ``sub`` do JWT já teve a assinatura conferida; só por isso ele pode virar o ator da transação.
    """

    def get_user(self, validated_token):
        try:
            id_usuario = validated_token[api_settings.USER_ID_CLAIM]
        except KeyError as exc:
            raise InvalidToken("Token sem identificação de usuário.") from exc
        try:
            id_usuario = int(id_usuario)
        except (TypeError, ValueError) as exc:
            raise InvalidToken("Token com identificação de usuário inválida.") from exc

        # Roda dentro da transação da requisição (ATOMIC_REQUESTS): o ator vale até o fim dela, para
        # as consultas seguintes da view. Fora de uma transação, contexto.definir_ator falha alto.
        contexto.definir_ator(id_usuario)
        usuario = Usuario.objects.select_related("perfil").filter(pk=id_usuario).first()
        if usuario is None:
            raise AuthenticationFailed("Usuário não encontrado.", code="user_not_found")
        if not usuario.is_active:
            raise AuthenticationFailed("Conta sem acesso liberado.", code="user_inactive")
        id_dispositivo = validated_token.get("did")
        if (
            id_dispositivo
            and Dispositivo.objects.filter(pk=id_dispositivo, revogado_em__isnull=False).exists()
        ):
            raise AuthenticationFailed(
                "Este aparelho teve o acesso revogado.", code="device_revoked"
            )
        return usuario


class AutenticacaoJWTEsquema(OpenApiAuthenticationExtension):
    target_class = "apps.contas.autenticacao.AutenticacaoJWT"
    name = "jwtAuth"

    def get_security_definition(self, auto_schema):
        return {"type": "http", "scheme": "bearer", "bearerFormat": "JWT"}
