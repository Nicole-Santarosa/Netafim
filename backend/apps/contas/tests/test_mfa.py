"""MFA por TOTP: ativação, login em dois passos, recuperação, desativação e redefinição."""

import uuid
from types import SimpleNamespace

import psycopg
import pytest
from django.db import ProgrammingError, connection
from django.urls import reverse

from apps.contas.servicos import totp

from .conftest import SENHA

LOGIN = reverse("auth-login")
VERIFICAR = reverse("auth-mfa-verificar")
ESTADO = reverse("me-mfa")
INICIAR = reverse("me-mfa-iniciar")
CONFIRMAR = reverse("me-mfa-confirmar")
DESATIVAR = reverse("me-mfa-desativar")


class Relogio:
    """O TOTP lê o relógio do processo; os testes o avançam para obter um passo novo de 30 s."""

    def __init__(self, monkeypatch):
        import time

        self.agora = time.time()
        monkeypatch.setattr(totp, "time", SimpleNamespace(time=lambda: self.agora))

    def avancar(self, passos=1):
        self.agora += totp.PASSO_SEGUNDOS * passos


@pytest.fixture
def relogio(monkeypatch):
    return Relogio(monkeypatch)


@pytest.fixture
def ativar_mfa(como, relogio):
    """Liga o MFA pela API e devolve (segredo, códigos de recuperação)."""

    def _ativar(pessoa):
        api = como(pessoa)
        segredo = api.post(INICIAR).data["segredo"]
        r = api.post(CONFIRMAR, {"codigo": totp.codigo_em(segredo, relogio.agora)}, format="json")
        assert r.status_code == 200, r.data
        relogio.avancar()  # o passo da confirmação já foi consumido
        return segredo, r.data["codigos_recuperacao"]

    return _ativar


def login(api, pessoa, **extra):
    return api.post(
        LOGIN,
        {
            "email": pessoa.email,
            "senha": SENHA,
            "dispositivo": {"id_dispositivo": str(uuid.uuid4())},
            **extra,
        },
        format="json",
    )


def desafio_de(api, pessoa):
    r = login(api, pessoa)
    assert r.status_code == 200, r.data
    assert r.data["mfa_necessario"] is True
    return r.data["desafio"]


# --------------------------------------------------------------------------- ativação


def test_estado_inicial_esta_desligado(como, netafim_usuario):
    r = como(netafim_usuario).get(ESTADO)
    assert r.status_code == 200
    assert r.data == {"ativo": False, "codigos_recuperacao_restantes": 0}


def test_iniciar_devolve_segredo_e_uri_e_nao_liga_o_mfa(como, banco_sql, netafim_usuario):
    r = como(netafim_usuario).post(INICIAR)
    assert r.status_code == 200, r.data
    assert len(r.data["segredo"]) == 32
    assert r.data["otpauth_uri"].startswith("otpauth://totp/")
    assert r.data["segredo"] in r.data["otpauth_uri"]
    assert (
        banco_sql.um("select mfa_ativo from usuario where id_usuario = %s", netafim_usuario.pk)
        is False
    )


def test_confirmar_com_codigo_errado_nao_liga(como, banco_sql, netafim_usuario):
    api = como(netafim_usuario)
    api.post(INICIAR)
    r = api.post(CONFIRMAR, {"codigo": "000000"}, format="json")
    assert r.status_code == 400
    assert r.data["codigo"] == "codigo_mfa_invalido"
    assert (
        banco_sql.um("select mfa_ativo from usuario where id_usuario = %s", netafim_usuario.pk)
        is False
    )


def test_confirmar_sem_iniciar_da_409(como, netafim_usuario):
    r = como(netafim_usuario).post(CONFIRMAR, {"codigo": "123456"}, format="json")
    assert r.status_code == 409
    assert r.data["codigo"] == "mfa_nao_iniciado"


