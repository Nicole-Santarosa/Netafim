"""Login, sessão (refresh com rotação), bloqueio, aparelhos e senha."""

import re
import uuid

import pytest
from django.core import mail
from django.urls import reverse

from apps.contas.constantes import SituacaoUsuario, TipoToken
from apps.contas.models import Dispositivo, TokenAcesso, Usuario

from .conftest import SENHA

LOGIN = reverse("auth-login")
REFRESH = reverse("auth-refresh")
LOGOUT = reverse("auth-logout")
ME = reverse("me")


def logar(api, usuario, senha=SENHA, **extra):
    return api.post(LOGIN, {"email": usuario.email, "senha": senha, **extra}, format="json")


def test_login_devolve_tokens_e_perfil_com_permissoes(api, distrib_admin):
    r = logar(api, distrib_admin)
    assert r.status_code == 200, r.data
    assert {"access", "refresh", "access_expira_em", "refresh_expira_em"} <= r.data.keys()
    usuario = r.data["usuario"]
    assert usuario["perfil"]["nome_perfil"] == "Distribuidor Admin"
    assert usuario["organizacao"] == {
        "tipo": "distribuidor",
        "id": distrib_admin.distribuidor_id,
        "nome": "Agro Irriga Ltda",
    }
    assert "vincular_tecnico" in usuario["permissoes"]
    assert "aprovar_cadastro" not in usuario["permissoes"]


def test_login_ignora_maiusculas_no_email(api, cliente_usuario):
    r = api.post(LOGIN, {"email": cliente_usuario.email.upper(), "senha": SENHA}, format="json")
    assert r.status_code == 200


def test_access_token_autentica_me(api, netafim_usuario):
    access = logar(api, netafim_usuario).data["access"]
    api.credentials(HTTP_AUTHORIZATION=f"Bearer {access}")
    r = api.get(ME)
    assert r.status_code == 200
    assert r.data["email"] == netafim_usuario.email


def test_sem_token_nao_acessa(api):
    assert api.get(ME).status_code == 401


@pytest.mark.parametrize("email", ["naoexiste@gmail.com", None])
def test_erro_de_login_nao_revela_se_email_existe(api, netafim_usuario, email):
    r = api.post(
        LOGIN, {"email": email or netafim_usuario.email, "senha": "errada-123"}, format="json"
    )
    assert r.status_code == 401
    assert r.data["codigo"] == "credenciais_invalidas"


def test_bloqueio_temporario_apos_tentativas(api, netafim_usuario, settings):
    settings.CONTAS = {**settings.CONTAS, "MAX_FALHAS_LOGIN": 3}
    for _ in range(3):
        assert logar(api, netafim_usuario, senha="errada-123").status_code == 401
    netafim_usuario.refresh_from_db()
    assert netafim_usuario.bloqueado_ate is not None
    # Mesmo com a senha certa, continua bloqueado e com a mesma mensagem.
    r = logar(api, netafim_usuario)
    assert r.status_code == 401
    assert r.data["codigo"] == "credenciais_invalidas"


def test_login_com_sucesso_zera_falhas(api, netafim_usuario):
    logar(api, netafim_usuario, senha="errada-123")
    logar(api, netafim_usuario)
    netafim_usuario.refresh_from_db()
    assert netafim_usuario.falhas_login == 0
    assert netafim_usuario.last_login is not None


@pytest.mark.parametrize(
    ("situacao", "codigo"),
    [
        (SituacaoUsuario.PENDENTE, "conta_pendente"),
        (SituacaoUsuario.RECUSADO, "conta_recusada"),
        (SituacaoUsuario.INATIVO, "conta_inativa"),
        (SituacaoUsuario.BLOQUEADO, "conta_bloqueada"),
    ],
)
def test_conta_sem_acesso_liberado(api, criar_usuario, cliente, situacao, codigo):
    usuario = criar_usuario("cliente", "usuario", org=cliente, situacao=situacao)
    r = logar(api, usuario)
    assert r.status_code == 403
    assert r.data["codigo"] == codigo


def test_conta_inativada_perde_acesso_mesmo_com_token_valido(api, netafim_usuario):
    access = logar(api, netafim_usuario).data["access"]
    Usuario.objects.filter(pk=netafim_usuario.pk).update(situacao=SituacaoUsuario.INATIVO)
    api.credentials(HTTP_AUTHORIZATION=f"Bearer {access}")
    assert api.get(ME).status_code == 401


