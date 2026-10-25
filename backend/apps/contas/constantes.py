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
    Códigos das permissões (tabela PERMISSAO). Cada linha da matriz do Anexo B do
    MER/DER é uma permissão. Outros módulos devem usar estas constantes em vez de
    strings soltas, por exemplo: ``usuario.tem_permissao(Permissao.ABRIR_ATENDIMENTO)``.
    """

    APROVAR_CADASTRO = "aprovar_cadastro"
    CRIAR_USUARIO = "criar_usuario"
    EDITAR_USUARIO = "editar_usuario"
    EXCLUIR_USUARIO = "excluir_usuario"
    DEFINIR_PERFIL = "definir_perfil"
    ABRIR_ATENDIMENTO = "abrir_atendimento"
    VINCULAR_TECNICO = "vincular_tecnico"
    RESPONDER_ATENDIMENTO = "responder_atendimento"
    ENCERRAR_ATENDIMENTO = "encerrar_atendimento"
    CONSULTAR_PROPRIOS_ATENDIMENTOS = "consultar_proprios_atendimentos"
    CONSULTAR_ATENDIMENTOS_EMPRESA = "consultar_atendimentos_empresa"
    CONSULTAR_ATENDIMENTOS_CLIENTES_VINCULADOS = "consultar_atendimentos_clientes_vinculados"
    CONSULTAR_TODOS_ATENDIMENTOS = "consultar_todos_atendimentos"
    DASHBOARD_GERENCIAL = "dashboard_gerencial"
    DESTINAR_ATENDIMENTO = "destinar_atendimento"
    RECLASSIFICAR_ATENDIMENTO = "reclassificar_atendimento"
    EDITAR_ATENDIMENTO_FINALIZADO = "editar_atendimento_finalizado"
