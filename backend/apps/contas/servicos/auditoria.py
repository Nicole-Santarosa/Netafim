"""
Eventos de segurança do módulo de acesso.

A trilha oficial é a tabela ``log_auditoria`` (cadeia de hash calculada pelo banco). Quase tudo é
gravado pelo próprio banco, em gatilhos e funções (login e bloqueio em ``fn_registrar_login``;
cadastro, situação, perfil, senha e MFA em ``fn_usuario_depois``; aparelhos e permissões). O que o
banco não tem como perceber (logout, login recusado antes de ``fn_registrar_login``) a API grava
com ``gravar``, que chama ``fn_audit``.

``registrar`` só escreve no log e publica o sinal ``evento_auditoria``: é complementar.
Os nomes de ``acao`` são os aceitos pela constraint ``ck_audit_acao``.
"""

import ipaddress
import json
import logging
from dataclasses import asdict, dataclass, field
from typing import Any

from django.db import connection
from django.dispatch import Signal

logger = logging.getLogger("netafim.auditoria")

evento_auditoria = Signal()


class Acao:
    LOGIN_SUCESSO = "login_sucesso"
    LOGIN_FALHA = "login_falha"
    LOGOUT = "logout"
    CONTA_BLOQUEADA = "conta_bloqueada"
    SENHA_ALTERADA = "senha_alterada"
    CADASTRO_SOLICITADO = "cadastro_solicitado"
    CADASTRO_APROVADO = "cadastro_aprovado"
    CADASTRO_RECUSADO = "cadastro_recusado"
    SITUACAO_ALTERADA = "situacao_alterada"
    PERFIL_ALTERADO = "perfil_alterado"
    MFA_ALTERADO = "mfa_alterado"
    PERMISSAO_CONCEDIDA = "permissao_concedida"
    PERMISSAO_REVOGADA = "permissao_revogada"
    ACESSO_NEGADO = "acesso_negado"
    DISPOSITIVO_REVOGADO = "dispositivo_revogado"
    CONFIGURACAO_ALTERADA = "configuracao_alterada"
    EXPORTACAO_DADOS = "exportacao_dados"
    SOLICITACAO_TITULAR = "solicitacao_titular"
    DADOS_ANONIMIZADOS = "dados_anonimizados"


class Resultado:
    SUCESSO = "sucesso"
    FALHA = "falha"
    NEGADO = "negado"


@dataclass(frozen=True)
class EventoAuditoria:
    acao: str
    resultado: str
    id_usuario: int | None = None
    id_dispositivo: str | None = None
    entidade_tipo: str | None = None
    entidade_id: str | None = None
    ip: str | None = None
    user_agent: str | None = None
    detalhes: dict[str, Any] = field(default_factory=dict)  # nunca dados pessoais/segredos


def registrar(
    acao: str,
    resultado: str = Resultado.SUCESSO,
    *,
    usuario=None,
    request=None,
    entidade=None,
    id_dispositivo=None,
    **detalhes,
) -> EventoAuditoria:
    evento = EventoAuditoria(
        acao=acao,
        resultado=resultado,
        id_usuario=getattr(usuario, "pk", None),
        id_dispositivo=str(id_dispositivo) if id_dispositivo else None,
        entidade_tipo=entidade._meta.db_table if entidade is not None else None,
        entidade_id=str(entidade.pk) if entidade is not None else None,
        ip=ip_da_requisicao(request),
        user_agent=(request.META.get("HTTP_USER_AGENT", "")[:255] or None) if request else None,
        detalhes=detalhes,
    )
    logger.info("auditoria %s", asdict(evento))
    evento_auditoria.send(sender=EventoAuditoria, evento=evento)
    return evento


def gravar(
    acao: str,
    resultado: str = Resultado.SUCESSO,
    *,
    id_usuario: int | None = None,
    request=None,
    **detalhes,
) -> None:
    """Grava na trilha do banco, na transação em curso. ``detalhes`` nunca leva e-mail nem segredo.

    Se a gravação falhar, a exceção sobe e a operação é desfeita: não há ação sem rastro.
    """
    agente = (request.META.get("HTTP_USER_AGENT", "")[:255] or None) if request else None
    with connection.cursor() as cursor:
        cursor.execute(
            "select fn_audit(%s::text, %s::text, %s::text, %s::text, %s::jsonb, %s::integer, "
            "null, %s::inet, %s::text)",
            [
                acao,
                resultado,
                "usuario" if id_usuario is not None else None,
                str(id_usuario) if id_usuario is not None else None,
                json.dumps(detalhes) if detalhes else None,
                id_usuario,
                ip_da_requisicao(request),
                agente,
            ],
        )


def ip_da_requisicao(request) -> str | None:
    """IP do cliente, só se for um endereço válido (o banco grava como ``inet``)."""
    if request is None:
        return None
    # Atrás do Nginx, o IP real vem no X-Forwarded-For (primeiro da lista).
    encaminhado = request.META.get("HTTP_X_FORWARDED_FOR")
    candidato = (encaminhado.split(",")[0].strip() if encaminhado else None) or request.META.get(
        "REMOTE_ADDR"
    )
    try:
        return str(ipaddress.ip_address(candidato)) if candidato else None
    except ValueError:
        return None