# --------------------------------------------------------------------------- refresh


def test_refresh_rotaciona_o_token(api, netafim_usuario):
    primeiro = logar(api, netafim_usuario).data["refresh"]
    r = api.post(REFRESH, {"refresh": primeiro}, format="json")
    assert r.status_code == 200
    assert r.data["refresh"] != primeiro
    # O antigo não serve mais.
    assert api.post(REFRESH, {"refresh": r.data["refresh"]}, format="json").status_code == 200


def test_repeticao_logo_apos_rotacao_e_tolerada(api, netafim_usuario):
    """Se a resposta da rotação se perde na rede, repetir o refresh antigo não derruba a sessão."""
    antigo = logar(api, netafim_usuario).data["refresh"]
    perdido = api.post(REFRESH, {"refresh": antigo}, format="json").data["refresh"]

    r = api.post(REFRESH, {"refresh": antigo}, format="json")  # app não recebeu a resposta
    assert r.status_code == 200
    assert r.data["refresh"] not in (antigo, perdido)
    assert api.post(REFRESH, {"refresh": r.data["refresh"]}, format="json").status_code == 200


def test_reuso_de_refresh_rotacionado_encerra_a_sessao(api, netafim_usuario, settings):
    settings.CONTAS = {**settings.CONTAS, "REFRESH_TOLERANCIA_SEGUNDOS": 0}
    roubado = logar(api, netafim_usuario).data["refresh"]
    legitimo = api.post(REFRESH, {"refresh": roubado}, format="json").data["refresh"]

    r = api.post(REFRESH, {"refresh": roubado}, format="json")  # atacante reusa
    assert r.status_code == 401
    # A sessão legítima também foi encerrada por segurança.
    assert api.post(REFRESH, {"refresh": legitimo}, format="json").status_code == 401


def test_refresh_invalido(api):
    r = api.post(REFRESH, {"refresh": "nao-existe"}, format="json")
    assert r.status_code == 401
    assert r.data["codigo"] == "token_invalido"


def test_logout_revoga_o_refresh(api, netafim_usuario):
    dados = logar(api, netafim_usuario).data
    api.credentials(HTTP_AUTHORIZATION=f"Bearer {dados['access']}")
    assert api.post(LOGOUT, {"refresh": dados["refresh"]}, format="json").status_code == 204
    api.credentials()
    assert api.post(REFRESH, {"refresh": dados["refresh"]}, format="json").status_code == 401


def test_logout_nao_revoga_refresh_de_outro_usuario(api, como, netafim_usuario, netafim_admin):
    alheio = logar(api, netafim_admin).data["refresh"]
    r = como(netafim_usuario).post(LOGOUT, {"refresh": alheio}, format="json")
    assert r.status_code == 204
    assert api.post(REFRESH, {"refresh": alheio}, format="json").status_code == 200


# --------------------------------------------------------------------------- aparelhos


def test_access_token_de_aparelho_revogado_deixa_de_valer(
    api, como, netafim_usuario, netafim_admin
):
    id_aparelho = str(uuid.uuid4())
    access = logar(api, netafim_usuario, dispositivo={"id_dispositivo": id_aparelho}).data["access"]
    api.credentials(HTTP_AUTHORIZATION=f"Bearer {access}")
    assert api.get(ME).status_code == 200
    como(netafim_admin).post(f"/api/v1/dispositivos/{id_aparelho}/revogar")
    assert api.get(ME).status_code == 401


def test_login_registra_o_aparelho(api, netafim_usuario):
    id_aparelho = uuid.uuid4()
    r = logar(
        api,
        netafim_usuario,
        dispositivo={
            "id_dispositivo": str(id_aparelho),
            "modelo": "Galaxy A54",
            "versao_app": "0.1.0",
        },
    )
    assert r.status_code == 200
    aparelho = Dispositivo.objects.get(pk=id_aparelho)
    assert aparelho.usuario == netafim_usuario
    assert TokenAcesso.objects.filter(dispositivo=aparelho, tipo=TipoToken.REFRESH).exists()


