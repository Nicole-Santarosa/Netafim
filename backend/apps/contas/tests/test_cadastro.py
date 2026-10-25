"""Solicitação de cadastro, convite e aprovação pelo Netafim Admin (fluxograma de cadastro)."""

import re

from django.core import mail
from django.urls import reverse

from apps.contas.constantes import SituacaoUsuario
from apps.contas.models import Usuario

from .conftest import SENHA, perfil

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


def aprovar(cliente_api, usuario, **dados):
    return cliente_api.post(f"/api/v1/usuarios/{usuario.pk}/aprovar", dados, format="json")


# --------------------------------------------------------------------------- solicitação


def test_cadastro_nasce_pendente_sem_perfil_nem_organizacao(api):
    r = solicitar(api)
    assert r.status_code == 202
    usuario = Usuario.objects.get(email="produtor@gmail.com")
    assert usuario.situacao == SituacaoUsuario.PENDENTE
    assert usuario.perfil is None and usuario.tipo_organizacao is None
    assert usuario.consentimento_lgpd and usuario.versao_termo_consentimento == "1.0"


def test_usuario_pendente_nao_entra(api):
    solicitar(api)
    r = api.post(LOGIN, {"email": "produtor@gmail.com", "senha": SENHA}, format="json")
    assert r.status_code == 403
    assert r.data["codigo"] == "conta_pendente"


def test_cadastro_repetido_responde_igual_e_nao_duplica(api):
    solicitar(api)
    r = solicitar(api, email="PRODUTOR@gmail.com", nome="Outra pessoa")
    assert r.status_code == 202
    assert Usuario.objects.filter(email__iexact="produtor@gmail.com").count() == 1


def test_cadastro_exige_aceite_do_termo_vigente(api):
    assert solicitar(api, aceite_termo=False).status_code == 400
    r = solicitar(api, versao_termo="0.9")
    assert r.status_code == 400
    assert r.data["codigo"] == "termo_desatualizado"
    assert api.get(reverse("auth-termo")).data == {"versao_vigente": "1.0"}


def test_cadastro_rejeita_senha_fraca(api):
    r = solicitar(api, senha="123456")
    assert r.status_code == 400
    assert "senha" in r.data
    assert not Usuario.objects.filter(email="produtor@gmail.com").exists()


# --------------------------------------------------------------------------- aprovação


def test_netafim_admin_aprova_cliente_definindo_organizacao_e_perfil(
    api, como, netafim_admin, cliente
):
    solicitar(api)
    pendente = Usuario.objects.get(email="produtor@gmail.com")
    r = aprovar(
        como(netafim_admin),
        pendente,
        tipo_organizacao="cliente",
        id_cliente=cliente.pk,
        id_perfil=perfil("cliente", "admin").pk,
    )
    assert r.status_code == 200, r.data
    assert r.data["situacao"] == "ativo"
    assert r.data["perfil"]["nome_perfil"] == "Cliente Admin"
    pendente.refresh_from_db()
    assert pendente.aprovado_por == netafim_admin and pendente.data_aprovacao
    assert "liberado" in mail.outbox[-1].subject
    assert (
        api.post(LOGIN, {"email": pendente.email, "senha": SENHA}, format="json").status_code == 200
    )


def test_aprovacao_de_distribuidor_exige_dominio_corporativo(
    api, como, netafim_admin, distribuidor
):
    solicitar(api, email="tecnico@gmail.com")
    pendente = Usuario.objects.get(email="tecnico@gmail.com")
    r = aprovar(
        como(netafim_admin),
        pendente,
        tipo_organizacao="distribuidor",
        id_distribuidor=distribuidor.pk,
        id_perfil=perfil("distribuidor", "usuario").pk,
    )
    assert r.status_code == 400
    assert r.data["codigo"] == "dominio_nao_autorizado"


def test_aprovacao_de_distribuidor_com_dominio_dele(api, como, netafim_admin, distribuidor):
    solicitar(api, email="tecnico@agroirriga.com.br")
    pendente = Usuario.objects.get(email="tecnico@agroirriga.com.br")
    r = aprovar(
        como(netafim_admin),
        pendente,
        tipo_organizacao="distribuidor",
        id_distribuidor=distribuidor.pk,
        id_perfil=perfil("distribuidor", "usuario").pk,
    )
    assert r.status_code == 200, r.data


def test_aprovacao_recusa_perfil_de_outra_organizacao(api, como, netafim_admin, cliente):
    solicitar(api)
    pendente = Usuario.objects.get(email="produtor@gmail.com")
    r = aprovar(
        como(netafim_admin),
        pendente,
        tipo_organizacao="cliente",
        id_cliente=cliente.pk,
        id_perfil=perfil("netafim", "admin").pk,
    )
    assert r.status_code == 400
    assert r.data["codigo"] == "perfil_incompativel"


def test_aprovacao_exige_organizacao(api, como, netafim_admin):
    solicitar(api)
    pendente = Usuario.objects.get(email="produtor@gmail.com")
    r = aprovar(como(netafim_admin), pendente, id_perfil=perfil("cliente", "usuario").pk)
    assert r.status_code == 400
    assert r.data["codigo"] == "organizacao_obrigatoria"


