"""
Infraestrutura dos testes do módulo de acesso.

Os testes rodam EXATAMENTE como a API roda no ar: conectados como ``app_django`` (sem BYPASSRLS),
contra um PostgreSQL com o retrato do banco do grupo (``backend/db/snapshot_banco_atual.sql``): RLS,
gatilhos e funções reais. Por isso cada teste ganha um banco novo, criado a partir de um
banco-modelo (CREATE DATABASE ... TEMPLATE), em vez de uma transação revertida: as transações da API
precisam ser de verdade (ator por transação, contagem de falhas de login gravada mesmo na falha,
etc.).

Quem monta e derruba os bancos é um superusuário, o do ``DATABASE_URL``. Só os dados de apoio
(organizações, usuários) são gravados por ele, direto no banco; tudo o que é testado passa pela API
como ``app_django``.
"""

import itertools
import os
import uuid
from dataclasses import dataclass
from pathlib import Path

import psycopg
import pytest
from django.contrib.auth.hashers import make_password
from django.db import connections
from psycopg import sql
from psycopg.conninfo import conninfo_to_dict, make_conninfo
from rest_framework.test import APIClient
from rest_framework_simplejwt.tokens import AccessToken

from apps.contas.constantes import NivelPerfil, SituacaoUsuario, TipoOrganizacao

SENHA = "Irrigacao#Segura2026"
RETRATO = Path(__file__).resolve().parents[3] / "db" / "snapshot_banco_atual.sql"
PROPOSTA_REVOGAR_TOKENS = (
    Path(__file__).resolve().parents[3] / "db" / "propostas" / "001_fn_revogar_tokens_usuario.sql"
)
SENHA_APP_DJANGO = os.environ.get("TEST_APP_DJANGO_PASSWORD", "app_django_teste")
_seq = itertools.count(1)


def _conninfo(nome_banco: str) -> str:
    dados = conninfo_to_dict(os.environ["DATABASE_URL"])
    dados["dbname"] = nome_banco
    return make_conninfo(**dados)


# --------------------------------------------------------------------------- bancos


@pytest.fixture(scope="session")
def _modelo():
    """Banco-modelo com o retrato aplicado (criado uma vez por execução)."""
    nome = f"netafim_modelo_{os.getpid()}"
    with psycopg.connect(_conninfo("postgres"), autocommit=True) as adm:
        adm.execute(sql.SQL("DROP DATABASE IF EXISTS {} WITH (FORCE)").format(sql.Identifier(nome)))
        adm.execute(sql.SQL("CREATE DATABASE {}").format(sql.Identifier(nome)))
    with psycopg.connect(_conninfo(nome), autocommit=True) as conn:
        conn.execute(RETRATO.read_text(encoding="utf-8"))
    yield nome
    with psycopg.connect(_conninfo("postgres"), autocommit=True) as adm:
        adm.execute(sql.SQL("DROP DATABASE IF EXISTS {} WITH (FORCE)").format(sql.Identifier(nome)))


@pytest.fixture(autouse=True)
def banco(_modelo, django_db_blocker, settings):
    """Um banco novo por teste; o Django conecta nele como app_django."""
    nome = f"netafim_t_{uuid.uuid4().hex[:12]}"
    with psycopg.connect(_conninfo("postgres"), autocommit=True) as adm:
        adm.execute(
            sql.SQL("CREATE DATABASE {} TEMPLATE {}").format(
                sql.Identifier(nome), sql.Identifier(_modelo)
            )
        )
    base = conninfo_to_dict(os.environ["DATABASE_URL"])
    conexao = connections["default"]
    conexao.close()
    originais = {
        k: conexao.settings_dict.get(k) for k in ("NAME", "USER", "PASSWORD", "HOST", "PORT")
    }
    conexao.settings_dict.update(
        NAME=nome,
        USER="app_django",
        PASSWORD=SENHA_APP_DJANGO,
        HOST=base.get("host", "localhost"),
        PORT=base.get("port", "5432"),
    )
    # Hash rápido nos testes; o teste do cadastro confere o Argon2 de produção à parte.
    settings.PASSWORD_HASHERS = [
        "django.contrib.auth.hashers.MD5PasswordHasher",
        "django.contrib.auth.hashers.Argon2PasswordHasher",
    ]
    with django_db_blocker.unblock():
        yield nome
        connections.close_all()
    conexao.settings_dict.update(originais)
    with psycopg.connect(_conninfo("postgres"), autocommit=True) as adm:
        adm.execute(sql.SQL("DROP DATABASE IF EXISTS {} WITH (FORCE)").format(sql.Identifier(nome)))


