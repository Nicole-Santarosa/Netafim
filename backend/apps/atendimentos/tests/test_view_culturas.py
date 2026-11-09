import pytest
from rest_framework.test import APIClient

from apps.atendimentos.tests.test_view_listagem import cliente_autenticado

URL = "/api/v1/culturas"

# Culturas que os dados iniciais do banco cadastram (04_dados_iniciais.sql),
# já na ordem de nome em que a API deve devolver.
CULTURAS_INICIAIS = ["Cacau", "Café", "Cana-de-açúcar", "Citrus", "Grãos", "Outros"]


@pytest.mark.django_db
def test_devolve_as_culturas_dos_dados_iniciais_em_ordem_de_nome(mundo, entrar_como):
    entrar_como(mundo["id_usuario_cliente"])

    resposta = cliente_autenticado(mundo["id_usuario_cliente"]).get(URL)

    assert resposta.status_code == 200
    # Lista simples, sem o envelope da paginação, e cada item só com os dois campos.
    assert isinstance(resposta.data, list)
    assert all(set(item) == {"id_cultura", "nome"} for item in resposta.data)
    # Se o banco tiver outras culturas além das iniciais, as iniciais continuam
    # aparecendo todas, e na mesma ordem relativa.
    nomes = [item["nome"] for item in resposta.data]
    assert [n for n in nomes if n in CULTURAS_INICIAIS] == CULTURAS_INICIAIS


@pytest.mark.django_db
def test_nao_devolve_cultura_excluida(mundo, cultura_excluida, entrar_como):
    entrar_como(mundo["id_usuario_cliente"])

    resposta = cliente_autenticado(mundo["id_usuario_cliente"]).get(URL)

    assert resposta.status_code == 200
    assert cultura_excluida not in [item["id_cultura"] for item in resposta.data]


@pytest.mark.django_db
def test_sem_autenticacao_nao_devolve_culturas():
    resposta = APIClient().get(URL)

    assert resposta.status_code in (401, 403)
