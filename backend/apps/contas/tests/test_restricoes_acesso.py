"""Restrições de acesso: escopo por organização, hierarquia e proteções de administrador."""

import pytest

from apps.contas.constantes import SituacaoUsuario
from apps.contas.models import Usuario

from .conftest import perfil

USUARIOS = "/api/v1/usuarios"


def emails(resposta) -> set[str]:
    return {u["email"] for u in resposta.data["results"]}


# --------------------------------------------------------------------------- visibilidade


def test_netafim_admin_ve_todos(
    como, netafim_admin, distrib_usuario, cliente_usuario, outro_cliente, criar_usuario
):
    outro = criar_usuario("cliente", "usuario", org=outro_cliente)
    r = como(netafim_admin).get(USUARIOS)
    assert {distrib_usuario.email, cliente_usuario.email, outro.email} <= emails(r)


def test_admin_de_distribuidor_ve_so_a_propria_organizacao(
    como, distrib_admin, distrib_usuario, cliente_usuario, criar_usuario, outro_distribuidor
):
    de_outro = criar_usuario("distribuidor", "usuario", org=outro_distribuidor)
    r = como(distrib_admin).get(USUARIOS)
    assert emails(r) == {distrib_admin.email, distrib_usuario.email}
    assert de_outro.email not in emails(r) and cliente_usuario.email not in emails(r)
    assert como(distrib_admin).get(f"{USUARIOS}/{de_outro.pk}").status_code == 404


def test_admin_de_cliente_ve_so_a_propria_empresa(
    como, cliente_admin, cliente_usuario, outro_cliente, criar_usuario
):
    criar_usuario("cliente", "usuario", org=outro_cliente)
    r = como(cliente_admin).get(USUARIOS)
    assert emails(r) == {cliente_admin.email, cliente_usuario.email}


@pytest.mark.parametrize("fixture", ["netafim_usuario", "distrib_usuario", "cliente_usuario"])
def test_perfil_usuario_nao_gerencia_usuarios(request, como, fixture):
    ator = request.getfixturevalue(fixture)
    assert como(ator).get(USUARIOS).status_code == 403


def test_filtros_da_listagem(como, netafim_admin, distrib_usuario, cliente_usuario):
    cli = como(netafim_admin)
    r = cli.get(USUARIOS, {"tipo_organizacao": "distribuidor"})
    assert emails(r) == {distrib_usuario.email}
    r = cli.get(USUARIOS, {"busca": cliente_usuario.email[:8]})
    assert cliente_usuario.email in emails(r)


# --------------------------------------------------------------------------- gestão no escopo


def test_admin_de_organizacao_edita_usuario_da_propria(como, cliente_admin, cliente_usuario):
    r = como(cliente_admin).patch(
        f"{USUARIOS}/{cliente_usuario.pk}", {"nome": "Novo nome"}, format="json"
    )
    assert r.status_code == 200
    assert r.data["nome"] == "Novo nome"


def test_admin_de_organizacao_nao_gerencia_outro_admin(como, criar_usuario, cliente, cliente_admin):
    outro_admin = criar_usuario("cliente", "admin", org=cliente)
    r = como(cliente_admin).post(
        f"{USUARIOS}/{outro_admin.pk}/situacao", {"situacao": "inativo"}, format="json"
    )
    assert r.status_code == 403
    assert como(cliente_admin).delete(f"{USUARIOS}/{outro_admin.pk}").status_code == 403


def test_ninguem_gerencia_a_si_mesmo_pela_lista(como, cliente_admin):
    r = como(cliente_admin).delete(f"{USUARIOS}/{cliente_admin.pk}")
    assert r.status_code == 403


def test_desativar_e_reativar(como, distrib_admin, distrib_usuario):
    cli = como(distrib_admin)
    r = cli.post(
        f"{USUARIOS}/{distrib_usuario.pk}/situacao", {"situacao": "inativo"}, format="json"
    )
    assert r.status_code == 200 and r.data["situacao"] == "inativo"
    r = cli.post(f"{USUARIOS}/{distrib_usuario.pk}/situacao", {"situacao": "ativo"}, format="json")
    assert r.status_code == 200 and r.data["situacao"] == "ativo"


def test_bloquear_e_exclusivo_do_netafim_admin(como, distrib_admin, distrib_usuario, netafim_admin):
    r = como(distrib_admin).post(
        f"{USUARIOS}/{distrib_usuario.pk}/situacao", {"situacao": "bloqueado"}, format="json"
    )
    assert r.status_code == 403
    r = como(netafim_admin).post(
        f"{USUARIOS}/{distrib_usuario.pk}/situacao", {"situacao": "bloqueado"}, format="json"
    )
    assert r.status_code == 200 and r.data["situacao"] == "bloqueado"


def test_transicao_invalida(como, netafim_admin, criar_usuario, cliente):
    pendente = criar_usuario("cliente", "usuario", org=cliente, situacao="pendente")
    r = como(netafim_admin).post(
        f"{USUARIOS}/{pendente.pk}/situacao", {"situacao": "ativo"}, format="json"
    )
    assert r.status_code == 400
    assert r.data["codigo"] == "transicao_invalida"


