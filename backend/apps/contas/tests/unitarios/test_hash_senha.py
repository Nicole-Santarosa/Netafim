"""
DT-002, sem banco: o hash da senha é só de escrita no Django e só chega pela conexão ``auth``.

Estes testes rodam em qualquer máquina. Os que exigem PostgreSQL (privilégios reais do app_django e
do app_auth) estão em ``tests/test_hash_senha.py``.
"""

from datetime import UTC, datetime

import pytest
from django.contrib.auth.hashers import make_password
from django.core.exceptions import ImproperlyConfigured
from django.db import OperationalError, ProgrammingError, models
from django.db.models.sql import UpdateQuery

from apps.contas.apps import verificar_conexao_auth
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


# ---------------------------------------------------------------- modo seguro x modo legado


def _erro_de_banco(classe, estado=None):
    """Erro do Django com a causa do psycopg (que traz o sqlstate), sem tocar no banco."""
    erro = classe("falha simulada")
    if estado is not None:
        erro.__cause__ = type("Causa", (Exception,), {"sqlstate": estado})()
    return erro


class _CursorQueFalha(_Cursor):
    def __init__(self, erro):
        super().__init__(None)
        self.erro = erro

    def execute(self, comando, params):
        super().execute(comando, params)
        raise self.erro


class _ConexoesProibidas(dict):
    """Falha se o código tentar usar a conexão auth (o modo legado não pode)."""

    def __getitem__(self, chave):
        raise AssertionError("o modo legado não pode abrir a conexão auth")


@pytest.fixture
def conexao_comum(monkeypatch):
    def _preparar(cursor):
        monkeypatch.setattr(credenciais, "connection", _Conexao(cursor))
        return cursor

    return _preparar


@pytest.fixture
def sem_conexao_auth(settings, monkeypatch):
    settings.CONEXAO_AUTH_CONFIGURADA = False
    monkeypatch.setattr(credenciais, "connections", _ConexoesProibidas())


def test_modo_legado_le_pela_conexao_comum_sem_abrir_a_auth(sem_conexao_auth, conexao_comum):
    hash_da_senha = make_password(SENHA)
    cursor = conexao_comum(_Cursor((9, hash_da_senha, "ativo", 0, None)))

    credencial = credenciais.por_email(" Maria@Gmail.com ")

    assert credenciais.modo_seguro() is False
    assert credencial == credenciais.Credencial(9, hash_da_senha, "ativo", 0, None)
    comando, params = cursor.chamadas[0]
    assert "from usuario" in comando and "fn_credencial" not in comando
    assert "deletado_em is null" in comando  # conta excluída não existe, como nas funções
    assert params == ["maria@gmail.com"]


def test_modo_legado_por_id(sem_conexao_auth, conexao_comum):
    cursor = conexao_comum(_Cursor((9, make_password(SENHA), "ativo", 0, None)))
    assert credenciais.por_id(9).id_usuario == 9
    assert cursor.chamadas[0][1] == [9]


def test_modo_legado_depois_do_revoke_falha_com_mensagem_clara(sem_conexao_auth, conexao_comum):
    conexao_comum(_CursorQueFalha(_erro_de_banco(ProgrammingError, "42501")))
    with pytest.raises(ImproperlyConfigured, match="DB_AUTH_PASSWORD"):
        credenciais.por_email("maria@gmail.com")


def test_modo_legado_nao_mascara_outros_erros_de_banco(sem_conexao_auth, conexao_comum):
    conexao_comum(_CursorQueFalha(_erro_de_banco(ProgrammingError, "42P01")))  # tabela inexistente
    with pytest.raises(ProgrammingError):
        credenciais.por_email("maria@gmail.com")


def test_modo_seguro_nao_toca_na_conexao_comum(conexao_auth, monkeypatch):
    conexao_auth((9, make_password(SENHA), "ativo", 0, None))
    monkeypatch.setattr(credenciais, "connection", None)  # qualquer uso levantaria erro
    assert credenciais.modo_seguro() is True
    assert credenciais.por_email("maria@gmail.com").id_usuario == 9


@pytest.mark.parametrize(
    "erro",
    [
        _erro_de_banco(OperationalError),  # papel ou senha da auth inexistentes (004 não aplicada)
        _erro_de_banco(ProgrammingError, "42883"),  # função fn_credencial_* ainda não existe
    ],
    ids=["auth_inexistente", "funcao_inexistente"],
)
def test_modo_seguro_sem_a_004_cai_no_legado_e_avisa(erro, monkeypatch, conexao_comum, caplog):
    monkeypatch.setattr(credenciais, "connections", {"auth": _Conexao(_CursorQueFalha(erro))})
    hash_da_senha = make_password(SENHA)
    conexao_comum(_Cursor((9, hash_da_senha, "ativo", 0, None)))

    with caplog.at_level("WARNING", logger=credenciais.log.name):
        credencial = credenciais.por_email("maria@gmail.com")

    assert credencial.hash == hash_da_senha
    assert "conexao auth indisponivel" in caplog.text
    assert hash_da_senha not in caplog.text  # o hash nunca vai para o log


def test_modo_seguro_sem_privilegio_na_funcao_nao_cai_no_legado(monkeypatch, conexao_comum):
    erro = _erro_de_banco(ProgrammingError, "42501")  # a função existe, mas a auth não pode usá-la
    monkeypatch.setattr(credenciais, "connections", {"auth": _Conexao(_CursorQueFalha(erro))})
    comum = conexao_comum(_Cursor((9, make_password(SENHA), "ativo", 0, None)))

    with pytest.raises(ProgrammingError):
        credenciais.por_email("maria@gmail.com")
    assert comum.chamadas == []  # nem tentou o modo legado


def test_auth_fora_do_ar_depois_do_revoke_sobe_o_erro_da_auth(monkeypatch, conexao_comum):
    erro_auth = _erro_de_banco(OperationalError)
    monkeypatch.setattr(credenciais, "connections", {"auth": _Conexao(_CursorQueFalha(erro_auth))})
    conexao_comum(_CursorQueFalha(_erro_de_banco(ProgrammingError, "42501")))  # REVOKE já aplicado

    with pytest.raises(OperationalError) as info:
        credenciais.por_email("maria@gmail.com")
    assert info.value is erro_auth  # o problema é a conexão auth, não a configuração legada


# --------------------------------------------------------------------------- configuração


def test_alias_auth_e_separado_da_conexao_comum(settings):
    """Nos testes o pytest-env define DB_AUTH_PASSWORD, então a conexão auth existe."""
    bancos = settings.DATABASES
    assert settings.CONEXAO_AUTH_CONFIGURADA is True
    assert "auth" in bancos
    assert bancos["auth"]["USER"] != bancos["default"]["USER"]
    assert bancos["auth"]["ATOMIC_REQUESTS"] is False
    assert bancos["default"]["ATOMIC_REQUESTS"] is True


def test_check_avisa_so_no_modo_legado(settings):
    settings.CONEXAO_AUTH_CONFIGURADA = True
    assert verificar_conexao_auth(None) == []

    settings.CONEXAO_AUTH_CONFIGURADA = False
    avisos = verificar_conexao_auth(None)
    assert [a.id for a in avisos] == ["contas.W001"]
    assert avisos[0].level == 30  # Warning: não derruba o --fail-level ERROR do CI
