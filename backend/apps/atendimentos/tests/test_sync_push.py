"""Testes de POST /api/v1/sync/push contra o banco real (RLS, gatilhos, savepoints).

Cada teste monta o próprio cenário com a fixture org (uma empresa, o Cliente
Usuário dela e um Netafim Admin). Os atendimentos nascem dentro da transação
que o pytest desfaz no fim; só usuários e organizações ficam gravados.
"""

import uuid

import pytest
from rest_framework.test import APIClient

from apps.atendimentos.models import Atendimento
from apps.atendimentos.tests.test_rls_perfis import criar_usuario_do_perfil
from apps.atendimentos.tests.test_sincronizacao_pull import (  # noqa: F401
    criar_organizacao,
    culturas_ativas,
    org,
)
from apps.atendimentos.tests.test_view_abertura import cliente_autenticado, corpo_valido

pytestmark = pytest.mark.django_db

URL = "/api/v1/sync/push"


def como(entrar_como, id_usuario):
    """Informa ao banco quem está agindo (como a autenticação real faz) e
    devolve um cliente HTTP autenticado como esse usuário."""
    entrar_como(id_usuario)
    return cliente_autenticado(id_usuario)


def operacao_criar(payload, id_operacao=None):
    """Operação de criação. O id da operação é o do payload, como o app faz."""
    return {
        "id_operacao": id_operacao or str(uuid.uuid4()),
        "entidade": "atendimento",
        "tipo": "criar",
        "id_atendimento": payload["id_atendimento"],
        "payload": payload,
    }


def operacao_atualizar(id_atendimento, versao_esperada, payload, id_operacao=None):
    return {
        "id_operacao": id_operacao or str(uuid.uuid4()),
        "entidade": "atendimento",
        "tipo": "atualizar",
        "id_atendimento": str(id_atendimento),
        "versao_esperada": versao_esperada,
        "payload": payload,
    }


def enviar(cliente, *operacoes):
    return cliente.post(URL, {"operacoes": list(operacoes)}, format="json")


def resultado_unico(resposta):
    """Confere o envelope (200, uma operação) e devolve o único resultado."""
    assert resposta.status_code == 200
    resultados = resposta.json()["resultados"]
    assert len(resultados) == 1
    return resultados[0]


# Criar.


def test_criar_devolve_criado_com_registro_completo(org, entrar_como):
    cliente = como(entrar_como, org["cliente"])
    payload = corpo_valido(org)

    resultado = resultado_unico(enviar(cliente, operacao_criar(payload)))

    assert resultado["resultado"] == "criado"
    assert resultado["status"] == 201
    assert resultado["id_atendimento"] == payload["id_atendimento"]
    registro = resultado["registro"]
    assert registro["numero_protocolo"].startswith("OS-")
    assert registro["versao"] == 1
    assert registro["sync_seq"] > 0
    assert registro["status"] == "aberto"
    # O registro leva a linha do tempo, igual ao do pull.
    assert [e["tipo_evento"] for e in registro["historico"]][0] == "criado"
    # O solicitante é o usuário da sessão, nunca algo do payload.
    assert registro["id_solicitante"] == org["cliente"]


def test_resultados_voltam_na_ordem_e_com_o_id_da_operacao(org, entrar_como):
    cliente = como(entrar_como, org["cliente"])
    primeira = operacao_criar(corpo_valido(org))
    segunda = operacao_criar(corpo_valido(org))

    resposta = enviar(cliente, primeira, segunda)

    resultados = resposta.json()["resultados"]
    assert [r["id_operacao"] for r in resultados] == [
        primeira["id_operacao"],
        segunda["id_operacao"],
    ]
    assert [r["id_atendimento"] for r in resultados] == [
        primeira["id_atendimento"],
        segunda["id_atendimento"],
    ]


def test_rejeicao_por_tipo_venda_ausente_nao_toca_no_banco(org, entrar_como):
    cliente = como(entrar_como, org["cliente"])
    payload = corpo_valido(org)
    del payload["tipo_venda"]

    resultado = resultado_unico(enviar(cliente, operacao_criar(payload)))

    assert resultado["resultado"] == "rejeitado"
    assert resultado["status"] == 400
    assert "tipo_venda" in resultado["erro"]["campos"]
    # Erro de campo, sem codigo, como o 400 do DRF.
    assert "codigo" not in resultado["erro"]
    assert not Atendimento.objects.filter(id_atendimento=payload["id_atendimento"]).exists()


