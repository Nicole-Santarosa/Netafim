import uuid

import pytest
from rest_framework.test import APIClient

from apps.atendimentos.servicos import abrir_atendimento
from apps.atendimentos.tests.test_view_alteracao import criar_admin_netafim
from apps.atendimentos.tests.test_view_listagem import (
    cliente_autenticado,
    criar_empresa_e_usuario,
    dados_pedido,
)

CAMPOS_DO_DETALHE = {
    "id_atendimento", "numero_protocolo", "origem", "motivo", "status", "prioridade",
    "id_solicitante", "id_cliente", "id_propriedade", "id_distribuidor_origem",
    "nome_produtor", "nome_fazenda_informado", "cidade", "area_setor", "destinado_a",
    "id_distribuidor", "id_tecnico_responsavel", "contato_solicitante",
    "numero_projeto_br", "situacao_projeto", "estado_projeto", "tipo_venda",
    "tipo_atendimento", "descricao_problema", "data_inicio_problema", "data_sugerida",
    "necessidade", "qtd_participantes_prevista", "snapshot_cliente",
    "snapshot_propriedade", "data_abertura", "data_conclusao", "data_encerramento",
    "versao", "data_criacao", "data_atualizacao", "culturas",
}  # mais a chave "historico", que a view acrescenta


def url(id_atendimento):
    return f"/api/v1/atendimentos/{id_atendimento}"


@pytest.mark.django_db
def test_quem_abriu_recebe_o_detalhe_com_a_linha_do_tempo(mundo, entrar_como):
    entrar_como(mundo["id_usuario_cliente"])
    atendimento = abrir_atendimento(
        mundo["id_usuario_cliente"], dados_pedido(mundo["id_cliente"])
    )

    resposta = cliente_autenticado(mundo["id_usuario_cliente"]).get(
        url(atendimento.id_atendimento)
    )

    assert resposta.status_code == 200
    assert set(resposta.data) == CAMPOS_DO_DETALHE | {"historico"}
    assert resposta.data["numero_protocolo"] == atendimento.numero_protocolo
    # Os dois eventos de criação, na ordem em que o banco os gravou.
    tipos = [e["tipo_evento"] for e in resposta.data["historico"]]
    assert tipos == ["criado", "protocolo_gerado"]


@pytest.mark.django_db
def test_usuario_de_outra_empresa_recebe_404(mundo, conexao_admin, entrar_como):
    entrar_como(mundo["id_usuario_cliente"])
    atendimento = abrir_atendimento(
        mundo["id_usuario_cliente"], dados_pedido(mundo["id_cliente"])
    )
    outro = criar_empresa_e_usuario(conexao_admin, mundo)

    entrar_como(outro["id_usuario"])
    resposta = cliente_autenticado(outro["id_usuario"]).get(url(atendimento.id_atendimento))

    assert resposta.status_code == 404


@pytest.mark.django_db
def test_id_inexistente_devolve_404(mundo, entrar_como):
    entrar_como(mundo["id_usuario_cliente"])

    resposta = cliente_autenticado(mundo["id_usuario_cliente"]).get(url(uuid.uuid4()))

    assert resposta.status_code == 404


@pytest.mark.django_db
def test_atendimento_excluido_devolve_404(mundo, conexao_admin, entrar_como):
    # Empresa e usuário novos; os dois atendimentos são gravados pela conexão de
    # administrador (e confirmados), porque a transação do teste não é visível
    # para ela. Um normal, para provar que o 404 vem do deletado_em.
    outro = criar_empresa_e_usuario(conexao_admin, mundo)
    id_visivel, id_excluido = uuid.uuid4(), uuid.uuid4()
    with conexao_admin.cursor() as cur:
        for id_atendimento, excluido in ((id_visivel, False), (id_excluido, True)):
            cur.execute(
                """
                insert into atendimento (id_atendimento, numero_protocolo, origem, motivo,
                                         id_solicitante, id_cliente, contato_solicitante,
                                         deletado_em, sync_seq)
                values (%s, 'OS-0000', 'cliente_final', 'pos_venda', %s, %s, '11999990000',
                        case when %s then now() end, 0)
                """,
                (id_atendimento, outro["id_usuario"], outro["id_cliente"], excluido),
            )
    conexao_admin.commit()

    entrar_como(outro["id_usuario"])
    cliente = cliente_autenticado(outro["id_usuario"])

    assert cliente.get(url(id_visivel)).status_code == 200
    assert cliente.get(url(id_excluido)).status_code == 404


@pytest.mark.django_db
def test_sem_autenticacao_nao_devolve_detalhe():
    resposta = APIClient().get(url(uuid.uuid4()))

    assert resposta.status_code in (401, 403)


@pytest.mark.django_db
def test_resposta_nao_traz_deletado_em_nem_observacao(mundo, entrar_como):
    entrar_como(mundo["id_usuario_cliente"])
    atendimento = abrir_atendimento(
        mundo["id_usuario_cliente"], dados_pedido(mundo["id_cliente"])
    )

    resposta = cliente_autenticado(mundo["id_usuario_cliente"]).get(
        url(atendimento.id_atendimento)
    )

    assert "deletado_em" not in resposta.data
    assert resposta.data["historico"]
    for evento in resposta.data["historico"]:
        assert "observacao" not in evento


