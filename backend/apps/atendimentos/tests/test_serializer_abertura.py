import uuid

import pytest

from apps.atendimentos.serializers import AberturaAtendimentoSerializer
from apps.atendimentos.servicos import COLUNAS_ACEITAS_NA_ABERTURA, abrir_atendimento


def dados_minimos(**extras):
    """Pedido mínimo válido de cliente final (id_cliente fictício: o serializer
    não consulta o banco). Os extras entram por cima."""
    dados = {
        "id_atendimento": str(uuid.uuid4()),
        "origem": "cliente_final",
        "motivo": "pos_venda",
        "id_cliente": 1,
        "contato_solicitante": "11999990000",
    }
    dados.update(extras)
    return dados


def validar(dados):
    """Roda o serializer e devolve (serializer, é_válido)."""
    serializer = AberturaAtendimentoSerializer(data=dados)
    return serializer, serializer.is_valid()


@pytest.mark.django_db
def test_dados_minimos_validos_vao_direto_para_o_servico(mundo, entrar_como):
    entrar_como(mundo["id_usuario_cliente"])
    # Aqui o id_cliente precisa ser real, porque o banco confere no INSERT.
    serializer, valido = validar(dados_minimos(id_cliente=mundo["id_cliente"]))
    assert valido, serializer.errors

    atendimento = abrir_atendimento(mundo["id_usuario_cliente"], serializer.validated_data)

    assert atendimento.status == "aberto"
    assert atendimento.numero_protocolo.startswith("OS-")


@pytest.mark.parametrize(
    "campo", ["id_atendimento", "origem", "motivo", "contato_solicitante"]
)
def test_falta_de_campo_obrigatorio_gera_erro_no_campo(campo):
    dados = dados_minimos()
    del dados[campo]

    serializer, valido = validar(dados)

    assert not valido
    assert campo in serializer.errors


@pytest.mark.parametrize("campo", ["origem", "motivo"])
def test_valor_fora_da_lista_gera_erro(campo):
    serializer, valido = validar(dados_minimos(**{campo: "invalido"}))

    assert not valido
    assert campo in serializer.errors


def test_cliente_final_sem_id_cliente_gera_erro():
    dados = dados_minimos()
    del dados["id_cliente"]

    serializer, valido = validar(dados)

    assert not valido
    assert "id_cliente" in serializer.errors


def test_netafim_sem_area_setor_gera_erro():
    dados = dados_minimos(origem="netafim")
    del dados["id_cliente"]

    serializer, valido = validar(dados)

    assert not valido
    assert "area_setor" in serializer.errors


def test_qtd_participantes_precisa_ser_maior_que_zero():
    serializer, valido = validar(dados_minimos(qtd_participantes_prevista=0))

    assert not valido
    assert "qtd_participantes_prevista" in serializer.errors


def test_chaves_que_o_servidor_controla_nao_chegam_ao_validated_data():
    serializer, valido = validar(
        dados_minimos(
            status="concluido",
            numero_protocolo="X",
            id_solicitante=99,
            snapshot_cliente={"nome": "falso"},
        )
    )
    assert valido, serializer.errors

    for chave in ("status", "numero_protocolo", "id_solicitante", "snapshot_cliente"):
        assert chave not in serializer.validated_data


def test_id_distribuidor_origem_e_aceito_e_aparece_no_validated_data():
    serializer, valido = validar(dados_minimos(id_distribuidor_origem=7))

    assert valido, serializer.errors
    assert serializer.validated_data["id_distribuidor_origem"] == 7


def test_todo_campo_do_serializer_esta_nas_colunas_aceitas_do_servico():
    # Guarda: se alguém acrescentar um campo aqui que o serviço descarta, o
    # dado seria aceito na validação e perdido em silêncio na gravação.
    # A única exceção é culturas, que não é coluna: o serviço lê essa chave à
    # parte e grava em atendimento_cultura.
    campos = set(AberturaAtendimentoSerializer().fields)

    assert campos <= set(COLUNAS_ACEITAS_NA_ABERTURA) | {"culturas"}


# Os cinco campos de lista fechada que o banco só aceita com nulo ou um valor
# da lista, e um valor válido de cada um.
CAMPOS_DE_LISTA = {
    "situacao_projeto": "garantia",
    "estado_projeto": "operando",
    "tipo_venda": "projeto",
    "tipo_atendimento": "online",
    "area_setor": "comercial",
}


@pytest.mark.parametrize("campo", list(CAMPOS_DE_LISTA))
def test_texto_vazio_em_campo_de_lista_gera_erro_no_campo(campo):
    # Antes o "" passava e o banco recusava no CHECK, com um 422 genérico.
    serializer, valido = validar(dados_minimos(**{campo: ""}))

    assert not valido
    assert campo in serializer.errors


@pytest.mark.parametrize("campo", list(CAMPOS_DE_LISTA))
def test_campo_de_lista_ausente_ou_nulo_continua_aceito(campo):
    serializer, valido = validar(dados_minimos())
    assert valido, serializer.errors
    assert campo not in serializer.validated_data

    serializer, valido = validar(dados_minimos(**{campo: None}))
    assert valido, serializer.errors
    assert serializer.validated_data[campo] is None


@pytest.mark.parametrize("campo,valor", list(CAMPOS_DE_LISTA.items()))
def test_campo_de_lista_com_valor_valido_continua_aceito(campo, valor):
    serializer, valido = validar(dados_minimos(**{campo: valor}))

    assert valido, serializer.errors
    assert serializer.validated_data[campo] == valor


def test_netafim_com_area_setor_vazio_continua_erro_no_campo():
    dados = dados_minimos(origem="netafim", area_setor="")
    del dados["id_cliente"]

    serializer, valido = validar(dados)

    assert not valido
    assert "area_setor" in serializer.errors
