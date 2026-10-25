"""Login, logout, renovação de sessão e redefinição de senha."""

from dataclasses import dataclass
from datetime import timedelta
from uuid import UUID

from django.conf import settings
from django.contrib.auth.hashers import make_password
from django.contrib.auth.password_validation import validate_password
from django.core.exceptions import ValidationError as ErroValidacaoDjango
from django.db import transaction
from django.db.models import F
from django.utils import timezone
from rest_framework.exceptions import ValidationError

from ..constantes import SituacaoUsuario, TipoToken
from ..excecoes import CredenciaisInvalidas, ErroNegocio
from ..models import Dispositivo, Usuario
from . import auditoria, notificacoes, tokens
from .auditoria import Acao, Resultado


@dataclass(frozen=True)
class DadosDispositivo:
    id_dispositivo: UUID
    modelo: str | None = None
    versao_app: str | None = None


MENSAGENS_SITUACAO = {
    SituacaoUsuario.PENDENTE: (
        "conta_pendente",
        "Seu cadastro ainda está em análise pela Netafim. Você receberá um e-mail "
        "quando o acesso for liberado.",
    ),
    SituacaoUsuario.RECUSADO: ("conta_recusada", "Sua solicitação de acesso não foi aprovada."),
    SituacaoUsuario.INATIVO: ("conta_inativa", "Sua conta está inativa. Procure o administrador."),
    SituacaoUsuario.BLOQUEADO: ("conta_bloqueada", "Sua conta está bloqueada. Procure a Netafim."),
}


def autenticar(
    email: str, senha: str, *, request=None, dispositivo: DadosDispositivo | None = None
):
    """Retorna (usuario, ParDeTokens). Erros nunca revelam se o e-mail existe."""
    email = (email or "").strip().lower()
    ip = auditoria.ip_da_requisicao(request)
    usuario = Usuario.objects.select_related("perfil").filter(email__iexact=email).first()

    if usuario is None:
        make_password(senha)  # mesmo custo de tempo de um usuário existente
        auditoria.registrar(
            Acao.LOGIN_FALHA, Resultado.FALHA, request=request, motivo="email_inexistente"
        )
        raise CredenciaisInvalidas()

    if usuario.esta_bloqueado_temporariamente:
        auditoria.registrar(
            Acao.LOGIN_FALHA,
            Resultado.NEGADO,
            usuario=usuario,
            request=request,
            motivo="bloqueio_temporario",
        )
        raise CredenciaisInvalidas()

    if not usuario.check_password(senha):
        _registrar_falha(usuario, request)
        raise CredenciaisInvalidas()

    if usuario.situacao != SituacaoUsuario.ATIVO:
        codigo, mensagem = MENSAGENS_SITUACAO[usuario.situacao]
        auditoria.registrar(
            Acao.LOGIN_FALHA, Resultado.NEGADO, usuario=usuario, request=request, motivo=codigo
        )
        raise ErroNegocio(mensagem, codigo, status_code=403)

    with transaction.atomic():
        aparelho = _registrar_dispositivo(usuario, dispositivo) if dispositivo else None
        Usuario.objects.filter(pk=usuario.pk).update(
            falhas_login=0, bloqueado_ate=None, last_login=timezone.now()
        )
        par = tokens.emitir_sessao(usuario, dispositivo=aparelho, ip=ip)

    auditoria.registrar(
        Acao.LOGIN_SUCESSO,
        usuario=usuario,
        request=request,
        id_dispositivo=aparelho.pk if aparelho else None,
    )
    usuario.refresh_from_db()
    return usuario, par


def _registrar_falha(usuario: Usuario, request) -> None:
    limite = settings.CONTAS["MAX_FALHAS_LOGIN"]
    with transaction.atomic():
        Usuario.objects.filter(pk=usuario.pk).update(falhas_login=F("falhas_login") + 1)
        usuario.refresh_from_db(fields=["falhas_login"])
        bloqueou = usuario.falhas_login >= limite
        if bloqueou:
            Usuario.objects.filter(pk=usuario.pk).update(
                falhas_login=0,
                bloqueado_ate=timezone.now()
                + timedelta(minutes=settings.CONTAS["BLOQUEIO_MINUTOS"]),
            )
    auditoria.registrar(
        Acao.LOGIN_FALHA,
        Resultado.FALHA,
        usuario=usuario,
        request=request,
        motivo="senha_incorreta",
    )
    if bloqueou:
        auditoria.registrar(
            Acao.CONTA_BLOQUEADA,
            usuario=usuario,
            request=request,
            minutos=settings.CONTAS["BLOQUEIO_MINUTOS"],
        )


