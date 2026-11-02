"""
DT-002, sem banco: o hash da senha é só de escrita no Django e só chega pela conexão ``auth``.

Estes testes rodam em qualquer máquina. Os que exigem PostgreSQL (privilégios reais do app_django e
do app_auth) estão em ``tests/test_hash_senha.py``.
"""

from datetime import UTC, datetime

import pytest
from django.contrib.auth.hashers import make_password
from django.db import models
from django.db.models.sql import UpdateQuery

from apps.contas.models import Dispositivo, Usuario
from apps.contas.servicos import credenciais

SENHA = "Irrigacao#Segura2026"


# ---------------------------------------------------------------- o SELECT nunca lê o hash


def _colunas(queryset) -> str:
    return str(queryset.query).split(" FROM ")[0]


@pytest.mark.parametrize(
    "consulta",
    [
        lambda: Usuario.objects.all(),
        lambda: Usuario.todos.all(),
        lambda: Usuario.objects.select_related("perfil", "distribuidor", "cliente"),
        # join vindo de OUTRO modelo (ex.: atendimento.solicitante): o caso que o defer() não cobre
        lambda: Dispositivo.objects.select_related("usuario"),
    ],
    ids=["objects", "todos", "select_related", "join_de_outro_modelo"],
)
def test_select_troca_a_coluna_password_por_null(consulta):
    colunas = _colunas(consulta())
    assert '"password"' not in colunas
    assert "NULL" in colunas


def test_update_continua_gravando_o_hash():
    consulta = UpdateQuery(Usuario)
    consulta.add_update_values({"password": make_password(SENHA)})
    comando, _ = consulta.get_compiler("default").as_sql()
    assert '"password" =' in comando


# --------------------------------------------------------------------------- uso indevido do hash


def test_check_password_do_model_recusa_ler_o_hash():
    with pytest.raises(NotImplementedError, match="credenciais.conferir"):
        Usuario(email="a@b.com").check_password(SENHA)


def test_has_usable_password_do_model_recusa_ler_o_hash():
    with pytest.raises(NotImplementedError, match="senha_utilizavel"):
        Usuario(email="a@b.com").has_usable_password()


def test_convite_pendente_acompanha_senha_alterada_em():
    convidado = Usuario(email="convidado@gmail.com")
    convidado.set_unusable_password()
    assert convidado.aguarda_definir_senha is True

    convidado.set_password(SENHA)  # aceitou o convite
    assert convidado.aguarda_definir_senha is False


# ---------------------------------------------------------------- save() nunca grava None


@pytest.fixture
def atualizacao(monkeypatch):
    """Captura os argumentos que chegariam ao UPDATE, sem tocar no banco."""
    capturado: dict = {}

    def falso(self, *args, **kwargs):
        capturado.clear()
        capturado.update(kwargs)

    monkeypatch.setattr(models.Model, "save", falso)
    return capturado


def _carregado() -> Usuario:
    usuario = Usuario(id_usuario=7, nome="Maria", email="maria@gmail.com")
    usuario._state.adding = False  # como se tivesse vindo do banco (password = None)
    return usuario


def test_save_comum_de_usuario_carregado_nao_regrava_o_hash(atualizacao):
    _carregado().save()
    assert "password" not in atualizacao["update_fields"]
    assert "nome" in atualizacao["update_fields"]


def test_update_fields_com_password_sem_hash_novo_nao_regrava(atualizacao):
    _carregado().save(update_fields=["password", "nome"])
    assert atualizacao["update_fields"] == ["nome"]


def test_hash_novo_e_gravado_uma_unica_vez(atualizacao):
    usuario = _carregado()
    usuario.set_password(SENHA)
    usuario.save(update_fields=["password", "senha_alterada_em"])
    assert atualizacao["update_fields"] == ["password", "senha_alterada_em"]

    usuario.save(update_fields=["password", "nome"])  # segunda gravação: o hash já foi
    assert atualizacao["update_fields"] == ["nome"]


