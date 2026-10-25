import itertools

import pytest
from django.db import transaction
from django.utils import timezone
from rest_framework.test import APIClient
from rest_framework_simplejwt.tokens import AccessToken

from apps.contas.constantes import NivelPerfil, SituacaoUsuario, TipoOrganizacao
from apps.contas.models import (
    Cliente,
    Distribuidor,
    DominioEmailCorporativo,
    Perfil,
    Usuario,
)

SENHA = "Irrigacao#Segura2026"
_seq = itertools.count(1)


@pytest.fixture(autouse=True)
def _db(db):
    """Todos os testes do módulo usam o banco (PostgreSQL)."""


@pytest.fixture(autouse=True)
def _on_commit_imediato(monkeypatch):
    """
    Cada teste roda dentro de uma transação que nunca é confirmada, então os
    callbacks de on_commit (envio de e-mail) são executados na hora.
    """
    monkeypatch.setattr(transaction, "on_commit", lambda funcao, using=None, robust=False: funcao())


@pytest.fixture
def api():
    return APIClient()


@pytest.fixture
def distribuidor():
    return Distribuidor.objects.create(nome="Agro Irriga Ltda", regiao="Ribeirão Preto")


@pytest.fixture
def outro_distribuidor():
    return Distribuidor.objects.create(nome="Campo Verde Revenda", regiao="Triângulo Mineiro")


@pytest.fixture
def cliente(distribuidor):
    return Cliente.objects.create(nome="Fazenda Santa Clara", distribuidor=distribuidor)


@pytest.fixture
def outro_cliente(outro_distribuidor):
    return Cliente.objects.create(nome="Sítio Boa Esperança", distribuidor=outro_distribuidor)


@pytest.fixture
def dominios(distribuidor, outro_distribuidor):
    DominioEmailCorporativo.objects.create(dominio="netafim.com", tipo_organizacao="netafim")
    DominioEmailCorporativo.objects.create(
        dominio="agroirriga.com.br", tipo_organizacao="distribuidor", distribuidor=distribuidor
    )
    DominioEmailCorporativo.objects.create(
        dominio="campoverde.com.br",
        tipo_organizacao="distribuidor",
        distribuidor=outro_distribuidor,
    )


def perfil(tipo: str, nivel: str) -> Perfil:
    return Perfil.objects.get(tipo_organizacao=tipo, nivel=nivel)


@pytest.fixture
def criar_usuario(dominios):
    """Cria usuário direto no banco. Domínio do e-mail segue a organização."""

    def _criar(
        tipo=TipoOrganizacao.NETAFIM,
        nivel=NivelPerfil.USUARIO,
        *,
        org=None,
        situacao=SituacaoUsuario.ATIVO,
        email=None,
        senha=SENHA,
        **extra,
    ):
        n = next(_seq)
        if email is None:
            dominio = {
                TipoOrganizacao.NETAFIM: "netafim.com",
                TipoOrganizacao.CLIENTE: "gmail.com",
            }.get(tipo)
            if tipo == TipoOrganizacao.DISTRIBUIDOR:
                dominio = org.dominios.first().dominio
            email = f"pessoa{n}@{dominio}"
        dados = {
            "nome": f"Pessoa {n}",
            "email": email,
            "situacao": situacao,
            "tipo_organizacao": tipo,
            "perfil": perfil(tipo, nivel) if tipo else None,
            "distribuidor": org if tipo == TipoOrganizacao.DISTRIBUIDOR else None,
            "cliente": org if tipo == TipoOrganizacao.CLIENTE else None,
            "data_aprovacao": timezone.now() if situacao == SituacaoUsuario.ATIVO else None,
            **extra,
        }
        usuario = Usuario(**dados)
        usuario.set_password(senha)
        usuario.save()
        return usuario

    return _criar


@pytest.fixture
def netafim_admin(criar_usuario):
    return criar_usuario(TipoOrganizacao.NETAFIM, NivelPerfil.ADMIN)


@pytest.fixture
def netafim_usuario(criar_usuario):
    return criar_usuario(TipoOrganizacao.NETAFIM, NivelPerfil.USUARIO)


@pytest.fixture
def distrib_admin(criar_usuario, distribuidor):
    return criar_usuario(TipoOrganizacao.DISTRIBUIDOR, NivelPerfil.ADMIN, org=distribuidor)


@pytest.fixture
def distrib_usuario(criar_usuario, distribuidor):
    return criar_usuario(TipoOrganizacao.DISTRIBUIDOR, NivelPerfil.USUARIO, org=distribuidor)


@pytest.fixture
def cliente_admin(criar_usuario, cliente):
    return criar_usuario(TipoOrganizacao.CLIENTE, NivelPerfil.ADMIN, org=cliente)


@pytest.fixture
def cliente_usuario(criar_usuario, cliente):
    return criar_usuario(TipoOrganizacao.CLIENTE, NivelPerfil.USUARIO, org=cliente)


@pytest.fixture
def como():
    """Retorna um APIClient autenticado como o usuário informado (JWT real)."""

    def _como(usuario):
        cliente_api = APIClient()
        cliente_api.credentials(HTTP_AUTHORIZATION=f"Bearer {AccessToken.for_user(usuario)}")
        return cliente_api

    return _como
