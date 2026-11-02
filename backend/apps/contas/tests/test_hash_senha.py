"""
DT-002 contra o PostgreSQL real: a API (app_django) não lê o hash da senha nem a trilha de
auditoria, e o hash só chega pela conexão ``auth`` (papel app_auth), por duas funções do banco.

Os testes de comportamento sem banco estão em ``tests/unitarios/test_hash_senha.py``.
"""

import pytest
from django.contrib.auth.hashers import check_password, make_password
from django.db import DatabaseError, connection, connections, transaction
from django.urls import reverse

from apps.contas import contexto
from apps.contas.models import Usuario
from apps.contas.servicos import credenciais

from .conftest import SENHA

ME = reverse("me")


# --------------------------------------------------------------------------- app_django (a API)


@pytest.mark.parametrize(
    "consulta",
    [
        "select password from usuario",
        "select * from usuario",
        "select count(*) from log_auditoria",
        "select * from fn_credencial_por_email('qualquer@gmail.com')",
        "select fn_credencial_por_id(1)",
    ],
    ids=["coluna_password", "select_estrela", "trilha_de_auditoria", "funcao_email", "funcao_id"],
)
def test_conexao_comum_da_api_nao_le_hash_nem_trilha(netafim_admin, consulta):
    with (
        pytest.raises(DatabaseError, match="permission denied"),
        contexto.como_ator(netafim_admin.pk),
        connection.cursor() as cursor,
    ):
        cursor.execute(consulta)


def test_orm_continua_lendo_usuario_e_perfil_sem_o_hash(netafim_admin):
    with contexto.como_ator(netafim_admin.pk):
        usuario = Usuario.objects.select_related("perfil").get(pk=netafim_admin.pk)
    assert usuario.email == netafim_admin.email
    assert usuario.perfil.eh_admin
    assert usuario.password is None  # nunca lido


def test_api_ainda_grava_o_hash_ao_trocar_a_senha(como, netafim_usuario, banco_sql):
    antes = banco_sql.um("select password from usuario where id_usuario = %s", netafim_usuario.pk)
    r = como(netafim_usuario).post(
        reverse("me-senha"),
        {"senha_atual": SENHA, "nova_senha": "Outra#Senha2026x"},
        format="json",
    )
    assert r.status_code == 204
    depois = banco_sql.um("select password from usuario where id_usuario = %s", netafim_usuario.pk)
    assert depois != antes
    assert check_password("Outra#Senha2026x", depois)


def test_save_comum_nao_apaga_o_hash(netafim_usuario, banco_sql):
    """O usuário carregado tem password = None; um save() sem update_fields não pode gravá-lo."""
    antes = banco_sql.um("select password from usuario where id_usuario = %s", netafim_usuario.pk)
    with contexto.como_ator(netafim_usuario.pk):
        usuario = Usuario.objects.get(pk=netafim_usuario.pk)
        usuario.nome = "Nome Novo"
        usuario.save()
    assert (
        banco_sql.um("select password from usuario where id_usuario = %s", netafim_usuario.pk)
        == antes
    )
    assert (
        banco_sql.um("select nome from usuario where id_usuario = %s", netafim_usuario.pk)
        == "Nome Novo"
    )


# ---------------------------------------------------------------- app_auth (a conexão auth)


def test_conexao_auth_nao_tem_privilegio_em_nenhuma_tabela(netafim_admin):
    with (
        pytest.raises(DatabaseError, match="permission denied"),
        transaction.atomic(using="auth"),
        connections["auth"].cursor() as cursor,
    ):
        cursor.execute("select 1 from usuario limit 1")


def test_credencial_por_email_devolve_o_hash_sem_diferenciar_maiusculas(netafim_usuario):
    credencial = credenciais.por_email(netafim_usuario.email.upper())
    assert credencial is not None
    assert credencial.id_usuario == netafim_usuario.pk
    assert credencial.situacao == "ativo"
    assert check_password(SENHA, credencial.hash)
    assert credenciais.conferir(credencial, SENHA) is True
    assert credenciais.conferir(credencial, "senha-errada") is False


def test_credencial_por_id(netafim_usuario):
    credencial = credenciais.por_id(netafim_usuario.pk)
    assert credencial is not None and check_password(SENHA, credencial.hash)


def test_credencial_de_conta_inexistente_ou_excluida_e_none(netafim_usuario, banco_sql):
    assert credenciais.por_email("ninguem@gmail.com") is None
    assert credenciais.por_id(999_999) is None

    banco_sql.executar(
        "update usuario set deletado_em = now() where id_usuario = %s", netafim_usuario.pk
    )
    assert credenciais.por_email(netafim_usuario.email) is None
    assert credenciais.por_id(netafim_usuario.pk) is None


def test_credencial_traz_falhas_e_bloqueio_do_banco(netafim_usuario, banco_sql):
    banco_sql.executar(
        "update usuario set falhas_login = 3, bloqueado_ate = now() + interval '10 minutes' "
        "where id_usuario = %s",
        netafim_usuario.pk,
    )
    credencial = credenciais.por_id(netafim_usuario.pk)
    assert credencial.falhas_login == 3
    assert credencial.bloqueado_ate is not None


def test_senha_utilizavel_distingue_convite_nao_aceito(netafim_usuario, banco_sql):
    assert credenciais.senha_utilizavel(netafim_usuario.pk) is True
    banco_sql.executar(
        "update usuario set password = %s where id_usuario = %s",
        make_password(None),  # senha inutilizada: convite ainda não aceito
        netafim_usuario.pk,
    )
    assert credenciais.senha_utilizavel(netafim_usuario.pk) is False


# --------------------------------------------------------------------------- convite_pendente


def test_convite_pendente_vem_de_senha_alterada_em(como, criar_usuario):
    convidado = criar_usuario()  # nasce sem senha_alterada_em: o convite ainda não foi aceito
    assert como(convidado).get(ME).data["convite_pendente"] is True

    ja_definiu = criar_usuario(senha_alterada_em="2026-01-02T03:04:05+00:00")
    assert como(ja_definiu).get(ME).data["convite_pendente"] is False
