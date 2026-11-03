"""
Autenticação em dois fatores (MFA) por TOTP: ativar, confirmar, usar no login, desativar, redefinir.

O segredo e os códigos de recuperação ficam em tabelas que a conexão comum da API
(``app_django``) não lê. Só a conexão ``auth`` (papel ``app_auth``) executa as funções
``fn_mfa_*`` da migração 007, o mesmo isolamento que a DT-002 deu ao hash da senha. Por isso o
MFA exige a conexão ``auth`` configurada (``DB_AUTH_PASSWORD``); sem ela, ou sem a migração 007,
as rotas respondem 503 ``mfa_indisponivel`` e o login segue sem segundo fator (nenhum usuário
tem MFA ativo antes de a 007 existir).

Fluxo do login com MFA ativo (ver ``acesso.autenticar`` e ``acesso.concluir_login_mfa``):

1. e-mail e senha corretos -> a API NÃO abre sessão: devolve um desafio assinado (5 minutos);
2. desafio + código de 6 dígitos (ou código de recuperação) -> abre a sessão.

Cada código errado conta como falha de login (``fn_registrar_login``): 5 falhas seguidas
bloqueiam a conta por 15 minutos, como na senha. Um código TOTP vale uma única vez
(``fn_mfa_registrar_passo``).
"""

import hashlib
import secrets
from datetime import datetime, timedelta

from django.core import signing
from django.db import OperationalError, ProgrammingError, connections
from django.utils import timezone

from ..constantes import TipoToken
from ..excecoes import ErroNegocio, SemPermissao
from ..models import Usuario
from . import auditoria, banco, credenciais, tokens, totp
from .auditoria import Acao

ALIAS = credenciais.ALIAS
SAL_DESAFIO = "netafim.mfa.desafio"
VALIDADE_DESAFIO = 300  # segundos
QTD_RECUPERACAO = 10
_ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"  # sem 0, O, 1, I (confundem na leitura)
_TAMANHO_RECUPERACAO = 10
_SQLSTATE_FUNCAO_INEXISTENTE = "42883"


# --------------------------------------------------------------------------- acesso ao banco


def _indisponivel() -> ErroNegocio:
    return ErroNegocio(
        "A autenticação em dois fatores não está disponível neste servidor.",
        "mfa_indisponivel",
        status_code=503,
    )


def _executar(comando: str, *args):
    """Roda uma função fn_mfa_* pela conexão auth e devolve as linhas. 503 se não estiver pronta."""
    if not credenciais.modo_seguro():
        raise _indisponivel()
    try:
        with connections[ALIAS].cursor() as cursor:
            cursor.execute(comando, list(args))
            return cursor.fetchall() if cursor.description else []
    except OperationalError as exc:
        raise _indisponivel() from exc
    except ProgrammingError as exc:
        if banco.sqlstate(exc) == _SQLSTATE_FUNCAO_INEXISTENTE:
            raise _indisponivel() from exc
        raise


def _um(comando: str, *args):
    linhas = _executar(comando, *args)
    return linhas[0][0] if linhas else None


# --------------------------------------------------------------------------- códigos de recuperação


def _normalizar_recuperacao(codigo: str) -> str | None:
    limpo = "".join(ch for ch in (codigo or "").upper() if ch not in " -")
    ok = len(limpo) == _TAMANHO_RECUPERACAO and all(ch in _ALFABETO for ch in limpo)
    return limpo if ok else None


def _hash_recuperacao(normalizado: str) -> str:
    return hashlib.sha256(normalizado.encode("ascii")).hexdigest()


def _gerar_recuperacao() -> list[str]:
    """10 códigos ``XXXXX-XXXXX`` (50 bits cada). Só aparecem na resposta que os cria."""
    codigos: set[str] = set()
    while len(codigos) < QTD_RECUPERACAO:
        bruto = "".join(secrets.choice(_ALFABETO) for _ in range(_TAMANHO_RECUPERACAO))
        codigos.add(f"{bruto[:5]}-{bruto[5:]}")
    return sorted(codigos)


# --------------------------------------------------------------------------- segundo fator


def verificar_segundo_fator(id_usuario: int, codigo: str) -> str | None:
    """
    Confere um código TOTP ou de recuperação. Devolve ``"totp"``, ``"recuperacao"`` ou ``None``.
    Consome o passo ou o código: o mesmo valor não vale duas vezes.
    """
    linhas = _executar(
        "select segredo, confirmado, ultimo_passo from fn_mfa_segredo(%s)", id_usuario
    )
    if not linhas or not linhas[0][1]:
        return None
    segredo, _, ultimo_passo = linhas[0]
    if totp.normalizar_codigo(codigo):
        passo = totp.verificar(segredo, codigo, ultimo_passo=int(ultimo_passo))
        if passo is None:
            return None
        return "totp" if _um("select fn_mfa_registrar_passo(%s, %s)", id_usuario, passo) else None
    normalizado = _normalizar_recuperacao(codigo)
    if normalizado is None:
        return None
    usado = _um(
        "select fn_mfa_consumir_recuperacao(%s, %s)", id_usuario, _hash_recuperacao(normalizado)
    )
    return "recuperacao" if usado else None


# --------------------------------------------------------------------------- desafio do login


