import uuid

import psycopg
import pytest

from apps.atendimentos.models import Atendimento
from apps.atendimentos.tests.test_view_abertura import (
    URL,
    cliente_autenticado,
    corpo_valido,
)
from apps.atendimentos.tests.test_view_listagem import criar_empresa_e_usuario


def criar_propriedade(conexao_admin, id_cliente):
    """A propriedade ainda não tem model, então ela é criada pela conexão de
    administrador, com commit, para a conexão da API enxergar a linha. O
    sync_seq é preenchido pelo gatilho da tabela."""
    with conexao_admin.cursor() as cur:
        cur.execute(
            """
            insert into propriedade (id_cliente, nome_fazenda, localizacao)
            values (%s, 'Fazenda de Teste', 'Localizacao de Teste')
            returning id_propriedade
            """,
            (id_cliente,),
        )
        id_propriedade = cur.fetchone()[0]
    conexao_admin.commit()
    return id_propriedade


@pytest.mark.django_db
def test_propriedade_do_mesmo_cliente_abre(mundo, conexao_admin, entrar_como):
    id_propriedade = criar_propriedade(conexao_admin, mundo["id_cliente"])
    entrar_como(mundo["id_usuario_cliente"])
    corpo = corpo_valido(mundo, id_propriedade=id_propriedade)

    resposta = cliente_autenticado(mundo["id_usuario_cliente"]).post(
        URL, corpo, format="json"
    )

    assert resposta.status_code == 201
    atendimento = Atendimento.objects.get(id_atendimento=corpo["id_atendimento"])
    assert atendimento.id_propriedade == id_propriedade


@pytest.mark.django_db
def test_propriedade_de_outro_cliente_e_recusada(mundo, conexao_admin, entrar_como):
    # A propriedade pertence a outra empresa: a FK composta
    # (id_propriedade, id_cliente) não encontra o par e o banco recusa.
    outra = criar_empresa_e_usuario(conexao_admin, mundo)
    id_propriedade_alheia = criar_propriedade(conexao_admin, outra["id_cliente"])
    entrar_como(mundo["id_usuario_cliente"])
    corpo = corpo_valido(mundo, id_propriedade=id_propriedade_alheia)

    resposta = cliente_autenticado(mundo["id_usuario_cliente"]).post(
        URL, corpo, format="json"
    )

    assert resposta.status_code == 422
    assert resposta.data["codigo"] == "referencia_invalida"


def test_check_propriedade_exige_cliente(mundo, conexao_admin):
    # INSERT direto pela conexão de administrador, que passa por cima do RLS e
    # chega ao CHECK ck_atend_propriedade_cliente (id_propriedade exige
    # id_cliente). Os outros campos são válidos, para que nenhum outro CHECK dispare:
    # origem netafim com area_setor preenchida e id_cliente nulo.
    id_propriedade = criar_propriedade(conexao_admin, mundo["id_cliente"])

    with pytest.raises(psycopg.errors.CheckViolation) as erro:
        # O bloco transaction() usa um savepoint, então a conexão segue
        # utilizável depois da recusa.
        with conexao_admin.transaction():
            conexao_admin.execute(
                """
                insert into atendimento (id_atendimento, origem, motivo, area_setor,
                                         id_solicitante, id_propriedade, contato_solicitante)
                values (%s, 'netafim', 'duvidas', 'comercial', %s, %s, '11999990000')
                """,
                (uuid.uuid4(), mundo["id_usuario_cliente"], id_propriedade),
            )

    assert erro.value.diag.constraint_name == "ck_atend_propriedade_cliente"


@pytest.mark.django_db
def test_cliente_usuario_sem_id_cliente_leva_403_do_rls(mundo, conexao_admin, entrar_como):
    # Pela API, o Cliente Usuário só consegue inserir para o próprio id_cliente.
    # A policy de INSERT do RLS recusa antes de o CHECK ser avaliado, então esse
    # perfil recebe 403 e o CHECK ck_atend_propriedade_cliente não é alcançável
    # por aqui (ele é coberto no teste acima, pela conexão de administrador).
    id_propriedade = criar_propriedade(conexao_admin, mundo["id_cliente"])
    entrar_como(mundo["id_usuario_cliente"])
    corpo = corpo_valido(mundo, origem="distribuidor", id_propriedade=id_propriedade)
    del corpo["id_cliente"]

    resposta = cliente_autenticado(mundo["id_usuario_cliente"]).post(
        URL, corpo, format="json"
    )

    assert resposta.status_code == 403
    assert resposta.data["codigo"] == "sem_permissao"
