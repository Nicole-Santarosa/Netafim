import uuid

import psycopg
import pytest
from django.db import connection

from apps.atendimentos.tests.test_view_abertura import (
    URL,
    cliente_autenticado,
    corpo_valido,
)


def criar_atendimento_pelo_admin(conexao_admin, mundo):
    """Cria um atendimento pela conexão de administrador, com commit, para as
    linhas de histórico terem a quem se ligar. Os gatilhos geram protocolo e os
    eventos iniciais, como em qualquer abertura."""
    id_atendimento = uuid.uuid4()
    with conexao_admin.cursor() as cur:
        cur.execute(
            """
            insert into atendimento (id_atendimento, origem, motivo, id_solicitante,
                                     id_cliente, contato_solicitante)
            values (%s, 'cliente_final', 'pos_venda', %s, %s, '11999990000')
            """,
            (id_atendimento, mundo["id_usuario_cliente"], mundo["id_cliente"]),
        )
    conexao_admin.commit()
    return id_atendimento


def inserir_evento(conexao_admin, id_atendimento, origem, responsavel):
    """Insere uma linha no histórico pela conexão de administrador. O bloco
    transaction() usa um savepoint: se o banco recusar, só ele é desfeito e a
    conexão continua utilizável para o próximo caso."""
    with conexao_admin.transaction():
        conexao_admin.execute(
            """
            insert into historico_atendimento
                   (id_atendimento, tipo_evento, origem_evento, valor_novo,
                    id_usuario_responsavel)
            values (%s, 'editado_pos_finalizacao', %s, 'teste', %s)
            """,
            (id_atendimento, origem, responsavel),
        )


def test_usuario_com_responsavel_e_aceito(mundo, conexao_admin):
    id_atendimento = criar_atendimento_pelo_admin(conexao_admin, mundo)
    inserir_evento(conexao_admin, id_atendimento, "usuario", mundo["id_usuario_cliente"])


def test_usuario_sem_responsavel_e_recusado(mundo, conexao_admin):
    id_atendimento = criar_atendimento_pelo_admin(conexao_admin, mundo)
    with pytest.raises(psycopg.errors.CheckViolation):
        inserir_evento(conexao_admin, id_atendimento, "usuario", None)


def test_sistema_sem_responsavel_e_aceito(mundo, conexao_admin):
    id_atendimento = criar_atendimento_pelo_admin(conexao_admin, mundo)
    inserir_evento(conexao_admin, id_atendimento, "sistema", None)


def test_sistema_com_responsavel_e_recusado(mundo, conexao_admin):
    id_atendimento = criar_atendimento_pelo_admin(conexao_admin, mundo)
    with pytest.raises(psycopg.errors.CheckViolation):
        inserir_evento(conexao_admin, id_atendimento, "sistema", mundo["id_usuario_cliente"])


@pytest.mark.django_db
def test_evento_criado_da_abertura_pela_api_e_do_usuario(mundo, entrar_como):
    # Fluxo real: quem abre pela API aparece como responsável do evento "criado".
    entrar_como(mundo["id_usuario_cliente"])
    corpo = corpo_valido(mundo)

    resposta = cliente_autenticado(mundo["id_usuario_cliente"]).post(
        URL, corpo, format="json"
    )
    assert resposta.status_code == 201

    with connection.cursor() as cursor:
        cursor.execute(
            """
            select origem_evento, id_usuario_responsavel from historico_atendimento
             where id_atendimento = %s and tipo_evento = 'criado'
            """,
            [corpo["id_atendimento"]],
        )
        linhas = cursor.fetchall()
    assert linhas == [("usuario", mundo["id_usuario_cliente"])]
