"""Direitos do titular (LGPD): pedir, acompanhar, exportar e anonimizar."""

import json

import psycopg
import pytest
from django.urls import reverse

from apps.contas.servicos import titular

from .conftest import MIGRACAO_TITULAR
from .test_autenticacao import logar

MEUS = reverse("me-solicitacoes-lgpd")
DADOS = reverse("me-dados")
FILA = reverse("solicitacao-lgpd-list")


def pedir(como, pessoa, tipo="acesso", **extra):
    return como(pessoa).post(MEUS, {"tipo": tipo, **extra}, format="json")


def acao(como, admin, id_solicitacao, nome, corpo=None):
    url = f"/api/v1/solicitacoes-lgpd/{id_solicitacao}/{nome}"
    return como(admin).post(url, corpo or {}, format="json")


def usuario_no_banco(banco_sql, pk):
    return banco_sql.todos(
        "select nome, email, situacao, deletado_em is not null, mfa_ativo "
        "from usuario where id_usuario = %s",
        pk,
    )[0]


# --------------------------------------------------------------------------- o titular


def test_titular_registra_e_acompanha_o_proprio_pedido(como, banco_sql, cliente_usuario):
    r = pedir(como, cliente_usuario, "acesso", observacao="Quero uma cópia dos meus dados")
    assert r.status_code == 201, r.data
    assert r.data["tipo"] == "acesso"
    assert r.data["status"] == "recebida"
    assert r.data["data_conclusao"] is None
    assert r.data["data_prazo"] > r.data["data_solicitacao"]

    lista = como(cliente_usuario).get(MEUS)
    assert lista.status_code == 200
    assert [p["id_solicitacao"] for p in lista.data] == [r.data["id_solicitacao"]]

    # o nome e o e-mail vêm do cadastro, nunca do corpo da requisição
    nome, email = banco_sql.todos("select nome_titular, email_contato from solicitacao_titular")[0]
    nome_cadastro = banco_sql.um(
        "select nome from usuario where id_usuario = %s", cliente_usuario.pk
    )
    assert (nome, email) == (nome_cadastro, cliente_usuario.email)
    # a trilha do banco guarda o evento, sem e-mail nos detalhes
    assert (
        banco_sql.um("select count(*) from log_auditoria where acao = 'solicitacao_titular'") == 1
    )
    assert "@" not in banco_sql.um(
        "select detalhes::text from log_auditoria where acao = 'solicitacao_titular'"
    )


def test_tipo_invalido_e_recusado(como, cliente_usuario):
    r = pedir(como, cliente_usuario, "apagar_tudo_agora")
    assert r.status_code == 400
    assert "tipo" in r.data


def test_so_um_pedido_em_aberto_por_tipo(como, netafim_admin, cliente_usuario):
    primeiro = pedir(como, cliente_usuario, "acesso")
    r = pedir(como, cliente_usuario, "acesso")
    assert r.status_code == 409
    assert r.data["codigo"] == "solicitacao_em_aberto"
    # outro tipo é permitido
    assert pedir(como, cliente_usuario, "correcao").status_code == 201
    # depois de concluído, o mesmo tipo pode ser pedido de novo
    assert acao(como, netafim_admin, primeiro.data["id_solicitacao"], "atender").status_code == 200
    assert pedir(como, cliente_usuario, "acesso").status_code == 201


def test_titular_so_ve_os_proprios_pedidos(como, cliente_usuario, distrib_usuario):
    pedir(como, cliente_usuario, "acesso")
    assert como(distrib_usuario).get(MEUS).data == []
    outro = pedir(como, distrib_usuario, "correcao")
    assert [p["id_solicitacao"] for p in como(cliente_usuario).get(MEUS).data] != [
        outro.data["id_solicitacao"]
    ]


def test_corpo_nao_consegue_registrar_pedido_em_nome_de_outro(
    como, banco_sql, cliente_usuario, distrib_usuario
):
    r = pedir(como, cliente_usuario, "acesso", id_usuario=distrib_usuario.pk)
    assert r.status_code == 201
    assert banco_sql.um("select id_usuario from solicitacao_titular") == cliente_usuario.pk


