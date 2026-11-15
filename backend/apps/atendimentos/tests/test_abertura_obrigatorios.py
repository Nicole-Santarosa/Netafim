"""Campos obrigatórios da abertura conforme o formulário de solicitação da
Netafim: obrigatórios por origem, cultura obrigatória e os 5 motivos aceitos.

Essas regras são da API (AberturaAtendimentoSerializer), não do banco. A maior
parte dos casos roda só no serializer, que é mais rápido; no fim, alguns pela
view, com POST de verdade, para conferir o 400 e o formato do corpo de erro.
"""

import uuid

import pytest

from apps.atendimentos.models import Atendimento, Cultura
from apps.atendimentos.serializers import OBRIGATORIOS_POR_ORIGEM, AberturaAtendimentoSerializer
from apps.atendimentos.tests.test_view_abertura import URL, cliente_autenticado, corpo_valido

# O validate_culturas consulta o catálogo, então todo teste precisa do banco e
# de um usuário ativo no contexto (o RLS da cultura só mostra o catálogo a ele).
pytestmark = pytest.mark.django_db


@pytest.fixture(autouse=True)
def usuario_no_contexto(mundo, entrar_como):
    entrar_como(mundo["id_usuario_cliente"])


def uma_cultura():
    """Id de uma cultura ativa dos dados iniciais do banco."""
    id_cultura = (
        Cultura.objects.filter(deletado_em__isnull=True)
        .values_list("id_cultura", flat=True)
        .first()
    )
    assert id_cultura is not None, "o banco de teste precisa ter ao menos uma cultura"
    return id_cultura


def corpo_completo(origem, **extras):
    """Pedido com todos os obrigatórios da origem preenchidos. Os ids de
    cliente e distribuidor são fictícios: o serializer não consulta essas
    tabelas. Os extras entram por cima."""
    corpo = {
        "id_atendimento": str(uuid.uuid4()),
        "origem": origem,
        "motivo": "pos_venda",
        "contato_solicitante": "11999990000",
        "culturas": [uma_cultura()],
    }
    if origem == "cliente_final":
        corpo.update(
            id_cliente=1,
            id_distribuidor_origem=1,
            cidade="Ribeirão Preto",
            nome_fazenda_informado="Fazenda Santa Rita",
        )
    elif origem == "distribuidor":
        corpo.update(nome_produtor="João da Silva", cidade="Ribeirão Preto")
    elif origem == "netafim":
        corpo.update(area_setor="comercial", nome_produtor="João da Silva", cidade="Ribeirão Preto")
    corpo.update(extras)
    return corpo


def validar(corpo):
    """Roda o serializer e devolve (serializer, é_válido)."""
    serializer = AberturaAtendimentoSerializer(data=corpo)
    return serializer, serializer.is_valid()


# Cada par (origem, campo obrigatório), lido da própria regra do serializer,
# para o teste acompanhar se alguém mudar a lista.
PARES_ORIGEM_CAMPO = [
    (origem, campo) for origem, campos in OBRIGATORIOS_POR_ORIGEM.items() for campo in campos
]


def test_regra_cobre_os_obrigatorios_do_formulario():
    # Guarda contra alguém tirar um campo da regra sem perceber: é a lista do
    # formulário de solicitação (a cultura e o motivo são conferidos à parte).
    assert OBRIGATORIOS_POR_ORIGEM == {
        "cliente_final": (
            "id_cliente",
            "id_distribuidor_origem",
            "cidade",
            "nome_fazenda_informado",
        ),
        "distribuidor": ("nome_produtor", "cidade"),
        "netafim": ("area_setor", "nome_produtor", "cidade"),
    }


@pytest.mark.parametrize("origem,campo", PARES_ORIGEM_CAMPO)
def test_obrigatorio_ausente_gera_erro_no_campo(origem, campo):
    corpo = corpo_completo(origem)
    del corpo[campo]

    serializer, valido = validar(corpo)

    assert not valido
    assert campo in serializer.errors


