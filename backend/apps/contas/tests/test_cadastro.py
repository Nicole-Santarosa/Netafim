"""Solicitação de cadastro, convite e aprovação pelo Netafim Admin (fluxograma de cadastro)."""

import re
import uuid

from django.core import mail
from django.urls import reverse

from .conftest import SENHA

CADASTRO = reverse("auth-cadastro")
LOGIN = reverse("auth-login")


def solicitar(api, email="produtor@gmail.com", **extra):
    dados = {
        "nome": "João Produtor",
        "email": email,
        "senha": SENHA,
        "telefone": "16988887777",
        "aceite_termo": True,
        "versao_termo": "1.0",
        **extra,
    }
    return api.post(CADASTRO, dados, format="json")


def entrar(api, email, senha):
    return api.post(
        LOGIN,
        {"email": email, "senha": senha, "dispositivo": {"id_dispositivo": str(uuid.uuid4())}},
        format="json",
    )


def aprovar(cliente_api, id_usuario, **dados):
    return cliente_api.post(f"/api/v1/usuarios/{id_usuario}/aprovar", dados, format="json")


def usuario_por_email(banco_sql, email):
    return banco_sql.um(
        "select id_usuario, situacao, id_perfil, tipo_organizacao, id_aprovado_por, "
        "data_aprovacao, consentimento_lgpd, versao_termo_consentimento "
        "from usuario where lower(email) = lower(%s)",
        email,
    )


def token_do_email() -> str:
    return re.search(r"token=([\w-]+)", mail.outbox[-1].body).group(1)


# --------------------------------------------------------------------------- solicitação


def test_cadastro_nasce_pendente_sem_perfil_nem_organizacao(api, banco_sql):
    r = solicitar(api)
    assert r.status_code == 202
    _, situacao, perfil, tipo, _, _, consentiu, versao = usuario_por_email(
        banco_sql, "produtor@gmail.com"
    )
    assert situacao == "pendente"
    assert perfil is None and tipo is None
    assert consentiu and versao == "1.0"


def test_cadastro_grava_hash_argon2(api, banco_sql, settings):
    settings.PASSWORD_HASHERS = ["django.contrib.auth.hashers.Argon2PasswordHasher"]
    solicitar(api)
    hash_gravado = banco_sql.um("select password from usuario where email = 'produtor@gmail.com'")
    assert hash_gravado.startswith("argon2$argon2id$")
    assert SENHA not in hash_gravado


def test_cadastro_audita_a_solicitacao_pelo_banco(api, banco_sql):
    solicitar(api)
    assert banco_sql.um("select acao from log_auditoria order by id_evento desc limit 1") == (
        "cadastro_solicitado"
    )


def test_usuario_pendente_nao_entra(api):
    solicitar(api)
    r = entrar(api, "produtor@gmail.com", SENHA)
    assert r.status_code == 403
    assert r.data["codigo"] == "conta_pendente"


def test_cadastro_repetido_responde_igual_e_nao_duplica(api, banco_sql):
    solicitar(api)
    r = solicitar(api, email="PRODUTOR@gmail.com", nome="Outra pessoa")
    assert r.status_code == 202
    assert (
        banco_sql.um("select count(*) from usuario where lower(email) = 'produtor@gmail.com'") == 1
    )


def test_cadastro_exige_aceite_do_termo_vigente(api):
    assert solicitar(api, aceite_termo=False).status_code == 400
    r = solicitar(api, versao_termo="0.9")
    assert r.status_code == 400
    assert r.data["codigo"] == "termo_desatualizado"
    assert api.get(reverse("auth-termo")).data == {"versao_vigente": "1.0"}


def test_cadastro_rejeita_senha_fraca(api, banco_sql):
    r = solicitar(api, senha="123456")
    assert r.status_code == 400
    assert "senha" in r.data
    assert usuario_por_email(banco_sql, "produtor@gmail.com") is None


# --------------------------------------------------------------------------- aprovação


def test_netafim_admin_aprova_cliente_definindo_organizacao_e_perfil(
    api, como, netafim_admin, cliente, perfil_id, banco_sql
):
    solicitar(api)
    id_pendente = usuario_por_email(banco_sql, "produtor@gmail.com")[0]
    r = aprovar(
        como(netafim_admin),
        id_pendente,
        tipo_organizacao="cliente",
        id_cliente=cliente.pk,
        id_perfil=perfil_id("cliente", "admin"),
    )
    assert r.status_code == 200, r.data
    assert r.data["situacao"] == "ativo"
    assert r.data["perfil"]["nome_perfil"] == "Cliente Admin"
    _, _, _, _, aprovado_por, data_aprovacao, *_ = usuario_por_email(
        banco_sql, "produtor@gmail.com"
    )
    assert aprovado_por == netafim_admin.pk and data_aprovacao
    assert "liberado" in mail.outbox[-1].subject
    assert entrar(api, "produtor@gmail.com", SENHA).status_code == 200