def test_sem_login_nao_acessa(api):
    assert api.get(MEUS).status_code == 401
    assert api.post(MEUS, {"tipo": "acesso"}, format="json").status_code == 401
    assert api.get(DADOS).status_code == 401
    assert api.get(FILA).status_code == 401


def test_funcao_do_banco_recusa_chamada_sem_usuario_identificado(banco_sql):
    with pytest.raises(psycopg.errors.InsufficientPrivilege):
        banco_sql.executar("select fn_titular_registrar_solicitacao('acesso', null)")


# --------------------------------------------------------------------------- exportação


def test_exportacao_traz_os_dados_do_titular_sem_segredos(api, como, banco_sql, cliente_usuario):
    logar(api, cliente_usuario)
    pedir(como, cliente_usuario, "portabilidade")
    r = como(cliente_usuario).get(DADOS)
    assert r.status_code == 200, r.data
    assert r.data["formato"] == 1
    assert r.data["conta"]["email"] == cliente_usuario.email
    assert len(r.data["dispositivos"]) == 1
    assert [p["tipo"] for p in r.data["solicitacoes_lgpd"]] == ["portabilidade"]
    assert {"aceito", "data", "versao_termo", "data_revogacao"} <= r.data["consentimento"].keys()
    texto = json.dumps(r.data, default=str).lower()
    assert "password" not in texto
    assert "segredo" not in texto
    assert "refresh" not in texto
    assert banco_sql.um("select count(*) from log_auditoria where acao = 'exportacao_dados'") == 1


def test_exportacao_sem_o_modulo_de_atendimentos_nao_traz_a_secao(como, cliente_usuario):
    r = como(cliente_usuario).get(DADOS)
    assert r.status_code == 200
    assert "atendimentos" not in r.data


def test_exportacao_inclui_a_secao_de_atendimentos_e_audita_uma_vez(
    como, banco_sql, cliente_usuario, monkeypatch
):
    secao = {"id_usuario": cliente_usuario.pk, "atendimentos": [], "historico": []}
    monkeypatch.setattr(titular, "_secao_atendimentos", lambda usuario: secao)
    r = como(cliente_usuario).get(DADOS)
    assert r.status_code == 200, r.data
    assert r.data["atendimentos"] == secao
    assert banco_sql.um("select count(*) from log_auditoria where acao = 'exportacao_dados'") == 1


def test_se_a_secao_de_atendimentos_falha_a_exportacao_nao_e_auditada(
    como, banco_sql, cliente_usuario, monkeypatch
):
    def falha(usuario):
        raise RuntimeError("falha no módulo de atendimentos")

    monkeypatch.setattr(titular, "_secao_atendimentos", falha)
    with pytest.raises(RuntimeError):
        como(cliente_usuario).get(DADOS)
    assert banco_sql.um("select count(*) from log_auditoria where acao = 'exportacao_dados'") == 0


def test_exportacao_traz_so_os_dados_de_quem_pediu(como, cliente_usuario, distrib_usuario):
    r = como(cliente_usuario).get(DADOS)
    assert r.data["conta"]["id_usuario"] == cliente_usuario.pk
    assert distrib_usuario.email not in json.dumps(r.data, default=str)


# --------------------------------------------------------------------------- a fila do admin


def test_so_netafim_admin_ve_e_atende_a_fila(como, cliente_usuario, distrib_admin, netafim_usuario):
    pedido = pedir(como, cliente_usuario, "acesso").data["id_solicitacao"]
    for pessoa in (cliente_usuario, distrib_admin, netafim_usuario):
        assert como(pessoa).get(FILA).status_code == 403
        assert acao(como, pessoa, pedido, "atender").status_code == 403


