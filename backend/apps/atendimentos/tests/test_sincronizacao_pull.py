"""Testes do pull_atendimentos contra o banco real (RLS, gatilhos e sync_seq).

O banco de teste guarda atendimentos confirmados por outros testes, e o Netafim
Admin enxerga todos. Para cada teste olhar só o que ele mesmo criou, o pull
começa no marco: o último sync_seq já tirado da sequência antes de o teste abrir
qualquer coisa. Os atendimentos de cada teste nascem dentro da transação que o
pytest desfaz no fim; só usuários e organizações ficam gravados.
"""

import json
import uuid

import pytest
from django.utils import timezone
from rest_framework.test import APIClient

from apps.atendimentos.models import Atendimento, Cultura
from apps.atendimentos.servicos import abrir_atendimento, atualizar_atendimento
from apps.atendimentos.sincronizacao import pull_atendimentos
from apps.atendimentos.tests.test_rls_perfis import criar_usuario_do_perfil

pytestmark = pytest.mark.django_db


class UsuarioDeTeste:
    """Usuário autenticado fictício para o force_authenticate do GET."""

    is_authenticated = True

    def __init__(self, pk):
        self.pk = pk


def marco(conexao_admin):
    """Último valor tirado da sequência global do sync_seq. Tudo que o teste
    gravar depois disso tem sync_seq maior. A sequência não depende de
    transação, então a leitura pela conexão de administrador basta."""
    with conexao_admin.cursor() as cur:
        cur.execute("select last_value from seq_sync")
        valor = cur.fetchone()[0]
    conexao_admin.rollback()
    return valor


def criar_organizacao(conexao_admin):
    """Distribuidor novo com um cliente na carteira dele (com commit)."""
    sufixo = uuid.uuid4().hex[:8]
    with conexao_admin.cursor() as cur:
        cur.execute(
            "insert into distribuidor (nome, regiao) values (%s, %s) returning id_distribuidor",
            (f"Distribuidor Pull {sufixo}", "Regiao Pull"),
        )
        id_distribuidor = cur.fetchone()[0]
        cur.execute(
            "insert into cliente (nome, id_distribuidor) values (%s, %s) returning id_cliente",
            (f"Cliente Pull {sufixo}", id_distribuidor),
        )
        id_cliente = cur.fetchone()[0]
    conexao_admin.commit()
    return id_distribuidor, id_cliente


@pytest.fixture
def org(conexao_admin):
    """Uma organização com o Cliente Usuário dela e um Netafim Admin."""
    id_distribuidor, id_cliente = criar_organizacao(conexao_admin)
    return {
        "id_distribuidor": id_distribuidor,
        "id_cliente": id_cliente,
        "cliente": criar_usuario_do_perfil(
            conexao_admin, "Cliente Usuário", "cliente", id_cliente=id_cliente
        ),
        "admin": criar_usuario_do_perfil(conexao_admin, "Netafim Admin", "netafim"),
        "marco": marco(conexao_admin),
    }


def abrir(entrar_como, id_usuario, id_cliente, **extras):
    """Abre um atendimento de cliente final no nome do usuário dado."""
    entrar_como(id_usuario)
    dados = {
        "id_atendimento": uuid.uuid4(),
        "origem": "cliente_final",
        "motivo": "pos_venda",
        "id_cliente": id_cliente,
        "contato_solicitante": "11999990000",
    }
    dados.update(extras)
    return abrir_atendimento(id_usuario, dados)


def excluir(entrar_como, id_admin, atendimento):
    """Exclusão lógica pelo Netafim Admin, do mesmo jeito do teste da exportação."""
    entrar_como(id_admin)
    Atendimento.objects.filter(id_atendimento=atendimento.id_atendimento).update(
        deletado_em=timezone.now()
    )


def ids(resultado):
    return [item["registro"]["id_atendimento"] for item in resultado["itens"]]


def seqs(resultado):
    return [item["registro"]["sync_seq"] for item in resultado["itens"]]


def culturas_ativas(quantidade=2):
    """Culturas ativas dos dados iniciais (precisa de usuário no contexto)."""
    ids = list(
        Cultura.objects.filter(deletado_em__isnull=True)
        .order_by("id_cultura")
        .values_list("id_cultura", flat=True)[:quantidade]
    )
    assert len(ids) == quantidade, "o banco de teste não tem culturas suficientes"
    return ids


