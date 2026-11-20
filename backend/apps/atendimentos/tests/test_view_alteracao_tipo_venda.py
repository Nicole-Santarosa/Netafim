"""PATCH de atendimento e o tipo de venda.

O tipo de venda passou a ser obrigatório só na ABERTURA (POST, no
AberturaAtendimentoSerializer). O PATCH usa outro serializer, o
AtendimentoAlteracaoSerializer, que não conhece esse campo. Estes testes provam
três coisas: a regra da abertura não vazou para a edição (PATCH parcial sem
tipo_venda segue aceito), a chave tipo_venda mandada no PATCH é ignorada de
propósito (não altera nada e não gera erro) e um PATCH que só traga o
tipo_venda não tem alteração nenhuma para fazer e responde 400.
"""

import pytest

from apps.atendimentos.models import Atendimento
from apps.atendimentos.servicos import abrir_atendimento
from apps.atendimentos.tests.test_view_alteracao import (
    criar_admin_netafim,
    criar_usuario,
    url,
)
from apps.atendimentos.tests.test_view_listagem import cliente_autenticado, dados_pedido


def abrir_com_tipo_de_venda(mundo, entrar_como):
    """Abre um atendimento no nome do cliente do mundo, já com o tipo de venda
    'projeto', e devolve o objeto. O tipo de venda é gravado direto pelo
    serviço, que não valida (a validação é do serializer da abertura)."""
    entrar_como(mundo["id_usuario_cliente"])
    return abrir_atendimento(
        mundo["id_usuario_cliente"],
        dados_pedido(mundo["id_cliente"], tipo_venda="projeto"),
    )


def tipo_de_venda_no_banco(atendimento):
    """Lê o tipo de venda gravado, para provar que o PATCH não o mudou."""
    return Atendimento.objects.get(id_atendimento=atendimento.id_atendimento).tipo_venda


# Cada alteração real do PATCH, com o corpo SEM tipo_venda. Quem faz é o Netafim
# Admin, que é o perfil que o banco autoriza a destinar, cancelar, vincular
# técnico e mudar a situação do projeto.
ALTERACOES = {
    "so_situacao_projeto": lambda tecnico: {"situacao_projeto": "fora_garantia"},
    "so_status": lambda tecnico: {"status": "cancelado"},
    "so_tecnico": lambda tecnico: {"id_tecnico_responsavel": tecnico},
}


@pytest.mark.django_db
@pytest.mark.parametrize("alteracao", list(ALTERACOES))
def test_patch_parcial_sem_tipo_venda_e_aceito(
    mundo, conexao_admin, entrar_como, alteracao
):
    atendimento = abrir_com_tipo_de_venda(mundo, entrar_como)
    admin = criar_admin_netafim(conexao_admin)
    tecnico = criar_usuario(conexao_admin, "Netafim Usuário", "netafim")
    entrar_como(admin)

    corpo = {"versao": 1, **ALTERACOES[alteracao](tecnico)}
    assert "tipo_venda" not in corpo
    resposta = cliente_autenticado(admin).patch(
        url(atendimento.id_atendimento), corpo, format="json"
    )

    # A regra de obrigatório da abertura não vazou para a edição.
    assert resposta.status_code == 200
    assert resposta.data["versao"] == 2
    # E o tipo de venda segue como estava.
    assert resposta.data["tipo_venda"] == "projeto"
    assert tipo_de_venda_no_banco(atendimento) == "projeto"


@pytest.mark.django_db
@pytest.mark.parametrize("valor", ["venda_regular", "", "fora_da_lista", None])
def test_tipo_venda_junto_de_campo_valido_e_ignorado_de_proposito(
    mundo, conexao_admin, entrar_como, valor
):
    atendimento = abrir_com_tipo_de_venda(mundo, entrar_como)
    admin = criar_admin_netafim(conexao_admin)
    entrar_como(admin)

    # O corpo traz um tipo_venda (válido, vazio, fora da lista ou nulo) ao lado
    # de uma alteração válida. O AtendimentoAlteracaoSerializer não tem esse
    # campo e ignora chaves desconhecidas DE PROPÓSITO: o tipo de venda é um
    # dado do pedido, definido na abertura, e não se edita depois. Por isso não
    # há 400 nem para valor inválido; a chave simplesmente não chega à gravação.
    resposta = cliente_autenticado(admin).patch(
        url(atendimento.id_atendimento),
        {"versao": 1, "situacao_projeto": "garantia", "tipo_venda": valor},
        format="json",
    )

    assert resposta.status_code == 200
    # A alteração válida foi feita (a versão subiu e a situação mudou)...
    assert resposta.data["versao"] == 2
    assert resposta.data["situacao_projeto"] == "garantia"
    # ...e o tipo de venda NÃO mudou, nem na resposta nem no banco.
    assert resposta.data["tipo_venda"] == "projeto"
    assert tipo_de_venda_no_banco(atendimento) == "projeto"


@pytest.mark.django_db
@pytest.mark.parametrize("valor", ["venda_regular", "", "fora_da_lista", None])
def test_patch_so_com_tipo_venda_devolve_400_de_nenhuma_alteracao(
    mundo, conexao_admin, entrar_como, valor
):
    atendimento = abrir_com_tipo_de_venda(mundo, entrar_como)
    admin = criar_admin_netafim(conexao_admin)
    entrar_como(admin)

    resposta = cliente_autenticado(admin).patch(
        url(atendimento.id_atendimento),
        {"versao": 1, "tipo_venda": valor},
        format="json",
    )

    # Como a chave é ignorada, sobra só a versão: não há o que alterar. O erro
    # é o geral do corpo (non_field_errors), não um erro no campo tipo_venda.
    assert resposta.status_code == 400
    assert "tipo_venda" not in resposta.data
    assert "Informe ao menos uma alteração" in str(resposta.data["non_field_errors"][0])
    # Nada foi gravado: a versão do banco continua 1 e o tipo de venda também.
    gravado = Atendimento.objects.get(id_atendimento=atendimento.id_atendimento)
    assert gravado.versao == 1
    assert gravado.tipo_venda == "projeto"
