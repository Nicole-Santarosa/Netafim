import os
import uuid

import psycopg
import pytest
from django.db import connection
from psycopg.conninfo import conninfo_to_dict


@pytest.fixture(scope="session")
def django_db_setup():
    """Não faz nada de propósito.

    O padrão do pytest-django é criar um banco novo de teste e rodar as
    migrations nele. Aqui isso não serve: o banco local já existe, montado pelos
    scripts SQL de infra/db, e o Django nunca cria tabelas (os models são
    managed = False e não há migrations). Os testes usam esse banco como está.
    """


# Conexão de administrador (postgres), usada só para montar dados de teste.
# Ela passa por cima do RLS, então fica restrita a banco local, igual ao
# settings_teste.py.
DSN_ADMIN_PADRAO = "postgresql://postgres:senha_local_teste@127.0.0.1:5544/netafim_teste"


@pytest.fixture(scope="session")
def conexao_admin():
    dsn = os.environ.get("TESTE_ADMIN_DSN", DSN_ADMIN_PADRAO)
    host = conninfo_to_dict(dsn).get("host")
    if host not in ("127.0.0.1", "localhost"):
        raise pytest.UsageError(
            "A conexão de administrador dos testes só pode apontar para um banco "
            f"local (127.0.0.1 ou localhost), mas o host é {host!r}. "
            "Confira a variável TESTE_ADMIN_DSN."
        )
    with psycopg.connect(dsn) as conexao:
        yield conexao


@pytest.fixture(scope="session")
def mundo(conexao_admin):
    """Cria e confirma (commit) os dados mínimos que os testes precisam: um
    distribuidor, um cliente atendido por ele e um usuário ativo de cliente com
    o perfil Cliente Usuário. Devolve os ids em um dicionário.

    O sufixo aleatório evita colisão de e-mail e nome, então o mesmo banco pode
    ser reaproveitado entre execuções sem limpar nada.
    """
    sufixo = uuid.uuid4().hex[:8]
    with conexao_admin.cursor() as cur:
        cur.execute(
            "insert into distribuidor (nome, regiao) values (%s, %s) returning id_distribuidor",
            (f"Distribuidor Teste {sufixo}", "Regiao Teste"),
        )
        id_distribuidor = cur.fetchone()[0]

        cur.execute(
            "insert into cliente (nome, id_distribuidor) values (%s, %s) returning id_cliente",
            (f"Cliente Teste {sufixo}", id_distribuidor),
        )
        id_cliente = cur.fetchone()[0]

        # Senha começando com "!" é a senha inutilizada que o CHECK aceita: o
        # usuário existe, mas ninguém consegue fazer login com ele.
        cur.execute(
            """
            insert into usuario (nome, email, password, situacao, tipo_organizacao,
                                 id_perfil, id_cliente)
            select %s, %s, '!sem-login', 'ativo', 'cliente', p.id_perfil, %s
              from perfil p where p.nome_perfil = 'Cliente Usuário'
            returning id_usuario
            """,
            (f"Usuario Cliente {sufixo}", f"cliente-{sufixo}@exemplo.com", id_cliente),
        )
        id_usuario = cur.fetchone()[0]
    conexao_admin.commit()
    return {
        "id_distribuidor": id_distribuidor,
        "id_cliente": id_cliente,
        "id_usuario_cliente": id_usuario,
    }


@pytest.fixture
def entrar_como():
    """Devolve uma função que informa ao banco quem está agindo, como a API faz
    em cada requisição. O true do set_config vale só até o fim da transação (no
    teste, a transação que o pytest abre e desfaz), e o RLS usa esse valor.
    """

    def entrar(id_usuario):
        with connection.cursor() as cursor:
            cursor.execute(
                "select set_config('app.current_user_id', %s, true)", [str(id_usuario)]
            )

    return entrar