@pytest.mark.parametrize("valor", [None, "", "   "], ids=["nulo", "vazio", "so_espacos"])
@pytest.mark.parametrize("origem,campo", PARES_ORIGEM_CAMPO)
def test_obrigatorio_nulo_vazio_ou_so_espacos_gera_erro_no_campo(origem, campo, valor):
    serializer, valido = validar(corpo_completo(origem, **{campo: valor}))

    assert not valido
    assert campo in serializer.errors


@pytest.mark.parametrize("origem", list(OBRIGATORIOS_POR_ORIGEM))
def test_corpo_completo_de_cada_origem_e_aceito(origem):
    serializer, valido = validar(corpo_completo(origem))

    assert valido, serializer.errors


def test_todos_os_obrigatorios_que_faltam_vem_juntos():
    # O aplicativo marca todos os campos de uma vez, sem ir corrigindo um a um.
    corpo = corpo_completo("netafim")
    for campo in ("nome_produtor", "cidade"):
        del corpo[campo]

    serializer, valido = validar(corpo)

    assert not valido
    assert set(serializer.errors) == {"nome_produtor", "cidade"}


@pytest.mark.parametrize("origem", list(OBRIGATORIOS_POR_ORIGEM))
def test_culturas_ausente_gera_erro_no_campo(origem):
    corpo = corpo_completo(origem)
    del corpo["culturas"]

    serializer, valido = validar(corpo)

    assert not valido
    assert "culturas" in serializer.errors


@pytest.mark.parametrize("origem", list(OBRIGATORIOS_POR_ORIGEM))
def test_culturas_vazia_gera_erro_no_campo(origem):
    serializer, valido = validar(corpo_completo(origem, culturas=[]))

    assert not valido
    assert "culturas" in serializer.errors


@pytest.mark.parametrize(
    "motivo", ["digital_farming", "duvidas", "entrega_tecnica", "pos_venda", "treinamento"]
)
def test_cada_um_dos_cinco_motivos_e_aceito(motivo):
    serializer, valido = validar(corpo_completo("cliente_final", motivo=motivo))

    assert valido, serializer.errors
    assert serializer.validated_data["motivo"] == motivo


def test_programacao_feiras_gera_erro_no_campo_motivo():
    serializer, valido = validar(corpo_completo("cliente_final", motivo="programacao_feiras"))

    assert not valido
    assert "motivo" in serializer.errors


# Pela view, com POST de verdade.


def test_post_sem_cidade_devolve_400_com_o_erro_no_campo(mundo):
    corpo = corpo_valido(mundo)
    del corpo["cidade"]

    resposta = cliente_autenticado(mundo["id_usuario_cliente"]).post(URL, corpo, format="json")

    # Mesmo formato dos outros erros de validação do DRF: o nome do campo
    # aponta para a lista de mensagens.
    assert resposta.status_code == 400
    assert resposta.json() == {"cidade": ["Obrigatório quando a origem é cliente_final."]}
    assert not Atendimento.objects.filter(id_atendimento=corpo["id_atendimento"]).exists()


def test_post_com_culturas_vazia_devolve_400_no_campo(mundo):
    corpo = corpo_valido(mundo, culturas=[])

    resposta = cliente_autenticado(mundo["id_usuario_cliente"]).post(URL, corpo, format="json")

    assert resposta.status_code == 400
    assert list(resposta.json()) == ["culturas"]
    assert not Atendimento.objects.filter(id_atendimento=corpo["id_atendimento"]).exists()


def test_post_com_programacao_feiras_devolve_400_no_campo_motivo(mundo):
    corpo = corpo_valido(mundo, motivo="programacao_feiras")

    resposta = cliente_autenticado(mundo["id_usuario_cliente"]).post(URL, corpo, format="json")

    assert resposta.status_code == 400
    assert list(resposta.json()) == ["motivo"]
    assert not Atendimento.objects.filter(id_atendimento=corpo["id_atendimento"]).exists()
