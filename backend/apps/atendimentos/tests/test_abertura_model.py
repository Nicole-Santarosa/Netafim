import re
import uuid

import pytest
from django.db import connection

from apps.atendimentos.models import Atendimento


@pytest.mark.django_db
def test_abertura_preenche_campos_do_banco(mundo, entrar_como):
    # O banco precisa saber quem está agindo antes de qualquer consulta.
    entrar_como(mundo["id_usuario_cliente"])

    atendimento = Atendimento.objects.create(
        id_atendimento=uuid.uuid4(),
        origem="cliente_final",
        motivo="pos_venda",
        id_solicitante=mundo["id_usuario_cliente"],
        id_cliente=mundo["id_cliente"],
        contato_solicitante="11999990000",
    )
    # Lê de volta o que os gatilhos preencheram (protocolo, versao, sync_seq).
    atendimento.refresh_from_db()

    assert re.fullmatch(r"OS-\d{4,}", atendimento.numero_protocolo)
    assert atendimento.status == "aberto"
    assert atendimento.prioridade == "media"
    assert atendimento.versao == 1
    assert atendimento.sync_seq is not None

    # O banco grava sozinho os eventos de criação no histórico.
    with connection.cursor() as cursor:
        cursor.execute(
            "select tipo_evento from historico_atendimento where id_atendimento = %s",
            [atendimento.id_atendimento],
        )
        eventos = {linha[0] for linha in cursor.fetchall()}
    assert {"criado", "protocolo_gerado"} <= eventos
