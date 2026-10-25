"""
Erros de regra de negócio do módulo de acesso.

Mantém o formato padrão do DRF (``{"detail": ...}``) e acrescenta ``codigo``, estável
e em snake_case, para o aplicativo decidir a mensagem/fluxo sem depender do texto.
"""

from rest_framework import status
from rest_framework.exceptions import APIException


class ErroNegocio(APIException):
    status_code = status.HTTP_400_BAD_REQUEST
    default_code = "erro_negocio"

    def __init__(
        self, mensagem: str, codigo: str | None = None, status_code: int | None = None, **extras
    ):
        if status_code is not None:
            self.status_code = status_code
        codigo = codigo or self.default_code
        super().__init__(detail={"detail": mensagem, "codigo": codigo, **extras}, code=codigo)
        self.codigo = codigo


class CredenciaisInvalidas(ErroNegocio):
    status_code = status.HTTP_401_UNAUTHORIZED
    default_code = "credenciais_invalidas"

    def __init__(self):
        super().__init__(
            "E-mail ou senha inválidos, ou acesso temporariamente bloqueado. "
            "Aguarde alguns minutos e tente novamente.",
            self.default_code,
        )


class TokenInvalido(ErroNegocio):
    status_code = status.HTTP_401_UNAUTHORIZED
    default_code = "token_invalido"

    def __init__(self, mensagem: str = "Token inválido ou expirado."):
        super().__init__(mensagem, self.default_code)


class SemPermissao(ErroNegocio):
    status_code = status.HTTP_403_FORBIDDEN
    default_code = "sem_permissao"

    def __init__(self, mensagem: str = "Você não tem permissão para esta ação."):
        super().__init__(mensagem, self.default_code)
