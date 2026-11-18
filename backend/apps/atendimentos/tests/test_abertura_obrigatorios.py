"""Campos obrigatórios da abertura conforme o formulário de solicitação da
Netafim: obrigatórios por origem, cultura obrigatória, tipo de venda
obrigatório em todas as origens e os 5 motivos aceitos. O distribuidor de
origem é opcional (decisão da Netafim de 18/11/2026): o cliente final sem
distribuidor padrão abre o atendimento e ele fica sem distribuidor de origem.

Essas regras são da API (AberturaAtendimentoSerializer), não do banco. A maior
parte dos casos roda só no serializer, que é mais rápido; no fim, alguns pela
view, com POST de verdade, para conferir o 400 e o formato do corpo de erro.
"""

import uuid

import pytest

from apps.atendimentos.models import Atendimento, Cultura
from apps.atendimentos.serializers import (
    MENSAGEM_TIPO_VENDA,
    OBRIGATORIOS_POR_ORIGEM,
    AberturaAtendimentoSerializer,
)
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
        # Obrigatório em todas as origens.
        "tipo_venda": "projeto",
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
    # A cidade vale nas três origens e o nome da fazenda só no cliente final. O
    # id_distribuidor_origem saiu da lista do cliente final em 18/11/2026.
    assert OBRIGATORIOS_POR_ORIGEM == {
        "cliente_final": ("id_cliente", "cidade", "nome_fazenda_informado"),
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


# Tipo de venda: obrigatório em todo atendimento, em todas as origens.

ORIGENS = list(OBRIGATORIOS_POR_ORIGEM)


@pytest.mark.parametrize("origem", ORIGENS)
def test_tipo_venda_ausente_gera_erro_no_campo(origem):
    corpo = corpo_completo(origem)
    del corpo["tipo_venda"]

    serializer, valido = validar(corpo)

    assert not valido
    # Só o tipo de venda falta, e a mensagem é a específica do campo.
    assert set(serializer.errors) == {"tipo_venda"}
    assert [str(erro) for erro in serializer.errors["tipo_venda"]] == [MENSAGEM_TIPO_VENDA]


@pytest.mark.parametrize("valor", [None, "", "   "], ids=["nulo", "vazio", "so_espacos"])
@pytest.mark.parametrize("origem", ORIGENS)
def test_tipo_venda_nulo_ou_vazio_gera_erro_no_campo(origem, valor):
    serializer, valido = validar(corpo_completo(origem, tipo_venda=valor))

    assert not valido
    assert "tipo_venda" in serializer.errors


def test_tipo_venda_fora_da_lista_gera_erro_no_campo():
    serializer, valido = validar(corpo_completo("cliente_final", tipo_venda="outro"))

    assert not valido
    assert "tipo_venda" in serializer.errors


@pytest.mark.parametrize("tipo_venda", ["projeto", "venda_regular"])
@pytest.mark.parametrize("origem", ORIGENS)
def test_cada_tipo_de_venda_valido_e_aceito_em_cada_origem(origem, tipo_venda):
    serializer, valido = validar(corpo_completo(origem, tipo_venda=tipo_venda))

    assert valido, serializer.errors
    assert serializer.validated_data["tipo_venda"] == tipo_venda


# Cidade e nome da fazenda por origem. A cidade ausente nas três origens já é
# coberta por test_obrigatorio_ausente_gera_erro_no_campo (o par (origem,
# "cidade") existe para cada uma); aqui fica a regra da fazenda, que é só do
# cliente final.


def test_nome_da_fazenda_ausente_gera_erro_so_no_cliente_final():
    corpo = corpo_completo("cliente_final")
    del corpo["nome_fazenda_informado"]
    serializer, valido = validar(corpo)

    assert not valido
    assert set(serializer.errors) == {"nome_fazenda_informado"}


@pytest.mark.parametrize("origem", ["netafim", "distribuidor"])
def test_nome_da_fazenda_ausente_passa_na_netafim_e_no_distribuidor(origem):
    corpo = corpo_completo(origem)
    assert "nome_fazenda_informado" not in corpo

    serializer, valido = validar(corpo)

    assert valido, serializer.errors


# Distribuidor de origem opcional. Pela view, com POST de verdade.


def test_cliente_final_sem_distribuidor_e_aceito_no_serializer():
    corpo = corpo_completo("cliente_final")
    del corpo["id_distribuidor_origem"]

    serializer, valido = validar(corpo)

    assert valido, serializer.errors
    assert "id_distribuidor_origem" not in serializer.validated_data


def test_cliente_final_com_distribuidor_nulo_e_aceito_no_serializer():
    serializer, valido = validar(corpo_completo("cliente_final", id_distribuidor_origem=None))

    assert valido, serializer.errors
    assert serializer.validated_data["id_distribuidor_origem"] is None


def test_post_cliente_final_sem_distribuidor_devolve_201_e_fica_sem_distribuidor(mundo):
    corpo = corpo_valido(mundo)
    del corpo["id_distribuidor_origem"]
    cliente = cliente_autenticado(mundo["id_usuario_cliente"])

    resposta = cliente.post(URL, corpo, format="json")

    assert resposta.status_code == 201
    assert resposta.data["status"] == "aberto"
    # O distribuidor de origem não vem na resposta da abertura; confere no
    # detalhe e no banco que o atendimento nasceu sem ele.
    detalhe = cliente.get(f"{URL}/{corpo['id_atendimento']}")
    assert detalhe.status_code == 200
    assert detalhe.data["id_distribuidor_origem"] is None
    assert detalhe.data["status"] == "aberto"
    atendimento = Atendimento.objects.get(id_atendimento=corpo["id_atendimento"])
    assert atendimento.id_distribuidor_origem is None


def test_post_cliente_final_com_distribuidor_continua_devolvendo_201(mundo):
    corpo = corpo_valido(mundo)
    assert corpo["id_distribuidor_origem"] == mundo["id_distribuidor"]
    cliente = cliente_autenticado(mundo["id_usuario_cliente"])

    resposta = cliente.post(URL, corpo, format="json")

    assert resposta.status_code == 201
    detalhe = cliente.get(f"{URL}/{corpo['id_atendimento']}")
    assert detalhe.data["id_distribuidor_origem"] == mundo["id_distribuidor"]


def test_post_sem_tipo_venda_devolve_400_com_o_erro_no_campo(mundo):
    corpo = corpo_valido(mundo)
    del corpo["tipo_venda"]

    resposta = cliente_autenticado(mundo["id_usuario_cliente"]).post(URL, corpo, format="json")

    assert resposta.status_code == 400
    assert resposta.json() == {"tipo_venda": [MENSAGEM_TIPO_VENDA]}
    assert not Atendimento.objects.filter(id_atendimento=corpo["id_atendimento"]).exists()


def test_post_com_tipo_venda_vazio_devolve_400_no_campo(mundo):
    corpo = corpo_valido(mundo, tipo_venda="")

    resposta = cliente_autenticado(mundo["id_usuario_cliente"]).post(URL, corpo, format="json")

    assert resposta.status_code == 400
    assert resposta.json() == {"tipo_venda": [MENSAGEM_TIPO_VENDA]}
    assert not Atendimento.objects.filter(id_atendimento=corpo["id_atendimento"]).exists()


def test_reenvio_do_mesmo_uuid_sem_distribuidor_continua_devolvendo_409_duplicado(mundo):
    corpo = corpo_valido(mundo)
    del corpo["id_distribuidor_origem"]
    cliente = cliente_autenticado(mundo["id_usuario_cliente"])
    assert cliente.post(URL, corpo, format="json").status_code == 201

    resposta = cliente.post(URL, corpo, format="json")

    # Mesmo comportamento de antes: duplicado, e o app confirma com o GET.
    assert resposta.status_code == 409
    assert resposta.data["codigo"] == "duplicado"