def test_id_do_payload_diferente_do_da_operacao_e_400_no_campo(org, entrar_como):
    cliente = como(entrar_como, org["cliente"])
    payload = corpo_valido(org)
    operacao = operacao_criar(payload)
    operacao["id_atendimento"] = str(uuid.uuid4())

    resultado = resultado_unico(enviar(cliente, operacao))

    assert resultado["resultado"] == "rejeitado"
    assert resultado["status"] == 400
    assert "id_atendimento" in resultado["erro"]["campos"]
    assert not Atendimento.objects.filter(id_atendimento=payload["id_atendimento"]).exists()


def test_entidade_desconhecida_e_400_no_campo(org, entrar_como):
    cliente = como(entrar_como, org["cliente"])
    operacao = operacao_criar(corpo_valido(org))
    operacao["entidade"] = "evidencia"

    resultado = resultado_unico(enviar(cliente, operacao))

    assert resultado["resultado"] == "rejeitado"
    assert resultado["status"] == 400
    assert "entidade" in resultado["erro"]["campos"]


def test_versao_esperada_sem_sentido_e_ignorada_em_criar(org, entrar_como):
    cliente = como(entrar_como, org["cliente"])
    operacao = operacao_criar(corpo_valido(org))
    operacao["versao_esperada"] = "lixo"

    assert resultado_unico(enviar(cliente, operacao))["resultado"] == "criado"


# Duplicado: o servidor relê o atendimento e compara o conteúdo.


def test_reenvio_do_mesmo_lote_de_criacao_e_idempotente(org, entrar_como):
    cliente = como(entrar_como, org["cliente"])
    operacoes = [operacao_criar(corpo_valido(org)), operacao_criar(corpo_valido(org))]

    primeira = enviar(cliente, *operacoes).json()["resultados"]
    segunda = enviar(cliente, *operacoes).json()["resultados"]

    assert [r["resultado"] for r in primeira] == ["criado", "criado"]
    # Reenviar não duplica: volta como duplicado igual, que é sucesso, com o
    # registro que já estava gravado (mesmo protocolo e mesma versão).
    assert [r["resultado"] for r in segunda] == ["duplicado", "duplicado"]
    assert [r["comparacao"] for r in segunda] == ["igual", "igual"]
    assert [r["status"] for r in segunda] == [200, 200]
    for antes, depois in zip(primeira, segunda, strict=True):
        assert depois["registro"]["numero_protocolo"] == antes["registro"]["numero_protocolo"]
        assert depois["registro"]["versao"] == antes["registro"]["versao"] == 1
    ids = [op["id_atendimento"] for op in operacoes]
    assert Atendimento.objects.filter(id_atendimento__in=ids).count() == 2


def test_duplicado_com_conteudo_diferente_e_conflito_com_registro_do_servidor(org, entrar_como):
    cliente = como(entrar_como, org["cliente"])
    payload = corpo_valido(org, cidade="Jau")
    enviar(cliente, operacao_criar(payload))

    resultado = resultado_unico(enviar(cliente, operacao_criar({**payload, "cidade": "Bauru"})))

    assert resultado["resultado"] == "duplicado"
    assert resultado["comparacao"] == "diferente"
    assert resultado["status"] == 409
    assert resultado["erro"]["codigo"] == "duplicado"
    # O que volta é o que está gravado, não o que foi reenviado.
    assert resultado["registro_servidor"]["cidade"] == "Jau"
    assert "registro" not in resultado


def test_duplicado_de_id_invisivel_ao_usuario_e_rejeitado_sem_registro(
    org, conexao_admin, entrar_como
):
    # O atendimento é de outra empresa: o 23505 acontece, mas a releitura pelo
    # RLS não o enxerga. Responder igual ou diferente revelaria que ele existe.
    _, outro_cliente = criar_organizacao(conexao_admin)
    outro_usuario = criar_usuario_do_perfil(
        conexao_admin, "Cliente Usuário", "cliente", id_cliente=outro_cliente
    )
    dono = como(entrar_como, org["cliente"])
    payload = corpo_valido(org)
    enviar(dono, operacao_criar(payload))

    intruso = como(entrar_como, outro_usuario)
    resultado = resultado_unico(
        enviar(intruso, operacao_criar({**payload, "id_cliente": outro_cliente}))
    )

    assert resultado["resultado"] == "rejeitado"
    assert resultado["status"] == 409
    assert resultado["erro"]["codigo"] == "duplicado"
    assert "registro" not in resultado
    assert "registro_servidor" not in resultado
    assert "comparacao" not in resultado


