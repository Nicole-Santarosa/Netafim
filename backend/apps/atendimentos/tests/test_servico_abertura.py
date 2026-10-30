import re
import uuid

import pytest
from django.db import IntegrityError, transaction

from apps.atendimentos.servicos import abrir_atendimento


def dados_basicos(mundo, **extras):
    """Monta os dados mínimos de um pedido de cliente final, com UUID novo.
    Os extras entram por cima, para cada teste acrescentar o que precisa."""
    dados = {
        "id_atendimento": uuid.uuid4(),
        "origem": "cliente_final",
        "motivo": "pos_venda",
        "id_cliente": mundo["id_cliente"],
        "contato_solicitante": "11999990000",
    }
    dados.update(extras)
    return dados


@pytest.mark.django_db
def test_abre_atendimento_com_o_que_o_banco_preenche(mundo, entrar_como):
    # O banco precisa saber quem está agindo antes de qualquer consulta.
    entrar_como(mundo["id_usuario_cliente"])

    atendimento = abrir_atendimento(mundo["id_usuario_cliente"], dados_basicos(mundo))

    assert re.fullmatch(r"OS-\d{4,}", atendimento.numero_protocolo)
    assert atendimento.status == "aberto"
    assert atendimento.versao == 1
    assert atendimento.sync_seq is not None
    assert atendimento.id_solicitante == mundo["id_usuario_cliente"]


@pytest.mark.django_db
def test_ignora_status_e_protocolo_enviados(mundo, entrar_como):
    entrar_como(mundo["id_usuario_cliente"])

    # Se o status "concluido" chegasse ao banco, o gatilho recusaria o INSERT.
    # Como o serviço descarta a chave, o atendimento nasce aberto.
    atendimento = abrir_atendimento(
        mundo["id_usuario_cliente"],
        dados_basicos(mundo, status="concluido", numero_protocolo="X"),
    )

    assert atendimento.status == "aberto"
    assert atendimento.numero_protocolo != "X"
    assert re.fullmatch(r"OS-\d{4,}", atendimento.numero_protocolo)


@pytest.mark.django_db
def test_solicitante_e_sempre_o_do_argumento(mundo, entrar_como):
    entrar_como(mundo["id_usuario_cliente"])

    # Um id diferente do usuário logado chega escondido nos dados, como se
    # alguém quisesse abrir o atendimento em nome de outra pessoa.
    outro_id = mundo["id_usuario_cliente"] + 1
    atendimento = abrir_atendimento(
        mundo["id_usuario_cliente"], dados_basicos(mundo, id_solicitante=outro_id)
    )

    # O serviço ignora o valor dos dados e usa o id do argumento.
    assert atendimento.id_solicitante == mundo["id_usuario_cliente"]


@pytest.mark.django_db
def test_id_repetido_levanta_erro_do_banco(mundo, entrar_como):
    entrar_como(mundo["id_usuario_cliente"])
    dados = dados_basicos(mundo)
    abrir_atendimento(mundo["id_usuario_cliente"], dados)

    # O atomic isola o erro num savepoint, para a transação do teste continuar
    # utilizável depois da falha.
    with pytest.raises(IntegrityError):
        with transaction.atomic():
            abrir_atendimento(mundo["id_usuario_cliente"], dados)