def duas_culturas():
    return culturas_ativas(2)


# Ordem, desde, limite e cursor.


def test_ordem_crescente_e_atendimento_alterado_vai_para_o_fim(org, entrar_como):
    a = abrir(entrar_como, org["cliente"], org["id_cliente"])
    b = abrir(entrar_como, org["cliente"], org["id_cliente"])
    c = abrir(entrar_como, org["cliente"], org["id_cliente"])
    # A alteração tira um sync_seq novo, então "a" passa a ser o mais recente.
    entrar_como(org["admin"])
    atualizar_atendimento(a.id_atendimento, 1, {"status": "cancelado"})

    entrar_como(org["cliente"])
    resultado = pull_atendimentos(desde=org["marco"])

    assert ids(resultado) == [str(x.id_atendimento) for x in (b, c, a)]
    assert seqs(resultado) == sorted(seqs(resultado))
    assert len(set(seqs(resultado))) == 3
    assert resultado["itens"][-1]["registro"]["status"] == "cancelado"


def test_desde_nao_devolve_o_igual_nem_o_menor(org, entrar_como):
    abrir(entrar_como, org["cliente"], org["id_cliente"])
    b = abrir(entrar_como, org["cliente"], org["id_cliente"])
    c = abrir(entrar_como, org["cliente"], org["id_cliente"])

    resultado = pull_atendimentos(desde=b.sync_seq)

    assert ids(resultado) == [str(c.id_atendimento)]


def test_paginas_com_limite_chegam_ao_fim_sem_repetir_nem_pular(org, entrar_como):
    abertos = [abrir(entrar_como, org["cliente"], org["id_cliente"]) for _ in range(5)]

    paginas = []
    desde = org["marco"]
    while True:
        pagina = pull_atendimentos(desde=desde, limite=2)
        paginas.append(pagina)
        # O cursor é sempre o último sync_seq da página.
        assert pagina["cursor"] == seqs(pagina)[-1]
        desde = pagina["cursor"]
        if not pagina["tem_mais"]:
            break

    assert [len(p["itens"]) for p in paginas] == [2, 2, 1]
    assert [p["tem_mais"] for p in paginas] == [True, True, False]
    todos = [i for p in paginas for i in ids(p)]
    assert todos == [str(a.id_atendimento) for a in abertos]


def test_limite_exato_nao_marca_tem_mais(org, entrar_como):
    for _ in range(3):
        abrir(entrar_como, org["cliente"], org["id_cliente"])

    resultado = pull_atendimentos(desde=org["marco"], limite=3)

    assert len(resultado["itens"]) == 3
    assert resultado["tem_mais"] is False


def test_sem_novidade_o_cursor_e_o_proprio_desde(org, entrar_como):
    entrar_como(org["cliente"])
    assert pull_atendimentos(desde=org["marco"]) == {
        "itens": [],
        "cursor": org["marco"],
        "tem_mais": False,
    }

    ultimo = abrir(entrar_como, org["cliente"], org["id_cliente"])
    resultado = pull_atendimentos(desde=ultimo.sync_seq)
    assert resultado == {"itens": [], "cursor": ultimo.sync_seq, "tem_mais": False}


# Exclusão lógica.


def test_excluido_desce_como_tombstone_so_com_id_versao_e_sync_seq(org, entrar_como):
    mantido = abrir(entrar_como, org["cliente"], org["id_cliente"])
    excluido = abrir(entrar_como, org["cliente"], org["id_cliente"])
    excluir(entrar_como, org["admin"], excluido)

    # O RLS não esconde o excluído de quem tem acesso: o próprio cliente recebe
    # o tombstone, e o detalhe dele (GET) responde 404 porque a view filtra.
    entrar_como(org["cliente"])
    resultado = pull_atendimentos(desde=org["marco"])

    por_id = {i["registro"]["id_atendimento"]: i for i in resultado["itens"]}
    assert set(por_id) == {str(mantido.id_atendimento), str(excluido.id_atendimento)}
    assert por_id[str(mantido.id_atendimento)]["removido"] is False
    tombstone = por_id[str(excluido.id_atendimento)]
    atual = Atendimento.objects.get(id_atendimento=excluido.id_atendimento)
    assert tombstone == {
        "entidade": "atendimento",
        "removido": True,
        "registro": {
            "id_atendimento": str(excluido.id_atendimento),
            "versao": atual.versao,
            "sync_seq": atual.sync_seq,
        },
    }
    # A exclusão é uma alteração: tirou sync_seq novo e subiu a versão.
    assert atual.sync_seq > excluido.sync_seq
    assert atual.versao == 2