def test_usuario_novo_grava_o_hash_no_insert(atualizacao):
    novo = Usuario(nome="Novo", email="novo@gmail.com")
    novo.set_password(SENHA)
    novo.save()
    assert atualizacao.get("update_fields") is None  # INSERT: o Django grava todas as colunas
    assert novo.password.startswith("argon2$")


# --------------------------------------------------------------------------- credenciais


class _Cursor:
    def __init__(self, linha):
        self.linha = linha
        self.chamadas = []

    def __enter__(self):
        return self

    def __exit__(self, *erro):
        return False

    def execute(self, comando, params):
        self.chamadas.append((comando, params))

    def fetchone(self):
        return self.linha


class _Conexao:
    def __init__(self, cursor):
        self._cursor = cursor

    def cursor(self):
        return self._cursor


@pytest.fixture
def conexao_auth(monkeypatch):
    """Troca as conexões por uma falsa e devolve o cursor, para ver o que foi consultado."""

    def _preparar(linha):
        cursor = _Cursor(linha)
        monkeypatch.setattr(credenciais, "connections", {"auth": _Conexao(cursor)})
        return cursor

    return _preparar


def test_por_email_consulta_a_funcao_pela_conexao_auth(conexao_auth):
    hash_da_senha = make_password(SENHA)
    cursor = conexao_auth((9, hash_da_senha, "ativo", 0, None))

    credencial = credenciais.por_email("  Maria@Gmail.com ")

    assert credencial == credenciais.Credencial(9, hash_da_senha, "ativo", 0, None)
    comando, params = cursor.chamadas[0]
    assert "fn_credencial_por_email" in comando
    assert params == ["maria@gmail.com"]  # sem espaços e em minúsculas


def test_por_id_consulta_a_funcao_pelo_id(conexao_auth):
    cursor = conexao_auth((9, make_password(SENHA), "ativo", 2, datetime(2026, 1, 1, tzinfo=UTC)))

    assert credenciais.por_id("9").falhas_login == 2
    comando, params = cursor.chamadas[0]
    assert "fn_credencial_por_id" in comando
    assert params == [9]


def test_conta_inexistente_ou_excluida_devolve_none(conexao_auth):
    conexao_auth(None)
    assert credenciais.por_email("ninguem@gmail.com") is None
    assert credenciais.por_id(1) is None


def test_conferir_senha_certa_e_errada():
    credencial = credenciais.Credencial(1, make_password(SENHA), "ativo", 0, None)
    assert credenciais.conferir(credencial, SENHA) is True
    assert credenciais.conferir(credencial, "outra-senha") is False


def test_conferir_sem_credencial_nega_sem_erro():
    assert credenciais.conferir(None, SENHA) is False


def test_conferir_nao_aceita_hash_inutilizado():
    credencial = credenciais.Credencial(1, make_password(None), "pendente", 0, None)
    assert credenciais.conferir(credencial, SENHA) is False
    assert credenciais.conferir(credencial, "") is False


def test_hash_nunca_aparece_no_repr_da_credencial():
    credencial = credenciais.Credencial(1, make_password(SENHA), "ativo", 0, None)
    texto = f"{credencial!r} {credencial}"
    assert "argon2" not in texto and "$" not in texto


def test_senha_utilizavel(conexao_auth):
    conexao_auth((1, make_password(SENHA), "ativo", 0, None))
    assert credenciais.senha_utilizavel(1) is True

    conexao_auth((1, make_password(None), "pendente", 0, None))  # convite não aceito
    assert credenciais.senha_utilizavel(1) is False

    conexao_auth(None)  # conta inexistente
    assert credenciais.senha_utilizavel(1) is False


# --------------------------------------------------------------------------- configuração


def test_alias_auth_e_separado_da_conexao_comum(settings):
    bancos = settings.DATABASES
    assert "auth" in bancos
    assert bancos["auth"]["USER"] != bancos["default"]["USER"]
    assert bancos["auth"]["ATOMIC_REQUESTS"] is False
    assert bancos["default"]["ATOMIC_REQUESTS"] is True