def emitir_desafio(email: str, dispositivo: dict) -> tuple[str, datetime]:
    """Desafio assinado (não criptografado): e-mail e aparelho do login, válido por 5 minutos."""
    token = signing.dumps({"e": email, "d": dispositivo}, salt=SAL_DESAFIO, compress=False)
    return token, timezone.now() + timedelta(seconds=VALIDADE_DESAFIO)


def ler_desafio(token: str) -> dict:
    try:
        dados = signing.loads(token, salt=SAL_DESAFIO, max_age=VALIDADE_DESAFIO)
    except signing.BadSignature as exc:  # inclui SignatureExpired
        raise ErroNegocio(
            "Desafio inválido ou expirado. Entre com e-mail e senha de novo.",
            "desafio_mfa_invalido",
            status_code=401,
        ) from exc
    if not isinstance(dados, dict) or "e" not in dados or "d" not in dados:
        raise ErroNegocio("Desafio inválido.", "desafio_mfa_invalido", status_code=401)
    return dados


# --------------------------------------------------------------------------- conta do usuário


def estado(usuario: Usuario) -> dict:
    ativo = bool(usuario.mfa_ativo)
    restantes = 0
    if ativo:
        restantes = int(_um("select fn_mfa_recuperacao_restante(%s)", usuario.pk) or 0)
    return {"ativo": ativo, "codigos_recuperacao_restantes": restantes}


def iniciar(usuario: Usuario) -> dict:
    """Gera um segredo novo (não confirmado). Pode repetir enquanto o MFA estiver desligado."""
    if usuario.mfa_ativo:
        raise ErroNegocio("A autenticação em dois fatores já está ativa.", "mfa_ja_ativo", 409)
    segredo = totp.novo_segredo()
    _executar("select fn_mfa_guardar_segredo(%s, %s)", usuario.pk, segredo)
    return {"segredo": segredo, "otpauth_uri": totp.uri_otpauth(segredo, usuario.email)}


def confirmar(usuario: Usuario, codigo: str, *, request=None) -> list[str]:
    """O titular prova que o app gera o código certo; liga o MFA e devolve os códigos."""
    if usuario.mfa_ativo:
        raise ErroNegocio("A autenticação em dois fatores já está ativa.", "mfa_ja_ativo", 409)
    linhas = _executar(
        "select segredo, confirmado, ultimo_passo from fn_mfa_segredo(%s)", usuario.pk
    )
    if not linhas or linhas[0][1]:
        raise ErroNegocio("Comece a configuração antes de confirmar.", "mfa_nao_iniciado", 409)
    segredo, _, ultimo_passo = linhas[0]
    passo = totp.verificar(segredo, codigo, ultimo_passo=int(ultimo_passo))
    if passo is None:
        raise ErroNegocio("Código incorreto ou expirado.", "codigo_mfa_invalido")
    codigos = _gerar_recuperacao()
    hashes = [_hash_recuperacao(_normalizar_recuperacao(c)) for c in codigos]
    # confirma o segredo primeiro: o gatilho do banco só deixa ligar usuario.mfa_ativo depois disso
    try:
        _executar("select fn_mfa_confirmar(%s, %s, %s)", usuario.pk, passo, hashes)
    except ProgrammingError as exc:
        raise ErroNegocio(
            "Comece a configuração antes de confirmar.", "mfa_nao_iniciado", 409
        ) from exc
    usuario.mfa_ativo = True
    banco.salvar(usuario, update_fields=["mfa_ativo", "data_atualizacao"])
    auditoria.registrar(
        Acao.MFA_ALTERADO, usuario=usuario, request=request, entidade=usuario, ativo=True
    )
    return codigos


def desativar(usuario: Usuario, senha: str, codigo: str, *, request=None) -> None:
    """Exige a senha E um código (TOTP ou recuperação). Um gatilho do banco apaga o segredo."""
    if not usuario.mfa_ativo:
        raise ErroNegocio("A autenticação em dois fatores não está ativa.", "mfa_inativo", 409)
    if not credenciais.conferir(credenciais.por_id(usuario.pk), senha):
        raise ErroNegocio("A senha está incorreta.", "senha_incorreta")
    if verificar_segundo_fator(usuario.pk, codigo) is None:
        raise ErroNegocio("Código incorreto ou expirado.", "codigo_mfa_invalido")
    usuario.mfa_ativo = False
    banco.salvar(usuario, update_fields=["mfa_ativo", "data_atualizacao"])
    auditoria.registrar(
        Acao.MFA_ALTERADO, usuario=usuario, request=request, entidade=usuario, ativo=False
    )


def redefinir(ator: Usuario, alvo: Usuario, *, request=None) -> None:
    """Netafim Admin remove o MFA de quem perdeu aparelho e códigos. Encerra as sessões do alvo."""
    if not ator.eh_netafim_admin:
        raise SemPermissao("Somente o Administrador Netafim redefine o MFA de outro usuário.")
    if not alvo.mfa_ativo:
        raise ErroNegocio("A autenticação em dois fatores não está ativa.", "mfa_inativo", 409)
    alvo.mfa_ativo = False
    banco.salvar(alvo, update_fields=["mfa_ativo", "data_atualizacao"])
    tokens.revogar_todos(alvo.pk, tipo=TipoToken.REFRESH)
    auditoria.registrar(
        Acao.MFA_ALTERADO, usuario=ator, request=request, entidade=alvo, ativo=False, via="admin"
    )