def test_admin_lista_filtra_e_ve_pedido_atrasado(como, banco_sql, netafim_admin, cliente_usuario):
    pedido = pedir(como, cliente_usuario, "acesso").data["id_solicitacao"]
    r = como(netafim_admin).get(FILA)
    assert r.status_code == 200
    item = r.data[0]
    assert item["email_contato"] == cliente_usuario.email
    assert item["atrasada"] is False

    banco_sql.executar(
        "update solicitacao_titular set data_solicitacao = now() - interval '30 days', "
        "data_prazo = now() - interval '15 days' where id_solicitacao = %s",
        pedido,
    )
    assert como(netafim_admin).get(FILA).data[0]["atrasada"] is True
    assert como(netafim_admin).get(FILA, {"status": "atendida"}).data == []
    assert como(netafim_admin).get(FILA, {"status": "recebida"}).data[0]["id_solicitacao"] == pedido
    assert como(netafim_admin).get(FILA, {"status": "inventado"}).status_code == 400


def test_analisar_registra_o_responsavel(como, netafim_admin, cliente_usuario):
    pedido = pedir(como, cliente_usuario, "correcao").data["id_solicitacao"]
    r = acao(como, netafim_admin, pedido, "analisar")
    assert r.status_code == 200
    assert (r.data["status"], r.data["id_responsavel"]) == ("em_analise", netafim_admin.pk)
    # o titular passa a ver o andamento
    assert como(cliente_usuario).get(MEUS).data[0]["status"] == "em_analise"


def test_negar_exige_motivo_e_o_titular_o_le(como, netafim_admin, cliente_usuario):
    pedido = pedir(como, cliente_usuario, "eliminacao").data["id_solicitacao"]
    assert acao(como, netafim_admin, pedido, "negar", {"justificativa": "   "}).status_code == 400
    assert acao(como, netafim_admin, pedido, "negar", {}).status_code == 400

    r = acao(como, netafim_admin, pedido, "negar", {"justificativa": "Obrigação legal de guarda"})
    assert r.status_code == 200
    assert r.data["status"] == "negada"
    assert r.data["data_conclusao"] is not None
    vista = como(cliente_usuario).get(MEUS).data[0]
    assert (vista["status"], vista["justificativa_negativa"]) == (
        "negada",
        "Obrigação legal de guarda",
    )


def test_pedido_concluido_nao_muda_e_inexistente_da_404(como, netafim_admin, cliente_usuario):
    pedido = pedir(como, cliente_usuario, "acesso").data["id_solicitacao"]
    assert acao(como, netafim_admin, pedido, "atender").status_code == 200
    for nome, corpo in (("atender", {}), ("analisar", {}), ("negar", {"justificativa": "x"})):
        r = acao(como, netafim_admin, pedido, nome, corpo)
        assert (r.status_code, r.data["codigo"]) == (409, "solicitacao_encerrada")
    r = acao(como, netafim_admin, 99999, "atender")
    assert (r.status_code, r.data["codigo"]) == (404, "solicitacao_nao_encontrada")


def test_atender_pedido_de_acesso_nao_mexe_na_conta(
    como, banco_sql, netafim_admin, cliente_usuario
):
    pedido = pedir(como, cliente_usuario, "acesso").data["id_solicitacao"]
    r = acao(como, netafim_admin, pedido, "atender")
    assert r.data["status"] == "atendida"
    assert usuario_no_banco(banco_sql, cliente_usuario.pk)[2] == "ativo"


# --------------------------------------------------------------------------- anonimização


def test_atender_anonimizacao_anonimiza_a_conta_e_encerra_as_sessoes(
    api, como, banco_sql, netafim_admin, cliente_usuario
):
    sessao = logar(api, cliente_usuario).data
    pedido = pedir(como, cliente_usuario, "anonimizacao").data["id_solicitacao"]

    r = acao(como, netafim_admin, pedido, "atender")
    assert r.status_code == 200, r.data
    assert r.data["status"] == "atendida"

    nome, email, situacao, excluido, mfa = usuario_no_banco(banco_sql, cliente_usuario.pk)
    assert nome == "Usuário anonimizado"
    assert email == f"anonimizado-{cliente_usuario.pk}@anonimizado.invalid"
    assert (situacao, excluido, mfa) == ("inativo", True, False)
    # o registro do pedido fica, mas sem nome nem e-mail do titular
    nome_t, email_t = banco_sql.todos(
        "select nome_titular, email_contato from solicitacao_titular"
    )[0]
    assert nome_t is None
    assert "@anonimizado.invalid" in email_t
    assert cliente_usuario.email not in email_t
    # a sessão antiga não renova mais e a pessoa não entra de novo
    assert (
        api.post(reverse("auth-refresh"), {"refresh": sessao["refresh"]}, format="json").status_code
        == 401
    )
    assert logar(api, cliente_usuario).status_code == 401
    assert banco_sql.um("select count(*) from log_auditoria where acao = 'dados_anonimizados'") == 1


