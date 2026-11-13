import json
import uuid
from types import SimpleNamespace

import pytest
from django.utils import timezone

from apps.atendimentos.exportacao import exportar_atendimentos
from apps.atendimentos.models import Atendimento
from apps.atendimentos.servicos import abrir_atendimento, atualizar_atendimento
from apps.atendimentos.tests.test_view_alteracao import (
    criar_admin_netafim,
    criar_usuario,
)

# Os atendimentos de cada teste são abertos pela conexão da API, dentro da
# transação que o pytest desfaz no fim; só os usuários ficam gravados. Cada
# teste cria titulares novos, então nada de outro teste aparece na exportação.


def novo_cliente(conexao_admin, mundo):
    """Cliente Usuário novo na empresa do mundo, para servir de titular."""
    return criar_usuario(
        conexao_admin, "Cliente Usuário", "cliente", id_cliente=mundo["id_cliente"]
    )


def abrir(entrar_como, mundo, id_usuario, **extras):
    """Abre um atendimento de cliente final no nome do usuário dado."""
    entrar_como(id_usuario)
    dados = {
        "id_atendimento": uuid.uuid4(),
        "origem": "cliente_final",
        "motivo": "pos_venda",
        "id_cliente": mundo["id_cliente"],
        "contato_solicitante": "11999990000",
        "descricao_problema": "Gotejador entupido",
    }
    dados.update(extras)
    return abrir_atendimento(id_usuario, dados)


def protocolos(exportacao):
    return [a["numero_protocolo"] for a in exportacao["atendimentos"]]


@pytest.mark.django_db
def test_titular_sem_atendimento_devolve_listas_vazias(
    conexao_admin, mundo, entrar_como
):
    titular = novo_cliente(conexao_admin, mundo)
    entrar_como(titular)

    assert exportar_atendimentos(titular) == {
        "id_usuario": titular,
        "atendimentos": [],
        "historico": [],
    }


@pytest.mark.django_db
def test_titular_que_abriu_recebe_os_proprios_atendimentos_e_eventos(
    conexao_admin, mundo, entrar_como
):
    titular = novo_cliente(conexao_admin, mundo)
    atendimento = abrir(entrar_como, mundo, titular)

    exportacao = exportar_atendimentos(titular)

    assert protocolos(exportacao) == [atendimento.numero_protocolo]
    item = exportacao["atendimentos"][0]
    assert item["id_atendimento"] == str(atendimento.id_atendimento)
    assert item["papeis"] == ["solicitante"]
    assert item["excluido_em"] is None
    # O que ele mesmo digitou no pedido vem junto.
    assert item["dados_informados"]["contato_solicitante"] == "11999990000"
    assert item["dados_informados"]["descricao_problema"] == "Gotejador entupido"
    # Cópias do cadastro do cliente e ids de outras pessoas ficam fora.
    for campo in (
        "snapshot_cliente",
        "snapshot_propriedade",
        "id_cliente",
        "id_solicitante",
        "versao",
    ):
        assert campo not in item

    # Os eventos gerados na abertura têm o titular como responsável.
    assert exportacao["historico"]
    for evento in exportacao["historico"]:
        assert evento["id_atendimento"] == str(atendimento.id_atendimento)
        assert evento["numero_protocolo"] == atendimento.numero_protocolo
        assert "observacao" not in evento
        assert "id_usuario_responsavel" not in evento


@pytest.mark.django_db
def test_aceita_o_objeto_do_usuario_e_recusa_outros_tipos(
    conexao_admin, mundo, entrar_como
):
    titular = novo_cliente(conexao_admin, mundo)
    abrir(entrar_como, mundo, titular)

    # Qualquer objeto com pk serve, como o request.user das views.
    assert exportar_atendimentos(SimpleNamespace(pk=titular)) == exportar_atendimentos(
        titular
    )
    for invalido in (str(titular), True, None):
        with pytest.raises(TypeError):
            exportar_atendimentos(invalido)


@pytest.mark.django_db
def test_atendimento_de_outra_pessoa_nao_entra(conexao_admin, mundo, entrar_como):
    titular = novo_cliente(conexao_admin, mundo)
    outro = novo_cliente(conexao_admin, mundo)
    do_titular = abrir(entrar_como, mundo, titular)
    abrir(entrar_como, mundo, outro)

    entrar_como(titular)
    exportacao = exportar_atendimentos(titular)

    assert protocolos(exportacao) == [do_titular.numero_protocolo]
    assert {e["id_atendimento"] for e in exportacao["historico"]} == {
        str(do_titular.id_atendimento)
    }


@pytest.mark.django_db
def test_tecnico_responsavel_recebe_o_atendimento_sem_o_que_o_cliente_digitou(
    conexao_admin, mundo, entrar_como
):
    cliente = novo_cliente(conexao_admin, mundo)
    admin = criar_admin_netafim(conexao_admin)
    tecnico = criar_usuario(conexao_admin, "Netafim Usuário", "netafim")
    atendimento = abrir(entrar_como, mundo, cliente)
    entrar_como(admin)
    atualizar_atendimento(
        atendimento.id_atendimento, 1, {"id_tecnico_responsavel": tecnico}
    )

    entrar_como(tecnico)
    exportacao = exportar_atendimentos(tecnico)

    assert protocolos(exportacao) == [atendimento.numero_protocolo]
    item = exportacao["atendimentos"][0]
    assert item["papeis"] == ["tecnico_responsavel"]
    # O contato e o problema são dados do cliente, não do técnico.
    assert item["dados_informados"] is None
    # A atribuição foi feita pelo admin: o evento é do admin, não do técnico.
    assert exportacao["historico"] == []