def test_aprovacao_de_distribuidor_exige_dominio_corporativo(
    api, como, netafim_admin, distribuidor, perfil_id, banco_sql
):
    solicitar(api, email="tecnico@gmail.com")
    id_pendente = usuario_por_email(banco_sql, "tecnico@gmail.com")[0]
    r = aprovar(
        como(netafim_admin),
        id_pendente,
        tipo_organizacao="distribuidor",
        id_distribuidor=distribuidor.pk,
        id_perfil=perfil_id("distribuidor", "usuario"),
    )
    assert r.status_code == 400
    assert r.data["codigo"] == "dominio_nao_autorizado"


def test_aprovacao_de_distribuidor_com_dominio_dele(
    api, como, netafim_admin, distribuidor, perfil_id, banco_sql
):
    solicitar(api, email="tecnico@agroirriga.com.br")
    id_pendente = usuario_por_email(banco_sql, "tecnico@agroirriga.com.br")[0]
    r = aprovar(
        como(netafim_admin),
        id_pendente,
        tipo_organizacao="distribuidor",
        id_distribuidor=distribuidor.pk,
        id_perfil=perfil_id("distribuidor", "usuario"),
    )
    assert r.status_code == 200, r.data


def test_aprovacao_recusa_perfil_de_outra_organizacao(
    api, como, netafim_admin, cliente, perfil_id, banco_sql
):
    solicitar(api)
    id_pendente = usuario_por_email(banco_sql, "produtor@gmail.com")[0]
    r = aprovar(
        como(netafim_admin),
        id_pendente,
        tipo_organizacao="cliente",
        id_cliente=cliente.pk,
        id_perfil=perfil_id("netafim", "admin"),
    )
    assert r.status_code == 400
    assert r.data["codigo"] == "perfil_incompativel"


def test_aprovacao_exige_organizacao(api, como, netafim_admin, perfil_id, banco_sql):
    solicitar(api)
    id_pendente = usuario_por_email(banco_sql, "produtor@gmail.com")[0]
    r = aprovar(como(netafim_admin), id_pendente, id_perfil=perfil_id("cliente", "usuario"))
    assert r.status_code == 400
    assert r.data["codigo"] == "organizacao_obrigatoria"


def test_somente_netafim_admin_aprova(
    api, como, distrib_admin, cliente_admin, cliente, perfil_id, banco_sql
):
    solicitar(api)
    id_pendente = usuario_por_email(banco_sql, "produtor@gmail.com")[0]
    for ator in (distrib_admin, cliente_admin):
        r = aprovar(
            como(ator),
            id_pendente,
            tipo_organizacao="cliente",
            id_cliente=cliente.pk,
            id_perfil=perfil_id("cliente", "usuario"),
        )
        assert r.status_code in (403, 404)
    assert usuario_por_email(banco_sql, "produtor@gmail.com")[1] == "pendente"


def test_netafim_admin_recusa_cadastro(api, como, netafim_admin, banco_sql):
    solicitar(api)
    id_pendente = usuario_por_email(banco_sql, "produtor@gmail.com")[0]
    r = como(netafim_admin).post(f"/api/v1/usuarios/{id_pendente}/recusar")
    assert r.status_code == 200
    assert r.data["situacao"] == "recusado"
    r = aprovar(como(netafim_admin), id_pendente, tipo_organizacao="netafim")
    assert r.data["codigo"] == "nao_pendente"


def test_lista_de_pendentes_para_aprovacao(api, como, netafim_admin):
    solicitar(api)
    solicitar(api, email="outro@gmail.com")
    r = como(netafim_admin).get("/api/v1/usuarios", {"situacao": "pendente"})
    assert r.status_code == 200
    assert {u["email"] for u in r.data["results"]} == {"produtor@gmail.com", "outro@gmail.com"}


# --------------------------------------------------------------------------- convite


def criar_carlos(como, netafim_admin, perfil_id):
    return como(netafim_admin).post(
        "/api/v1/usuarios",
        {
            "nome": "Carlos Técnico",
            "email": "carlos@netafim.com",
            "tipo_organizacao": "netafim",
            "id_perfil": perfil_id("netafim", "usuario"),
        },
        format="json",
    )


def aceitar(api, token, senha="Fertirrigacao#2026"):
    return api.post(
        reverse("auth-convite-aceitar"),
        {"token": token, "senha": senha, "aceite_termo": True, "versao_termo": "1.0"},
        format="json",
    )