def test_somente_netafim_admin_aprova(api, como, distrib_admin, cliente_admin, cliente):
    solicitar(api)
    pendente = Usuario.objects.get(email="produtor@gmail.com")
    for ator in (distrib_admin, cliente_admin):
        r = aprovar(
            como(ator),
            pendente,
            tipo_organizacao="cliente",
            id_cliente=cliente.pk,
            id_perfil=perfil("cliente", "usuario").pk,
        )
        assert r.status_code in (403, 404)
    pendente.refresh_from_db()
    assert pendente.situacao == SituacaoUsuario.PENDENTE


def test_netafim_admin_recusa_cadastro(api, como, netafim_admin):
    solicitar(api)
    pendente = Usuario.objects.get(email="produtor@gmail.com")
    r = como(netafim_admin).post(f"/api/v1/usuarios/{pendente.pk}/recusar")
    assert r.status_code == 200
    assert r.data["situacao"] == "recusado"
    r = aprovar(como(netafim_admin), pendente, tipo_organizacao="netafim")
    assert r.data["codigo"] == "nao_pendente"


def test_lista_de_pendentes_para_aprovacao(api, como, netafim_admin):
    solicitar(api)
    solicitar(api, email="outro@gmail.com")
    r = como(netafim_admin).get("/api/v1/usuarios", {"situacao": "pendente"})
    assert r.status_code == 200
    assert {u["email"] for u in r.data["results"]} == {"produtor@gmail.com", "outro@gmail.com"}


# --------------------------------------------------------------------------- convite


def token_do_convite() -> str:
    return re.search(r"token=([\w-]+)", mail.outbox[-1].body).group(1)


def test_netafim_admin_cria_usuario_ativo_com_convite(api, como, netafim_admin):
    r = como(netafim_admin).post(
        "/api/v1/usuarios",
        {
            "nome": "Carlos Técnico",
            "email": "carlos@netafim.com",
            "tipo_organizacao": "netafim",
            "id_perfil": perfil("netafim", "usuario").pk,
        },
        format="json",
    )
    assert r.status_code == 201, r.data
    assert r.data["situacao"] == "ativo" and r.data["convite_pendente"] is True

    r = api.post(
        reverse("auth-convite-aceitar"),
        {
            "token": token_do_convite(),
            "senha": "Fertirrigacao#2026",
            "aceite_termo": True,
            "versao_termo": "1.0",
        },
        format="json",
    )
    assert r.status_code == 200
    r = api.post(
        LOGIN, {"email": "carlos@netafim.com", "senha": "Fertirrigacao#2026"}, format="json"
    )
    assert r.status_code == 200
    assert r.data["usuario"]["convite_pendente"] is False
    assert r.data["usuario"]["versao_termo_consentimento"] == "1.0"


def test_convite_vale_uma_vez(api, como, netafim_admin):
    como(netafim_admin).post(
        "/api/v1/usuarios",
        {
            "nome": "Carlos",
            "email": "carlos@netafim.com",
            "tipo_organizacao": "netafim",
            "id_perfil": perfil("netafim", "usuario").pk,
        },
        format="json",
    )
    token = token_do_convite()
    corpo = {
        "token": token,
        "senha": "Fertirrigacao#2026",
        "aceite_termo": True,
        "versao_termo": "1.0",
    }
    assert api.post(reverse("auth-convite-aceitar"), corpo, format="json").status_code == 200
    assert api.post(reverse("auth-convite-aceitar"), corpo, format="json").status_code == 401


def test_admin_de_distribuidor_cria_usuario_pendente_da_propria_organizacao(
    como, distrib_admin, netafim_admin
):
    r = como(distrib_admin).post(
        "/api/v1/usuarios",
        {
            "nome": "Paulo",
            "email": "paulo@agroirriga.com.br",
            # Tentativa de escolher outra organização e perfil é ignorada.
            "tipo_organizacao": "netafim",
            "id_perfil": perfil("netafim", "admin").pk,
        },
        format="json",
    )
    assert r.status_code == 201, r.data
    assert r.data["situacao"] == "pendente"
    assert r.data["perfil"]["nome_perfil"] == "Distribuidor Usuário"
    assert r.data["organizacao"]["id"] == distrib_admin.distribuidor_id

    # Netafim Admin valida mantendo o que o convite definiu.
    novo = Usuario.objects.get(email="paulo@agroirriga.com.br")
    r = aprovar(como(netafim_admin), novo)
    assert r.status_code == 200, r.data
    assert r.data["situacao"] == "ativo"


def test_usuario_comum_nao_cria_usuarios(como, distrib_usuario, cliente_usuario):
    for ator in (distrib_usuario, cliente_usuario):
        r = como(ator).post(
            "/api/v1/usuarios", {"nome": "X", "email": "x@gmail.com"}, format="json"
        )
        assert r.status_code == 403


def test_email_em_uso(como, netafim_admin, netafim_usuario):
    r = como(netafim_admin).post(
        "/api/v1/usuarios",
        {
            "nome": "Dup",
            "email": netafim_usuario.email.upper(),
            "tipo_organizacao": "netafim",
            "id_perfil": perfil("netafim", "usuario").pk,
        },
        format="json",
    )
    assert r.status_code == 400
    assert r.data["codigo"] == "email_em_uso"
