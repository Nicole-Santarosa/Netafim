"""
TOTP (RFC 6238) e HOTP (RFC 4226) só com a biblioteca padrão: sem dependência nova.

O código de 6 dígitos é compatível com Google Authenticator, Microsoft Authenticator, Authy e
1Password (SHA-1, passo de 30 s, 6 dígitos). O segredo é gerado com ``secrets`` (160 bits, o
tamanho recomendado pela RFC 4226) e codificado em base32 sem preenchimento, como esses
aplicativos esperam.
"""

import base64
import hashlib
import hmac
import secrets
import struct
import time
from urllib.parse import quote

PASSO_SEGUNDOS = 30
DIGITOS = 6
# Tolerância a relógios levemente diferentes: o passo atual e o anterior e o seguinte.
JANELA = 1
EMISSOR = "Netafim"


def novo_segredo() -> str:
    """160 bits aleatórios em base32 (32 caracteres, sem ``=``)."""
    return base64.b32encode(secrets.token_bytes(20)).decode("ascii").rstrip("=")


def _bytes_do_segredo(segredo: str) -> bytes:
    preenchido = segredo.upper() + "=" * (-len(segredo) % 8)
    return base64.b32decode(preenchido)


def hotp(segredo: str, contador: int, digitos: int = DIGITOS) -> str:
    resumo = hmac.new(
        _bytes_do_segredo(segredo), struct.pack(">Q", contador), hashlib.sha1
    ).digest()
    deslocamento = resumo[-1] & 0x0F
    valor = struct.unpack(">I", resumo[deslocamento : deslocamento + 4])[0] & 0x7FFFFFFF
    return str(valor % 10**digitos).zfill(digitos)


def passo_em(instante: float | None = None) -> int:
    return int((time.time() if instante is None else instante) // PASSO_SEGUNDOS)


def codigo_em(segredo: str, instante: float | None = None) -> str:
    return hotp(segredo, passo_em(instante))


def normalizar_codigo(codigo: str) -> str | None:
    """Só dígitos, com 6 posições; espaços e hífens (como o app mostra, ``123 456``) são aceitos."""
    limpo = "".join(ch for ch in (codigo or "") if ch not in " -")
    return limpo if len(limpo) == DIGITOS and limpo.isdigit() else None


def verificar(
    segredo: str, codigo: str, *, ultimo_passo: int = 0, instante: float | None = None
) -> int | None:
    """
    Devolve o passo de tempo em que o código confere, ou ``None``.

    Só aceita passos MAIORES que ``ultimo_passo``: o mesmo código (ou um mais antigo) não vale duas
    vezes. A comparação é em tempo constante e percorre toda a janela, para o tempo da resposta não
    revelar em qual posição o código bateu.
    """
    informado = normalizar_codigo(codigo)
    if informado is None:
        return None
    atual = passo_em(instante)
    achado = None
    for passo in range(atual - JANELA, atual + JANELA + 1):
        confere = hmac.compare_digest(hotp(segredo, passo), informado)
        if confere and passo > ultimo_passo and achado is None:
            achado = passo
    return achado


def uri_otpauth(segredo: str, conta: str, emissor: str = EMISSOR) -> str:
    """URI ``otpauth://`` (formato do Google Authenticator) para cadastro manual ou por QR."""
    rotulo = quote(f"{emissor}:{conta}", safe="")
    return (
        f"otpauth://totp/{rotulo}?secret={segredo}&issuer={quote(emissor, safe='')}"
        f"&algorithm=SHA1&digits={DIGITOS}&period={PASSO_SEGUNDOS}"
    )
