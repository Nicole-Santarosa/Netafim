import uuid

import psycopg
import pytest
from django.db import transaction
from django.db.utils import DataError, IntegrityError, ProgrammingError

from apps.atendimentos.models import Atendimento
from apps.core.erros import tratar_excecao


def _com_causa(erro_django, erro_psycopg):
    """Monta a exceção como o Django entrega: o erro do psycopg fica em __cause__."""
    try:
        raise erro_django("erro de teste") from erro_psycopg
    except erro_django as exc:
        return exc


@pytest.mark.django_db
def test_erro_real_do_banco_vira_422_regra_de_negocio(mundo, entrar_como):
    entrar_como(mundo["id_usuario_cliente"])

    # O gatilho do atendimento recusa qualquer status diferente de aberto na criação.
    with pytest.raises(IntegrityError) as capturado:
        with transaction.atomic():
            Atendimento.objects.create(
                id_atendimento=uuid.uuid4(),
                origem="cliente_final",
                motivo="pos_venda",
                status="destinado",
                id_solicitante=mundo["id_usuario_cliente"],
                id_cliente=mundo["id_cliente"],
                contato_solicitante="11999990000",
            )

    resposta = tratar_excecao(capturado.value, {})

    assert resposta.status_code == 422
    assert resposta.data["codigo"] == "regra_de_negocio"
    assert "status aberto" in resposta.data["detail"]


@pytest.mark.django_db
def test_check_de_tabela_nao_expoe_a_mensagem_do_banco(mundo, entrar_como):
    entrar_como(mundo["id_usuario_cliente"])

    # origem fora da lista quebra o CHECK da tabela (não é erro de gatilho).
    with pytest.raises(IntegrityError) as capturado:
        with transaction.atomic():
            Atendimento.objects.create(
                id_atendimento=uuid.uuid4(),
                origem="invalida",
                motivo="pos_venda",
                id_solicitante=mundo["id_usuario_cliente"],
                id_cliente=mundo["id_cliente"],
                contato_solicitante="11999990000",
            )

    resposta = tratar_excecao(capturado.value, {})

    assert resposta.status_code == 422
    assert resposta.data["codigo"] == "regra_de_negocio"
    assert resposta.data["detail"] == "Os dados informados não atendem às regras do sistema."
    assert "ck_atend" not in resposta.data["detail"]
    assert "check constraint" not in resposta.data["detail"]


@pytest.mark.parametrize(
    "erro_django, erro_psycopg, http, codigo",
    [
        (IntegrityError, psycopg.errors.UniqueViolation("dup"), 409, "duplicado"),
        (IntegrityError, psycopg.errors.ForeignKeyViolation("fk"), 422, "referencia_invalida"),
        (IntegrityError, psycopg.errors.NotNullViolation("nulo"), 422, "campo_obrigatorio"),
        (ProgrammingError, psycopg.errors.InsufficientPrivilege("rls"), 403, "sem_permissao"),
        # A classe genérica DataError não traz sqlstate; o banco sempre manda uma
        # subclasse específica, como esta (texto que não vira o tipo da coluna).
        (DataError, psycopg.errors.InvalidTextRepresentation("valor"), 400, "valor_invalido"),
    ],
)
def test_sqlstate_vira_o_status_http_esperado(erro_django, erro_psycopg, http, codigo):
    resposta = tratar_excecao(_com_causa(erro_django, erro_psycopg), {})

    assert resposta.status_code == http
    assert resposta.data["codigo"] == codigo


def test_sqlstate_nao_mapeado_nao_e_traduzido():
    # 40001 (falha de serialização) não está na tabela: deve subir como 500.
    exc = _com_causa(ProgrammingError, psycopg.errors.SerializationFailure("conflito"))

    assert tratar_excecao(exc, {}) is None
