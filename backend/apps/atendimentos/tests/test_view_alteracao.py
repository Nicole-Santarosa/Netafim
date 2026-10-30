import uuid

import pytest
from rest_framework.test import APIClient

from apps.atendimentos.servicos import abrir_atendimento
from apps.atendimentos.tests.test_view_listagem import (
    cliente_autenticado,
    criar_empresa_e_usuario,
    dados_pedido,
)


DOMINIO_DE_TESTE = "netafim-teste.exemplo"


def url(id_atendimento):
    return f"/api/v1/atendimentos/{id_atendimento}"


def criar_usuario(conexao_admin, nome_perfil, tipo_organizacao, id_cliente=None):
    """Cria, pela conexão de administrador, um usuário ativo com o perfil dado.
    Mesmo padrão do conftest: sufixo aleatório para o banco poder ser
    reaproveitado entre execuções. Usuário da Netafim exige e-mail de um domínio
    corporativo autorizado, e o banco local não traz nenhum; por isso o domínio
    de teste é cadastrado aqui (se já existir, nada muda). Devolve o id do
    usuário."""
    sufixo = uuid.uuid4().hex[:8]
    with conexao_admin.cursor() as cur:
        cur.execute(
            """
            insert into dominio_email_corporativo (dominio, tipo_organizacao)
            values (%s, 'netafim') on conflict (dominio) do nothing
            """,
            (DOMINIO_DE_TESTE,),
        )
        cur.execute(
            """
            insert into usuario (nome, email, password, situacao, tipo_organizacao,
                                 id_perfil, id_cliente)
            select %s, %s, '!sem-login', 'ativo', %s, p.id_perfil, %s
              from perfil p where p.nome_perfil = %s
            returning id_usuario
            """,
            (f"{nome_perfil} {sufixo}", f"u-{sufixo}@{DOMINIO_DE_TESTE}", tipo_organizacao,
             id_cliente, nome_perfil),
        )
        id_usuario = cur.fetchone()[0]
    conexao_admin.commit()
    return id_usuario


def criar_admin_netafim(conexao_admin):
    """Usuário do perfil Netafim Admin, que pode destinar, cancelar e vincular
    técnico."""
    return criar_usuario(conexao_admin, "Netafim Admin", "netafim")


def abrir_pedido_do_cliente(mundo):
    """Abre um atendimento no nome do usuário cliente do mundo."""
    return abrir_atendimento(
        mundo["id_usuario_cliente"], dados_pedido(mundo["id_cliente"])
    )


@pytest.mark.django_db
def test_admin_destina_atendimento_aberto(mundo, conexao_admin, entrar_como):
    entrar_como(mundo["id_usuario_cliente"])
    atendimento = abrir_pedido_do_cliente(mundo)
    admin = criar_admin_netafim(conexao_admin)

    entrar_como(admin)
    resposta = cliente_autenticado(admin).patch(
        url(atendimento.id_atendimento),
        {"versao": 1, "status": "destinado", "destinado_a": "netafim"},
        format="json",
    )

    assert resposta.status_code == 200
    assert resposta.data["status"] == "destinado"
    assert resposta.data["destinado_a"] == "netafim"
    assert resposta.data["versao"] == 2
    tipos = [e["tipo_evento"] for e in resposta.data["historico"]]
    assert "status_alterado" in tipos and "destinado" in tipos


@pytest.mark.django_db
def test_admin_cancela_atendimento_aberto(mundo, conexao_admin, entrar_como):
    entrar_como(mundo["id_usuario_cliente"])
    atendimento = abrir_pedido_do_cliente(mundo)
    admin = criar_admin_netafim(conexao_admin)

    entrar_como(admin)
    resposta = cliente_autenticado(admin).patch(
        url(atendimento.id_atendimento),
        {"versao": 1, "status": "cancelado"},
        format="json",
    )

    assert resposta.status_code == 200
    assert resposta.data["status"] == "cancelado"