def test_definir_perfil_e_exclusivo_do_netafim_admin(
    como, netafim_admin, distrib_admin, distrib_usuario
):
    admin_distrib = perfil("distribuidor", "admin").pk
    r = como(distrib_admin).post(
        f"{USUARIOS}/{distrib_usuario.pk}/perfil", {"id_perfil": admin_distrib}, format="json"
    )
    assert r.status_code == 403
    r = como(netafim_admin).post(
        f"{USUARIOS}/{distrib_usuario.pk}/perfil", {"id_perfil": admin_distrib}, format="json"
    )
    assert r.status_code == 200
    assert r.data["perfil"]["nome_perfil"] == "Distribuidor Admin"


def test_exclusao_logica_esconde_e_libera_email(como, cliente_admin, cliente_usuario):
    r = como(cliente_admin).delete(f"{USUARIOS}/{cliente_usuario.pk}")
    assert r.status_code == 204
    assert not Usuario.objects.filter(pk=cliente_usuario.pk).exists()
    excluido = Usuario.todos.get(pk=cliente_usuario.pk)
    assert excluido.deletado_em is not None
    assert excluido.situacao == SituacaoUsuario.INATIVO


def test_ultimo_netafim_admin_nao_pode_ser_removido(como, criar_usuario, netafim_admin):
    segundo = criar_usuario("netafim", "admin")
    cli = como(segundo)
    assert cli.delete(f"{USUARIOS}/{netafim_admin.pk}").status_code == 204
    # Agora `segundo` é o último; outro admin não existe para removê-lo, e ele não
    # pode se rebaixar nem ser rebaixado.
    r = como(segundo).post(
        f"{USUARIOS}/{segundo.pk}/perfil",
        {"id_perfil": perfil("netafim", "usuario").pk},
        format="json",
    )
    assert r.status_code == 400
    assert r.data["codigo"] == "ultimo_admin"


# --------------------------------------------------------------------------- organizações


def test_organizacoes_visiveis_por_perfil(
    como, netafim_usuario, distrib_usuario, cliente_usuario, cliente, outro_cliente
):
    r = como(netafim_usuario).get("/api/v1/clientes")
    assert {c["id_cliente"] for c in r.data["results"]} >= {cliente.pk, outro_cliente.pk}
    # Distribuidor vê a carteira dele.
    r = como(distrib_usuario).get("/api/v1/clientes")
    assert {c["id_cliente"] for c in r.data["results"]} == {cliente.pk}
    # Cliente vê só a própria empresa.
    r = como(cliente_usuario).get("/api/v1/clientes")
    assert {c["id_cliente"] for c in r.data["results"]} == {cliente.pk}
    r = como(distrib_usuario).get("/api/v1/distribuidores")
    assert [d["id_distribuidor"] for d in r.data["results"]] == [distrib_usuario.distribuidor_id]


def test_somente_netafim_admin_cadastra_organizacoes(como, netafim_admin, distrib_admin):
    corpo = {"nome": "Nova Revenda", "regiao": "Oeste Paulista"}
    assert (
        como(distrib_admin).post("/api/v1/distribuidores", corpo, format="json").status_code == 403
    )
    r = como(netafim_admin).post("/api/v1/distribuidores", corpo, format="json")
    assert r.status_code == 201
    r = como(netafim_admin).post(
        "/api/v1/clientes",
        {"nome": "Fazenda Nova", "id_distribuidor": r.data["id_distribuidor"]},
        format="json",
    )
    assert r.status_code == 201


def test_organizacao_com_usuarios_nao_e_excluida(como, netafim_admin, cliente_usuario, cliente):
    r = como(netafim_admin).delete(f"/api/v1/clientes/{cliente.pk}")
    assert r.status_code == 409


def test_distribuidor_com_clientes_nao_e_excluido(como, netafim_admin, distribuidor, cliente):
    r = como(netafim_admin).delete(f"/api/v1/distribuidores/{distribuidor.pk}")
    assert r.status_code == 409
    assert r.data["codigo"] == "distribuidor_com_clientes"


def test_dominios_restritos_ao_netafim_admin(como, netafim_admin, distrib_admin, distribuidor):
    corpo = {
        "dominio": "@NovaRevenda.com.br",
        "tipo_organizacao": "distribuidor",
        "id_distribuidor": distribuidor.pk,
    }
    assert como(distrib_admin).post("/api/v1/dominios", corpo, format="json").status_code == 403
    r = como(netafim_admin).post("/api/v1/dominios", corpo, format="json")
    assert r.status_code == 201
    assert r.data["dominio"] == "novarevenda.com.br"


# --------------------------------------------------------------------------- perfis


def test_perfis_e_permissoes_visiveis(como, cliente_usuario):
    r = como(cliente_usuario).get("/api/v1/perfis")
    assert len(r.data) == 6


def test_gerenciar_permissoes_do_perfil(como, netafim_admin, distrib_admin):
    id_perfil = perfil("cliente", "usuario").pk
    url = f"/api/v1/perfis/{id_perfil}/permissoes"
    corpo = {"permissoes": ["abrir_atendimento", "consultar_proprios_atendimentos"]}
    assert como(distrib_admin).put(url, corpo, format="json").status_code == 403
    r = como(netafim_admin).put(url, corpo, format="json")
    assert r.status_code == 200
    assert r.data["permissoes"] == ["abrir_atendimento", "consultar_proprios_atendimentos"]


def test_netafim_admin_nao_perde_permissoes_essenciais(como, netafim_admin):
    url = f"/api/v1/perfis/{perfil('netafim', 'admin').pk}/permissoes"
    r = como(netafim_admin).put(url, {"permissoes": ["abrir_atendimento"]}, format="json")
    assert r.status_code == 400
    assert r.data["codigo"] == "permissao_essencial"
