"""E-mail ao titular quando o pedido de privacidade é concluído (sem banco, e-mail em memória)."""

from unittest import mock

import pytest
from django.core import mail

from apps.contas.servicos import notificacoes


def test_pedido_atendido_avisa_o_titular_sem_links():
    notificacoes.pedido_titular_concluido(
        email="ana@exemplo.com.br", nome="Ana Reis", tipo="acesso", status="atendida"
    )
    assert len(mail.outbox) == 1
    msg = mail.outbox[0]
    assert msg.to == ["ana@exemplo.com.br"]
    assert "atendido" in msg.subject
    assert "Olá, Ana Reis." in msg.body
    assert "acesso aos dados" in msg.body
    assert "http" not in msg.body.lower() and "netafim://" not in msg.body
    assert "anonimizada" not in msg.body


def test_anonimizacao_avisa_que_a_conta_foi_anonimizada():
    notificacoes.pedido_titular_concluido(
        email="ana@exemplo.com.br",
        nome="Ana",
        tipo="anonimizacao",
        status="atendida",
        anonimizou=True,
    )
    corpo = mail.outbox[0].body
    assert "anonimização" in corpo
    assert "Sua conta foi anonimizada e o acesso ao aplicativo foi encerrado." in corpo


def test_pedido_negado_traz_o_motivo():
    notificacoes.pedido_titular_concluido(
        email="ana@exemplo.com.br",
        nome="Ana",
        tipo="eliminacao",
        status="negada",
        justificativa="  Há obrigação legal de guardar os registros.  ",
    )
    msg = mail.outbox[0]
    assert "não pôde ser atendido" in msg.body
    assert "Motivo: Há obrigação legal de guardar os registros." in msg.body
    assert "Resposta ao seu pedido" in msg.subject


def test_sem_email_ou_sem_nome():
    notificacoes.pedido_titular_concluido(email=None, nome="Ana", tipo="acesso", status="atendida")
    notificacoes.pedido_titular_concluido(email="", nome="Ana", tipo="acesso", status="atendida")
    assert mail.outbox == []

    notificacoes.pedido_titular_concluido(
        email="a@b.com", nome=None, tipo="acesso", status="atendida"
    )
    assert mail.outbox[0].body.startswith("Olá.")


def test_tipo_desconhecido_nao_quebra():
    notificacoes.pedido_titular_concluido(
        email="a@b.com", nome="A", tipo="novo_tipo", status="atendida"
    )
    assert "novo_tipo" in mail.outbox[0].body


def test_falha_no_envio_nao_derruba_o_pedido(caplog):
    with mock.patch.object(notificacoes, "send_mail", side_effect=OSError("SMTP fora do ar")):
        notificacoes.pedido_titular_concluido(
            email="a@b.com", nome="A", tipo="acesso", status="atendida"
        )
    assert "Falha ao enviar e-mail" in caplog.text


@pytest.mark.parametrize(
    "tipo",
    [
        "confirmacao",
        "acesso",
        "correcao",
        "anonimizacao",
        "eliminacao",
        "portabilidade",
        "compartilhamento",
        "revogacao_consentimento",
    ],
)
def test_todos_os_tipos_do_pedido_tem_rotulo(tipo):
    assert tipo in notificacoes._ROTULO_DO_PEDIDO


def test_os_tipos_do_servico_de_titular_estao_cobertos():
    from apps.contas.servicos import titular

    assert set(titular.TIPOS) <= set(notificacoes._ROTULO_DO_PEDIDO)