def test_confirmar_liga_devolve_10_codigos_e_audita(como, relogio, banco_sql, netafim_usuario):
    api = como(netafim_usuario)
    segredo = api.post(INICIAR).data["segredo"]
    r = api.post(CONFIRMAR, {"codigo": totp.codigo_em(segredo, relogio.agora)}, format="json")
    assert r.status_code == 200, r.data
    codigos = r.data["codigos_recuperacao"]
    assert len(codigos) == len(set(codigos)) == 10
    assert all(len(c) == 11 and c[5] == "-" for c in codigos)
    assert (
        banco_sql.um("select mfa_ativo from usuario where id_usuario = %s", netafim_usuario.pk)
        is True
    )
    assert api.get(ESTADO).data == {"ativo": True, "codigos_recuperacao_restantes": 10}
    assert banco_sql.um("select count(*) from log_auditoria where acao = 'mfa_alterado'") >= 1


def test_codigos_de_recuperacao_ficam_so_como_hash(ativar_mfa, banco_sql, netafim_usuario):
    _, codigos = ativar_mfa(netafim_usuario)
    guardados = {
        h.strip() for (h,) in banco_sql.todos("select codigo_hash from usuario_mfa_recuperacao")
    }
    assert len(guardados) == 10
    assert not guardados & {c.replace("-", "") for c in codigos}
    assert not guardados & set(codigos)


def test_iniciar_com_mfa_ativo_da_409(como, ativar_mfa, netafim_usuario):
    ativar_mfa(netafim_usuario)
    r = como(netafim_usuario).post(INICIAR)
    assert r.status_code == 409
    assert r.data["codigo"] == "mfa_ja_ativo"


def test_app_django_nao_le_segredo_nem_hash_de_recuperacao(ativar_mfa, netafim_usuario):
    ativar_mfa(netafim_usuario)
    for tabela in ("usuario_mfa", "usuario_mfa_recuperacao"):
        with pytest.raises(ProgrammingError), connection.cursor() as cursor:
            cursor.execute(f"select * from {tabela}")  # noqa: S608


def test_app_django_nao_executa_as_funcoes_de_mfa(ativar_mfa, netafim_usuario):
    ativar_mfa(netafim_usuario)
    with pytest.raises(ProgrammingError), connection.cursor() as cursor:
        cursor.execute("select * from fn_mfa_segredo(%s)", [netafim_usuario.pk])


# --------------------------------------------------------------------------- login


def test_login_com_mfa_nao_abre_sessao_e_devolve_desafio(
    api, ativar_mfa, banco_sql, netafim_usuario
):
    ativar_mfa(netafim_usuario)
    r = login(api, netafim_usuario)
    assert r.status_code == 200, r.data
    assert r.data["mfa_necessario"] is True
    assert r.data["desafio"]
    assert "access" not in r.data
    assert "refresh" not in r.data
    assert banco_sql.um("select count(*) from token_acesso") == 0


def test_login_com_mfa_conclui_com_o_codigo_do_app(api, ativar_mfa, relogio, netafim_usuario):
    segredo, _ = ativar_mfa(netafim_usuario)
    desafio = desafio_de(api, netafim_usuario)
    r = api.post(
        VERIFICAR,
        {"desafio": desafio, "codigo": totp.codigo_em(segredo, relogio.agora)},
        format="json",
    )
    assert r.status_code == 200, r.data
    assert {"access", "refresh", "usuario"} <= r.data.keys()
    api.credentials(HTTP_AUTHORIZATION=f"Bearer {r.data['access']}")
    assert api.get(reverse("me")).data["email"] == netafim_usuario.email


def test_codigo_totp_nao_vale_duas_vezes(api, ativar_mfa, relogio, netafim_usuario):
    segredo, _ = ativar_mfa(netafim_usuario)
    codigo = totp.codigo_em(segredo, relogio.agora)
    primeiro = api.post(
        VERIFICAR, {"desafio": desafio_de(api, netafim_usuario), "codigo": codigo}, format="json"
    )
    assert primeiro.status_code == 200
    repetido = api.post(
        VERIFICAR, {"desafio": desafio_de(api, netafim_usuario), "codigo": codigo}, format="json"
    )
    assert repetido.status_code == 401
    assert repetido.data["codigo"] == "codigo_mfa_invalido"


