"""Testes de GET /api/v1/sync/pull pela rota HTTP.

A regra do pull (ordem, tombstone, histórico, RLS) já é testada em
test_sincronizacao_pull.py sobre a função. Aqui o que se confere é a view: ler
desde e limite da URL, devolver o dicionário como está, validar com 400 e fechar
a rota para quem não está autenticado.
"""

import pytest
from rest_framework.test import APIClient

from apps.atendimentos.tests.test_rls_perfis import criar_usuario_do_perfil
from apps.atendimentos.tests.test_sincronizacao_pull import (  # noqa: F401
    abrir,
    criar_organizacao,
    excluir,
    marco,
    org,
)
from apps.atendimentos.tests.test_view_abertura import cliente_autenticado

pytestmark = pytest.mark.django_db

URL = "/api/v1/sync/pull"


def puxar(entrar_como, id_usuario, **parametros):
    entrar_como(id_usuario)
    return cliente_autenticado(id_usuario).get(URL, parametros)


def test_pagina_com_cursor_percorre_tudo_sem_repetir(org, entrar_como):
    abertos = [abrir(entrar_como, org["cliente"], org["id_cliente"]) for _ in range(5)]

    ids_recebidos = []
    paginas = []
    desde = org["marco"]
    while True:
        resposta = puxar(entrar_como, org["cliente"], desde=desde, limite=2)
        assert resposta.status_code == 200
        corpo = resposta.json()
        paginas.append(corpo)
        ids_recebidos += [i["registro"]["id_atendimento"] for i in corpo["itens"]]
        # Repetir a chamada com desde igual ao cursor enquanto tem_mais.
        desde = corpo["cursor"]
        if not corpo["tem_mais"]:
            break

    assert [len(p["itens"]) for p in paginas] == [2, 2, 1]
    assert ids_recebidos == [str(a.id_atendimento) for a in abertos]


def test_sem_parametros_vale_desde_zero_e_limite_padrao(org, entrar_como):
    abrir(entrar_como, org["cliente"], org["id_cliente"])

    corpo = puxar(entrar_como, org["cliente"]).json()

    assert set(corpo) == {"itens", "cursor", "tem_mais"}
    assert len(corpo["itens"]) >= 1


def test_registro_traz_historico_aninhado_e_culturas(org, entrar_como):
    atendimento = abrir(entrar_como, org["cliente"], org["id_cliente"])

    corpo = puxar(entrar_como, org["cliente"], desde=org["marco"]).json()

    [item] = corpo["itens"]
    assert item["entidade"] == "atendimento"
    assert item["removido"] is False
    registro = item["registro"]
    assert registro["id_atendimento"] == str(atendimento.id_atendimento)
    assert registro["historico"][0]["tipo_evento"] == "criado"
    assert registro["culturas"] == []


def test_excluido_chega_como_tombstone(org, entrar_como):
    atendimento = abrir(entrar_como, org["cliente"], org["id_cliente"])
    excluir(entrar_como, org["admin"], atendimento)

    corpo = puxar(entrar_como, org["cliente"], desde=org["marco"]).json()

    [item] = corpo["itens"]
    assert item["removido"] is True
    assert set(item["registro"]) == {"id_atendimento", "versao", "sync_seq"}


def test_rls_cada_perfil_recebe_so_o_que_enxerga(org, conexao_admin, entrar_como):
    _, outro_cliente = criar_organizacao(conexao_admin)
    outro_usuario = criar_usuario_do_perfil(
        conexao_admin, "Cliente Usuário", "cliente", id_cliente=outro_cliente
    )
    meu = abrir(entrar_como, org["cliente"], org["id_cliente"])
    dele = abrir(entrar_como, outro_usuario, outro_cliente)

    def ids_de(usuario):
        corpo = puxar(entrar_como, usuario, desde=org["marco"]).json()
        return {i["registro"]["id_atendimento"] for i in corpo["itens"]}

    assert ids_de(org["cliente"]) == {str(meu.id_atendimento)}
    assert ids_de(outro_usuario) == {str(dele.id_atendimento)}
    # O Netafim Admin vê os dois.
    assert {str(meu.id_atendimento), str(dele.id_atendimento)} <= ids_de(org["admin"])


@pytest.mark.parametrize(
    ("parametros", "campo"),
    [
        ({"desde": "abc"}, "desde"),
        ({"desde": "-1"}, "desde"),
        ({"desde": ""}, "desde"),
        ({"desde": "1.5"}, "desde"),
        ({"desde": str(2**63)}, "desde"),
        ({"limite": "abc"}, "limite"),
        ({"limite": "0"}, "limite"),
        ({"limite": "501"}, "limite"),
    ],
)
def test_desde_ou_limite_invalido_e_400_apontando_o_campo(org, entrar_como, parametros, campo):
    resposta = puxar(entrar_como, org["cliente"], **parametros)

    assert resposta.status_code == 400
    assert list(resposta.json()) == [campo]
    assert isinstance(resposta.json()[campo], list)


def test_limite_no_maximo_e_aceito(org, entrar_como):
    assert puxar(entrar_como, org["cliente"], limite=500).status_code == 200


def test_sem_autenticacao_nao_entra():
    resposta = APIClient().get(URL)

    # 403 nesta branch (o 401 vem do módulo de acesso, ainda fora da main).
    assert resposta.status_code in (401, 403)