# RLS por perfil.


def test_cada_perfil_recebe_so_o_que_o_rls_deixa_ver(conexao_admin, entrar_como):
    d1, c1 = criar_organizacao(conexao_admin)
    d2, c2 = criar_organizacao(conexao_admin)
    cliente_usuario = criar_usuario_do_perfil(
        conexao_admin, "Cliente Usuário", "cliente", id_cliente=c1
    )
    cliente_admin = criar_usuario_do_perfil(
        conexao_admin, "Cliente Admin", "cliente", id_cliente=c1
    )
    dist_admin = criar_usuario_do_perfil(
        conexao_admin, "Distribuidor Admin", "distribuidor", id_distribuidor=d1
    )
    netafim_admin = criar_usuario_do_perfil(conexao_admin, "Netafim Admin", "netafim")
    outro_cliente = criar_usuario_do_perfil(
        conexao_admin, "Cliente Usuário", "cliente", id_cliente=c2
    )
    outro_dist_admin = criar_usuario_do_perfil(
        conexao_admin, "Distribuidor Admin", "distribuidor", id_distribuidor=d2
    )
    inicio = marco(conexao_admin)

    do_cliente_usuario = abrir(entrar_como, cliente_usuario, c1)
    do_cliente_admin = abrir(entrar_como, cliente_admin, c1)
    da_outra_empresa = abrir(entrar_como, outro_cliente, c2)
    empresa_1 = {str(do_cliente_usuario.id_atendimento), str(do_cliente_admin.id_atendimento)}
    empresa_2 = {str(da_outra_empresa.id_atendimento)}

    esperado = {
        # Cliente Usuário só os que abriu; Cliente Admin todos da empresa.
        cliente_usuario: {str(do_cliente_usuario.id_atendimento)},
        cliente_admin: empresa_1,
        # Distribuidor Admin: os de clientes da sua carteira.
        dist_admin: empresa_1,
        # Netafim Admin: todos.
        netafim_admin: empresa_1 | empresa_2,
        # A outra organização não vê nada da primeira.
        outro_cliente: empresa_2,
        outro_dist_admin: empresa_2,
    }
    for usuario, visiveis in esperado.items():
        entrar_como(usuario)
        assert set(ids(pull_atendimentos(desde=inicio))) == visiveis, usuario


# Formato do registro.


def test_item_normal_e_igual_ao_get_do_detalhe(org, entrar_como):
    entrar_como(org["cliente"])
    *ativas, excluida = culturas_ativas(3)
    atendimento = abrir(
        entrar_como,
        org["cliente"],
        org["id_cliente"],
        cidade="Ribeirão Preto",
        nome_fazenda_informado="Fazenda Santa Rita",
        id_distribuidor_origem=org["id_distribuidor"],
        data_inicio_problema=timezone.localdate(),
        descricao_problema="Gotejador entupido",
        culturas=[*reversed(ativas), excluida],
    )
    # Depois do vínculo gravado, o Netafim Admin exclui logicamente a terceira
    # cultura, dentro da transação do teste (o pytest desfaz no fim). Não uso a
    # fixture cultura_excluida: ela apaga a cultura com commit no fim do teste,
    # e o DELETE ficaria esperando o vínculo desta transação ainda aberta.
    entrar_como(org["admin"])
    Cultura.objects.filter(id_cultura=excluida).update(deletado_em=timezone.now())
    entrar_como(org["cliente"])

    cliente_http = APIClient()
    cliente_http.force_authenticate(user=UsuarioDeTeste(org["cliente"]))
    resposta = cliente_http.get(f"/api/v1/atendimentos/{atendimento.id_atendimento}")
    assert resposta.status_code == 200
    detalhe = resposta.json()

    resultado = pull_atendimentos(desde=org["marco"])

    # Comparo o JSON renderizado dos dois lados: mesmas chaves e mesmos valores.
    # A única chave a mais do GET é o historico, que o pull não traz.
    registro = json.loads(json.dumps(resultado["itens"][0]["registro"]))
    historico = detalhe.pop("historico")
    assert historico
    assert registro.keys() == detalhe.keys()
    for chave in detalhe:
        assert registro[chave] == detalhe[chave], chave
    assert [c["id_cultura"] for c in registro["culturas"]] == sorted(
        ativas, key=lambda i: next(c["nome"] for c in detalhe["culturas"] if c["id_cultura"] == i)
    )
    assert excluida not in [c["id_cultura"] for c in registro["culturas"]]


