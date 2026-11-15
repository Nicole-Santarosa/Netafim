"""Login, logout, renovação de sessão e redefinição de senha."""

from dataclasses import dataclass
from uuid import UUID

from django.contrib.auth.hashers import make_password
from django.contrib.auth.password_validation import validate_password
from django.core.exceptions import ValidationError as ErroValidacaoDjango
from django.db import DatabaseError, IntegrityError, connection, transaction
from django.utils import timezone
from rest_framework.exceptions import ValidationError

from .. import contexto
from ..constantes import SituacaoUsuario, TipoToken
from ..excecoes import CredenciaisInvalidas, ErroNegocio
from ..models import Dispositivo, Usuario
from . import auditoria, banco, notificacoes, tokens
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
    """
    Retorna (usuario, ParDeTokens). Erros nunca revelam se o e-mail existe.

    Falhas de senha e bloqueio (5 erros, 15 minutos) são contados pelo BANCO, em
    ``fn_registrar_login``: a API não consegue alterar esses campos diretamente. Como a contagem
    precisa ficar gravada mesmo quando o login falha, o erro só é levantado DEPOIS de a transação
    terminar (e a view roda fora do ATOMIC_REQUESTS, ver views_auth).
    """
    email = (email or "").strip().lower()
    ip = auditoria.ip_da_requisicao(request)
    agente = (request.META.get("HTTP_USER_AGENT", "")[:255] or None) if request else None
    erro: Exception | None = None
    usuario = par = None

    with transaction.atomic():
        contexto.limpar()
        contexto.definir_email_login(email)  # sem ator ainda: o RLS libera só a linha deste e-mail
        usuario = Usuario.objects.select_related("perfil").filter(email__iexact=email).first()

        if usuario is None:
            make_password(senha)  # mesmo custo de tempo de um usuário existente
            auditoria.registrar(
                Acao.LOGIN_FALHA, Resultado.FALHA, request=request, motivo="email_inexistente"
            )
            erro = CredenciaisInvalidas()
        else:
            senha_ok = usuario.check_password(senha)
            bloqueado = usuario.bloqueado_ate is not None and usuario.bloqueado_ate > timezone.now()
            if bloqueado or not senha_ok:
                # Bloqueado: o banco audita a tentativa e não anda o contador. Senha errada: conta.
                _registrar_login(usuario.pk, False, ip, agente)
                erro = CredenciaisInvalidas()
            elif usuario.situacao != SituacaoUsuario.ATIVO:
                codigo, mensagem = MENSAGENS_SITUACAO[usuario.situacao]
                auditoria.registrar(
                    Acao.LOGIN_FALHA,
                    Resultado.NEGADO,
                    usuario=usuario,
                    request=request,
                    motivo=codigo,
                )
                erro = ErroNegocio(mensagem, codigo, status_code=403)
            else:
                _, _, bloqueado_ate = _registrar_login(usuario.pk, True, ip, agente)
                if bloqueado_ate is not None and bloqueado_ate > timezone.now():
                    erro = CredenciaisInvalidas()  # travou entre a leitura e o registro
                else:
                    try:
                        with (
                            transaction.atomic()
                        ):  # savepoint: o erro não desfaz o registro do login
                            par = _abrir_sessao(usuario, dispositivo, ip)
                    except ErroNegocio as exc:
                        erro = exc

    if erro is not None:
        raise erro

    auditoria.registrar(Acao.LOGIN_SUCESSO, usuario=usuario, request=request)
    with transaction.atomic():
        contexto.limpar()
        contexto.definir_ator(usuario.pk)
        usuario = banco.carregar_usuario_completo(usuario.pk)
    return usuario, par


def _registrar_login(id_usuario: int, sucesso: bool, ip: str | None, agente: str | None):
    """Chama ``fn_registrar_login``; devolve (situação, falhas, bloqueado_até)."""
    with connection.cursor() as cursor:
        cursor.execute(
            "select o_situacao, o_falhas, o_bloqueado_ate "
            "from fn_registrar_login(%s, %s, %s, %s, null, null)",
            [id_usuario, sucesso, ip, agente],
        )
        return cursor.fetchone()


