"""Testes da regra do id_distribuidor_origem: usuário de distribuidor só pode
informar o distribuidor do próprio cadastro (POST /atendimentos, POST
/sync/push e o serializer).

Os testes vão até o banco real: o validador pergunta ao banco quem é o ator
(app_tipo_org e app_id_distribuidor), e a policy de INSERT e a leitura do
registro recém-criado também rodam de verdade com usuário de distribuidor.
"""

import pytest

from apps.atendimentos.models import Atendimento
from apps.atendimentos.serializers import AberturaAtendimentoSerializer
from apps.atendimentos.tests.test_rls_perfis import criar_usuario_do_perfil
from apps.atendimentos.tests.test_sincronizacao_pull import (  # noqa: F401
    criar_organizacao,
    culturas_ativas,
    org,
)
from apps.atendimentos.tests.test_sync_push import (
    como,
    enviar,
    operacao_criar,
    resultado_unico,
)
from apps.atendimentos.tests.test_view_abertura import (
    URL,
    cliente_autenticado,
    corpo_valido,
)

pytestmark = pytest.mark.django_db

MENSAGEM = "Informe o distribuidor do seu cadastro."
PERFIS_DE_DISTRIBUIDOR = ["Distribuidor Admin", "Distribuidor Usuário"]


@pytest.fixture
def outro_distribuidor(conexao_admin):
    """Um segundo distribuidor, diferente do da fixture org."""
    id_distribuidor, _ = criar_organizacao(conexao_admin)
    return id_distribuidor


@pytest.fixture
def usuarios_de_distribuidor(org, conexao_admin):  # noqa: F811
    """Um usuário de cada perfil de distribuidor, os dois do distribuidor da org."""
    return {
        perfil: criar_usuario_do_perfil(
            conexao_admin, perfil, "distribuidor", id_distribuidor=org["id_distribuidor"]
        )
        for perfil in PERFIS_DE_DISTRIBUIDOR
    }


def corpo_do_distribuidor(org, **extras):  # noqa: F811
    """Corpo de abertura na origem distribuidor (nome do produtor e cidade, sem
    id_cliente). Quem chama precisa ter feito o entrar_como antes, porque a
    cultura é lida do banco."""
    return corpo_valido(
        org, origem="distribuidor", id_cliente=None, nome_produtor="Produtor Teste", **extras
    )


def existe_no_banco(entrar_como, org, id_atendimento):  # noqa: F811
    """Confere pelo Netafim Admin (que enxerga tudo) se o atendimento foi gravado."""
    entrar_como(org["admin"])
    return Atendimento.objects.filter(id_atendimento=id_atendimento).exists()


# POST /atendimentos.


@pytest.mark.parametrize("perfil", PERFIS_DE_DISTRIBUIDOR)
def test_distribuidor_com_o_proprio_id_abre_com_201(
    perfil, org, usuarios_de_distribuidor, entrar_como  # noqa: F811
):
    id_usuario = usuarios_de_distribuidor[perfil]
    cliente = como(entrar_como, id_usuario)
    corpo = corpo_do_distribuidor(org, id_distribuidor_origem=org["id_distribuidor"])

    resposta = cliente.post(URL, corpo, format="json")

    assert resposta.status_code == 201, resposta.content
    assert resposta.json()["id_atendimento"] == corpo["id_atendimento"]
    # A leitura do registro recém-criado (RETURNING e refresh_from_db) funcionou
    # com o usuário de distribuidor, e o distribuidor de origem foi gravado.
    entrar_como(id_usuario)
    gravado = Atendimento.objects.get(id_atendimento=corpo["id_atendimento"])
    assert gravado.id_distribuidor_origem == org["id_distribuidor"]
    assert gravado.id_solicitante == id_usuario


@pytest.mark.parametrize("perfil", PERFIS_DE_DISTRIBUIDOR)
def test_distribuidor_com_id_de_outro_distribuidor_recebe_400_no_campo(
    perfil, org, usuarios_de_distribuidor, outro_distribuidor, entrar_como  # noqa: F811
):
    cliente = como(entrar_como, usuarios_de_distribuidor[perfil])
    corpo = corpo_do_distribuidor(org, id_distribuidor_origem=outro_distribuidor)

    resposta = cliente.post(URL, corpo, format="json")

    # Prova também que a view passa o context com o id do usuário.
    assert resposta.status_code == 400
    assert resposta.json() == {"id_distribuidor_origem": [MENSAGEM]}
    assert not existe_no_banco(entrar_como, org, corpo["id_atendimento"])


