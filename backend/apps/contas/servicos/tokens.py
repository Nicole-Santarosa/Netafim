"""
Emissão, rotação e revogação de tokens (TOKEN_ACESSO).

- O token só existe em claro na resposta ao cliente; o banco guarda o SHA-256.
- Refresh token é rotacionado a cada uso: o antigo fica marcado como usado e
  aponta para o novo (substituido_por).
- Reuso de um refresh já rotacionado indica roubo: todos os refresh do usuário
  naquele aparelho são revogados. Exceção: dentro de uma janela curta
  (CONTAS["REFRESH_TOLERANCIA_SEGUNDOS"], padrão 30 s; 0 desliga) o reuso é tratado como
  repetição legítima, porque a resposta da rotação pode ter se perdido na rede móvel.
"""

import hashlib
import secrets
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta

from django.conf import settings
from django.db import transaction
from django.utils import timezone
from rest_framework_simplejwt.tokens import AccessToken

from ..constantes import TipoToken
from ..excecoes import TokenInvalido
from ..models import Dispositivo, TokenAcesso, Usuario

VALIDADE = {
    TipoToken.REFRESH: lambda: timedelta(days=settings.CONTAS["REFRESH_DIAS"]),
    TipoToken.CONVITE: lambda: timedelta(days=settings.CONTAS["CONVITE_DIAS"]),
    TipoToken.RESET_SENHA: lambda: timedelta(hours=settings.CONTAS["RESET_SENHA_HORAS"]),
}


def _tolerancia_reuso() -> timedelta:
    return timedelta(seconds=settings.CONTAS.get("REFRESH_TOLERANCIA_SEGUNDOS", 30))


def hash_token(token_claro: str) -> str:
    return hashlib.sha256(token_claro.encode("utf-8")).hexdigest()


def emitir(
    usuario: Usuario, tipo: str, *, dispositivo: Dispositivo | None = None, ip: str | None = None
) -> tuple[TokenAcesso, str]:
    token_claro = secrets.token_urlsafe(48)
    agora = timezone.now()
    validade = min(VALIDADE[tipo](), TokenAcesso.LIMITE_VALIDADE)
    registro = TokenAcesso.objects.create(
        usuario=usuario,
        dispositivo=dispositivo,
        tipo=tipo,
        token_hash=hash_token(token_claro),
        criado_em=agora,
        expira_em=agora + validade,
        ip=ip,
    )
    return registro, token_claro


@dataclass(frozen=True)
class ParDeTokens:
    access: str
    refresh: str
    access_expira_em: str
    refresh_expira_em: str


def emitir_sessao(
    usuario: Usuario, *, dispositivo: Dispositivo | None = None, ip: str | None = None
) -> ParDeTokens:
    refresh, refresh_claro = emitir(usuario, TipoToken.REFRESH, dispositivo=dispositivo, ip=ip)
    return _montar_par(usuario, refresh, refresh_claro, dispositivo)


def _montar_par(usuario, refresh, refresh_claro, dispositivo) -> ParDeTokens:
    access = AccessToken.for_user(usuario)
    if dispositivo is not None:
        access["did"] = str(dispositivo.id_dispositivo)
    expira = datetime.fromtimestamp(access["exp"], tz=UTC)
    return ParDeTokens(
        access=str(access),
        refresh=refresh_claro,
        access_expira_em=expira.isoformat(),
        refresh_expira_em=refresh.expira_em.isoformat(),
    )


def _buscar_para_uso(token_claro: str, tipo: str) -> TokenAcesso:
    if not token_claro:
        raise TokenInvalido()
    try:
        return (
            TokenAcesso.objects.select_for_update(of=("self",))
            .select_related("usuario", "dispositivo")
            .get(token_hash=hash_token(token_claro), tipo=tipo)
        )
    except TokenAcesso.DoesNotExist as exc:
        raise TokenInvalido() from exc


def rotacionar_refresh(token_claro: str, *, ip: str | None = None) -> tuple[Usuario, ParDeTokens]:
    resultado = _rotacionar(token_claro, ip)
    if isinstance(resultado, TokenInvalido):
        # Levantado fora da transação para a revogação por reuso ficar gravada.
        raise resultado
    return resultado


@transaction.atomic
def _rotacionar(token_claro: str, ip: str | None):
    atual = _buscar_para_uso(token_claro, TipoToken.REFRESH)

    agora = timezone.now()
    repeticao = atual.usado_em is not None and atual.revogado_em is None
    if repeticao:
        if agora - atual.usado_em > _tolerancia_reuso():
            # Token já rotacionado sendo reutilizado: provável roubo. Revoga a cadeia.
            revogar_refresh_do_aparelho(atual.usuario, atual.dispositivo)
            return TokenInvalido("Sessão encerrada por segurança. Entre novamente.")
        if atual.expira_em <= agora:
            return TokenInvalido("Sessão expirada. Entre novamente.")
    elif not atual.valido:
        return TokenInvalido("Sessão expirada. Entre novamente.")

    usuario = atual.usuario
    if not usuario.is_active:
        return TokenInvalido("Conta sem acesso liberado.")
    if atual.dispositivo is not None and atual.dispositivo.revogado_em is not None:
        return TokenInvalido("Este aparelho teve o acesso revogado.")

    novo, novo_claro = emitir(usuario, TipoToken.REFRESH, dispositivo=atual.dispositivo, ip=ip)
    if not repeticao:
        atual.usado_em = agora
        atual.substituido_por = novo
        atual.save(update_fields=["usado_em", "substituido_por"])
    return usuario, _montar_par(usuario, novo, novo_claro, atual.dispositivo)


@transaction.atomic
def consumir(token_claro: str, tipo: str) -> TokenAcesso:
    """Valida e marca como usado um token de uso único (convite ou redefinição)."""
    registro = _buscar_para_uso(token_claro, tipo)
    if not registro.valido or registro.usuario.deletado_em is not None:
        raise TokenInvalido("Link inválido ou expirado. Peça um novo.")
    registro.usado_em = timezone.now()
    registro.save(update_fields=["usado_em"])
    return registro


def revogar(
    token_claro: str, tipo: str = TipoToken.REFRESH, *, usuario: Usuario | None = None
) -> bool:
    """Revoga o token informado; com ``usuario``, só se o token for dele."""
    filtros = {"token_hash": hash_token(token_claro), "tipo": tipo, "revogado_em__isnull": True}
    if usuario is not None:
        filtros["usuario"] = usuario
    return bool(TokenAcesso.objects.filter(**filtros).update(revogado_em=timezone.now()))


def revogar_refresh_do_aparelho(usuario: Usuario, dispositivo: Dispositivo | None) -> int:
    return TokenAcesso.objects.filter(
        usuario=usuario,
        dispositivo=dispositivo,
        tipo=TipoToken.REFRESH,
        revogado_em__isnull=True,
    ).update(revogado_em=timezone.now())


def revogar_todos(usuario: Usuario, *, tipo: str | None = None) -> int:
    filtros = {"usuario": usuario, "revogado_em__isnull": True}
    if tipo:
        filtros["tipo"] = tipo
    return TokenAcesso.objects.filter(**filtros).update(revogado_em=timezone.now())
