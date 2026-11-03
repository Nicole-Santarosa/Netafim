import re
import uuid

import pytest
from django.db import connection
from rest_framework.test import APIClient

from apps.atendimentos.models import Atendimento

URL = "/api/v1/atendimentos"


class UsuarioDeTeste:
    """Usuário autenticado fictício para o force_authenticate. O usuário real
    virá do módulo de acesso, que ainda não está nesta branch; a view só usa
    pk e is_authenticated, então isto basta."""

    is_authenticated = True

    def __init__(self, pk):
        self.pk = pk


def cliente_autenticado(id_usuario):
    cliente = APIClient()
    cliente.force_authenticate(user=UsuarioDeTeste(id_usuario))
    return cliente


def corpo_valido(mundo, **extras):
    """Corpo mínimo de um pedido de cliente final, com UUID novo."""
    corpo = {
        "id_atendimento": str(uuid.uuid4()),
        "origem": "cliente_final",
        "motivo": "pos_venda",
        "id_cliente": mundo["id_cliente"],
        "contato_solicitante": "11999990000",
    }
    corpo.update(extras)
    return corpo


@pytest.mark.django_db
def test_pedido_valido_devolve_201_com_protocolo(mundo, entrar_como):
    entrar_como(mundo["id_usuario_cliente"])

    resposta = cliente_autenticado(mundo["id_usuario_cliente"]).post(
        URL, corpo_valido(mundo), format="json"
    )

    assert resposta.status_code == 201
    assert re.fullmatch(r"OS-\d{4,}", resposta.data["numero_protocolo"])
    assert resposta.data["status"] == "aberto"
    assert resposta.data["versao"] == 1


@pytest.mark.django_db
def test_origem_fora_da_lista_devolve_400_no_campo(mundo, entrar_como):
    entrar_como(mundo["id_usuario_cliente"])

    resposta = cliente_autenticado(mundo["id_usuario_cliente"]).post(
        URL, corpo_valido(mundo, origem="invalida"), format="json"
    )

    assert resposta.status_code == 400
    assert "origem" in resposta.data


@pytest.mark.django_db
def test_sem_autenticacao_nao_grava(mundo, entrar_como):
    entrar_como(mundo["id_usuario_cliente"])
    corpo = corpo_valido(mundo)

    resposta = APIClient().post(URL, corpo, format="json")

    assert resposta.status_code in (401, 403)
    assert not Atendimento.objects.filter(id_atendimento=corpo["id_atendimento"]).exists()


@pytest.mark.django_db
def test_id_solicitante_forjado_no_corpo_e_ignorado(mundo, entrar_como):
    entrar_como(mundo["id_usuario_cliente"])
    corpo = corpo_valido(mundo, id_solicitante=mundo["id_usuario_cliente"] + 1)

    resposta = cliente_autenticado(mundo["id_usuario_cliente"]).post(
        URL, corpo, format="json"
    )

    assert resposta.status_code == 201
    atendimento = Atendimento.objects.get(id_atendimento=corpo["id_atendimento"])
    assert atendimento.id_solicitante == mundo["id_usuario_cliente"]


@pytest.mark.django_db
def test_id_repetido_devolve_409_duplicado(mundo, entrar_como):
    entrar_como(mundo["id_usuario_cliente"])
    cliente = cliente_autenticado(mundo["id_usuario_cliente"])
    corpo = corpo_valido(mundo)
    assert cliente.post(URL, corpo, format="json").status_code == 201

    resposta = cliente.post(URL, corpo, format="json")

    assert resposta.status_code == 409
    assert resposta.data["codigo"] == "duplicado"


@pytest.mark.django_db
def test_cliente_de_outra_empresa_devolve_403(mundo, conexao_admin, entrar_como):
    # Cria uma segunda empresa pela conexão de administrador, com sufixo
    # aleatório para não colidir com execuções anteriores.
    sufixo = uuid.uuid4().hex[:8]
    with conexao_admin.cursor() as cur:
        cur.execute(
            "insert into cliente (nome, id_distribuidor) values (%s, %s) returning id_cliente",
            (f"Outro Cliente {sufixo}", mundo["id_distribuidor"]),
        )
        id_outro_cliente = cur.fetchone()[0]
    conexao_admin.commit()

    entrar_como(mundo["id_usuario_cliente"])

    resposta = cliente_autenticado(mundo["id_usuario_cliente"]).post(
        URL, corpo_valido(mundo, id_cliente=id_outro_cliente), format="json"
    )

    assert resposta.status_code == 403
    assert resposta.data["codigo"] == "sem_permissao"


@pytest.mark.django_db
def test_distribuidor_de_origem_inexistente_devolve_422(mundo, entrar_como):
    entrar_como(mundo["id_usuario_cliente"])
    # Id muito acima de qualquer distribuidor real: o banco recusa pela chave
    # estrangeira e o tradutor responde 422.
    corpo = corpo_valido(mundo, id_distribuidor_origem=2_000_000_000)

    resposta = cliente_autenticado(mundo["id_usuario_cliente"]).post(
        URL, corpo, format="json"
    )

    assert resposta.status_code == 422
    assert resposta.data["codigo"] == "referencia_invalida"
    assert not Atendimento.objects.filter(id_atendimento=corpo["id_atendimento"]).exists()