def test_culturas_de_cada_atendimento_vem_separadas(org, entrar_como):
    entrar_como(org["cliente"])
    primeira, segunda = duas_culturas()
    so_a_primeira = abrir(entrar_como, org["cliente"], org["id_cliente"], culturas=[primeira])
    as_duas = abrir(entrar_como, org["cliente"], org["id_cliente"], culturas=[primeira, segunda])
    nenhuma = abrir(entrar_como, org["cliente"], org["id_cliente"])

    resultado = pull_atendimentos(desde=org["marco"])

    culturas = {
        i["registro"]["id_atendimento"]: {c["id_cultura"] for c in i["registro"]["culturas"]}
        for i in resultado["itens"]
    }
    assert culturas == {
        str(so_a_primeira.id_atendimento): {primeira},
        str(as_duas.id_atendimento): {primeira, segunda},
        str(nenhuma.id_atendimento): set(),
    }


def test_resultado_e_serializavel_em_json(org, entrar_como):
    excluido = abrir(entrar_como, org["cliente"], org["id_cliente"])
    abrir(entrar_como, org["cliente"], org["id_cliente"], data_sugerida=timezone.localdate())
    excluir(entrar_como, org["admin"], excluido)

    entrar_como(org["cliente"])
    resultado = pull_atendimentos(desde=org["marco"])

    # json.dumps sem default: falha se sobrar UUID, data ou outro objeto.
    assert json.loads(json.dumps(resultado)) == resultado


# Número de consultas.


@pytest.mark.parametrize("quantidade", [1, 20])
def test_duas_consultas_qualquer_que_seja_o_tamanho_da_pagina(
    org, entrar_como, django_assert_num_queries, quantidade
):
    entrar_como(org["cliente"])
    culturas = duas_culturas()
    for _ in range(quantidade):
        abrir(entrar_como, org["cliente"], org["id_cliente"], culturas=culturas)

    # Uma para os atendimentos e uma para as culturas da página inteira.
    with django_assert_num_queries(2):
        resultado = pull_atendimentos(desde=org["marco"])

    assert len(resultado["itens"]) == quantidade
    assert all(len(i["registro"]["culturas"]) == 2 for i in resultado["itens"])


def test_pagina_so_com_tombstone_ou_vazia_faz_uma_consulta(
    org, entrar_como, django_assert_num_queries
):
    excluido = abrir(entrar_como, org["cliente"], org["id_cliente"])
    excluir(entrar_como, org["admin"], excluido)
    entrar_como(org["cliente"])

    with django_assert_num_queries(1):
        resultado = pull_atendimentos(desde=org["marco"])
    assert [i["removido"] for i in resultado["itens"]] == [True]

    with django_assert_num_queries(1):
        pull_atendimentos(desde=resultado["cursor"])


# Validação dos parâmetros.


@pytest.mark.parametrize("desde", [-1, "0", 1.5, None, True])
def test_desde_invalido_e_recusado(desde):
    with pytest.raises(ValueError, match="desde"):
        pull_atendimentos(desde=desde)


@pytest.mark.parametrize("limite", [0, 501, "10", 2.0, None, True])
def test_limite_invalido_e_recusado(limite):
    with pytest.raises(ValueError, match="limite"):
        pull_atendimentos(limite=limite)


def test_limites_da_faixa_sao_aceitos(org, entrar_como):
    entrar_como(org["cliente"])
    for limite in (1, 500):
        assert pull_atendimentos(desde=org["marco"], limite=limite)["itens"] == []
