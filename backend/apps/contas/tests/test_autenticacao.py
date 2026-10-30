"""Login, sessão (refresh com rotação), bloqueio, aparelhos e senha, pela API e como app_django."""

import re
import uuid

import pytest
from django.core import mail
from django.urls import reverse

from apps.contas.constantes import SituacaoUsuario

from .conftest import SENHA

LOGIN = reverse("auth-login")
REFRESH = reverse("auth-refresh")
LOGOUT = reverse("auth-logout")
ME = reverse("me")


def logar(api, pessoa, senha=SENHA, dispositivo=None, **extra):
    """Login com um aparelho novo (o banco só emite refresh para um aparelho do usuário)."""
    corpo = {
        "email": pessoa.email,
        "senha": senha,
        "dispositivo": dispositivo or {"id_dispositivo": str(uuid.uuid4())},
        **extra,
    }
    return api.post(LOGIN, corpo, format="json")


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


def test_login_exige_o_aparelho(api, netafim_usuario):
    r = api.post(LOGIN, {"email": netafim_usuario.email, "senha": SENHA}, format="json")
    assert r.status_code == 400
    assert "dispositivo" in r.data


def test_login_ignora_maiusculas_no_email(api, cliente_usuario):
    r = logar(api, cliente_usuario)
    r = api.post(
        LOGIN,
        {
            "email": cliente_usuario.email.upper(),
            "senha": SENHA,
            "dispositivo": {"id_dispositivo": str(uuid.uuid4())},
        },
        format="json",
    )
    assert r.status_code == 200


def test_ip_invalido_no_cabecalho_nao_derruba_o_login(api, netafim_usuario):
    r = api.post(
        LOGIN,
        {
            "email": netafim_usuario.email,
            "senha": SENHA,
            "dispositivo": {"id_dispositivo": str(uuid.uuid4())},
        },
        format="json",
        HTTP_X_FORWARDED_FOR="isto-nao-e-um-ip",
    )
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
        LOGIN,
        {
            "email": email or netafim_usuario.email,
            "senha": "errada-123",
            "dispositivo": {"id_dispositivo": str(uuid.uuid4())},
        },
        format="json",
    )
    assert r.status_code == 401
    assert r.data["codigo"] == "credenciais_invalidas"


def test_bloqueio_temporario_apos_cinco_falhas(api, netafim_usuario, banco_sql):
    """A contagem é do banco (fn_registrar_login): 5 falhas travam por 15 minutos e ficam
    gravadas."""
    for _ in range(5):
        assert logar(api, netafim_usuario, senha="errada-123").status_code == 401
    falhas, bloqueado_ate = banco_sql.um(
        "select falhas_login, bloqueado_ate from usuario where id_usuario = %s", netafim_usuario.pk
    )
    assert falhas == 5
    assert bloqueado_ate is not None
    # Mesmo com a senha certa continua bloqueado, com a mesma mensagem, e o contador não anda.
    r = logar(api, netafim_usuario)
    assert r.status_code == 401
    assert r.data["codigo"] == "credenciais_invalidas"
    assert logar(api, netafim_usuario, senha="errada-123").status_code == 401
    assert (
        banco_sql.um("select falhas_login from usuario where id_usuario = %s", netafim_usuario.pk)
        == 5
    )


def test_login_com_sucesso_zera_falhas_e_grava_ultimo_acesso(api, netafim_usuario, banco_sql):
    logar(api, netafim_usuario, senha="errada-123")
    assert logar(api, netafim_usuario).status_code == 200
    falhas, ultimo = banco_sql.um(
        "select falhas_login, last_login from usuario where id_usuario = %s", netafim_usuario.pk
    )
    assert falhas == 0
    assert ultimo is not None


def test_login_e_falha_ficam_na_trilha_de_auditoria(api, netafim_usuario, banco_sql):
    logar(api, netafim_usuario, senha="errada-123")
    logar(api, netafim_usuario)
    acoes = [a for (a,) in banco_sql.todos("select acao from log_auditoria order by id_evento")]
    assert acoes == ["login_falha", "login_sucesso"]


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


