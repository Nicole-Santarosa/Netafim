"""TOTP (RFC 6238) e HOTP (RFC 4226): vetores oficiais e regras de verificação, sem banco."""

import base64

import pytest

from apps.contas.servicos import totp

# Segredo ASCII "12345678901234567890" dos apêndices das RFCs, em base32.
SEGREDO_RFC = base64.b32encode(b"12345678901234567890").decode().rstrip("=")


@pytest.mark.parametrize(
    ("contador", "esperado"),
    [
        (0, "755224"),
        (1, "287082"),
        (2, "359152"),
        (3, "969429"),
        (4, "338314"),
        (5, "254676"),
        (6, "287922"),
        (7, "162583"),
        (8, "399871"),
        (9, "520489"),
    ],
)
def test_hotp_vetores_da_rfc_4226(contador, esperado):
    assert totp.hotp(SEGREDO_RFC, contador) == esperado


@pytest.mark.parametrize(
    ("instante", "esperado"),
    [
        (59, "94287082"),
        (1111111109, "07081804"),
        (1111111111, "14050471"),
        (1234567890, "89005924"),
        (2000000000, "69279037"),
        (20000000000, "65353130"),
    ],
)
def test_totp_vetores_da_rfc_6238_sha1(instante, esperado):
    assert totp.hotp(SEGREDO_RFC, totp.passo_em(instante), digitos=8) == esperado


def test_segredo_novo_e_base32_de_160_bits_e_nao_repete():
    a, b = totp.novo_segredo(), totp.novo_segredo()
    assert a != b
    assert len(a) == 32
    assert len(base64.b32decode(a)) == 20


def test_verificar_aceita_o_passo_atual_o_anterior_e_o_seguinte():
    t = 1_700_000_000
    passo = totp.passo_em(t)
    for deslocamento in (-1, 0, 1):
        codigo = totp.hotp(SEGREDO_RFC, passo + deslocamento)
        assert totp.verificar(SEGREDO_RFC, codigo, instante=t) == passo + deslocamento


def test_verificar_recusa_fora_da_janela():
    t = 1_700_000_000
    codigo = totp.hotp(SEGREDO_RFC, totp.passo_em(t) - 2)
    assert totp.verificar(SEGREDO_RFC, codigo, instante=t) is None


def test_verificar_nao_aceita_passo_ja_usado():
    t = 1_700_000_000
    passo = totp.passo_em(t)
    codigo = totp.hotp(SEGREDO_RFC, passo)
    assert totp.verificar(SEGREDO_RFC, codigo, ultimo_passo=passo, instante=t) is None
    assert totp.verificar(SEGREDO_RFC, codigo, ultimo_passo=passo - 1, instante=t) == passo


def test_verificar_aceita_espacos_e_hifen_como_o_app_mostra():
    t = 1_700_000_000
    codigo = totp.hotp(SEGREDO_RFC, totp.passo_em(t))
    assert totp.verificar(SEGREDO_RFC, f"{codigo[:3]} {codigo[3:]}", instante=t) is not None
    assert totp.verificar(SEGREDO_RFC, f"{codigo[:3]}-{codigo[3:]}", instante=t) is not None


@pytest.mark.parametrize("ruim", ["", "12345", "1234567", "abcdef", "12345a", None])
def test_verificar_recusa_formato_invalido(ruim):
    assert totp.verificar(SEGREDO_RFC, ruim, instante=1_700_000_000) is None


def test_uri_otpauth_tem_emissor_conta_e_parametros():
    uri = totp.uri_otpauth("ABCDEFGHIJKLMNOP", "ana+teste@netafim.com")
    assert uri.startswith("otpauth://totp/Netafim%3Aana%2Bteste%40netafim.com?")
    assert "secret=ABCDEFGHIJKLMNOP" in uri
    assert "issuer=Netafim" in uri
    assert "digits=6" in uri
    assert "period=30" in uri