class Consulta:
    """Acesso de superusuário ao banco do teste (preparar dados e conferir o que a API gravou)."""

    def __init__(self, conexao):
        self._c = conexao

    def executar(self, comando: str, *args):
        return self._c.execute(comando, args or None)

    def todos(self, comando: str, *args) -> list[tuple]:
        return self._c.execute(comando, args or None).fetchall()

    def um(self, comando: str, *args):
        linha = self._c.execute(comando, args or None).fetchone()
        return None if linha is None else (linha[0] if len(linha) == 1 else linha)


@pytest.fixture
def banco_sql(banco):
    with psycopg.connect(_conninfo(banco), autocommit=True) as conn:
        yield Consulta(conn)


@pytest.fixture
def com_revogar_tokens(banco_sql):
    """Aplica a função proposta fn_revogar_tokens_usuario (que ainda não existe no banco do
    grupo)."""
    banco_sql.executar(PROPOSTA_REVOGAR_TOKENS.read_text(encoding="utf-8"))


# --------------------------------------------------------------------------- pessoas e organizações


@dataclass
class Org:
    pk: int
    nome: str
    dominio: str | None = None


@dataclass
class Pessoa:
    pk: int
    email: str
    tipo_organizacao: str | None
    distribuidor_id: int | None = None
    cliente_id: int | None = None

    @property
    def id_usuario(self) -> int:  # usado pelo simplejwt
        return self.pk


@pytest.fixture
def api():
    return APIClient()


@pytest.fixture
def perfil_id(banco_sql):
    def _perfil(tipo: str, nivel: str) -> int:
        return banco_sql.um(
            "select id_perfil from perfil where tipo_organizacao = %s and nivel = %s", tipo, nivel
        )

    return _perfil


def _nova_org(banco_sql, tabela, id_coluna, nome, **colunas):
    nomes = ", ".join(["nome", *colunas])
    marcas = ", ".join(["%s"] * (1 + len(colunas)))
    return banco_sql.um(
        f"insert into {tabela} ({nomes}) values ({marcas}) returning {id_coluna}",  # noqa: S608
        nome,
        *colunas.values(),
    )


@pytest.fixture
def distribuidor(banco_sql):
    pk = _nova_org(
        banco_sql, "distribuidor", "id_distribuidor", "Agro Irriga Ltda", regiao="Ribeirão Preto"
    )
    return Org(pk, "Agro Irriga Ltda", "agroirriga.com.br")


@pytest.fixture
def outro_distribuidor(banco_sql):
    pk = _nova_org(
        banco_sql,
        "distribuidor",
        "id_distribuidor",
        "Campo Verde Revenda",
        regiao="Triângulo Mineiro",
    )
    return Org(pk, "Campo Verde Revenda", "campoverde.com.br")


@pytest.fixture
def cliente(banco_sql, distribuidor):
    pk = _nova_org(
        banco_sql, "cliente", "id_cliente", "Fazenda Santa Clara", id_distribuidor=distribuidor.pk
    )
    return Org(pk, "Fazenda Santa Clara")