def test_netafim_admin_cria_usuario_aprovado_com_convite(api, como, netafim_admin, perfil_id):
    r = criar_carlos(como, netafim_admin, perfil_id)
    assert r.status_code == 201, r.data
    assert r.data["situacao"] == "ativo" and r.data["convite_pendente"] is True

    assert aceitar(api, token_do_email()).status_code == 200
    r = entrar(api, "carlos@netafim.com", "Fertirrigacao#2026")
    assert r.status_code == 200
    assert r.data["usuario"]["convite_pendente"] is False
    assert r.data["usuario"]["versao_termo_consentimento"] == "1.0"


def test_convite_vale_uma_vez(api, como, netafim_admin, perfil_id):
    criar_carlos(como, netafim_admin, perfil_id)
    token = token_do_email()
    assert aceitar(api, token).status_code == 200
    assert aceitar(api, token).status_code == 401


def test_convite_com_termo_desatualizado_nao_consome_o_link(api, como, netafim_admin, perfil_id):
    criar_carlos(como, netafim_admin, perfil_id)
    token = token_do_email()
    r = api.post(
        reverse("auth-convite-aceitar"),
        {
            "token": token,
            "senha": "Fertirrigacao#2026",
            "aceite_termo": True,
            "versao_termo": "0.9",
        },
        format="json",
    )
    assert r.status_code == 400
    assert aceitar(api, token).status_code == 200


def test_refresh_nao_serve_como_convite(api, netafim_usuario):
    refresh = api.post(
        LOGIN,
        {
            "email": netafim_usuario.email,
            "senha": SENHA,
            "dispositivo": {"id_dispositivo": str(uuid.uuid4())},
        },
        format="json",
    ).data["refresh"]
    assert aceitar(api, refresh).status_code == 401
    # E a sessão continua valendo: o erro não consumiu o refresh.
    assert api.post(reverse("auth-refresh"), {"refresh": refresh}, format="json").status_code == 200


def test_admin_de_distribuidor_cria_usuario_pendente_da_propria_organizacao(
    como, distrib_admin, netafim_admin, perfil_id, banco_sql
):
    r = como(distrib_admin).post(
        "/api/v1/usuarios",
        {
            "nome": "Paulo",
            "email": "paulo@agroirriga.com.br",
            # Tentativa de escolher outra organização e perfil é ignorada.
            "tipo_organizacao": "netafim",
            "id_perfil": perfil_id("netafim", "admin"),
        },
        format="json",
    )
    assert r.status_code == 201, r.data
    assert r.data["situacao"] == "pendente"
    assert r.data["perfil"]["nome_perfil"] == "Distribuidor Usuário"
    assert r.data["organizacao"]["id"] == distrib_admin.distribuidor_id

    # Netafim Admin valida mantendo o que o convite definiu.
    id_novo = usuario_por_email(banco_sql, "paulo@agroirriga.com.br")[0]
    r = aprovar(como(netafim_admin), id_novo)
    assert r.status_code == 200, r.data
    assert r.data["situacao"] == "ativo"


def test_usuario_comum_nao_cria_usuarios(como, distrib_usuario, cliente_usuario):
    for ator in (distrib_usuario, cliente_usuario):
        r = como(ator).post(
            "/api/v1/usuarios", {"nome": "X", "email": "x@gmail.com"}, format="json"
        )
        assert r.status_code == 403


def test_email_em_uso(como, netafim_admin, netafim_usuario, perfil_id):
    r = como(netafim_admin).post(
        "/api/v1/usuarios",
        {
            "nome": "Dup",
            "email": netafim_usuario.email.upper(),
            "tipo_organizacao": "netafim",
            "id_perfil": perfil_id("netafim", "usuario"),
        },
        format="json",
    )
    assert r.status_code == 400
    assert r.data["codigo"] == "email_em_uso"


def test_reenviar_convite(api, como, netafim_admin, perfil_id, banco_sql):
    criar_carlos(como, netafim_admin, perfil_id)
    id_carlos = usuario_por_email(banco_sql, "carlos@netafim.com")[0]
    enviados = len(mail.outbox)
    r = como(netafim_admin).post(f"/api/v1/usuarios/{id_carlos}/reenviar-convite")
    assert r.status_code == 204
    assert len(mail.outbox) == enviados + 1
    # O link novo funciona; depois de definir a senha, não há mais o que reenviar.
    assert aceitar(api, token_do_email()).status_code == 200
    r = como(netafim_admin).post(f"/api/v1/usuarios/{id_carlos}/reenviar-convite")
    assert r.status_code == 400
    assert r.data["codigo"] == "convite_ja_aceito"