@pytest.mark.parametrize("perfil", PERFIS_DE_DISTRIBUIDOR)
@pytest.mark.parametrize("situacao", ["omitido", "nulo"])
def test_distribuidor_sem_o_campo_recebe_400_no_campo_e_nada_e_gravado(
    perfil, situacao, org, usuarios_de_distribuidor, entrar_como  # noqa: F811
):
    # Para o usuário de distribuidor o campo é obrigatório: o Distribuidor Admin
    # nem leria de volta o atendimento sem ele (403 do SELECT no RETURNING).
    cliente = como(entrar_como, usuarios_de_distribuidor[perfil])
    corpo = corpo_do_distribuidor(org)
    if situacao == "omitido":
        del corpo["id_distribuidor_origem"]
    else:
        corpo["id_distribuidor_origem"] = None

    resposta = cliente.post(URL, corpo, format="json")

    assert resposta.status_code == 400
    assert resposta.json() == {"id_distribuidor_origem": [MENSAGEM]}
    assert not existe_no_banco(entrar_como, org, corpo["id_atendimento"])


# Netafim e cliente seguem com o campo opcional, e o servidor não o preenche.


@pytest.mark.parametrize("situacao", ["omitido", "nulo", "de_outro_distribuidor"])
@pytest.mark.parametrize("quem", ["admin", "cliente"])
def test_netafim_e_cliente_nao_sao_afetados(
    quem, situacao, org, outro_distribuidor, entrar_como  # noqa: F811
):
    id_usuario = org[quem]
    cliente = como(entrar_como, id_usuario)
    corpo = corpo_valido(org)
    if situacao == "omitido":
        del corpo["id_distribuidor_origem"]
    elif situacao == "nulo":
        corpo["id_distribuidor_origem"] = None
    else:
        corpo["id_distribuidor_origem"] = outro_distribuidor

    resposta = cliente.post(URL, corpo, format="json")

    assert resposta.status_code == 201, resposta.content
    # O servidor não preenche: omitido ou nulo ficam nulos, o resto é o enviado.
    esperado = None if situacao != "de_outro_distribuidor" else outro_distribuidor
    entrar_como(org["admin"])
    gravado = Atendimento.objects.get(id_atendimento=corpo["id_atendimento"])
    assert gravado.id_distribuidor_origem == esperado


# Serializer sozinho.


def test_serializer_sem_context_pula_a_validacao(
    org, usuarios_de_distribuidor, outro_distribuidor, entrar_como  # noqa: F811
):
    entrar_como(usuarios_de_distribuidor["Distribuidor Admin"])
    corpo = corpo_do_distribuidor(org, id_distribuidor_origem=outro_distribuidor)

    serializer = AberturaAtendimentoSerializer(data=corpo)

    assert serializer.is_valid(), serializer.errors


def test_serializer_com_context_recusa_o_distribuidor_de_outro(
    org, usuarios_de_distribuidor, outro_distribuidor, entrar_como  # noqa: F811
):
    id_usuario = usuarios_de_distribuidor["Distribuidor Admin"]
    entrar_como(id_usuario)
    corpo = corpo_do_distribuidor(org, id_distribuidor_origem=outro_distribuidor)

    serializer = AberturaAtendimentoSerializer(data=corpo, context={"id_usuario": id_usuario})

    assert not serializer.is_valid()
    assert [str(e) for e in serializer.errors["id_distribuidor_origem"]] == [MENSAGEM]


def test_serializer_com_ator_nulo_nao_valida_o_distribuidor(
    org, outro_distribuidor, entrar_como  # noqa: F811
):
    # O corpo é montado antes, com um ator ativo: ele lê a cultura do banco, e
    # sem ator a consulta não devolve nada.
    entrar_como(org["admin"])
    corpo =corpo_do_distribuidor(org, id_distribuidor_origem=outro_distribuidor)
    # Id de usuário que não existe: app_tipo_org() volta nulo e a regra não vale.
    entrar_como(0)

    serializer = AberturaAtendimentoSerializer(data=corpo, context={"id_usuario": 0})

    serializer.is_valid()
    assert "id_distribuidor_origem" not in serializer.errors