@pytest.mark.django_db
def test_versao_velha_devolve_409_com_a_versao_atual(mundo, conexao_admin, entrar_como):
    entrar_como(mundo["id_usuario_cliente"])
    atendimento = abrir_pedido_do_cliente(mundo)
    admin = criar_admin_netafim(conexao_admin)
    entrar_como(admin)
    cliente = cliente_autenticado(admin)
    # Primeira alteração: a versão passa de 1 para 2.
    primeira = cliente.patch(
        url(atendimento.id_atendimento),
        {"versao": 1, "status": "destinado", "destinado_a": "netafim"},
        format="json",
    )
    assert primeira.status_code == 200

    # O aplicativo ainda acha que a versão é 1.
    resposta = cliente.patch(
        url(atendimento.id_atendimento),
        {"versao": 1, "status": "cancelado"},
        format="json",
    )

    assert resposta.status_code == 409
    assert resposta.data["codigo"] == "conflito_de_versao"
    assert resposta.data["versao_atual"] == 2


@pytest.mark.django_db
def test_cliente_tentando_destinar_recebe_422_do_gatilho(mundo, entrar_como):
    entrar_como(mundo["id_usuario_cliente"])
    atendimento = abrir_pedido_do_cliente(mundo)

    resposta = cliente_autenticado(mundo["id_usuario_cliente"]).patch(
        url(atendimento.id_atendimento),
        {"versao": 1, "status": "destinado", "destinado_a": "netafim"},
        format="json",
    )

    assert resposta.status_code == 422
    assert resposta.data["codigo"] == "regra_de_negocio"


@pytest.mark.django_db
def test_atendimento_inexistente_e_de_outra_empresa_devolvem_404(
    mundo, conexao_admin, entrar_como
):
    entrar_como(mundo["id_usuario_cliente"])
    atendimento = abrir_pedido_do_cliente(mundo)
    outro = criar_empresa_e_usuario(conexao_admin, mundo)
    entrar_como(outro["id_usuario"])
    cliente = cliente_autenticado(outro["id_usuario"])
    corpo = {"versao": 1, "status": "cancelado"}

    de_outra_empresa = cliente.patch(url(atendimento.id_atendimento), corpo, format="json")
    inexistente = cliente.patch(url(uuid.uuid4()), corpo, format="json")

    assert de_outra_empresa.status_code == 404
    assert inexistente.status_code == 404


@pytest.mark.django_db
def test_corpo_sem_mudanca_devolve_400(mundo, entrar_como):
    entrar_como(mundo["id_usuario_cliente"])
    atendimento = abrir_pedido_do_cliente(mundo)
    cliente = cliente_autenticado(mundo["id_usuario_cliente"])

    so_versao = cliente.patch(url(atendimento.id_atendimento), {"versao": 1}, format="json")
    chave_desconhecida = cliente.patch(
        url(atendimento.id_atendimento),
        {"versao": 1, "numero_protocolo": "X"},
        format="json",
    )

    assert so_versao.status_code == 400
    assert chave_desconhecida.status_code == 400


@pytest.mark.django_db
def test_sem_autenticacao_nao_altera():
    resposta = APIClient().patch(
        url(uuid.uuid4()), {"versao": 1, "status": "cancelado"}, format="json"
    )

    assert resposta.status_code in (401, 403)


@pytest.mark.django_db
def test_admin_vincula_tecnico(mundo, conexao_admin, entrar_como):
    entrar_como(mundo["id_usuario_cliente"])
    atendimento = abrir_pedido_do_cliente(mundo)
    admin = criar_admin_netafim(conexao_admin)
    tecnico = criar_usuario(conexao_admin, "Netafim Usuário", "netafim")

    entrar_como(admin)
    resposta = cliente_autenticado(admin).patch(
        url(atendimento.id_atendimento),
        {"versao": 1, "id_tecnico_responsavel": tecnico},
        format="json",
    )

    assert resposta.status_code == 200
    assert resposta.data["id_tecnico_responsavel"] == tecnico


@pytest.mark.django_db
def test_cliente_admin_da_mesma_empresa_nao_altera_pedido_de_outro_usuario(
    mundo, conexao_admin, entrar_como
):
    entrar_como(mundo["id_usuario_cliente"])
    atendimento = abrir_pedido_do_cliente(mundo)
    # Cliente Admin da mesma empresa: enxerga o atendimento (vê toda a empresa),
    # mas o RLS de UPDATE só deixa alterar o que ele mesmo abriu.
    cliente_admin = criar_usuario(
        conexao_admin, "Cliente Admin", "cliente", mundo["id_cliente"]
    )

    entrar_como(cliente_admin)
    resposta = cliente_autenticado(cliente_admin).patch(
        url(atendimento.id_atendimento),
        {"versao": 1, "status": "cancelado"},
        format="json",
    )

    assert resposta.status_code == 403
    assert resposta.data["codigo"] == "sem_permissao"
