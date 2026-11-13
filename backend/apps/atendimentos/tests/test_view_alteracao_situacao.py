import pytest

from apps.atendimentos.servicos import abrir_atendimento
from apps.atendimentos.tests.test_view_alteracao import (
    criar_admin_netafim,
    url,
)
from apps.atendimentos.tests.test_view_listagem import cliente_autenticado, dados_pedido

# Testes do PATCH com situacao_projeto. Só o Netafim Admin altera esse campo
# depois da abertura, e essa regra é do gatilho do banco. Aqui se prova o que a
# API faz (aceitar, validar a lista, devolver o valor e a versão nova); a recusa
# dos outros perfis fica nos testes que dependem da proposta do gatilho. Estes
# testes passam com ou sem ela, porque só usam o Netafim Admin.


def abrir_como_cliente(mundo, entrar_como, **extras):
    """Abre um atendimento no nome do usuário cliente do mundo e devolve o objeto."""
    entrar_como(mundo["id_usuario_cliente"])
    return abrir_atendimento(
        mundo["id_usuario_cliente"], dados_pedido(mundo["id_cliente"], **extras)
    )


@pytest.mark.django_db
def test_admin_altera_a_situacao_do_projeto(mundo, conexao_admin, entrar_como):
    atendimento = abrir_como_cliente(mundo, entrar_como)
    admin = criar_admin_netafim(conexao_admin)

    entrar_como(admin)
    cliente = cliente_autenticado(admin)
    # O corpo leva só a versão e a situação do projeto.
    resposta = cliente.patch(
        url(atendimento.id_atendimento),
        {"versao": 1, "situacao_projeto": "fora_garantia"},
        format="json",
    )

    assert resposta.status_code == 200
    assert resposta.data["situacao_projeto"] == "fora_garantia"
    assert resposta.data["versao"] == 2
    # O GET de detalhe traz o mesmo valor e a mesma versão.
    detalhe = cliente.get(url(atendimento.id_atendimento))
    assert detalhe.data["situacao_projeto"] == "fora_garantia"
    assert detalhe.data["versao"] == 2


@pytest.mark.django_db
def test_admin_limpa_a_situacao_com_nulo(mundo, conexao_admin, entrar_como):
    atendimento = abrir_como_cliente(mundo, entrar_como, situacao_projeto="garantia")
    admin = criar_admin_netafim(conexao_admin)

    entrar_como(admin)
    resposta = cliente_autenticado(admin).patch(
        url(atendimento.id_atendimento),
        {"versao": 1, "situacao_projeto": None},
        format="json",
    )

    assert resposta.status_code == 200
    assert resposta.data["situacao_projeto"] is None
    assert resposta.data["versao"] == 2


@pytest.mark.django_db
@pytest.mark.parametrize("valor", ["garantia_estendida", "", "GARANTIA", 1])
def test_valor_fora_da_lista_devolve_400_no_campo(valor, mundo, conexao_admin, entrar_como):
    atendimento = abrir_como_cliente(mundo, entrar_como)
    admin = criar_admin_netafim(conexao_admin)

    entrar_como(admin)
    resposta = cliente_autenticado(admin).patch(
        url(atendimento.id_atendimento),
        {"versao": 1, "situacao_projeto": valor},
        format="json",
    )

    assert resposta.status_code == 400
    assert "situacao_projeto" in resposta.data


@pytest.mark.django_db
def test_versao_desatualizada_devolve_409(mundo, conexao_admin, entrar_como):
    atendimento = abrir_como_cliente(mundo, entrar_como)
    admin = criar_admin_netafim(conexao_admin)
    entrar_como(admin)
    cliente = cliente_autenticado(admin)
    # Primeira alteração: a versão passa de 1 para 2.
    primeira = cliente.patch(
        url(atendimento.id_atendimento),
        {"versao": 1, "situacao_projeto": "garantia"},
        format="json",
    )
    assert primeira.status_code == 200

    # O aplicativo ainda acha que a versão é 1.
    resposta = cliente.patch(
        url(atendimento.id_atendimento),
        {"versao": 1, "situacao_projeto": "instalacao"},
        format="json",
    )

    assert resposta.status_code == 409
    assert resposta.data["codigo"] == "conflito_de_versao"
    assert resposta.data["versao_atual"] == 2
    # A alteração recusada não gravou nada.
    assert cliente.get(url(atendimento.id_atendimento)).data["situacao_projeto"] == "garantia"


@pytest.mark.django_db
def test_patch_sem_situacao_nao_mexe_no_campo(mundo, conexao_admin, entrar_como):
    atendimento = abrir_como_cliente(mundo, entrar_como, situacao_projeto="garantia")
    admin = criar_admin_netafim(conexao_admin)

    entrar_como(admin)
    resposta = cliente_autenticado(admin).patch(
        url(atendimento.id_atendimento),
        {"versao": 1, "status": "destinado", "destinado_a": "netafim"},
        format="json",
    )

    assert resposta.status_code == 200
    assert resposta.data["status"] == "destinado"
    assert resposta.data["situacao_projeto"] == "garantia"