# POST /sync/push.


def test_push_rejeita_so_a_operacao_com_distribuidor_de_outro(
    org, usuarios_de_distribuidor, outro_distribuidor, entrar_como  # noqa: F811
):
    cliente = como(entrar_como, usuarios_de_distribuidor["Distribuidor Admin"])
    errada = operacao_criar(
        corpo_do_distribuidor(org, id_distribuidor_origem=outro_distribuidor)
    )
    certa = operacao_criar(
        corpo_do_distribuidor(org, id_distribuidor_origem=org["id_distribuidor"])
    )

    resposta = enviar(cliente, errada, certa)

    # O lote não cai: a resposta é 200 e cada operação tem o seu resultado.
    assert resposta.status_code == 200
    rejeitada, criada = resposta.json()["resultados"]
    assert rejeitada["id_operacao"] == errada["id_operacao"]
    assert rejeitada["resultado"] == "rejeitado"
    assert rejeitada["status"] == 400
    assert rejeitada["erro"]["campos"] == {"id_distribuidor_origem": [MENSAGEM]}
    assert "codigo" not in rejeitada["erro"]
    assert criada["id_operacao"] == certa["id_operacao"]
    assert criada["resultado"] == "criado"
    assert criada["status"] == 201
    assert not existe_no_banco(entrar_como, org, errada["id_atendimento"])
    assert existe_no_banco(entrar_como, org, certa["id_atendimento"])


def test_push_reenvio_igual_da_criacao_aceita_volta_como_duplicado_igual(
    org, usuarios_de_distribuidor, entrar_como  # noqa: F811
):
    cliente = como(entrar_como, usuarios_de_distribuidor["Distribuidor Usuário"])
    operacao = operacao_criar(
        corpo_do_distribuidor(org, id_distribuidor_origem=org["id_distribuidor"])
    )

    primeira = resultado_unico(enviar(cliente, operacao))
    segunda = resultado_unico(enviar(cliente, operacao))

    assert primeira["resultado"] == "criado"
    assert segunda["resultado"] == "duplicado"
    assert segunda["comparacao"] == "igual"
    assert segunda["status"] == 200
    assert segunda["registro"]["numero_protocolo"] == primeira["registro"]["numero_protocolo"]


def test_push_do_distribuidor_admin_sem_o_campo_e_rejeitado_e_o_lote_responde_200(
    org, usuarios_de_distribuidor, entrar_como  # noqa: F811
):
    cliente = como(entrar_como, usuarios_de_distribuidor["Distribuidor Admin"])
    sem_o_campo = corpo_do_distribuidor(org)
    del sem_o_campo["id_distribuidor_origem"]
    operacao = operacao_criar(sem_o_campo)

    resposta = enviar(cliente, operacao)

    assert resposta.status_code == 200
    resultado = resultado_unico(resposta)
    assert resultado["resultado"] == "rejeitado"
    assert resultado["status"] == 400
    assert resultado["erro"]["campos"] == {"id_distribuidor_origem": [MENSAGEM]}
    assert not existe_no_banco(entrar_como, org, operacao["id_atendimento"])


# Regra do id_cliente: o usuário de distribuidor só informa cliente da carteira
# dele (app_cliente_visivel, a função do banco). Vale em qualquer origem.

MENSAGEM_CLIENTE = "Informe um cliente da carteira do seu distribuidor."


def corpo_do_distribuidor_com_cliente(org, id_cliente, **extras):  # noqa: F811
    """Corpo de origem distribuidor com um id_cliente informado. O helper
    corpo_do_distribuidor fixa id_cliente em None, então o id entra depois."""
    corpo = corpo_do_distribuidor(org, **extras)
    corpo["id_cliente"] = id_cliente
    return corpo