def test_chave_ausente_conta_como_nulo_gravado(org, entrar_como):
    cliente = como(entrar_como, org["cliente"])
    # nome_produtor não é obrigatório no cliente final: nasce nulo no banco.
    payload = corpo_valido(org)
    assert "nome_produtor" not in payload
    enviar(cliente, operacao_criar(payload))

    # Reenvio sem a chave e reenvio com nulo explícito e com vazio: tudo igual.
    for variacao in (payload, {**payload, "nome_produtor": None}, {**payload, "nome_produtor": ""}):
        resultado = resultado_unico(enviar(cliente, operacao_criar(variacao)))
        assert resultado["comparacao"] == "igual", variacao


def test_chave_ausente_contra_valor_gravado_e_diferente(org, entrar_como):
    cliente = como(entrar_como, org["cliente"])
    payload = corpo_valido(org, numero_projeto_br="P-123")
    enviar(cliente, operacao_criar(payload))

    sem_a_chave = {c: v for c, v in payload.items() if c != "numero_projeto_br"}
    resultado = resultado_unico(enviar(cliente, operacao_criar(sem_a_chave)))

    assert resultado["comparacao"] == "diferente"


def test_valor_enviado_contra_nulo_gravado_e_diferente(org, entrar_como):
    cliente = como(entrar_como, org["cliente"])
    payload = corpo_valido(org)
    enviar(cliente, operacao_criar(payload))

    resultado = resultado_unico(
        enviar(cliente, operacao_criar({**payload, "nome_produtor": "Joao"}))
    )

    assert resultado["comparacao"] == "diferente"


def test_ordem_das_culturas_nao_importa_mas_o_conjunto_sim(org, entrar_como):
    cliente = como(entrar_como, org["cliente"])
    a, b, c = culturas_ativas(3)
    payload = corpo_valido(org, culturas=[a, b])
    enviar(cliente, operacao_criar(payload))

    invertida = resultado_unico(enviar(cliente, operacao_criar({**payload, "culturas": [b, a]})))
    outro_conjunto = resultado_unico(
        enviar(cliente, operacao_criar({**payload, "culturas": [a, c]}))
    )

    assert invertida["comparacao"] == "igual"
    assert outro_conjunto["comparacao"] == "diferente"


def test_data_abertura_enviada_compara_pelo_instante_em_utc(org, entrar_como):
    cliente = como(entrar_como, org["cliente"])
    payload = corpo_valido(org, data_abertura="2026-11-02T14:03:11Z")
    enviar(cliente, operacao_criar(payload))

    # Mesmo instante escrito em outro fuso: igual. Outro instante: diferente.
    mesmo_instante = {**payload, "data_abertura": "2026-11-02T11:03:11-03:00"}
    outro_instante = {**payload, "data_abertura": "2026-11-02T14:03:12Z"}

    assert resultado_unico(enviar(cliente, operacao_criar(mesmo_instante)))["comparacao"] == "igual"
    assert (
        resultado_unico(enviar(cliente, operacao_criar(outro_instante)))["comparacao"]
        == "diferente"
    )


def test_data_abertura_nao_enviada_fica_fora_da_comparacao(org, entrar_como):
    cliente = como(entrar_como, org["cliente"])
    # Sem data_abertura o banco usa now(): o valor gravado é do servidor e o
    # reenvio, que também não a traz, não pode ser considerado diferente por isso.
    payload = corpo_valido(org)
    assert "data_abertura" not in payload
    enviar(cliente, operacao_criar(payload))

    assert resultado_unico(enviar(cliente, operacao_criar(payload)))["comparacao"] == "igual"


# Atualizar.


def test_atualizar_aplica_e_devolve_registro_novo(org, entrar_como):
    criado = resultado_unico(
        enviar(como(entrar_como, org["cliente"]), operacao_criar(corpo_valido(org)))
    )
    id_atendimento = criado["id_atendimento"]
    admin = como(entrar_como, org["admin"])

    # Uma "versao" dentro do payload é ignorada: vale a versao_esperada.
    resultado = resultado_unico(
        enviar(admin, operacao_atualizar(id_atendimento, 1, {"status": "cancelado", "versao": 99}))
    )

    assert resultado["resultado"] == "atualizado"
    assert resultado["status"] == 200
    assert resultado["registro"]["status"] == "cancelado"
    assert resultado["registro"]["versao"] == 2
    assert resultado["registro"]["sync_seq"] > criado["registro"]["sync_seq"]