def _registrar_dispositivo(usuario: Usuario, dados: DadosDispositivo) -> Dispositivo:
    aparelho = Dispositivo.objects.select_for_update().filter(pk=dados.id_dispositivo).first()
    if aparelho is None:
        return Dispositivo.objects.create(
            id_dispositivo=dados.id_dispositivo,
            usuario=usuario,
            modelo=dados.modelo,
            versao_app=dados.versao_app,
        )
    if aparelho.usuario_id != usuario.pk:
        # O app gera um novo identificador ao trocar de usuário no mesmo aparelho.
        raise ErroNegocio(
            "Este identificador de aparelho pertence a outro usuário. Gere um novo e tente "
            "novamente.",
            "dispositivo_de_outro_usuario",
            status_code=409,
        )
    if aparelho.revogado_em is not None:
        raise ErroNegocio(
            "Este aparelho teve o acesso revogado. Procure a Netafim.",
            "dispositivo_revogado",
            status_code=403,
        )
    aparelho.modelo = dados.modelo or aparelho.modelo
    aparelho.versao_app = dados.versao_app or aparelho.versao_app
    aparelho.save(update_fields=["modelo", "versao_app"])
    return aparelho


def validar_senha(senha: str, usuario: Usuario | None = None, campo: str = "nova_senha") -> None:
    try:
        validate_password(senha, usuario)
    except ErroValidacaoDjango as exc:
        raise ValidationError({campo: list(exc.messages)}) from exc


def renovar_sessao(refresh: str, *, request=None):
    return tokens.rotacionar_refresh(refresh, ip=auditoria.ip_da_requisicao(request))


def encerrar_sessao(usuario: Usuario, refresh: str, *, request=None) -> None:
    tokens.revogar(refresh)
    auditoria.registrar(Acao.LOGOUT, usuario=usuario, request=request)


def alterar_senha(usuario: Usuario, senha_atual: str, nova_senha: str, *, request=None) -> None:
    if not usuario.check_password(senha_atual):
        raise ErroNegocio("A senha atual está incorreta.", "senha_atual_incorreta")
    if senha_atual == nova_senha:
        raise ErroNegocio("A nova senha deve ser diferente da atual.", "senha_repetida")
    validar_senha(nova_senha, usuario)
    with transaction.atomic():
        usuario.set_password(nova_senha)
        usuario.exige_troca_senha = False
        usuario.save(
            update_fields=["password", "senha_alterada_em", "exige_troca_senha", "data_atualizacao"]
        )
        # Encerra todas as sessões: o app pede login com a nova senha.
        tokens.revogar_todos(usuario, tipo=TipoToken.REFRESH)
    auditoria.registrar(Acao.SENHA_ALTERADA, usuario=usuario, request=request)


def solicitar_redefinicao_senha(email: str, *, request=None) -> None:
    """Sempre silencioso: a resposta da API é a mesma exista ou não o e-mail."""
    usuario = Usuario.objects.filter(email__iexact=(email or "").strip()).first()
    if usuario is None or usuario.situacao != SituacaoUsuario.ATIVO:
        return
    tokens.revogar_todos(usuario, tipo=TipoToken.RESET_SENHA)
    _, token = tokens.emitir(usuario, TipoToken.RESET_SENHA, ip=auditoria.ip_da_requisicao(request))
    notificacoes.redefinicao_senha(usuario, token)


def definir_senha_por_token(token: str, nova_senha: str, tipo: str, *, request=None) -> Usuario:
    """Conclui convite ou redefinição de senha a partir do link enviado por e-mail."""
    with transaction.atomic():
        registro = tokens.consumir(token, tipo)
        usuario = registro.usuario
        validar_senha(nova_senha, usuario)
        usuario.set_password(nova_senha)
        usuario.exige_troca_senha = False
        usuario.falhas_login = 0
        usuario.bloqueado_ate = None
        usuario.save(
            update_fields=[
                "password",
                "senha_alterada_em",
                "exige_troca_senha",
                "falhas_login",
                "bloqueado_ate",
                "data_atualizacao",
            ]
        )
        tokens.revogar_todos(usuario, tipo=TipoToken.REFRESH)
    auditoria.registrar(Acao.SENHA_ALTERADA, usuario=usuario, request=request, via=tipo)
    return usuario
