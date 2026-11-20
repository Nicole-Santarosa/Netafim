import uuid

import pytest
from django.db import connection

# Reaproveita o domínio de e-mail de teste já usado nos testes de alteração.
from apps.atendimentos.tests.test_view_alteracao import DOMINIO_DE_TESTE

# Regras que os testes conferem (seção 5.3 do projeto), escritas à mão aqui e não
# copiadas do código do banco, para o teste poder discordar dele:
#
# Visualização
#   Netafim Admin        todos
#   Netafim Usuário      os que abriu e os atribuídos a ele (técnico)
#   Distribuidor Usuário os que abriu e os atribuídos a ele (técnico)
#   Distribuidor Admin   os do seu distribuidor (destino ou origem) e os de
#                        clientes da sua carteira
#   Cliente Admin        todos da sua empresa
#   Cliente Usuário      somente os que abriu
# Alteração (UPDATE)
#   o atendimento precisa estar visível; Netafim e distribuidor alteram qualquer
#   um que enxergam; cliente (Admin ou Usuário) só altera os que ele abriu.


def criar_usuario_do_perfil(conexao_admin, nome_perfil, tipo, id_distribuidor=None, id_cliente=None):
    """Cria, pela conexão de administrador e com commit, um usuário ativo do
    perfil dado. A organização segue o CHECK ck_usuario_organizacao: netafim sem
    nenhuma, distribuidor com id_distribuidor, cliente com id_cliente.

    O gatilho de usuário exige e-mail de domínio autorizado para Netafim e para
    distribuidor (cada distribuidor tem o seu domínio); o banco local não traz
    nenhum, então o domínio de teste é cadastrado aqui (se já existir, nada
    muda). Cliente não tem essa exigência.
    """
    sufixo = uuid.uuid4().hex[:8]
    if tipo == "netafim":
        dominio = DOMINIO_DE_TESTE
    elif tipo == "distribuidor":
        dominio = f"dist{id_distribuidor}.{DOMINIO_DE_TESTE}"
    else:
        dominio = "exemplo.com"
    with conexao_admin.cursor() as cur:
        if tipo != "cliente":
            cur.execute(
                """
                insert into dominio_email_corporativo (dominio, tipo_organizacao, id_distribuidor)
                values (%s, %s, %s) on conflict (dominio) do nothing
                """,
                (dominio, tipo, id_distribuidor),
            )
        cur.execute(
            """
            insert into usuario (nome, email, password, situacao, tipo_organizacao,
                                 id_perfil, id_distribuidor, id_cliente)
            select %s, %s, '!sem-login', 'ativo', %s, p.id_perfil, %s, %s
              from perfil p where p.nome_perfil = %s
            returning id_usuario
            """,
            (f"{nome_perfil} {sufixo}", f"rls-{sufixo}@{dominio}", tipo,
             id_distribuidor, id_cliente, nome_perfil),
        )
        id_usuario = cur.fetchone()[0]
    conexao_admin.commit()
    return id_usuario


