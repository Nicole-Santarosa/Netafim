"""
Emissão, rotação e revogação de tokens, sempre pelas funções do banco.

O papel da API (app_django) não tem privilégio na tabela TOKEN_ACESSO: quem emite, rotaciona e
revoga é o banco, por ``fn_emitir_token``, ``fn_rotacionar_token`` e ``fn_revogar_token`` (SECURITY
DEFINER). Aqui o token só existe em claro na resposta ao cliente; ao banco vai apenas o SHA-256.

Regras que quem decide é o banco (e a API apenas respeita):
- validade do refresh de 90 dias, do convite de 14 dias e da redefinição de 2 horas;
- refresh só para usuário ativo e com aparelho dele, não revogado;
- reuso de um refresh já rotacionado derruba todas as sessões do aparelho (sem janela de
  tolerância);
- inativar, bloquear, recusar ou excluir a conta, e revogar o aparelho, revogam os tokens
  (gatilhos).
"""

import hashlib
import logging
import secrets
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from uuid import UUID

from django.db import DatabaseError, connection, transaction
from django.utils import timezone
from rest_framework_simplejwt.tokens import AccessToken

from .. import contexto
from ..constantes import TipoToken
from ..excecoes import TokenInvalido
from ..models import Usuario
from . import banco

logger = logging.getLogger(__name__)

# Validade do refresh fixada no banco (fn_emitir_token). Usada só para informar o aplicativo.
REFRESH_DIAS_NO_BANCO = 90


def hash_token(token_claro: str) -> str:
    return hashlib.sha256(token_claro.encode("utf-8")).hexdigest()


def _novo_token() -> str:
    return secrets.token_urlsafe(48)


def _traduzir_ou_levantar(exc: DatabaseError):
    traduzido = banco.traduzir(exc)
    if traduzido is exc:
        raise exc
    raise traduzido from exc


def emitir(
    id_usuario: int, tipo: str, *, id_dispositivo: UUID | str | None = None, ip: str | None = None
) -> str:
    """Pede ao banco um token do tipo informado e devolve o valor em claro."""
    claro = _novo_token()
    try:
        with transaction.atomic(), connection.cursor() as cursor:
            cursor.execute(
                "select fn_emitir_token(%s, %s, %s, %s, %s)",
                [id_usuario, tipo, hash_token(claro), id_dispositivo, ip],
            )
    except DatabaseError as exc:
        _traduzir_ou_levantar(exc)
    return claro


@dataclass(frozen=True)
class ParDeTokens:
    access: str
    refresh: str
    access_expira_em: str
    refresh_expira_em: str


def montar_par(usuario: Usuario, refresh_claro: str, id_dispositivo) -> ParDeTokens:
    access = AccessToken.for_user(usuario)
    if id_dispositivo is not None:
        access["did"] = str(id_dispositivo)
    expira = datetime.fromtimestamp(access["exp"], tz=UTC)
    return ParDeTokens(
        access=str(access),
        refresh=refresh_claro,
        access_expira_em=expira.isoformat(),
        # O banco calcula a validade exata; este valor é aproximado (diferença de segundos).
        refresh_expira_em=(timezone.now() + timedelta(days=REFRESH_DIAS_NO_BANCO)).isoformat(),
    )


def emitir_sessao(usuario: Usuario, *, id_dispositivo, ip: str | None = None) -> ParDeTokens:
    refresh = emitir(usuario.pk, TipoToken.REFRESH, id_dispositivo=id_dispositivo, ip=ip)
    return montar_par(usuario, refresh, id_dispositivo)


def _erro_da_rotacao(resultado: str) -> TokenInvalido:
    mensagens = {
        "reuso_detectado": "Sessão encerrada por segurança. Entre novamente.",
        "expirado": "Sessão expirada. Entre novamente.",
        "revogado": "Sessão encerrada. Entre novamente.",
        "usuario_inativo": "Conta sem acesso liberado.",
    }
    return TokenInvalido(mensagens[resultado]) if resultado in mensagens else TokenInvalido()


