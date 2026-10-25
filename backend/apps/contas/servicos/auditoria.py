"""
Ponto único de emissão dos eventos de segurança do módulo de acesso.

A tabela LOG_AUDITORIA (MER/DER, Anexo A.5, cadeia de hash gravada só pelo banco)
não faz parte deste módulo. Aqui o evento é registrado em log e publicado no sinal
``evento_auditoria``: quem implementar LOG_AUDITORIA só precisa conectar um receptor,
sem alterar este módulo. Os nomes de ``acao`` seguem a lista do dicionário de dados.
"""

import logging
from dataclasses import asdict, dataclass, field
from typing import Any

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
    PERMISSAO_CONCEDIDA = "permissao_concedida"
    PERMISSAO_REVOGADA = "permissao_revogada"
    ACESSO_NEGADO = "acesso_negado"
    DISPOSITIVO_REVOGADO = "dispositivo_revogado"
    CONFIGURACAO_ALTERADA = "configuracao_alterada"


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


def ip_da_requisicao(request) -> str | None:
    if request is None:
        return None
    # Atrás do Nginx, o IP real vem no X-Forwarded-For (primeiro da lista).
    encaminhado = request.META.get("HTTP_X_FORWARDED_FOR")
    if encaminhado:
        return encaminhado.split(",")[0].strip() or None
    return request.META.get("REMOTE_ADDR")
