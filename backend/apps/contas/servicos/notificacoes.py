"""
E-mails transacionais do módulo de acesso (convite, redefinição de senha e
resultado do cadastro). Usa o backend de e-mail configurado no Django; em
desenvolvimento, o padrão é imprimir no console.

O envio de relatórios e comunicações de atendimento (ENVIO_COMUNICACAO) é de
outro módulo e não passa por aqui.
"""

import logging

from django.conf import settings
from django.core.mail import send_mail

from ..models import Usuario

logger = logging.getLogger(__name__)


def _enviar(usuario: Usuario, assunto: str, corpo: str) -> None:
    try:
        send_mail(assunto, corpo, settings.DEFAULT_FROM_EMAIL, [usuario.email])
    except Exception:  # e-mail não pode derrubar o fluxo de cadastro
        logger.exception("Falha ao enviar e-mail '%s' para o usuário %s", assunto, usuario.pk)


def _link(acao: str, token: str) -> str:
    return f"{settings.CONTAS['URL_APP_CONVITE']}?acao={acao}&token={token}"


def convite(usuario: Usuario, token: str) -> None:
    _enviar(
        usuario,
        "Convite de acesso ao aplicativo Netafim",
        f"Olá, {usuario.nome}.\n\n"
        "Você foi convidado para o aplicativo de pós-venda Netafim. Para definir sua "
        f"senha, abra o link no celular:\n\n{_link('convite', token)}\n\n"
        f"O link vale por {settings.CONTAS['CONVITE_DIAS']} dias.",
    )


def redefinicao_senha(usuario: Usuario, token: str) -> None:
    _enviar(
        usuario,
        "Redefinição de senha – Netafim",
        f"Olá, {usuario.nome}.\n\nPara criar uma nova senha, abra o link no celular:\n\n"
        f"{_link('redefinir-senha', token)}\n\n"
        f"O link vale por {settings.CONTAS['RESET_SENHA_HORAS']} horas. Se você não pediu "
        "a redefinição, ignore este e-mail.",
    )


def cadastro_aprovado(usuario: Usuario) -> None:
    _enviar(
        usuario,
        "Seu acesso ao aplicativo Netafim foi liberado",
        f"Olá, {usuario.nome}.\n\nSeu cadastro foi aprovado. Você já pode entrar no aplicativo "
        "com o seu e-mail e senha.",
    )


def cadastro_recusado(usuario: Usuario) -> None:
    _enviar(
        usuario,
        "Solicitação de acesso ao aplicativo Netafim",
        f"Olá, {usuario.nome}.\n\nSua solicitação de acesso não foi aprovada. Em caso de dúvida, "
        "procure o seu contato na Netafim ou no distribuidor.",
    )
