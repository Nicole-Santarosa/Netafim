"""Domínio de e-mail se desativa, não se apaga (sem banco: nada aqui consulta o PostgreSQL).

O esquema do grupo dá ao ``app_django`` só select, insert e update em ``dominio_email_corporativo``.
Um DELETE na API terminaria em ``permission denied`` (500), como aconteceu no teste no aparelho.
"""

from types import SimpleNamespace

from rest_framework.test import APIRequestFactory, force_authenticate

from apps.contas.api.views import DominioViewSet

fabrica = APIRequestFactory()


def test_a_rota_de_dominio_nao_aceita_delete():
    assert "delete" not in DominioViewSet.http_method_names
    assert "patch" in DominioViewSet.http_method_names  # ativar e desativar continuam


def test_delete_em_dominio_responde_405_para_o_netafim_admin():
    admin = SimpleNamespace(is_authenticated=True, eh_netafim_admin=True)
    requisicao = fabrica.delete("/api/v1/dominios/4")
    force_authenticate(requisicao, user=admin)

    visao = DominioViewSet.as_view(
        {"get": "retrieve", "patch": "partial_update", "delete": "destroy"}
    )
    resposta = visao(requisicao, id_dominio="4")

    assert resposta.status_code == 405
