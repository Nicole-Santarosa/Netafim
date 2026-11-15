"""Domínios fechados do módulo de acesso (valores das colunas com lista fixa no MER/DER)."""

from django.db import models


class TipoOrganizacao(models.TextChoices):
    NETAFIM = "netafim", "Netafim"
    DISTRIBUIDOR = "distribuidor", "Distribuidor"
    CLIENTE = "cliente", "Cliente final"


class NivelPerfil(models.TextChoices):
    ADMIN = "admin", "Administrador"
    USUARIO = "usuario", "Usuário"


class SituacaoUsuario(models.TextChoices):
    PENDENTE = "pendente", "Pendente"
    ATIVO = "ativo", "Ativo"
    RECUSADO = "recusado", "Recusado"
    INATIVO = "inativo", "Inativo"
    BLOQUEADO = "bloqueado", "Bloqueado"


class StatusOrganizacao(models.TextChoices):
    ATIVO = "ativo", "Ativo"
    INATIVO = "inativo", "Inativo"


class TipoToken(models.TextChoices):
    REFRESH = "refresh", "Refresh"
    CONVITE = "convite", "Convite"
    RESET_SENHA = "reset_senha", "Redefinição de senha"


class Permissao:
    """
    Códigos das permissões, EXATAMENTE como estão na tabela PERMISSAO do banco do grupo (o banco é a
    fonte da verdade; as políticas de RLS e os gatilhos já usam estes mesmos códigos). Outros
    módulos devem usar estas constantes em vez de strings soltas, por exemplo:
    ``usuario.tem_permissao(Permissao.ABRIR_ATENDIMENTO)``.
    """

    APROVAR_CADASTRO = "aprovar_cadastro"
    CRIAR_USUARIO = "criar_usuarios"
    EDITAR_USUARIO = "editar_usuarios"
    EXCLUIR_USUARIO = "excluir_usuarios"
    DEFINIR_PERFIL = "definir_perfil"
    ABRIR_ATENDIMENTO = "abrir_atendimento"
    VINCULAR_TECNICO = "vincular_tecnico"
    RESPONDER_ATENDIMENTO = "responder_atendimento"
    ENCERRAR_ATENDIMENTO = "encerrar_atendimento"
    CONSULTAR_PROPRIOS_ATENDIMENTOS = "consultar_proprios"
    CONSULTAR_ATENDIMENTOS_EMPRESA = "consultar_empresa"
    CONSULTAR_ATENDIMENTOS_CLIENTES_VINCULADOS = "consultar_clientes_vinculados"
    CONSULTAR_TODOS_ATENDIMENTOS = "consultar_todos"
    DASHBOARD_GERENCIAL = "dashboard_gerencial"
    DESTINAR_ATENDIMENTO = "destinar_atendimento"
    RECLASSIFICAR_ATENDIMENTO = "reclassificar_atendimento"
    EDITAR_ATENDIMENTO_FINALIZADO = "editar_atendimento_finalizado"