@pytest.fixture
def outro_cliente(banco_sql, outro_distribuidor):
    pk = _nova_org(
        banco_sql,
        "cliente",
        "id_cliente",
        "Sítio Boa Esperança",
        id_distribuidor=outro_distribuidor.pk,
    )
    return Org(pk, "Sítio Boa Esperança")


@pytest.fixture
def dominios(banco_sql, distribuidor, outro_distribuidor):
    banco_sql.executar(
        "insert into dominio_email_corporativo (dominio, tipo_organizacao) "
        "values ('netafim.com', 'netafim')"
    )
    for org in (distribuidor, outro_distribuidor):
        banco_sql.executar(
            "insert into dominio_email_corporativo (dominio, tipo_organizacao, id_distribuidor) "
            "values (%s, 'distribuidor', %s)",
            org.dominio,
            org.pk,
        )


@pytest.fixture
def criar_usuario(banco_sql, dominios, perfil_id):
    """Cria um usuário direto no banco (como dono). O domínio do e-mail acompanha a organização."""

    def _criar(
        tipo=TipoOrganizacao.NETAFIM,
        nivel=NivelPerfil.USUARIO,
        *,
        org=None,
        situacao=SituacaoUsuario.ATIVO,
        email=None,
        senha=SENHA,
        **extra,
    ) -> Pessoa:
        n = next(_seq)
        if email is None:
            dominio = {
                TipoOrganizacao.NETAFIM: "netafim.com",
                TipoOrganizacao.CLIENTE: "gmail.com",
                TipoOrganizacao.DISTRIBUIDOR: getattr(org, "dominio", None),
            }[tipo]
            email = f"pessoa{n}@{dominio}"
        colunas = {
            "nome": f"Pessoa {n}",
            "email": email,
            "password": make_password(senha),
            "situacao": situacao,
            "tipo_organizacao": tipo,
            "id_perfil": perfil_id(tipo, nivel),
            "id_distribuidor": org.pk if tipo == TipoOrganizacao.DISTRIBUIDOR else None,
            "id_cliente": org.pk if tipo == TipoOrganizacao.CLIENTE else None,
            **extra,
        }
        nomes = ", ".join(colunas)
        marcas = ", ".join(["%s"] * len(colunas))
        pk = banco_sql.um(
            f"insert into usuario ({nomes}) values ({marcas}) returning id_usuario",  # noqa: S608
            *colunas.values(),
        )
        return Pessoa(pk, email, tipo, colunas["id_distribuidor"], colunas["id_cliente"])

    return _criar


@pytest.fixture
def netafim_admin(criar_usuario):
    return criar_usuario(TipoOrganizacao.NETAFIM, NivelPerfil.ADMIN)


@pytest.fixture
def netafim_usuario(criar_usuario):
    return criar_usuario(TipoOrganizacao.NETAFIM, NivelPerfil.USUARIO)


@pytest.fixture
def distrib_admin(criar_usuario, distribuidor):
    return criar_usuario(TipoOrganizacao.DISTRIBUIDOR, NivelPerfil.ADMIN, org=distribuidor)


@pytest.fixture
def distrib_usuario(criar_usuario, distribuidor):
    return criar_usuario(TipoOrganizacao.DISTRIBUIDOR, NivelPerfil.USUARIO, org=distribuidor)


@pytest.fixture
def cliente_admin(criar_usuario, cliente):
    return criar_usuario(TipoOrganizacao.CLIENTE, NivelPerfil.ADMIN, org=cliente)


@pytest.fixture
def cliente_usuario(criar_usuario, cliente):
    return criar_usuario(TipoOrganizacao.CLIENTE, NivelPerfil.USUARIO, org=cliente)


@pytest.fixture
def como():
    """Retorna um APIClient autenticado como a pessoa informada (JWT real)."""

    def _como(pessoa):
        cliente_api = APIClient()
        cliente_api.credentials(HTTP_AUTHORIZATION=f"Bearer {AccessToken.for_user(pessoa)}")
        return cliente_api

    return _como
