import uuid

import pytest
from rest_framework.test import APIClient

from apps.atendimentos.servicos import abrir_atendimento

URL = "/api/v1/atendimentos"

CAMPOS_DA_LISTA = {
    "id_atendimento",
    "numero_protocolo",
    "status",
    "motivo",
    "origem",
    "nome_produtor",
    "nome_fazenda_informado",
    "cidade",
    "data_abertura",
    "data_atualizacao",
    "versao",
    "sync_seq",
}


class UsuarioDeTeste:
    """Usuário autenticado fictício para o force_authenticate. O usuário real
    virá do módulo de acesso, que ainda não está nesta branch; a view só usa
    pk e is_authenticated, então isto basta."""

    is_authenticated = True

    def __init__(self, pk):
        self.pk = pk


def cliente_autenticado(id_usuario):
    cliente = APIClient()
    cliente.force_authenticate(user=UsuarioDeTeste(id_usuario))
    return cliente


def criar_empresa_e_usuario(conexao_admin, mundo):
    """Cria, pela conexão de administrador, uma segunda empresa cliente e um
    usuário Cliente Usuário dela. Mesmo padrão do conftest: sufixo aleatório
    para o banco poder ser reaproveitado entre execuções. Devolve os ids."""
    sufixo = uuid.uuid4().hex[:8]
    with conexao_admin.cursor() as cur:
        cur.execute(
            "insert into cliente (nome, id_distribuidor) values (%s, %s) returning id_cliente",
            (f"Outra Empresa {sufixo}", mundo["id_distribuidor"]),
        )
        id_cliente = cur.fetchone()[0]
        cur.execute(
            """
            insert into usuario (nome, email, password, situacao, tipo_organizacao,
                                 id_perfil, id_cliente)
            select %s, %s, '!sem-login', 'ativo', 'cliente', p.id_perfil, %s
              from perfil p where p.nome_perfil = 'Cliente Usuário'
            returning id_usuario
            """,
            (f"Outro Usuario {sufixo}", f"outro-{sufixo}@exemplo.com", id_cliente),
        )
        id_usuario = cur.fetchone()[0]
    conexao_admin.commit()
    return {"id_cliente": id_cliente, "id_usuario": id_usuario}


def dados_pedido(id_cliente, **extras):
    """Dados mínimos de um pedido de cliente final, com UUID novo."""
    dados = {
        "id_atendimento": uuid.uuid4(),
        "origem": "cliente_final",
        "motivo": "pos_venda",
        "id_cliente": id_cliente,
        "contato_solicitante": "11999990000",
    }
    dados.update(extras)
    return dados


def abrir_dois(mundo):
    """Abre dois atendimentos do usuário do mundo, com datas de abertura
    diferentes (o now() do banco é igual dentro de uma mesma transação, então
    sem isso a ordem não seria testável). Devolve (antigo, novo)."""
    id_usuario = mundo["id_usuario_cliente"]
    antigo = abrir_atendimento(
        id_usuario,
        dados_pedido(mundo["id_cliente"], data_abertura="2026-10-01T10:00:00Z"),
    )
    novo = abrir_atendimento(
        id_usuario,
        dados_pedido(mundo["id_cliente"], data_abertura="2026-10-02T10:00:00Z"),
    )
    return antigo, novo


@pytest.mark.django_db
def test_lista_os_atendimentos_do_usuario_do_mais_novo_para_o_mais_antigo(
    mundo, entrar_como
):
    entrar_como(mundo["id_usuario_cliente"])
    antigo, novo = abrir_dois(mundo)

    resposta = cliente_autenticado(mundo["id_usuario_cliente"]).get(URL)

    assert resposta.status_code == 200
    resultados = resposta.data["results"]
    assert [r["id_atendimento"] for r in resultados] == [
        str(novo.id_atendimento),
        str(antigo.id_atendimento),
    ]
    assert set(resultados[0]) == CAMPOS_DA_LISTA


@pytest.mark.django_db
def test_usuario_de_outra_empresa_recebe_lista_vazia(mundo, conexao_admin, entrar_como):
    entrar_como(mundo["id_usuario_cliente"])
    abrir_dois(mundo)
    outro = criar_empresa_e_usuario(conexao_admin, mundo)

    # Quem pergunta agora é o usuário da outra empresa: o RLS esconde tudo.
    entrar_como(outro["id_usuario"])
    resposta = cliente_autenticado(outro["id_usuario"]).get(URL)

    assert resposta.status_code == 200
    assert resposta.data["results"] == []


@pytest.mark.django_db
def test_filtro_por_status(mundo, entrar_como):
    entrar_como(mundo["id_usuario_cliente"])
    abrir_dois(mundo)
    cliente = cliente_autenticado(mundo["id_usuario_cliente"])

    abertos = cliente.get(URL, {"status": "aberto"})
    encerrados = cliente.get(URL, {"status": "encerrado"})
    invalido = cliente.get(URL, {"status": "invalido"})

    assert abertos.status_code == 200
    assert len(abertos.data["results"]) == 2
    assert encerrados.status_code == 200
    assert encerrados.data["results"] == []
    assert invalido.status_code == 400
    assert "status" in invalido.data


@pytest.mark.django_db
def test_resposta_tem_formato_paginado(mundo, entrar_como):
    entrar_como(mundo["id_usuario_cliente"])
    abrir_dois(mundo)

    resposta = cliente_autenticado(mundo["id_usuario_cliente"]).get(URL)

    assert resposta.data["count"] == 2
    assert resposta.data["next"] is None
    assert resposta.data["previous"] is None
    assert len(resposta.data["results"]) == 2


@pytest.mark.django_db
def test_sem_autenticacao_nao_lista():
    resposta = APIClient().get(URL)

    assert resposta.status_code in (401, 403)


@pytest.mark.django_db
def test_atendimento_excluido_nao_aparece(mundo, conexao_admin, entrar_como):
    # Usa uma empresa e um usuário novos, para só existirem estes dois
    # atendimentos. Os dois são gravados pela conexão de administrador (e
    # confirmados), porque a transação do teste não é visível para ela.
    outro = criar_empresa_e_usuario(conexao_admin, mundo)
    id_visivel = uuid.uuid4()
    id_excluido = uuid.uuid4()
    with conexao_admin.cursor() as cur:
        for id_atendimento, deletado_em in ((id_visivel, None), (id_excluido, "now()")):
            cur.execute(
                """
                insert into atendimento (id_atendimento, numero_protocolo, origem, motivo,
                                         id_solicitante, id_cliente, contato_solicitante,
                                         deletado_em, sync_seq)
                values (%s, 'OS-0000', 'cliente_final', 'pos_venda', %s, %s, '11999990000',
                        case when %s then now() end, 0)
                """,
                (
                    id_atendimento,
                    outro["id_usuario"],
                    outro["id_cliente"],
                    deletado_em is not None,
                ),
            )
    conexao_admin.commit()

    entrar_como(outro["id_usuario"])
    resposta = cliente_autenticado(outro["id_usuario"]).get(URL)

    ids = [r["id_atendimento"] for r in resposta.data["results"]]
    assert ids == [str(id_visivel)]