def test_conflito_de_versao_traz_versao_atual_e_registro_do_servidor(org, entrar_como):
    criado = resultado_unico(
        enviar(como(entrar_como, org["cliente"]), operacao_criar(corpo_valido(org)))
    )
    id_atendimento = criado["id_atendimento"]
    admin = como(entrar_como, org["admin"])
    primeira = resultado_unico(
        enviar(admin, operacao_atualizar(id_atendimento, 1, {"status": "cancelado"}))
    )
    assert primeira["resultado"] == "atualizado"

    # O app ainda acha que a versão é 1.
    resultado = resultado_unico(
        enviar(
            admin,
            operacao_atualizar(
                id_atendimento, 1, {"status": "destinado", "destinado_a": "netafim"}
            ),
        )
    )

    assert resultado["resultado"] == "conflito_de_versao"
    assert resultado["status"] == 409
    assert resultado["erro"]["codigo"] == "conflito_de_versao"
    assert resultado["erro"]["versao_atual"] == 2
    assert resultado["registro_servidor"]["versao"] == 2
    assert resultado["registro_servidor"]["status"] == "cancelado"


def test_atualizar_atendimento_inexistente_e_404_nao_encontrado(org, entrar_como):
    admin = como(entrar_como, org["admin"])

    resultado = resultado_unico(
        enviar(admin, operacao_atualizar(uuid.uuid4(), 1, {"status": "cancelado"}))
    )

    assert resultado["resultado"] == "rejeitado"
    assert resultado["status"] == 404
    assert resultado["erro"]["codigo"] == "nao_encontrado"


def test_atualizar_atendimento_de_outra_empresa_tambem_e_404(org, conexao_admin, entrar_como):
    # O RLS esconde o atendimento: a resposta é igual à do id que não existe.
    criado = resultado_unico(
        enviar(como(entrar_como, org["cliente"]), operacao_criar(corpo_valido(org)))
    )
    _, outro_cliente = criar_organizacao(conexao_admin)
    outro_usuario = criar_usuario_do_perfil(
        conexao_admin, "Cliente Usuário", "cliente", id_cliente=outro_cliente
    )

    resultado = resultado_unico(
        enviar(
            como(entrar_como, outro_usuario),
            operacao_atualizar(criado["id_atendimento"], 1, {"status": "cancelado"}),
        )
    )

    assert resultado["status"] == 404
    assert resultado["erro"]["codigo"] == "nao_encontrado"


def test_atualizar_sem_permissao_e_403(org, conexao_admin, entrar_como):
    # Cliente Admin da mesma empresa vê o atendimento, mas o RLS de UPDATE não
    # deixa alterar o que ele não abriu.
    criado = resultado_unico(
        enviar(como(entrar_como, org["cliente"]), operacao_criar(corpo_valido(org)))
    )
    cliente_admin = criar_usuario_do_perfil(
        conexao_admin, "Cliente Admin", "cliente", id_cliente=org["id_cliente"]
    )

    resultado = resultado_unico(
        enviar(
            como(entrar_como, cliente_admin),
            operacao_atualizar(criado["id_atendimento"], 1, {"status": "cancelado"}),
        )
    )

    assert resultado["resultado"] == "rejeitado"
    assert resultado["status"] == 403
    assert resultado["erro"]["codigo"] == "sem_permissao"


def test_atualizar_sem_versao_esperada_ou_sem_mudanca_e_400(org, entrar_como):
    admin = como(entrar_como, org["admin"])
    sem_versao = operacao_atualizar(uuid.uuid4(), 1, {"status": "cancelado"})
    del sem_versao["versao_esperada"]
    sem_mudanca = operacao_atualizar(uuid.uuid4(), 1, {"tipo_venda": "projeto"})

    resposta = enviar(admin, sem_versao, sem_mudanca)

    um, dois = resposta.json()["resultados"]
    assert (um["resultado"], um["status"]) == ("rejeitado", 400)
    assert "versao_esperada" in um["erro"]["campos"]
    # tipo_venda é ignorado de propósito no PATCH: sobra nenhuma alteração.
    assert (dois["resultado"], dois["status"]) == ("rejeitado", 400)