def _abrir_sessao(usuario: Usuario, dados: DadosDispositivo | None, ip: str | None):
    """Registra o aparelho e emite os tokens, já com o usuário como ator (RLS)."""
    if dados is None:
        raise ErroNegocio(
            "Informe o identificador do aparelho para entrar.", "dispositivo_obrigatorio"
        )
    contexto.definir_ator(usuario.pk)
    aparelho = _registrar_dispositivo(usuario, dados)
    return tokens.emitir_sessao(usuario, id_dispositivo=aparelho.pk, ip=ip)


def _registrar_dispositivo(usuario: Usuario, dados: DadosDispositivo) -> Dispositivo:
    # O RLS só mostra ao usuário os aparelhos dele.
    aparelho = Dispositivo.objects.select_for_update().filter(pk=dados.id_dispositivo).first()
    if aparelho is None:
        try:
            with transaction.atomic():
                return Dispositivo.objects.create(
                    id_dispositivo=dados.id_dispositivo,
                    usuario=usuario,
                    modelo=dados.modelo,
                    versao_app=dados.versao_app,
                )
        except IntegrityError as exc:
            if banco.sqlstate(exc) != "23505":
                raise
            # O identificador existe e é de outro usuário (o RLS o esconde): o app gera outro.
            raise ErroNegocio(
                "Este identificador de aparelho pertence a outro usuário. Gere um novo e tente "
                "novamente.",
                "dispositivo_de_outro_usuario",
                status_code=409,
            ) from exc
    if aparelho.revogado_em is not None:
        raise ErroNegocio(
            "Este aparelho teve o acesso revogado. Procure a Netafim.",
            "dispositivo_revogado",
            status_code=403,
        )
    aparelho.modelo = dados.modelo or aparelho.modelo
    aparelho.versao_app = dados.versao_app or aparelho.versao_app
    banco.salvar(aparelho, update_fields=["modelo", "versao_app"])
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
    usuario.set_password(nova_senha)
    usuario.exige_troca_senha = False
    banco.salvar(
        usuario,
        update_fields=["password", "senha_alterada_em", "exige_troca_senha", "data_atualizacao"],
    )
    # Encerra as sessões (o app pede login com a nova senha), se o banco já tiver a função.
    tokens.revogar_todos(usuario.pk, tipo=TipoToken.REFRESH)
    auditoria.registrar(Acao.SENHA_ALTERADA, usuario=usuario, request=request)


def solicitar_redefinicao_senha(email: str, *, request=None) -> None:
    """Sempre silencioso: a resposta da API é a mesma exista ou não o e-mail."""
    email = (email or "").strip().lower()
    with transaction.atomic():
        contexto.limpar()
        contexto.definir_email_login(email)
        usuario = Usuario.objects.filter(email__iexact=email).first()
        if usuario is None or usuario.situacao != SituacaoUsuario.ATIVO:
            return
        try:
            token = tokens.emitir(
                usuario.pk, TipoToken.RESET_SENHA, ip=auditoria.ip_da_requisicao(request)
            )
        except (ErroNegocio, DatabaseError):  # conta sem como redefinir: segue sem revelar
            return
        transaction.on_commit(lambda: notificacoes.redefinicao_senha(usuario, token))


def definir_senha_por_token(token: str, nova_senha: str, tipo: str, *, request=None) -> Usuario:
    """Conclui convite ou redefinição de senha a partir do link enviado por e-mail."""
    with transaction.atomic():
        contexto.limpar()
        id_usuario = tokens.consumir(token, tipo)
        contexto.definir_ator(id_usuario)  # o dono do token passa a ser o ator
        usuario = Usuario.objects.get(pk=id_usuario)
        validar_senha(nova_senha, usuario)
        usuario.set_password(nova_senha)
        usuario.exige_troca_senha = False
        banco.salvar(
            usuario,
            update_fields=[
                "password",
                "senha_alterada_em",
                "exige_troca_senha",
                "data_atualizacao",
            ],
        )
        # Ainda com o dono do token como ator: a função do banco só aceita o titular ou o Netafim
        # Admin.
        tokens.revogar_todos(usuario.pk, tipo=TipoToken.REFRESH)
    auditoria.registrar(Acao.SENHA_ALTERADA, usuario=usuario, request=request, via=tipo)
    return usuario
