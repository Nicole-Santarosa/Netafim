"""Regras garantidas pelo PostgreSQL (MER/DER, seção 5.1) e carga da matriz (Anexo B)."""

import uuid
from datetime import timedelta

import pytest
from django.db import IntegrityError, transaction
from django.db.utils import DatabaseError
from django.utils import timezone

from apps.contas.constantes import NivelPerfil, Permissao, SituacaoUsuario, TipoToken
from apps.contas.models import Dispositivo, Perfil, TokenAcesso, Usuario
from apps.contas.servicos import tokens

from .conftest import perfil


def violacao(funcao):
    with pytest.raises((IntegrityError, DatabaseError)), transaction.atomic():
        funcao()


# --------------------------------------------------------------------------- matriz

NA, NU, DA, DU, CA, CU = (
    ("netafim", "admin"),
    ("netafim", "usuario"),
    ("distribuidor", "admin"),
    ("distribuidor", "usuario"),
    ("cliente", "admin"),
    ("cliente", "usuario"),
)
TODOS = {NA, NU, DA, DU, CA, CU}
MATRIZ_ANEXO_B = {
    Permissao.APROVAR_CADASTRO: {NA},
    Permissao.CRIAR_USUARIO: {NA, DA, CA},
    Permissao.EDITAR_USUARIO: {NA, DA, CA},
    Permissao.EXCLUIR_USUARIO: {NA, DA, CA},
    Permissao.DEFINIR_PERFIL: {NA},
    Permissao.ABRIR_ATENDIMENTO: TODOS,
    Permissao.VINCULAR_TECNICO: {NA, DA},
    Permissao.RESPONDER_ATENDIMENTO: TODOS,
    Permissao.ENCERRAR_ATENDIMENTO: {NA},
    Permissao.CONSULTAR_PROPRIOS_ATENDIMENTOS: TODOS,
    Permissao.CONSULTAR_ATENDIMENTOS_EMPRESA: {NA, DA, CA},
    Permissao.CONSULTAR_ATENDIMENTOS_CLIENTES_VINCULADOS: {NA, DA},
    Permissao.CONSULTAR_TODOS_ATENDIMENTOS: {NA},
    Permissao.DASHBOARD_GERENCIAL: {NA, DA, CA},
    Permissao.DESTINAR_ATENDIMENTO: {NA},
    Permissao.RECLASSIFICAR_ATENDIMENTO: {NA},
    Permissao.EDITAR_ATENDIMENTO_FINALIZADO: {NA},
}


def test_seis_perfis_carregados():
    assert Perfil.objects.count() == 6
    assert {(p.tipo_organizacao, p.nivel) for p in Perfil.objects.all()} == TODOS


@pytest.mark.parametrize(("codigo", "perfis"), MATRIZ_ANEXO_B.items())
def test_matriz_de_permissoes_igual_ao_anexo_b(codigo, perfis):
    reais = {
        (p.tipo_organizacao, p.nivel) for p in Perfil.objects.filter(permissoes__codigo=codigo)
    }
    assert reais == perfis


# --------------------------------------------------------------------------- usuário


def test_usuario_ativo_exige_perfil_e_organizacao():
    u = Usuario(nome="Sem perfil", email="x@gmail.com", situacao=SituacaoUsuario.ATIVO)
    u.set_unusable_password()
    violacao(u.save)


def test_usuario_nao_pode_ter_duas_organizacoes(criar_usuario, cliente, distribuidor):
    u = criar_usuario("cliente", "usuario", org=cliente)
    u.distribuidor = distribuidor
    violacao(u.save)


def test_tipo_organizacao_coerente_com_chave(criar_usuario, cliente):
    u = criar_usuario("cliente", "usuario", org=cliente)
    u.tipo_organizacao = "netafim"
    u.perfil = perfil("netafim", "usuario")
    violacao(u.save)


def test_perfil_precisa_ser_do_mesmo_tipo_de_organizacao(criar_usuario, cliente):
    u = criar_usuario("cliente", "usuario", org=cliente)
    u.perfil = perfil("netafim", "admin")  # escalada de privilégio barrada pelo banco
    violacao(u.save)


def test_email_unico_sem_diferenciar_maiusculas():
    u = Usuario(nome="A", email="joao@gmail.com")
    u.set_unusable_password()
    u.save()
    dup = Usuario(nome="B", email="JOAO@gmail.com")
    dup.set_unusable_password()
    violacao(dup.save)


def test_email_liberado_apos_exclusao_logica():
    u = Usuario(nome="A", email="maria@gmail.com")
    u.set_unusable_password()
    u.save()
    u.deletado_em = timezone.now()
    u.save()
    novo = Usuario(nome="B", email="maria@gmail.com")
    novo.set_unusable_password()
    novo.save()


def test_banco_so_aceita_hash_de_senha():
    u = Usuario(nome="A", email="texto@gmail.com", password="senha-em-texto-puro")
    violacao(u.save)


