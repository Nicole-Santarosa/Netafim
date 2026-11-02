"""
Credenciais (hash da senha), lidas SOMENTE aqui (DT-002).

MODO SEGURO (conexão ``auth`` configurada, ``DB_AUTH_PASSWORD``): o hash chega pela conexão
``auth`` (papel ``app_auth``, sem privilégio em tabela), por duas funções do banco da proposta 004:

* ``fn_credencial_por_email(email)``: login, antes de existir ator;
* ``fn_credencial_por_id(id_usuario)``: confere a senha atual na troca de senha.

Assim a conexão comum da API (``app_django``) não precisa ler ``usuario.password``, e uma falha na
API comum (injeção de SQL, credencial vazada) não lista os hashes de todos os usuários. Limite: o
Django ainda precisa do hash para conferir a senha, então ele chega a este processo.

MODO LEGADO (``DB_AUTH_PASSWORD`` ausente): lê o hash pela conexão comum, como a API fazia antes.
Existe para a transição, para o código poder entrar antes do banco mudar sem quebrar ninguém. Só
funciona enquanto o banco ainda deixa o ``app_django`` ler ``usuario.password``. Depois do
``REVOKE`` (script 005) falha com uma mensagem clara, e nunca lê o hash de outro jeito.
``manage.py check`` avisa (``contas.W001``) enquanto estiver neste modo.

Se o modo seguro estiver configurado, mas a conexão ``auth`` ou as funções ainda não existirem
(proposta 004 não aplicada), cai no modo legado e registra um aviso. Se o banco já fechou a leitura
do hash e a conexão ``auth`` falhar, sobe o erro original da conexão ``auth``: nunca há leitura de
hash escondida.

As consultas do modo legado dependem do mesmo contexto de RLS que o ORM já exigia: o e-mail de
login (``app.login_email``) no login e o ator nas demais chamadas, dentro de uma transação.
"""

import logging
from dataclasses import dataclass
from datetime import datetime

from django.conf import settings
from django.contrib.auth.hashers import check_password, is_password_usable, make_password
from django.core.exceptions import ImproperlyConfigured
from django.db import OperationalError, ProgrammingError, connection, connections

from .banco import sqlstate

ALIAS = "auth"

log = logging.getLogger(__name__)

_COLUNAS = "id_usuario, password, situacao, falhas_login, bloqueado_ate"

# modo seguro: funções do banco, pela conexão "auth"
_POR_EMAIL = f"select {_COLUNAS} from fn_credencial_por_email(%s)"  # noqa: S608
_POR_ID = f"select {_COLUNAS} from fn_credencial_por_id(%s)"  # noqa: S608

# modo legado: tabela, pela conexão comum (mesma regra das funções: e-mail sem diferenciar
# maiúsculas e conta excluída não existe)
_LEGADO_POR_EMAIL = (
    f"select {_COLUNAS} from usuario "  # noqa: S608
    "where lower(email) = lower(%s) and deletado_em is null"
)
_LEGADO_POR_ID = (
    f"select {_COLUNAS} from usuario "  # noqa: S608
    "where id_usuario = %s and deletado_em is null"
)

_SQLSTATE_FUNCAO_INEXISTENTE = "42883"
_SQLSTATE_SEM_PRIVILEGIO = "42501"


@dataclass(frozen=True)
class Credencial:
    id_usuario: int
    hash: str
    situacao: str
    falhas_login: int
    bloqueado_ate: datetime | None

    def __repr__(self) -> str:  # o hash nunca vai para log nem para mensagem de erro
        return f"Credencial(id_usuario={self.id_usuario}, situacao={self.situacao!r})"


def modo_seguro() -> bool:
    """``True`` quando a conexão ``auth`` está configurada (``DB_AUTH_PASSWORD``)."""
    return bool(getattr(settings, "CONEXAO_AUTH_CONFIGURADA", False))


def _executar(conexao, comando: str, valor) -> Credencial | None:
    with conexao.cursor() as cursor:
        cursor.execute(comando, [valor])
        linha = cursor.fetchone()
    return None if linha is None else Credencial(*linha)


def _auth_indisponivel(exc: Exception) -> bool:
    """A conexão auth não existe ainda: papel ou senha inexistente, ou função da 004 ausente."""
    if isinstance(exc, OperationalError):
        return True
    return isinstance(exc, ProgrammingError) and sqlstate(exc) == _SQLSTATE_FUNCAO_INEXISTENTE


def _legado(comando: str, valor) -> Credencial | None:
    try:
        return _executar(connection, comando, valor)
    except ProgrammingError as exc:
        if sqlstate(exc) == _SQLSTATE_SEM_PRIVILEGIO:
            raise ImproperlyConfigured(
                "O banco já não deixa a conexão comum ler a senha (proposta 004, DT-002). "
                "Configure a conexão auth: defina DB_AUTH_USER e DB_AUTH_PASSWORD."
            ) from exc
        raise


def _buscar(seguro: str, legado: str, valor) -> Credencial | None:
    if not modo_seguro():
        return _legado(legado, valor)
    try:
        return _executar(connections[ALIAS], seguro, valor)
    except (OperationalError, ProgrammingError) as exc_auth:
        if not _auth_indisponivel(exc_auth):
            raise
        try:
            credencial = _legado(legado, valor)
        except ImproperlyConfigured:
            # O banco já fechou a leitura do hash e a conexão auth não responde: o problema é ela.
            raise exc_auth from None
        log.warning(
            "DT-002: conexao auth indisponivel (%s); usando o modo legado. Aplique a proposta 004 "
            "e confira DB_AUTH_USER e DB_AUTH_PASSWORD.",
            type(exc_auth).__name__,
        )
        return credencial


def por_email(email: str) -> Credencial | None:
    """Conta inexistente ou excluída: ``None``. O e-mail é comparado sem diferenciar maiúsculas."""
    return _buscar(_POR_EMAIL, _LEGADO_POR_EMAIL, (email or "").strip().lower())


def por_id(id_usuario: int) -> Credencial | None:
    return _buscar(_POR_ID, _LEGADO_POR_ID, int(id_usuario))


def conferir(credencial: Credencial | None, senha: str) -> bool:
    """
    Confere a senha. Sem credencial, gasta o mesmo tempo de CPU de um hash real e devolve ``False``,
    para o tempo da resposta não revelar se o e-mail existe.

    Não regrava o hash (o ``check_password`` do Django faria isso ao trocar de algoritmo): isso só
    aconteceria por uma ação explícita de troca de senha.
    """
    if credencial is None:
        make_password(senha)
        return False
    return check_password(senha, credencial.hash)


def senha_utilizavel(id_usuario: int) -> bool:
    """``False`` quando a conta ainda não definiu a senha (convite não aceito) ou não existe."""
    credencial = por_id(id_usuario)
    return credencial is not None and is_password_usable(credencial.hash)
