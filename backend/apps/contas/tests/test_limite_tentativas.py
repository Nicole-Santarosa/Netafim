"""Limite de tentativas por IP (throttling) nas rotas públicas."""

import uuid

import pytest
from django.urls import reverse
from rest_framework.throttling import SimpleRateThrottle

from .conftest import SENHA

ESQUECI = reverse("auth-senha-esqueci")
LOGIN = reverse("auth-login")


@pytest.fixture
def taxas(monkeypatch):
    """Troca as taxas só neste teste (o DRF lê o dicionário uma vez, na carga do módulo)."""
    novas = {**SimpleRateThrottle.THROTTLE_RATES}
    monkeypatch.setattr(SimpleRateThrottle, "THROTTLE_RATES", novas)
    return novas


def test_cadastro_e_esqueci_a_senha_dividem_o_mesmo_limite(api, taxas):
    taxas["cadastro"] = "3/hour"
    codigos = [
        api.post(ESQUECI, {"email": f"a{n}@x.com"}, format="json").status_code for n in range(5)
    ]
    assert codigos == [202, 202, 202, 429, 429]


def test_excedido_o_limite_a_resposta_e_429_sem_codigo(api, taxas):
    taxas["cadastro"] = "1/hour"
    api.post(ESQUECI, {"email": "a@x.com"}, format="json")
    r = api.post(ESQUECI, {"email": "b@x.com"}, format="json")
    assert r.status_code == 429
    assert "detail" in r.data
    assert "codigo" not in r.data


def test_estourar_um_grupo_nao_bloqueia_os_outros(api, taxas, netafim_usuario):
    taxas["cadastro"] = "1/hour"
    api.post(ESQUECI, {"email": "a@x.com"}, format="json")
    assert api.post(ESQUECI, {"email": "b@x.com"}, format="json").status_code == 429
    r = api.post(
        LOGIN,
        {
            "email": netafim_usuario.email,
            "senha": SENHA,
            "dispositivo": {"id_dispositivo": str(uuid.uuid4())},
        },
        format="json",
    )
    assert r.status_code == 200


def test_login_tem_limite_proprio(api, taxas):
    taxas["login"] = "2/min"
    corpo = {
        "email": "x@netafim.com",
        "senha": "errada",
        "dispositivo": {"id_dispositivo": str(uuid.uuid4())},
    }
    codigos = [api.post(LOGIN, corpo, format="json").status_code for _ in range(4)]
    assert codigos == [401, 401, 429, 429]