@pytest.fixture(scope="session")
def elenco(conexao_admin):
    """Monta, com commit, os seis perfis e os atendimentos que exercitam cada
    regra. O sufixo aleatório de cada usuário evita colisão entre execuções, e
    os testes só olham os atendimentos criados aqui.

    Organizações: distribuidor 1 com o cliente 1; distribuidor 2 com o cliente 2.
    Usuários: um de cada perfil na organização 1, mais o Distribuidor Admin e o
    Cliente Usuário da organização 2.

    Não usa a fixture mundo de propósito: os atendimentos daqui ficam confirmados
    no banco, e se fossem do cliente do mundo apareceriam nas listagens dos
    outros testes, que esperam encontrar só o que eles mesmos abriram.
    """
    def criar_organizacao(numero):
        sufixo = uuid.uuid4().hex[:8]
        with conexao_admin.cursor() as cur:
            cur.execute(
                "insert into distribuidor (nome, regiao) values (%s, %s) returning id_distribuidor",
                (f"Distribuidor RLS {numero} {sufixo}", f"Regiao RLS {numero}"),
            )
            id_distribuidor = cur.fetchone()[0]
            cur.execute(
                "insert into cliente (nome, id_distribuidor) values (%s, %s) returning id_cliente",
                (f"Cliente RLS {numero} {sufixo}", id_distribuidor),
            )
            id_cliente = cur.fetchone()[0]
        conexao_admin.commit()
        return id_distribuidor, id_cliente

    d1, c1 = criar_organizacao(1)
    d2, c2 = criar_organizacao(2)

    u = {
        "netafim_admin": criar_usuario_do_perfil(conexao_admin, "Netafim Admin", "netafim"),
        "netafim_usuario": criar_usuario_do_perfil(conexao_admin, "Netafim Usuário", "netafim"),
        "dist_admin": criar_usuario_do_perfil(conexao_admin, "Distribuidor Admin", "distribuidor", id_distribuidor=d1),
        "dist_usuario": criar_usuario_do_perfil(conexao_admin, "Distribuidor Usuário", "distribuidor", id_distribuidor=d1),
        "cliente_admin": criar_usuario_do_perfil(conexao_admin, "Cliente Admin", "cliente", id_cliente=c1),
        "cliente_usuario": criar_usuario_do_perfil(conexao_admin, "Cliente Usuário", "cliente", id_cliente=c1),
        "dist_admin_2": criar_usuario_do_perfil(conexao_admin, "Distribuidor Admin", "distribuidor", id_distribuidor=d2),
        "cliente_usuario_2": criar_usuario_do_perfil(conexao_admin, "Cliente Usuário", "cliente", id_cliente=c2),
    }

    # Cada atendimento: quem abriu e o que o torna visível para os outros.
    # origem "netafim" exige area_setor; "cliente_final" exige id_cliente.
    modelos = {
        "do_cliente_usuario": dict(solicitante="cliente_usuario", origem="cliente_final", id_cliente=c1),
        "do_cliente_admin": dict(solicitante="cliente_admin", origem="cliente_final", id_cliente=c1),
        "do_netafim_usuario": dict(solicitante="netafim_usuario", origem="netafim", area_setor="comercial"),
        "do_dist_usuario": dict(solicitante="dist_usuario", origem="distribuidor"),
        "do_outro_cliente": dict(solicitante="cliente_usuario_2", origem="cliente_final", id_cliente=c2),
        # Abertos pelo Netafim Admin e atribuídos como técnico a outra pessoa.
        "tecnico_netafim_usuario": dict(
            solicitante="netafim_admin", origem="netafim", area_setor="comercial",
            id_tecnico_responsavel=u["netafim_usuario"],
        ),
        # Também destinado ao distribuidor 1, o que o torna visível ao Admin dele.
        "tecnico_dist_usuario": dict(
            solicitante="netafim_admin", origem="netafim", area_setor="comercial",
            destinado_a="distribuidor", id_distribuidor=d1,
            id_tecnico_responsavel=u["dist_usuario"],
        ),
        # Origem no distribuidor 1, sem cliente nem técnico.
        "origem_distribuidor_1": dict(solicitante="netafim_admin", origem="distribuidor", id_distribuidor_origem=d1),
    }

    ids = {}
    with conexao_admin.cursor() as cur:
        # Destinação e técnico só nascem pelo Netafim Admin ou pelo processo
        # automático. A conexão de administrador não é usuário nenhum, então o
        # modo "sistema" (local à transação) libera a montagem do cenário.
        cur.execute("select set_config('app.acao_sistema', 'on', true)")
        for nome, m in modelos.items():
            id_atendimento = uuid.uuid4()
            cur.execute(
                """
                insert into atendimento (id_atendimento, origem, motivo, area_setor,
                       id_solicitante, id_cliente, id_distribuidor, id_distribuidor_origem,
                       destinado_a, id_tecnico_responsavel, contato_solicitante)
                values (%s, %s, 'duvidas', %s, %s, %s, %s, %s, %s, %s, '11999990000')
                """,
                (id_atendimento, m["origem"], m.get("area_setor"), u[m["solicitante"]],
                 m.get("id_cliente"), m.get("id_distribuidor"), m.get("id_distribuidor_origem"),
                 m.get("destinado_a"), m.get("id_tecnico_responsavel")),
            )
            ids[nome] = id_atendimento
    conexao_admin.commit()
    return {"usuarios": u, "atendimentos": ids}


TODOS = [
    "do_cliente_usuario", "do_cliente_admin", "do_netafim_usuario", "do_dist_usuario",
    "do_outro_cliente", "tecnico_netafim_usuario", "tecnico_dist_usuario",
    "origem_distribuidor_1",
]

