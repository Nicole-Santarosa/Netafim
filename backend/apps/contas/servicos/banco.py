"""Tradução dos erros do banco (RLS e gatilhos do banco do grupo) em erros de negócio da API."""

from django.db import DatabaseError, transaction

from ..excecoes import ErroNegocio, SemPermissao
from ..models import Usuario

# Trechos das mensagens dos gatilhos e funções do banco (todas com SQLSTATE 23514) e o erro de
# negócio correspondente. O texto do banco nunca vai para o cliente.
_MENSAGENS_23514 = (
    (
        "domínio de e-mail",
        "O domínio do e-mail não está autorizado para esta organização.",
        "dominio_nao_autorizado",
    ),
    (
        "somente o netafim admin",
        "Somente o Administrador Netafim faz esta alteração.",
        "sem_permissao",
    ),
    ("seu perfil não pode excluir", "Seu perfil não pode excluir usuários.", "sem_permissao"),
    ("só usuário ativo recebe sessão", "Sua conta está sem acesso liberado.", "conta_inativa"),
    (
        "refresh exige um aparelho",
        "Este aparelho teve o acesso revogado. Procure a Netafim.",
        "dispositivo_revogado",
    ),
    (
        "convite só para usuário pendente",
        "Este usuário não está aguardando convite.",
        "convite_indisponivel",
    ),
    (
        "redefinição de senha indisponível",
        "Redefinição de senha indisponível para esta conta.",
        "reset_indisponivel",
    ),
)


def sqlstate(exc: BaseException) -> str | None:
    causa = getattr(exc, "__cause__", None) or exc
    return getattr(causa, "sqlstate", None)


def constraint(exc: BaseException) -> str:
    causa = getattr(exc, "__cause__", None)
    return getattr(getattr(causa, "diag", None), "constraint_name", None) or ""


def mensagem_banco(exc: BaseException) -> str:
    causa = getattr(exc, "__cause__", None)
    return (getattr(getattr(causa, "diag", None), "message_primary", None) or str(exc)).lower()


def traduzir(exc: DatabaseError) -> Exception:
    """Devolve o erro de negócio que melhor descreve a falha do banco (ou o próprio erro)."""
    estado = sqlstate(exc)
    if estado == "42501":  # RLS ou privilégio: o ator não pode fazer isto
        return SemPermissao("Você não tem permissão para esta ação.")
    if estado == "23505" and constraint(exc) == "ux_usuario_email":
        return ErroNegocio("Já existe um usuário com este e-mail.", "email_em_uso")
    if estado == "23514":
        texto = mensagem_banco(exc)
        for trecho, mensagem, codigo in _MENSAGENS_23514:
            if trecho in texto:
                return ErroNegocio(
                    mensagem,
                    codigo,
                    status_code=403 if codigo.startswith(("conta_", "dispositivo_")) else 400,
                )
        return ErroNegocio("A operação não é permitida pelas regras do banco.", "regra_do_banco")
    if "did not affect any rows" in str(exc):  # UPDATE barrado pelo RLS (0 linhas)
        return SemPermissao("Você não tem permissão para esta ação.")
    return exc


def carregar_usuario_completo(id_usuario: int) -> Usuario | None:
    """
    Usuário com perfil, organização e permissões já carregados. Precisa do ator definido (RLS) e
    serve às respostas montadas depois de a transação acabar, quando o banco já não mostraria esses
    dados.
    """
    usuario = (
        Usuario.objects.select_related("perfil", "distribuidor", "cliente")
        .filter(pk=id_usuario)
        .first()
    )
    if usuario is not None:
        usuario.codigos_permissao()  # preenche o cache
    return usuario


def salvar(instancia, **kwargs) -> None:
    """Salva dentro de um savepoint, convertendo as recusas do banco em erro de negócio."""
    try:
        with transaction.atomic():
            instancia.save(**kwargs)
    except DatabaseError as exc:
        traduzido = traduzir(exc)
        if traduzido is exc:
            raise
        raise traduzido from exc
