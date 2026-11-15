"""
Contrato com o banco do grupo (RLS, gatilhos, funções e a matriz de permissões).

O banco é a fonte da verdade e as regras abaixo são dele. Estes testes existem para (1) avisar se o
nosso código se afastar do esquema real e (2) garantir que a API, conectada como app_django, só
enxerga e altera o que o RLS e os gatilhos permitem.
"""

import pytest
from django.db import DatabaseError, connection, transaction

from apps.contas import contexto
from apps.contas.constantes import Permissao
from apps.contas.models import (
    Cliente,
    Dispositivo,
    Distribuidor,
    DominioEmailCorporativo,
    Perfil,
    Usuario,
)
from apps.contas.models import (
    Permissao as PermissaoModelo,
)

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


# --------------------------------------------------------------------------- esquema e matriz


def test_seis_perfis_carregados(banco_sql):
    perfis = {tuple(p) for p in banco_sql.todos("select tipo_organizacao, nivel from perfil")}
    assert perfis == TODOS


def test_constantes_de_permissao_existem_no_banco(banco_sql):
    """Evita o descasamento de códigos (ex.: criar_usuarios x criar_usuario) entre código e
    banco."""
    no_banco = {c for (c,) in banco_sql.todos("select codigo from permissao")}
    assert set(MATRIZ_ANEXO_B) == no_banco


@pytest.mark.parametrize(("codigo", "perfis"), MATRIZ_ANEXO_B.items())
def test_matriz_de_permissoes_igual_ao_anexo_b(banco_sql, codigo, perfis):
    reais = {
        tuple(p)
        for p in banco_sql.todos(
            "select p.tipo_organizacao, p.nivel from perfil p "
            "join perfil_permissao pp using (id_perfil) join permissao pe using (id_permissao) "
            "where pe.codigo = %s",
            codigo,
        )
    }
    assert reais == perfis


@pytest.mark.parametrize(
    "modelo",
    [Usuario, Perfil, PermissaoModelo, Distribuidor, Cliente, Dispositivo, DominioEmailCorporativo],
)
def test_modelos_mapeiam_colunas_que_existem_no_banco(banco_sql, modelo):
    """Se o banco mudar uma coluna que o Django usa, o teste acusa antes de a API quebrar."""
    no_banco = {
        c
        for (c,) in banco_sql.todos(
            "select column_name from information_schema.columns where table_name = %s",
            modelo._meta.db_table,
        )
    }
    do_modelo = {f.column for f in modelo._meta.concrete_fields}
    assert do_modelo <= no_banco, do_modelo - no_banco


# --------------------------------------------------------------------------- RLS e privilégios


def test_api_nao_tem_acesso_a_tabela_de_tokens():
    """Tokens só passam pelas funções do banco: o papel da API não tem privilégio em
    token_acesso."""
    with pytest.raises(DatabaseError), transaction.atomic(), connection.cursor() as cursor:
        cursor.execute("select count(*) from token_acesso")


def test_sem_ator_o_rls_nega_tudo(netafim_admin):
    with transaction.atomic():
        contexto.limpar()
        assert Usuario.objects.count() == 0


def test_ator_comum_enxerga_so_a_si_mesmo(netafim_usuario, netafim_admin):
    with contexto.como_ator(netafim_usuario.pk):
        assert list(Usuario.objects.values_list("pk", flat=True)) == [netafim_usuario.pk]


def test_netafim_admin_enxerga_todos(netafim_usuario, netafim_admin):
    with contexto.como_ator(netafim_admin.pk):
        assert Usuario.objects.count() == 2


def test_contexto_fora_de_transacao_falha_alto():
    with pytest.raises(RuntimeError):
        contexto.definir_ator(1)


def test_ator_nao_vaza_para_a_transacao_seguinte(netafim_usuario):
    with contexto.como_ator(netafim_usuario.pk):
        assert Usuario.objects.count() == 1
    with transaction.atomic():
        assert Usuario.objects.count() == 0  # nova transação: sem ator


# --------------------------------------------------------------------------- gatilhos de proteção