# Quem enxerga o quê, segundo as regras do comentário no topo do arquivo.
VISIVEIS = {
    "netafim_admin": set(TODOS),
    "netafim_usuario": {"do_netafim_usuario", "tecnico_netafim_usuario"},
    "dist_usuario": {"do_dist_usuario", "tecnico_dist_usuario"},
    # Carteira do distribuidor 1 (cliente 1), destino no distribuidor 1 e origem nele.
    "dist_admin": {"do_cliente_usuario", "do_cliente_admin", "tecnico_dist_usuario", "origem_distribuidor_1"},
    "cliente_admin": {"do_cliente_usuario", "do_cliente_admin"},
    "cliente_usuario": {"do_cliente_usuario"},
    # Organização 2: só o que é dela.
    "dist_admin_2": {"do_outro_cliente"},
    "cliente_usuario_2": {"do_outro_cliente"},
}

# Quem consegue alterar: o que vê, menos, para perfis de cliente, o que outro abriu.
ALTERAVEIS = {
    **VISIVEIS,
    "cliente_admin": {"do_cliente_admin"},
}

PERFIS = list(VISIVEIS)


def ids_visiveis(elenco):
    """Ids (como nomes) dos atendimentos do cenário que o usuário atual enxerga
    pela conexão da API (role app_django, com RLS)."""
    por_id = {v: k for k, v in elenco["atendimentos"].items()}
    with connection.cursor() as cursor:
        cursor.execute(
            "select id_atendimento from atendimento where id_atendimento = any(%s)",
            [list(por_id)],
        )
        return {por_id[linha[0]] for linha in cursor.fetchall()}


def ids_alterados(elenco):
    """Tenta um UPDATE inofensivo (só a descrição) em cada atendimento do cenário
    e devolve os nomes dos que o banco aceitou (rowcount 1)."""
    alterados = set()
    with connection.cursor() as cursor:
        for nome, id_atendimento in elenco["atendimentos"].items():
            cursor.execute(
                "update atendimento set descricao_problema = 'alterado-pelo-teste' "
                "where id_atendimento = %s",
                [id_atendimento],
            )
            if cursor.rowcount == 1:
                alterados.add(nome)
    return alterados


@pytest.mark.django_db
@pytest.mark.parametrize("perfil", PERFIS)
def test_visualizacao_por_perfil(perfil, elenco, entrar_como):
    entrar_como(elenco["usuarios"][perfil])

    assert ids_visiveis(elenco) == VISIVEIS[perfil]


@pytest.mark.django_db
@pytest.mark.parametrize("perfil", PERFIS)
def test_alteracao_por_perfil(perfil, elenco, entrar_como):
    entrar_como(elenco["usuarios"][perfil])

    assert ids_alterados(elenco) == ALTERAVEIS[perfil]


@pytest.mark.django_db
def test_cliente_admin_ve_o_de_outro_usuario_da_empresa_mas_nao_altera(elenco, entrar_como):
    # O Cliente Admin enxerga o atendimento aberto pelo Cliente Usuário da mesma
    # empresa, mas o UPDATE não pega nenhuma linha: perfil de cliente só altera o
    # que ele mesmo abriu.
    entrar_como(elenco["usuarios"]["cliente_admin"])
    alheio = elenco["atendimentos"]["do_cliente_usuario"]

    assert "do_cliente_usuario" in ids_visiveis(elenco)
    with connection.cursor() as cursor:
        cursor.execute(
            "update atendimento set descricao_problema = 'alterado-pelo-teste' "
            "where id_atendimento = %s",
            [alheio],
        )
        assert cursor.rowcount == 0


@pytest.mark.django_db
def test_distribuidor_admin_nao_ve_nada_do_outro_distribuidor(elenco, entrar_como):
    # Distribuidor 2 não enxerga nenhum atendimento do distribuidor 1 ou de
    # clientes dele, e o Admin do distribuidor 1 não enxerga o cliente do 2.
    entrar_como(elenco["usuarios"]["dist_admin_2"])
    do_distribuidor_1 = set(TODOS) - {"do_outro_cliente"}
    assert ids_visiveis(elenco).isdisjoint(do_distribuidor_1)

    entrar_como(elenco["usuarios"]["dist_admin"])
    assert "do_outro_cliente" not in ids_visiveis(elenco)


@pytest.mark.django_db
def test_usuario_do_outro_cliente_nao_ve_os_da_primeira_empresa(elenco, entrar_como):
    entrar_como(elenco["usuarios"]["cliente_usuario_2"])
    da_primeira_empresa = {"do_cliente_usuario", "do_cliente_admin"}

    assert ids_visiveis(elenco).isdisjoint(da_primeira_empresa)
