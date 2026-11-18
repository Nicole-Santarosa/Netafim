"""Erros do DRF com ``codigo`` (sem banco: nenhum destes casos chega a consultar o PostgreSQL)."""

import pytest
from rest_framework import serializers
from rest_framework.exceptions import NotFound, PermissionDenied, Throttled, ValidationError
from rest_framework.parsers import JSONParser
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.response import Response
from rest_framework.test import APIRequestFactory
from rest_framework.views import APIView
from rest_framework_simplejwt.exceptions import AuthenticationFailed as FalhaJWT

from apps.contas.autenticacao import AutenticacaoJWT
from apps.contas.erros_drf import padronizar_erro
from apps.contas.excecoes import ErroNegocio

fabrica = APIRequestFactory()


class Protegida(APIView):
    authentication_classes = [AutenticacaoJWT]
    permission_classes = [IsAuthenticated]

    def get(self, request):
        return Response({"ok": True})


class Aberta(APIView):
    authentication_classes = []
    permission_classes = [AllowAny]
    parser_classes = [JSONParser]

    def get(self, request):
        raise NotFound()

    def post(self, request):
        request.data  # noqa: B018  (ler o corpo é o que dispara o 415 com um tipo que o parser não aceita)
        return Response({"ok": True})


def chamar(view, requisicao):
    resposta = view.as_view()(requisicao)
    resposta.render()
    return resposta


def test_sem_token_devolve_nao_autenticado_sem_code():
    r = chamar(Protegida, fabrica.get("/x"))
    assert r.status_code == 401
    assert r.data["codigo"] == "nao_autenticado"
    assert "code" not in r.data and "messages" not in r.data
    assert r.data["detail"]


def test_token_invalido_vira_token_invalido_e_esconde_o_messages():
    r = chamar(Protegida, fabrica.get("/x", HTTP_AUTHORIZATION="Bearer abc.def.ghi"))
    assert r.status_code == 401
    assert r.data["codigo"] == "token_invalido"
    assert "code" not in r.data
    assert "messages" not in r.data and "token_class" not in r.content.decode()


@pytest.mark.parametrize(
    ("code", "esperado"),
    [
        ("user_inactive", "conta_inativa"),
        ("device_revoked", "dispositivo_revogado"),
        ("user_not_found", "token_invalido"),
        ("qualquer_outro", "token_invalido"),
    ],
)
def test_codes_do_jwt_viram_codigos_do_app(code, esperado):
    resposta = padronizar_erro(FalhaJWT("Mensagem.", code=code), {})
    assert resposta.status_code == 401
    assert resposta.data == {"detail": "Mensagem.", "codigo": esperado}


def test_404_405_415_e_403_ganham_codigo():
    assert padronizar_erro(NotFound(), {}).data["codigo"] == "nao_encontrado"
    assert padronizar_erro(PermissionDenied(), {}).data["codigo"] == "sem_permissao"

    r404 = chamar(Aberta, fabrica.get("/x"))
    assert (r404.status_code, r404.data["codigo"]) == (404, "nao_encontrado")

    r405 = chamar(Aberta, fabrica.put("/x", {}, format="json"))
    assert (r405.status_code, r405.data["codigo"]) == (405, "metodo_nao_permitido")

    r415 = chamar(Aberta, fabrica.post("/x", "texto", content_type="text/plain"))
    assert (r415.status_code, r415.data["codigo"]) == (415, "tipo_de_midia_nao_suportado")


def test_400_de_validacao_continua_no_formato_padrao_do_drf():
    resposta = padronizar_erro(ValidationError({"email": ["Informe um e-mail válido."]}), {})
    assert resposta.status_code == 400
    assert resposta.data == {"email": ["Informe um e-mail válido."]}
    assert "codigo" not in resposta.data

    em_lista = padronizar_erro(serializers.ValidationError(["erro solto"]), {})
    assert em_lista.data == ["erro solto"]


def test_erro_de_negocio_mantem_o_proprio_codigo():
    resposta = padronizar_erro(
        ErroNegocio("Conta aguardando.", "conta_pendente", status_code=403), {}
    )
    assert resposta.data["codigo"] == "conta_pendente"


def test_429_nao_e_alterado():
    resposta = padronizar_erro(Throttled(wait=30), {})
    assert resposta.status_code == 429
    assert "codigo" not in resposta.data


def test_o_acesso_usa_este_tratador_nas_configuracoes():
    from django.conf import settings

    assert settings.REST_FRAMEWORK["EXCEPTION_HANDLER"] == "apps.contas.erros_drf.padronizar_erro"