def test_aparelho_de_outro_usuario(api, netafim_usuario, netafim_admin):
    id_aparelho = str(uuid.uuid4())
    logar(api, netafim_admin, dispositivo={"id_dispositivo": id_aparelho})
    r = logar(api, netafim_usuario, dispositivo={"id_dispositivo": id_aparelho})
    assert r.status_code == 409
    assert r.data["codigo"] == "dispositivo_de_outro_usuario"


def test_aparelho_revogado_nao_entra_nem_renova(api, como, netafim_usuario, netafim_admin):
    id_aparelho = str(uuid.uuid4())
    refresh = logar(api, netafim_usuario, dispositivo={"id_dispositivo": id_aparelho}).data[
        "refresh"
    ]
    r = como(netafim_admin).post(f"/api/v1/dispositivos/{id_aparelho}/revogar")
    assert r.status_code == 200
    assert api.post(REFRESH, {"refresh": refresh}, format="json").status_code == 401
    r = logar(api, netafim_usuario, dispositivo={"id_dispositivo": id_aparelho})
    assert r.status_code == 403
    assert r.data["codigo"] == "dispositivo_revogado"


def test_somente_netafim_admin_revoga_aparelho(api, como, netafim_usuario, distrib_admin):
    id_aparelho = str(uuid.uuid4())
    logar(api, netafim_usuario, dispositivo={"id_dispositivo": id_aparelho})
    r = como(distrib_admin).post(f"/api/v1/dispositivos/{id_aparelho}/revogar")
    assert r.status_code == 403


# --------------------------------------------------------------------------- senha e /me


def test_atualizar_os_proprios_dados(como, cliente_usuario):
    r = como(cliente_usuario).patch(
        ME, {"nome": "Ana Souza", "telefone": "16999998888"}, format="json"
    )
    assert r.status_code == 200
    assert r.data["nome"] == "Ana Souza"
    cliente_usuario.refresh_from_db()
    assert cliente_usuario.telefone == "16999998888"


def test_me_nao_altera_perfil_nem_email(como, cliente_usuario):
    r = como(cliente_usuario).patch(ME, {"email": "outro@gmail.com", "id_perfil": 1}, format="json")
    assert r.status_code == 200
    cliente_usuario.refresh_from_db()
    assert cliente_usuario.email != "outro@gmail.com"
    assert cliente_usuario.perfil.nome_perfil == "Cliente Usuário"


def test_alterar_senha_encerra_as_sessoes(api, como, netafim_usuario):
    refresh = logar(api, netafim_usuario).data["refresh"]
    r = como(netafim_usuario).post(
        reverse("me-senha"),
        {"senha_atual": SENHA, "nova_senha": "Gotejamento#Novo2026"},
        format="json",
    )
    assert r.status_code == 204
    assert api.post(REFRESH, {"refresh": refresh}, format="json").status_code == 401
    assert logar(api, netafim_usuario, senha="Gotejamento#Novo2026").status_code == 200


def test_alterar_senha_exige_senha_atual_e_senha_forte(como, netafim_usuario):
    cli = como(netafim_usuario)
    r = cli.post(
        reverse("me-senha"), {"senha_atual": "errada", "nova_senha": "X#9abcdefg"}, format="json"
    )
    assert r.data["codigo"] == "senha_atual_incorreta"
    r = cli.post(
        reverse("me-senha"), {"senha_atual": SENHA, "nova_senha": "12345678"}, format="json"
    )
    assert r.status_code == 400
    assert "nova_senha" in r.data


def test_redefinicao_de_senha_por_email(api, netafim_usuario):
    r = api.post(reverse("auth-senha-esqueci"), {"email": netafim_usuario.email}, format="json")
    assert r.status_code == 202
    token = re.search(r"token=([\w-]+)", mail.outbox[-1].body).group(1)

    r = api.post(
        reverse("auth-senha-redefinir"),
        {"token": token, "nova_senha": "Aspersao#Nova2026"},
        format="json",
    )
    assert r.status_code == 200
    assert logar(api, netafim_usuario, senha="Aspersao#Nova2026").status_code == 200
    # Link de uso único.
    r = api.post(
        reverse("auth-senha-redefinir"),
        {"token": token, "nova_senha": "Outra#Senha2026"},
        format="json",
    )
    assert r.status_code == 401


def test_esqueci_senha_nao_revela_email(api):
    r = api.post(reverse("auth-senha-esqueci"), {"email": "ninguem@gmail.com"}, format="json")
    assert r.status_code == 202
    assert len(mail.outbox) == 0