def test_codigo_errado_nao_abre_sessao(api, ativar_mfa, netafim_usuario):
    ativar_mfa(netafim_usuario)
    r = api.post(
        VERIFICAR,
        {"desafio": desafio_de(api, netafim_usuario), "codigo": "000000"},
        format="json",
    )
    assert r.status_code == 401
    assert r.data["codigo"] == "codigo_mfa_invalido"


def test_cinco_codigos_errados_bloqueiam_a_conta(
    api, ativar_mfa, relogio, banco_sql, netafim_usuario
):
    segredo, _ = ativar_mfa(netafim_usuario)
    desafio = desafio_de(api, netafim_usuario)
    for _ in range(5):
        r = api.post(VERIFICAR, {"desafio": desafio, "codigo": "000000"}, format="json")
        assert r.status_code == 401
    assert banco_sql.um(
        "select bloqueado_ate is not null from usuario where id_usuario = %s", netafim_usuario.pk
    )
    # com a conta bloqueada nem o código certo entra
    r = api.post(
        VERIFICAR,
        {"desafio": desafio, "codigo": totp.codigo_em(segredo, relogio.agora)},
        format="json",
    )
    assert r.status_code == 401
    assert r.data["codigo"] != "codigo_mfa_invalido"


def test_codigo_de_recuperacao_entra_uma_vez_so(api, ativar_mfa, banco_sql, netafim_usuario):
    _, codigos = ativar_mfa(netafim_usuario)
    r = api.post(
        VERIFICAR,
        {"desafio": desafio_de(api, netafim_usuario), "codigo": codigos[0].lower()},
        format="json",
    )
    assert r.status_code == 200, r.data
    de_novo = api.post(
        VERIFICAR,
        {"desafio": desafio_de(api, netafim_usuario), "codigo": codigos[0]},
        format="json",
    )
    assert de_novo.status_code == 401
    assert (
        banco_sql.um("select count(*) from usuario_mfa_recuperacao where usado_em is not null") == 1
    )


def test_desafio_adulterado_ou_expirado_e_recusado(api, ativar_mfa, netafim_usuario, monkeypatch):
    ativar_mfa(netafim_usuario)
    desafio = desafio_de(api, netafim_usuario)
    r = api.post(VERIFICAR, {"desafio": desafio[:-3] + "xxx", "codigo": "123456"}, format="json")
    assert r.status_code == 401
    assert r.data["codigo"] == "desafio_mfa_invalido"

    from apps.contas.servicos import mfa

    monkeypatch.setattr(mfa, "VALIDADE_DESAFIO", -1)
    r = api.post(VERIFICAR, {"desafio": desafio, "codigo": "123456"}, format="json")
    assert r.status_code == 401
    assert r.data["codigo"] == "desafio_mfa_invalido"


def test_senha_errada_nao_gera_desafio(api, ativar_mfa, netafim_usuario):
    ativar_mfa(netafim_usuario)
    r = login(api, netafim_usuario, senha="errada-errada-1")
    assert r.status_code == 401
    assert "desafio" not in r.data


# --------------------------------------------------------------------------- desativar e redefinir


def test_desativar_exige_senha_e_codigo_e_apaga_o_segredo(
    como, ativar_mfa, relogio, banco_sql, netafim_usuario
):
    segredo, _ = ativar_mfa(netafim_usuario)
    api = como(netafim_usuario)
    codigo = totp.codigo_em(segredo, relogio.agora)

    r = api.post(DESATIVAR, {"senha": "errada-errada-1", "codigo": codigo}, format="json")
    assert r.status_code == 400
    assert r.data["codigo"] == "senha_incorreta"

    r = api.post(DESATIVAR, {"senha": SENHA, "codigo": "000000"}, format="json")
    assert r.status_code == 400
    assert r.data["codigo"] == "codigo_mfa_invalido"
    assert (
        banco_sql.um("select mfa_ativo from usuario where id_usuario = %s", netafim_usuario.pk)
        is True
    )

    r = api.post(DESATIVAR, {"senha": SENHA, "codigo": codigo}, format="json")
    assert r.status_code == 204, r.data
    assert (
        banco_sql.um("select mfa_ativo from usuario where id_usuario = %s", netafim_usuario.pk)
        is False
    )
    assert banco_sql.um("select count(*) from usuario_mfa") == 0
    assert banco_sql.um("select count(*) from usuario_mfa_recuperacao") == 0