def test_conta_inativada_perde_acesso_mesmo_com_token_valido(api, netafim_usuario, banco_sql):
    access = logar(api, netafim_usuario).data["access"]
    banco_sql.executar(
        "update usuario set situacao = 'inativo' where id_usuario = %s", netafim_usuario.pk
    )
    api.credentials(HTTP_AUTHORIZATION=f"Bearer {access}")
    assert api.get(ME).status_code == 401


# --------------------------------------------------------------------------- refresh


def test_refresh_rotaciona_o_token(api, netafim_usuario):
    primeiro = logar(api, netafim_usuario).data["refresh"]
    r = api.post(REFRESH, {"refresh": primeiro}, format="json")
    assert r.status_code == 200
    assert r.data["refresh"] != primeiro
    assert r.data["usuario"]["permissoes"]
    assert api.post(REFRESH, {"refresh": r.data["refresh"]}, format="json").status_code == 200


def test_reuso_de_refresh_rotacionado_encerra_a_sessao(api, netafim_usuario):
    roubado = logar(api, netafim_usuario).data["refresh"]
    legitimo = api.post(REFRESH, {"refresh": roubado}, format="json").data["refresh"]

    r = api.post(REFRESH, {"refresh": roubado}, format="json")  # atacante reusa
    assert r.status_code == 401
    # A revogação feita pelo banco ficou gravada: a sessão legítima também acabou.
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


def test_refresh_so_e_guardado_como_hash(api, netafim_usuario, banco_sql):
    refresh = logar(api, netafim_usuario).data["refresh"]
    (hash_guardado,) = banco_sql.todos("select token_hash from token_acesso")[0]
    assert refresh not in hash_guardado
    assert len(hash_guardado.strip()) == 64


# --------------------------------------------------------------------------- aparelhos


def test_login_registra_o_aparelho(api, netafim_usuario, banco_sql):
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
    dono, modelo = banco_sql.um(
        "select id_usuario, modelo from dispositivo where id_dispositivo = %s", id_aparelho
    )
    assert (dono, modelo) == (netafim_usuario.pk, "Galaxy A54")
    assert banco_sql.um("select count(*) from token_acesso where id_dispositivo = %s", id_aparelho)


def test_aparelho_de_outro_usuario(api, netafim_usuario, netafim_admin):
    id_aparelho = {"id_dispositivo": str(uuid.uuid4())}
    assert logar(api, netafim_admin, dispositivo=id_aparelho).status_code == 200
    r = logar(api, netafim_usuario, dispositivo=id_aparelho)
    assert r.status_code == 409
    assert r.data["codigo"] == "dispositivo_de_outro_usuario"


def test_aparelho_revogado_nao_entra_nem_renova(api, como, netafim_usuario, netafim_admin):
    id_aparelho = {"id_dispositivo": str(uuid.uuid4())}
    dados = logar(api, netafim_usuario, dispositivo=id_aparelho).data
    r = como(netafim_admin).post(f"/api/v1/dispositivos/{id_aparelho['id_dispositivo']}/revogar")
    assert r.status_code == 200
    # O banco revogou os tokens do aparelho; o access ainda não expirado também deixa de valer.
    assert api.post(REFRESH, {"refresh": dados["refresh"]}, format="json").status_code == 401
    api.credentials(HTTP_AUTHORIZATION=f"Bearer {dados['access']}")
    assert api.get(ME).status_code == 401
    api.credentials()
    r = logar(api, netafim_usuario, dispositivo=id_aparelho)
    assert r.status_code == 403
    assert r.data["codigo"] == "dispositivo_revogado"


def test_somente_netafim_admin_revoga_aparelho(api, como, netafim_usuario, distrib_admin):
    id_aparelho = str(uuid.uuid4())
    logar(api, netafim_usuario, dispositivo={"id_dispositivo": id_aparelho})
    r = como(distrib_admin).post(f"/api/v1/dispositivos/{id_aparelho}/revogar")
    assert r.status_code in (403, 404)