@pytest.mark.django_db
def test_admin_exportando_outro_usuario_recebe_so_o_do_titular(
    conexao_admin, mundo, entrar_como
):
    titular = novo_cliente(conexao_admin, mundo)
    outro = novo_cliente(conexao_admin, mundo)
    admin = criar_admin_netafim(conexao_admin)
    do_titular = abrir(entrar_como, mundo, titular)
    do_outro = abrir(entrar_como, mundo, outro)
    # O admin altera o do outro, para ter um atendimento visível a ele que
    # também não é do titular.
    entrar_como(admin)
    atualizar_atendimento(do_outro.id_atendimento, 1, {"status": "cancelado"})

    # O admin enxerga os dois pelo RLS; a exportação traz só o do titular.
    exportacao = exportar_atendimentos(titular)

    assert (
        Atendimento.objects.filter(
            id_atendimento__in=[do_titular.id_atendimento, do_outro.id_atendimento]
        ).count()
        == 2
    )
    assert protocolos(exportacao) == [do_titular.numero_protocolo]
    assert {e["id_atendimento"] for e in exportacao["historico"]} == {
        str(do_titular.id_atendimento)
    }

    # Na exportação do próprio admin, o atendimento que ele alterou aparece
    # com o papel de último a alterar, e o do titular não.
    do_admin = exportar_atendimentos(admin)
    assert protocolos(do_admin) == [do_outro.numero_protocolo]
    assert do_admin["atendimentos"][0]["papeis"] == ["ultimo_a_alterar"]
    assert do_admin["atendimentos"][0]["dados_informados"] is None


@pytest.mark.django_db
def test_resultado_e_serializavel_em_json(conexao_admin, mundo, entrar_como):
    titular = novo_cliente(conexao_admin, mundo)
    abrir(entrar_como, mundo, titular, data_inicio_problema=timezone.localdate())

    exportacao = exportar_atendimentos(titular)

    # json.dumps sem default: falha se sobrar UUID, data ou outro objeto.
    texto = json.dumps(exportacao)
    assert json.loads(texto) == exportacao


@pytest.mark.django_db
def test_ordem_por_data_de_abertura_e_protocolo(conexao_admin, mundo, entrar_como):
    titular = novo_cliente(conexao_admin, mundo)
    agora = timezone.now()
    # Abertos fora de ordem; dois com a mesma data, para o protocolo desempatar.
    tarde = abrir(entrar_como, mundo, titular, data_abertura=agora)
    cedo = abrir(
        entrar_como, mundo, titular, data_abertura=agora - timezone.timedelta(days=2)
    )
    empate_1 = abrir(
        entrar_como, mundo, titular, data_abertura=agora - timezone.timedelta(days=1)
    )
    empate_2 = abrir(
        entrar_como, mundo, titular, data_abertura=agora - timezone.timedelta(days=1)
    )
    empates = sorted([empate_1.numero_protocolo, empate_2.numero_protocolo])

    primeira = exportar_atendimentos(titular)

    assert protocolos(primeira) == [
        cedo.numero_protocolo,
        *empates,
        tarde.numero_protocolo,
    ]
    assert exportar_atendimentos(titular) == primeira
    datas = [e["data_evento"] for e in primeira["historico"]]
    assert datas == sorted(datas)


@pytest.mark.django_db
def test_numero_de_consultas_nao_cresce_com_o_volume(
    conexao_admin, mundo, entrar_como, django_assert_num_queries
):
    titular = novo_cliente(conexao_admin, mundo)
    entrar_como(titular)
    with django_assert_num_queries(2):
        exportar_atendimentos(titular)

    for _ in range(4):
        abrir(entrar_como, mundo, titular)
    with django_assert_num_queries(2):
        exportacao = exportar_atendimentos(titular)
    assert len(exportacao["atendimentos"]) == 4


@pytest.mark.django_db
def test_atendimento_excluido_logicamente_entra_com_a_data_da_exclusao(
    conexao_admin, mundo, entrar_como
):
    titular = novo_cliente(conexao_admin, mundo)
    admin = criar_admin_netafim(conexao_admin)
    mantido = abrir(entrar_como, mundo, titular)
    excluido = abrir(entrar_como, mundo, titular)
    entrar_como(admin)
    Atendimento.objects.filter(id_atendimento=excluido.id_atendimento).update(
        deletado_em=timezone.now()
    )

    entrar_como(titular)
    exportacao = exportar_atendimentos(titular)

    por_id = {a["id_atendimento"]: a for a in exportacao["atendimentos"]}
    assert set(por_id) == {str(mantido.id_atendimento), str(excluido.id_atendimento)}
    assert por_id[str(mantido.id_atendimento)]["excluido_em"] is None
    assert por_id[str(excluido.id_atendimento)]["excluido_em"] is not None