def test_depois_de_desativar_o_login_volta_a_ser_so_senha(
    api, como, ativar_mfa, relogio, netafim_usuario
):
    segredo, _ = ativar_mfa(netafim_usuario)
    como(netafim_usuario).post(
        DESATIVAR, {"senha": SENHA, "codigo": totp.codigo_em(segredo, relogio.agora)}, format="json"
    )
    r = login(api, netafim_usuario)
    assert r.status_code == 200
    assert "access" in r.data


def test_desativar_sem_mfa_ativo_da_409(como, netafim_usuario):
    r = como(netafim_usuario).post(DESATIVAR, {"senha": SENHA, "codigo": "123456"}, format="json")
    assert r.status_code == 409
    assert r.data["codigo"] == "mfa_inativo"


def test_netafim_admin_redefine_o_mfa_de_outro_usuario(
    api, como, ativar_mfa, netafim_admin, cliente_usuario
):
    ativar_mfa(cliente_usuario)
    r = como(netafim_admin).post(reverse("usuario-mfa-resetar", args=[cliente_usuario.pk]))
    assert r.status_code == 204, r.data
    assert como(cliente_usuario).get(ESTADO).data["ativo"] is False
    assert "access" in login(api, cliente_usuario).data


def test_so_netafim_admin_redefine_mfa(como, ativar_mfa, distrib_admin, cliente_usuario):
    ativar_mfa(cliente_usuario)
    r = como(distrib_admin).post(reverse("usuario-mfa-resetar", args=[cliente_usuario.pk]))
    assert r.status_code in (403, 404)
    assert como(cliente_usuario).get(ESTADO).data["ativo"] is True


# ------------------------------------------------------------------------ banco e disponibilidade


def test_banco_nao_deixa_ligar_mfa_sem_segredo_confirmado(banco_sql, netafim_usuario):
    with pytest.raises(psycopg.errors.CheckViolation):
        banco_sql.executar(
            "update usuario set mfa_ativo = true where id_usuario = %s", netafim_usuario.pk
        )


def test_desligar_pelo_banco_tambem_apaga_segredo_e_codigos(ativar_mfa, banco_sql, netafim_usuario):
    ativar_mfa(netafim_usuario)
    banco_sql.executar(
        "update usuario set mfa_ativo = false where id_usuario = %s", netafim_usuario.pk
    )
    assert banco_sql.um("select count(*) from usuario_mfa") == 0
    assert banco_sql.um("select count(*) from usuario_mfa_recuperacao") == 0


def test_sem_conexao_auth_o_mfa_responde_503(como, netafim_usuario, settings):
    settings.CONEXAO_AUTH_CONFIGURADA = False
    r = como(netafim_usuario).post(INICIAR)
    assert r.status_code == 503
    assert r.data["codigo"] == "mfa_indisponivel"


def test_banco_sem_a_migracao_007_responde_503(como, banco_sql, netafim_usuario):
    banco_sql.executar("drop function fn_mfa_guardar_segredo(integer, text)")
    r = como(netafim_usuario).post(INICIAR)
    assert r.status_code == 503
    assert r.data["codigo"] == "mfa_indisponivel"


def test_migracao_007_e_idempotente(banco_sql):
    from .conftest import MIGRACAO_MFA

    banco_sql.executar(MIGRACAO_MFA.read_text(encoding="utf-8"))
    banco_sql.executar(MIGRACAO_MFA.read_text(encoding="utf-8"))