@pytest.fixture
def cliente_de_outro_distribuidor(conexao_admin):
    """Um cliente da carteira de um distribuidor diferente do da fixture org.
    Devolve o id do cliente.

    O distribuidor e o cliente são gravados (e confirmados) pela conexão de
    administrador. Depois do teste, passe ou falhe, as duas linhas são apagadas
    de verdade, para o banco de teste não acumular um par novo a cada rodada. O
    rollback antes do delete limpa a conexão caso o teste tenha deixado uma
    transação quebrada nela. Nenhum teste que usa esta fixture grava
    atendimento com esse cliente (a regra recusa antes), então o delete não
    esbarra em chave estrangeira.
    """
    id_distribuidor, id_cliente = criar_organizacao(conexao_admin)
    try:
        yield id_cliente
    finally:
        conexao_admin.rollback()
        with conexao_admin.cursor() as cur:
            # Se um teste falhar e deixar um atendimento preso a este cliente, o
            # delete esperaria o lock para sempre. Com o limite, o teardown
            # estoura com erro claro em vez de travar a suíte.
            cur.execute("set local lock_timeout = '3s'")
            cur.execute("delete from cliente where id_cliente = %s", (id_cliente,))
            cur.execute("delete from distribuidor where id_distribuidor = %s", (id_distribuidor,))
        conexao_admin.commit()


# POST /atendimentos.


@pytest.mark.parametrize("perfil", PERFIS_DE_DISTRIBUIDOR)
@pytest.mark.parametrize("origem", ["distribuidor", "cliente_final"])
def test_distribuidor_com_cliente_da_propria_carteira_abre_com_201(
    perfil, origem, org, usuarios_de_distribuidor, entrar_como  # noqa: F811
):
    cliente = como(entrar_como, usuarios_de_distribuidor[perfil])
    if origem == "distribuidor":
        corpo = corpo_do_distribuidor_com_cliente(
            org, org["id_cliente"], id_distribuidor_origem=org["id_distribuidor"]
        )
    else:
        # O corpo padrão já é de cliente final com o cliente da carteira da org.
        corpo = corpo_valido(org, id_distribuidor_origem=org["id_distribuidor"])

    resposta = cliente.post(URL, corpo, format="json")

    assert resposta.status_code == 201, resposta.content
    assert existe_no_banco(entrar_como, org, corpo["id_atendimento"])


@pytest.mark.parametrize("perfil", PERFIS_DE_DISTRIBUIDOR)
@pytest.mark.parametrize("origem", ["distribuidor", "cliente_final"])
def test_distribuidor_com_cliente_de_outro_distribuidor_recebe_400_no_campo(
    perfil, origem, org, usuarios_de_distribuidor, cliente_de_outro_distribuidor, entrar_como  # noqa: F811
):
    cliente = como(entrar_como, usuarios_de_distribuidor[perfil])
    if origem == "distribuidor":
        corpo = corpo_do_distribuidor_com_cliente(
            org, cliente_de_outro_distribuidor, id_distribuidor_origem=org["id_distribuidor"]
        )
    else:
        corpo = corpo_valido(
            org,
            id_cliente=cliente_de_outro_distribuidor,
            id_distribuidor_origem=org["id_distribuidor"],
        )

    resposta = cliente.post(URL, corpo, format="json")

    assert resposta.status_code == 400
    assert resposta.json() == {"id_cliente": [MENSAGEM_CLIENTE]}
    assert not existe_no_banco(entrar_como, org, corpo["id_atendimento"])


@pytest.mark.parametrize("perfil", PERFIS_DE_DISTRIBUIDOR)
def test_distribuidor_com_cliente_inexistente_recebe_400_no_campo(
    perfil, org, usuarios_de_distribuidor, entrar_como  # noqa: F811
):
    cliente = como(entrar_como, usuarios_de_distribuidor[perfil])
    # Id muito acima de qualquer cliente real: para esse perfil o inexistente
    # também é fora da carteira (400), e não o 422 de chave estrangeira.
    corpo = corpo_do_distribuidor_com_cliente(
        org, 2_000_000_000, id_distribuidor_origem=org["id_distribuidor"]
    )

    resposta = cliente.post(URL, corpo, format="json")

    assert resposta.status_code == 400
    assert resposta.json() == {"id_cliente": [MENSAGEM_CLIENTE]}