def rotacionar_refresh(token_claro: str, *, ip: str | None = None) -> tuple[Usuario, ParDeTokens]:
    """
    Troca o refresh por um novo. O banco detecta o reuso e revoga as sessões do aparelho; por isso o
    erro só é levantado DEPOIS de a transação terminar (a revogação por reuso precisa ficar
    gravada).
    """
    if not token_claro:
        raise TokenInvalido()
    novo = _novo_token()
    erro: TokenInvalido | None = None
    usuario = par = None
    with transaction.atomic():
        contexto.limpar()
        try:
            with connection.cursor() as cursor:
                cursor.execute(
                    "select resultado, id_usuario, id_dispositivo "
                    "from fn_rotacionar_token(%s, %s, %s)",
                    [hash_token(token_claro), hash_token(novo), ip],
                )
                resultado, id_usuario, id_dispositivo = cursor.fetchone()
        except DatabaseError as exc:
            _traduzir_ou_levantar(exc)
        if resultado != "ok":
            erro = _erro_da_rotacao(resultado)
        else:
            contexto.definir_ator(id_usuario)
            usuario = banco.carregar_usuario_completo(id_usuario)
            if usuario is None or not usuario.is_active:
                erro = TokenInvalido("Conta sem acesso liberado.")
            else:
                par = montar_par(usuario, novo, id_dispositivo)
    if erro is not None:
        raise erro
    return usuario, par


def consumir(token_claro: str, tipo: str) -> int:
    """
    Valida e marca como usado um token de uso único (convite ou redefinição) e devolve o id do dono.
    Deve rodar na transação de quem vai usar o token: se a operação seguinte falhar, o uso é
    desfeito.

    O banco não informa o tipo do token consumido; tipo trocado não dá poder novo (quem tem o
    convite ou a redefinição já pode definir a senha daquela conta).
    """
    if not token_claro:
        raise TokenInvalido("Link inválido ou expirado. Peça um novo.")
    try:
        with transaction.atomic(), connection.cursor() as cursor:
            cursor.execute(
                "select resultado, id_usuario from fn_rotacionar_token(%s, null, null)",
                [hash_token(token_claro)],
            )
            resultado, id_usuario = cursor.fetchone()
    except DatabaseError as exc:  # por exemplo, um refresh enviado no lugar de convite
        logger.info("Token recusado pelo banco ao consumir %s: %s", tipo, banco.mensagem_banco(exc))
        raise TokenInvalido("Link inválido ou expirado. Peça um novo.") from exc
    if resultado != "ok":
        raise TokenInvalido("Link inválido ou expirado. Peça um novo.")
    return id_usuario


def revogar(token_claro: str) -> bool:
    """Revoga o token informado. Quem o apresenta já o possui, então não há dono a conferir."""
    with connection.cursor() as cursor:
        cursor.execute("select fn_revogar_token(%s)", [hash_token(token_claro)])
        return bool(cursor.fetchone()[0])


def revogar_todos(id_usuario: int, *, tipo: str | None = None) -> int | None:
    """
    Encerra todas as sessões do usuário (troca ou redefinição de senha).

    Depende de ``fn_revogar_tokens_usuario``, proposta em ``backend/db/propostas``. Enquanto a
    função não existir no banco, as sessões antigas continuam valendo até expirar e isto só registra
    um aviso.
    """
    try:
        with transaction.atomic(), connection.cursor() as cursor:
            cursor.execute("select fn_revogar_tokens_usuario(%s, %s)", [id_usuario, tipo])
            return cursor.fetchone()[0]
    except DatabaseError as exc:
        if banco.sqlstate(exc) == "42883":  # undefined_function
            logger.warning(
                "fn_revogar_tokens_usuario ausente no banco: as sessões antigas do usuário %s "
                "NÃO foram encerradas.",
                id_usuario,
            )
            return None
        _traduzir_ou_levantar(exc)