def test_anonimizacao_tambem_apaga_o_mfa(como, banco_sql, netafim_admin, cliente_usuario):
    from apps.contas.servicos import totp

    api = como(cliente_usuario)
    segredo = api.post(reverse("me-mfa-iniciar")).data["segredo"]
    api.post(reverse("me-mfa-confirmar"), {"codigo": totp.codigo_em(segredo)}, format="json")
    assert banco_sql.um("select count(*) from usuario_mfa") == 1

    pedido = pedir(como, cliente_usuario, "eliminacao").data["id_solicitacao"]
    assert acao(como, netafim_admin, pedido, "atender").status_code == 200
    assert banco_sql.um("select count(*) from usuario_mfa") == 0
    assert banco_sql.um("select count(*) from usuario_mfa_recuperacao") == 0


def test_admin_nao_atende_o_proprio_pedido_de_anonimizacao(
    como, banco_sql, criar_usuario, netafim_admin
):
    outro_admin = criar_usuario(*_admin_netafim())
    pedido = pedir(como, netafim_admin, "anonimizacao").data["id_solicitacao"]

    r = acao(como, netafim_admin, pedido, "atender")
    assert (r.status_code, r.data["codigo"]) == (403, "proprio_pedido")
    assert usuario_no_banco(banco_sql, netafim_admin.pk)[2] == "ativo"

    # outro administrador pode atender
    assert acao(como, outro_admin, pedido, "atender").status_code == 200
    assert usuario_no_banco(banco_sql, netafim_admin.pk)[0] == "Usuário anonimizado"


def test_titular_que_ja_foi_apagado_nao_gera_erro_de_servidor(
    como, banco_sql, netafim_admin, cliente_usuario
):
    pedido = pedir(como, cliente_usuario, "anonimizacao").data["id_solicitacao"]
    banco_sql.executar("update solicitacao_titular set id_usuario = null")
    r = acao(como, netafim_admin, pedido, "atender")
    assert (r.status_code, r.data["codigo"]) == (400, "titular_inexistente")


def test_migracao_010_e_idempotente(banco_sql):
    banco_sql.executar(MIGRACAO_TITULAR.read_text(encoding="utf-8"))
    banco_sql.executar(MIGRACAO_TITULAR.read_text(encoding="utf-8"))


def _admin_netafim():
    from apps.contas.constantes import NivelPerfil, TipoOrganizacao

    return TipoOrganizacao.NETAFIM, NivelPerfil.ADMIN


# --------------------------------------------------------------------------- sem a migração 010


@pytest.fixture
def sem_migracao_010(banco_sql):
    banco_sql.executar("drop function fn_titular_registrar_solicitacao(text, text)")
    banco_sql.executar("drop function fn_titular_minhas_solicitacoes()")


def test_sem_a_migracao_010_as_rotas_do_titular_respondem_503(
    como, sem_migracao_010, cliente_usuario
):
    for r in (pedir(como, cliente_usuario, "acesso"), como(cliente_usuario).get(MEUS)):
        assert (r.status_code, r.data["codigo"]) == (503, "lgpd_indisponivel")


def test_sem_a_migracao_010_a_exportacao_continua_funcionando(
    como, sem_migracao_010, cliente_usuario
):
    r = como(cliente_usuario).get(DADOS)
    assert r.status_code == 200
    assert r.data["conta"]["email"] == cliente_usuario.email
    assert r.data["solicitacoes_lgpd"] == []