@pytest.mark.parametrize("perfil", PERFIS_DE_DISTRIBUIDOR)
@pytest.mark.parametrize("situacao", ["omitido", "nulo"])
def test_distribuidor_sem_id_cliente_na_origem_distribuidor_segue_aceito(
    perfil, situacao, org, usuarios_de_distribuidor, entrar_como  # noqa: F811
):
    cliente = como(entrar_como, usuarios_de_distribuidor[perfil])
    corpo = corpo_do_distribuidor(org, id_distribuidor_origem=org["id_distribuidor"])
    if situacao == "omitido":
        del corpo["id_cliente"]

    resposta = cliente.post(URL, corpo, format="json")

    assert resposta.status_code == 201, resposta.content


def test_distribuidor_com_os_dois_erros_recebe_os_dois_numa_resposta_so(
    org, usuarios_de_distribuidor, outro_distribuidor, cliente_de_outro_distribuidor, entrar_como  # noqa: F811
):
    cliente = como(entrar_como, usuarios_de_distribuidor["Distribuidor Admin"])
    corpo = corpo_do_distribuidor_com_cliente(
        org, cliente_de_outro_distribuidor, id_distribuidor_origem=outro_distribuidor
    )

    resposta = cliente.post(URL, corpo, format="json")

    assert resposta.status_code == 400
    assert resposta.json() == {
        "id_distribuidor_origem": [MENSAGEM],
        "id_cliente": [MENSAGEM_CLIENTE],
    }


# Netafim e cliente não são afetados. Ficam no nível do serializer: se o POST
# desse certo com um cliente de fora, o atendimento gravado prenderia o cliente
# de teste e impediria a limpeza da fixture.


@pytest.mark.parametrize("quem", ["admin", "cliente"])
def test_netafim_e_cliente_nao_tem_a_regra_da_carteira(
    quem, org, cliente_de_outro_distribuidor, entrar_como  # noqa: F811
):
    id_usuario = org[quem]
    entrar_como(id_usuario)
    corpo = corpo_valido(org, id_cliente=cliente_de_outro_distribuidor)

    serializer = AberturaAtendimentoSerializer(data=corpo, context={"id_usuario": id_usuario})

    assert serializer.is_valid(), serializer.errors


def test_serializer_sem_context_pula_a_regra_do_cliente(
    org, usuarios_de_distribuidor, cliente_de_outro_distribuidor, entrar_como  # noqa: F811
):
    entrar_como(usuarios_de_distribuidor["Distribuidor Admin"])
    corpo = corpo_valido(
        org, id_cliente=cliente_de_outro_distribuidor, id_distribuidor_origem=org["id_distribuidor"]
    )

    serializer = AberturaAtendimentoSerializer(data=corpo)

    assert serializer.is_valid(), serializer.errors


# POST /sync/push.


def test_push_rejeita_so_a_operacao_com_cliente_de_fora_da_carteira(
    org, usuarios_de_distribuidor, cliente_de_outro_distribuidor, entrar_como  # noqa: F811
):
    cliente = como(entrar_como, usuarios_de_distribuidor["Distribuidor Admin"])
    errada = operacao_criar(
        corpo_do_distribuidor_com_cliente(
            org, cliente_de_outro_distribuidor, id_distribuidor_origem=org["id_distribuidor"]
        )
    )
    certa = operacao_criar(
        corpo_do_distribuidor_com_cliente(
            org, org["id_cliente"], id_distribuidor_origem=org["id_distribuidor"]
        )
    )

    resposta = enviar(cliente, errada, certa)

    # O lote não cai: a resposta é 200 e cada operação tem o seu resultado.
    assert resposta.status_code == 200
    rejeitada, criada = resposta.json()["resultados"]
    assert rejeitada["id_operacao"] == errada["id_operacao"]
    assert rejeitada["resultado"] == "rejeitado"
    assert rejeitada["status"] == 400
    assert rejeitada["erro"]["campos"] == {"id_cliente": [MENSAGEM_CLIENTE]}
    assert criada["id_operacao"] == certa["id_operacao"]
    assert criada["resultado"] == "criado"
    assert criada["status"] == 201
    assert not existe_no_banco(entrar_como, org, errada["id_atendimento"])
    assert existe_no_banco(entrar_como, org, certa["id_atendimento"])