def _tentar_update(id_ator, comando, *args):
    with pytest.raises(DatabaseError), contexto.como_ator(id_ator), connection.cursor() as cursor:
        cursor.execute(comando, args)


def test_usuario_nao_escala_o_proprio_privilegio(cliente_usuario, perfil_id):
    _tentar_update(
        cliente_usuario.pk,
        "update usuario set id_perfil = %s where id_usuario = %s",
        perfil_id("cliente", "admin"),
        cliente_usuario.pk,
    )


def test_perfil_de_outro_tipo_de_organizacao_e_barrado_pelo_banco(
    netafim_admin, cliente_usuario, perfil_id
):
    # Mesmo o Netafim Admin: a chave estrangeira composta impede perfil de outro tipo de
    # organização.
    _tentar_update(
        netafim_admin.pk,
        "update usuario set id_perfil = %s where id_usuario = %s",
        perfil_id("netafim", "admin"),
        cliente_usuario.pk,
    )


def test_contadores_de_login_nao_mudam_direto(cliente_usuario):
    _tentar_update(
        cliente_usuario.pk,
        "update usuario set falhas_login = 3, bloqueado_ate = null where id_usuario = %s",
        cliente_usuario.pk,
    )


def test_aparelho_nao_muda_de_dono_nem_volta_atras_na_revogacao(
    netafim_usuario, netafim_admin, banco_sql
):
    import uuid

    id_aparelho = uuid.uuid4()
    banco_sql.executar(
        "insert into dispositivo (id_dispositivo, id_usuario, revogado_em) values (%s, %s, now())",
        id_aparelho,
        netafim_usuario.pk,
    )
    _tentar_update(
        netafim_usuario.pk,
        "update dispositivo set revogado_em = null where id_dispositivo = %s",
        id_aparelho,
    )


# --------------------------------------------------------------------------- regras de integridade


def test_email_unico_sem_diferenciar_maiusculas(criar_usuario, perfil_id, banco_sql):
    pessoa = criar_usuario("netafim", "usuario", email="joao@netafim.com")
    with pytest.raises(Exception, match="ux_usuario_email"):
        criar_usuario("netafim", "usuario", email="JOAO@netafim.com")
    assert pessoa.pk


def test_banco_so_aceita_hash_de_senha(banco_sql):
    with pytest.raises(Exception, match="ck_usuario_password_formato"):
        banco_sql.executar(
            "insert into usuario (nome, email, password) values ('A', 'x@gmail.com', 'senha-pura')"
        )


def test_aprovacao_netafim_exige_dominio_corporativo(criar_usuario):
    with pytest.raises(Exception, match="não está autorizado"):
        criar_usuario("netafim", "usuario", email="alguem@gmail.com")


def test_dominio_de_distribuidor_vale_so_para_o_proprio(
    criar_usuario, distribuidor, outro_distribuidor
):
    with pytest.raises(Exception, match="não está autorizado"):
        criar_usuario(
            "distribuidor", "usuario", org=outro_distribuidor, email=f"a@{distribuidor.dominio}"
        )


def test_sync_seq_atribuido_pelo_banco_e_crescente(banco_sql, distribuidor, cliente):
    sync_dist = banco_sql.um(
        "select sync_seq from distribuidor where id_distribuidor = %s", distribuidor.pk
    )
    sync_cli = banco_sql.um("select sync_seq from cliente where id_cliente = %s", cliente.pk)
    assert 0 < sync_dist < sync_cli
    banco_sql.executar("update cliente set nome = 'Fazenda II' where id_cliente = %s", cliente.pk)
    assert banco_sql.um("select sync_seq from cliente where id_cliente = %s", cliente.pk) > sync_cli


def test_trilha_de_auditoria_e_imutavel(netafim_admin, banco_sql):
    banco_sql.executar("select fn_audit('logout')")
    with pytest.raises(Exception, match="somente inserção"):
        banco_sql.executar("update log_auditoria set resultado = 'falha'")
    with pytest.raises(Exception, match="somente inserção"):
        banco_sql.executar("delete from log_auditoria")