def duas_culturas(conexao_admin):
    """As duas primeiras culturas que o banco já traz nos dados iniciais, como
    lista de (id_cultura, nome). Não cria cultura nova: a tabela é catálogo."""
    with conexao_admin.cursor() as cur:
        cur.execute(
            "select id_cultura, nome from cultura where deletado_em is null "
            "order by id_cultura limit 2"
        )
        culturas = cur.fetchall()
    assert len(culturas) == 2, "o banco de teste precisa ter ao menos duas culturas"
    return culturas


def gravar_atendimento_com_culturas(conexao_admin, outro, ids_cultura):
    """Grava, pela conexão de administrador, um atendimento do usuário de uma
    empresa nova e os vínculos dele com as culturas dadas, na ordem dada.
    Os dados são confirmados porque a transação do teste não enxerga o que a
    conexão de administrador grava sem commit. Devolve o id do atendimento."""
    id_atendimento = uuid.uuid4()
    with conexao_admin.cursor() as cur:
        cur.execute(
            """
            insert into atendimento (id_atendimento, numero_protocolo, origem, motivo,
                                     id_solicitante, id_cliente, contato_solicitante, sync_seq)
            values (%s, 'OS-0000', 'cliente_final', 'pos_venda', %s, %s, '11999990000', 0)
            """,
            (id_atendimento, outro["id_usuario"], outro["id_cliente"]),
        )
        for id_cultura in ids_cultura:
            cur.execute(
                "insert into atendimento_cultura (id_atendimento, id_cultura, sync_seq) "
                "values (%s, %s, 0)",
                (id_atendimento, id_cultura),
            )
    conexao_admin.commit()
    return id_atendimento


def esperado(culturas):
    """Formato que o detalhe devolve, em ordem de nome."""
    return [{"id_cultura": i, "nome": n} for i, n in sorted(culturas, key=lambda c: c[1])]


@pytest.mark.django_db
def test_atendimento_sem_cultura_devolve_lista_vazia(mundo, entrar_como):
    entrar_como(mundo["id_usuario_cliente"])
    atendimento = abrir_atendimento(
        mundo["id_usuario_cliente"], dados_pedido(mundo["id_cliente"])
    )

    resposta = cliente_autenticado(mundo["id_usuario_cliente"]).get(
        url(atendimento.id_atendimento)
    )

    assert resposta.status_code == 200
    assert resposta.data["culturas"] == []


@pytest.mark.django_db
def test_duas_culturas_vem_em_ordem_de_nome(mundo, conexao_admin, entrar_como):
    culturas = duas_culturas(conexao_admin)
    outro = criar_empresa_e_usuario(conexao_admin, mundo)
    # Vínculos gravados em ordem decrescente de nome, para a ordem da resposta
    # vir da consulta e não da ordem de gravação.
    ids_ao_contrario = [i for i, _ in sorted(culturas, key=lambda c: c[1], reverse=True)]
    id_atendimento = gravar_atendimento_com_culturas(conexao_admin, outro, ids_ao_contrario)

    entrar_como(outro["id_usuario"])
    resposta = cliente_autenticado(outro["id_usuario"]).get(url(id_atendimento))

    assert resposta.status_code == 200
    assert resposta.data["culturas"] == esperado(culturas)


@pytest.mark.django_db
def test_culturas_de_outro_atendimento_nao_aparecem(mundo, conexao_admin, entrar_como):
    (id_a, nome_a), (id_b, nome_b) = duas_culturas(conexao_admin)
    outro = criar_empresa_e_usuario(conexao_admin, mundo)
    # Dois atendimentos do mesmo usuário, cada um com uma cultura diferente.
    atendimento_a = gravar_atendimento_com_culturas(conexao_admin, outro, [id_a])
    atendimento_b = gravar_atendimento_com_culturas(conexao_admin, outro, [id_b])

    entrar_como(outro["id_usuario"])
    cliente = cliente_autenticado(outro["id_usuario"])

    assert cliente.get(url(atendimento_a)).data["culturas"] == [{"id_cultura": id_a, "nome": nome_a}]
    assert cliente.get(url(atendimento_b)).data["culturas"] == [{"id_cultura": id_b, "nome": nome_b}]


@pytest.mark.django_db
def test_patch_tambem_devolve_as_culturas(mundo, conexao_admin, entrar_como):
    culturas = duas_culturas(conexao_admin)
    outro = criar_empresa_e_usuario(conexao_admin, mundo)
    id_atendimento = gravar_atendimento_com_culturas(
        conexao_admin, outro, [i for i, _ in culturas]
    )
    # Quem altera é o Netafim Admin, que pode cancelar e enxerga todos.
    admin = criar_admin_netafim(conexao_admin)

    entrar_como(admin)
    resposta = cliente_autenticado(admin).patch(
        url(id_atendimento), {"versao": 1, "status": "cancelado"}, format="json"
    )

    assert resposta.status_code == 200
    assert resposta.data["status"] == "cancelado"
    assert resposta.data["culturas"] == esperado(culturas)