# --------------------------------------------------------------------------- senha e /me


def test_atualizar_os_proprios_dados(como, cliente_usuario):
    r = como(cliente_usuario).patch(
        ME, {"nome": "Ana Souza", "telefone": "16999998888"}, format="json"
    )
    assert r.status_code == 200
    assert r.data["nome"] == "Ana Souza"
    assert r.data["telefone"] == "16999998888"


def test_me_nao_altera_perfil_nem_email(como, cliente_usuario, banco_sql):
    r = como(cliente_usuario).patch(ME, {"email": "outro@gmail.com", "id_perfil": 1}, format="json")
    assert r.status_code == 200
    email, perfil = banco_sql.um(
        "select u.email, p.nome_perfil from usuario u join perfil p using (id_perfil) "
        "where u.id_usuario = %s",
        cliente_usuario.pk,
    )
    assert email == cliente_usuario.email
    assert perfil == "Cliente Usuário"


def test_alterar_senha(api, como, netafim_usuario):
    r = como(netafim_usuario).post(
        reverse("me-senha"),
        {"senha_atual": SENHA, "nova_senha": "Gotejamento#Novo2026"},
        format="json",
    )
    assert r.status_code == 204
    assert logar(api, netafim_usuario, senha=SENHA).status_code == 401
    assert logar(api, netafim_usuario, senha="Gotejamento#Novo2026").status_code == 200


def test_alterar_senha_encerra_as_outras_sessoes(api, como, netafim_usuario, com_revogar_tokens):
    """Com fn_revogar_tokens_usuario no banco (proposta), a troca de senha encerra as sessões."""
    refresh = logar(api, netafim_usuario).data["refresh"]
    como(netafim_usuario).post(
        reverse("me-senha"),
        {"senha_atual": SENHA, "nova_senha": "Gotejamento#Novo2026"},
        format="json",
    )
    assert api.post(REFRESH, {"refresh": refresh}, format="json").status_code == 401


def test_sem_a_funcao_do_banco_a_troca_de_senha_funciona_e_avisa(
    api, como, netafim_usuario, caplog
):
    """Hoje o banco não tem fn_revogar_tokens_usuario: a troca funciona e o aviso fica no log."""
    refresh = logar(api, netafim_usuario).data["refresh"]
    r = como(netafim_usuario).post(
        reverse("me-senha"),
        {"senha_atual": SENHA, "nova_senha": "Gotejamento#Novo2026"},
        format="json",
    )
    assert r.status_code == 204
    assert "fn_revogar_tokens_usuario ausente" in caplog.text
    assert api.post(REFRESH, {"refresh": refresh}, format="json").status_code == 200


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


def test_senha_fraca_nao_consome_o_link(api, netafim_usuario):
    api.post(reverse("auth-senha-esqueci"), {"email": netafim_usuario.email}, format="json")
    token = re.search(r"token=([\w-]+)", mail.outbox[-1].body).group(1)
    fraca = api.post(
        reverse("auth-senha-redefinir"), {"token": token, "nova_senha": "12345678"}, format="json"
    )
    assert fraca.status_code == 400
    boa = api.post(
        reverse("auth-senha-redefinir"),
        {"token": token, "nova_senha": "Aspersao#Nova2026"},
        format="json",
    )
    assert boa.status_code == 200


def test_esqueci_senha_nao_revela_email(api):
    r = api.post(reverse("auth-senha-esqueci"), {"email": "ninguem@gmail.com"}, format="json")
    assert r.status_code == 202
    assert len(mail.outbox) == 0


def test_esqueci_senha_de_conta_nao_ativa_nao_envia(api, criar_usuario, cliente):
    pendente = criar_usuario("cliente", "usuario", org=cliente, situacao="pendente")
    r = api.post(reverse("auth-senha-esqueci"), {"email": pendente.email}, format="json")
    assert r.status_code == 202
    assert len(mail.outbox) == 0