# Savepoint por operação.


def test_erro_do_banco_numa_operacao_nao_derruba_as_outras(org, entrar_como):
    cliente = como(entrar_como, org["cliente"])
    criado = resultado_unico(enviar(cliente, operacao_criar(corpo_valido(org))))
    nova = corpo_valido(org)

    # O Cliente Usuário não pode cancelar: o gatilho do banco recusa (422) e a
    # transação do savepoint fica abortada. A operação seguinte tem de rodar.
    resposta = enviar(
        cliente,
        operacao_atualizar(criado["id_atendimento"], 1, {"status": "cancelado"}),
        operacao_criar(nova),
    )

    recusada, seguinte = resposta.json()["resultados"]
    assert recusada["resultado"] == "rejeitado"
    assert recusada["status"] == 422
    assert recusada["erro"]["codigo"] == "regra_de_negocio"
    assert seguinte["resultado"] == "criado"
    assert Atendimento.objects.filter(id_atendimento=nova["id_atendimento"]).exists()
    # E a recusa não mexeu no atendimento.
    assert Atendimento.objects.get(id_atendimento=criado["id_atendimento"]).versao == 1


def test_operacao_invalida_nao_derruba_as_outras(org, entrar_como):
    cliente = como(entrar_como, org["cliente"])
    ruim = corpo_valido(org)
    del ruim["tipo_venda"]
    boa = corpo_valido(org)

    resposta = enviar(cliente, operacao_criar(ruim), operacao_criar(boa))

    primeira, segunda = resposta.json()["resultados"]
    assert primeira["resultado"] == "rejeitado"
    assert segunda["resultado"] == "criado"


# Lote.


def test_lote_acima_de_50_e_400_com_a_mensagem_do_limite(org, entrar_como):
    cliente = como(entrar_como, org["cliente"])
    operacoes = [operacao_criar(corpo_valido(org)) for _ in range(51)]

    resposta = enviar(cliente, *operacoes)

    assert resposta.status_code == 400
    assert resposta.json() == {"operacoes": ["O lote aceita no máximo 50 operações."]}
    # Nada foi executado.
    ids = [op["id_atendimento"] for op in operacoes]
    assert not Atendimento.objects.filter(id_atendimento__in=ids).exists()


def test_lote_de_exatamente_50_e_aceito(org, entrar_como):
    cliente = como(entrar_como, org["cliente"])
    # Operações de atualização num id que não existe são baratas: cada uma vira
    # um 404, o que basta para provar que o lote de 50 foi processado inteiro.
    operacoes = [operacao_atualizar(uuid.uuid4(), 1, {"status": "cancelado"}) for _ in range(50)]

    resposta = enviar(cliente, *operacoes)

    assert resposta.status_code == 200
    assert len(resposta.json()["resultados"]) == 50


@pytest.mark.parametrize(
    "corpo",
    [
        {},
        {"operacoes": []},
        {"operacoes": "x"},
        {"operacoes": ["nao e objeto"]},
    ],
)
def test_lote_malformado_e_400(org, entrar_como, corpo):
    cliente = como(entrar_como, org["cliente"])

    assert cliente.post(URL, corpo, format="json").status_code == 400


def test_id_operacao_ausente_invalido_ou_repetido_invalida_o_lote(org, entrar_como):
    cliente = como(entrar_como, org["cliente"])
    a = operacao_criar(corpo_valido(org))
    b = operacao_criar(corpo_valido(org), id_operacao=a["id_operacao"])
    sem_id = {c: v for c, v in operacao_criar(corpo_valido(org)).items() if c != "id_operacao"}
    invalido = operacao_criar(corpo_valido(org), id_operacao="nao-e-uuid")

    for lote in ([a, b], [sem_id], [invalido]):
        resposta = enviar(cliente, *lote)
        assert resposta.status_code == 400, lote
        assert "operacoes" in resposta.json()
    # Nenhuma operação do lote inválido foi executada.
    assert not Atendimento.objects.filter(id_atendimento=a["id_atendimento"]).exists()


def test_sem_autenticacao_nao_entra():
    resposta = APIClient().post(URL, {"operacoes": []}, format="json")

    # 403 nesta branch (o 401 vem do módulo de acesso, ainda fora da main).
    assert resposta.status_code in (401, 403)