def test_consentimento_exige_data_e_versao():
    u = Usuario(nome="A", email="c@gmail.com", consentimento_lgpd=True)
    u.set_unusable_password()
    violacao(u.save)


def test_aprovacao_netafim_exige_dominio_corporativo(dominios):
    u = Usuario(
        nome="A",
        email="alguem@gmail.com",
        situacao=SituacaoUsuario.ATIVO,
        tipo_organizacao="netafim",
        perfil=perfil("netafim", "usuario"),
    )
    u.set_unusable_password()
    with pytest.raises(IntegrityError, match="não autorizado"), transaction.atomic():
        u.save()


def test_dominio_de_distribuidor_vale_so_para_o_proprio(dominios, outro_distribuidor):
    u = Usuario(
        nome="A",
        email="a@agroirriga.com.br",
        situacao=SituacaoUsuario.ATIVO,
        tipo_organizacao="distribuidor",
        distribuidor=outro_distribuidor,
        perfil=perfil("distribuidor", "usuario"),
    )
    u.set_unusable_password()
    violacao(u.save)


def test_desativar_dominio_nao_trava_conta_ja_ativa(netafim_usuario):
    from apps.contas.models import DominioEmailCorporativo

    DominioEmailCorporativo.objects.filter(dominio="netafim.com").update(ativo=False)
    netafim_usuario.telefone = "16999990000"
    netafim_usuario.save()  # não deve levantar


# --------------------------------------------------------------------------- tokens


def test_inativar_conta_revoga_tokens(netafim_usuario):
    registro, _ = tokens.emitir(netafim_usuario, TipoToken.REFRESH)
    netafim_usuario.situacao = SituacaoUsuario.INATIVO
    netafim_usuario.save()
    registro.refresh_from_db()
    assert registro.revogado_em is not None


def test_revogar_dispositivo_revoga_tokens_dele(netafim_usuario):
    aparelho = Dispositivo.objects.create(id_dispositivo=uuid.uuid4(), usuario=netafim_usuario)
    do_aparelho, _ = tokens.emitir(netafim_usuario, TipoToken.REFRESH, dispositivo=aparelho)
    outro, _ = tokens.emitir(netafim_usuario, TipoToken.REFRESH)
    aparelho.revogado_em = timezone.now()
    aparelho.save()
    do_aparelho.refresh_from_db()
    outro.refresh_from_db()
    assert do_aparelho.revogado_em is not None
    assert outro.revogado_em is None


def test_token_guardado_somente_como_hash(netafim_usuario):
    registro, claro = tokens.emitir(netafim_usuario, TipoToken.REFRESH)
    assert claro not in registro.token_hash
    assert registro.token_hash == tokens.hash_token(claro)


def test_revogacao_de_token_nao_volta_atras(netafim_usuario):
    registro, _ = tokens.emitir(netafim_usuario, TipoToken.REFRESH)
    TokenAcesso.objects.filter(pk=registro.pk).update(revogado_em=timezone.now())
    violacao(lambda: TokenAcesso.objects.filter(pk=registro.pk).update(revogado_em=None))


def test_validade_e_dono_do_token_sao_imutaveis(netafim_usuario, netafim_admin):
    registro, _ = tokens.emitir(netafim_usuario, TipoToken.REFRESH)
    violacao(
        lambda: TokenAcesso.objects.filter(pk=registro.pk).update(
            expira_em=registro.expira_em + timedelta(days=1)
        )
    )
    violacao(lambda: TokenAcesso.objects.filter(pk=registro.pk).update(usuario=netafim_admin))


def test_validade_maxima_de_90_dias(netafim_usuario):
    agora = timezone.now()
    violacao(
        lambda: TokenAcesso.objects.create(
            usuario=netafim_usuario,
            tipo=TipoToken.REFRESH,
            token_hash="a" * 64,
            criado_em=agora,
            expira_em=agora + timedelta(days=91),
        )
    )


# --------------------------------------------------------------------------- sincronização


def test_sync_seq_atribuido_pelo_banco_e_crescente(distribuidor, cliente):
    distribuidor.refresh_from_db()
    cliente.refresh_from_db()
    assert distribuidor.sync_seq > 0
    assert cliente.sync_seq > distribuidor.sync_seq
    anterior = cliente.sync_seq
    cliente.nome = "Fazenda Santa Clara II"
    cliente.save()
    cliente.refresh_from_db()
    assert cliente.sync_seq > anterior


def test_perfil_admin_tem_nivel_admin():
    assert perfil("netafim", NivelPerfil.ADMIN).eh_admin


def test_createsuperuser_cria_netafim_admin_e_dominio():
    from apps.contas.models import DominioEmailCorporativo

    admin = Usuario.objects.create_superuser("Chefe@NetafimBR.com", "Chefe", "Gotejo#Admin2026")
    assert admin.eh_netafim_admin and admin.is_active and admin.is_staff
    assert admin.email == "chefe@netafimbr.com"
    assert DominioEmailCorporativo.objects.filter(dominio="netafimbr.com", ativo=True).exists()
    assert admin.has_perm("qualquer") and admin.has_module_perms("contas")
