"""
Credenciais (hash da senha), lidas SOMENTE pela conexão ``auth`` (DT-002).

A conexão comum da API (``app_django``) não lê ``usuario.password``. O hash chega ao processo só por
duas funções do banco, executáveis apenas pelo papel ``app_auth`` (conexão ``auth``):

* ``fn_credencial_por_email(email)``: login, antes de existir ator;
* ``fn_credencial_por_id(id_usuario)``: confere a senha atual na troca de senha.

Limite, para ficar claro: o Django precisa do hash para conferir a senha, então ele ainda chega a
este processo. O ganho é que uma falha na API comum (injeção de SQL, credencial vazada) não lista os
hashes de todos os usuários.

A conexão ``auth`` não passa por ``ATOMIC_REQUESTS`` nem informa ator: as funções são
``SECURITY DEFINER`` e não dependem do RLS.
"""

from dataclasses import dataclass
from datetime import datetime

from django.contrib.auth.hashers import check_password, is_password_usable, make_password
from django.db import connections

ALIAS = "auth"

_POR_EMAIL = (
    "select id_usuario, password, situacao, falhas_login, bloqueado_ate "
    "from fn_credencial_por_email(%s)"
)
_POR_ID = (
    "select id_usuario, password, situacao, falhas_login, bloqueado_ate "
    "from fn_credencial_por_id(%s)"
)


@dataclass(frozen=True)
class Credencial:
    id_usuario: int
    hash: str
    situacao: str
    falhas_login: int
    bloqueado_ate: datetime | None

    def __repr__(self) -> str:  # o hash nunca vai para log nem para mensagem de erro
        return f"Credencial(id_usuario={self.id_usuario}, situacao={self.situacao!r})"


def _buscar(comando: str, valor) -> Credencial | None:
    with connections[ALIAS].cursor() as cursor:
        cursor.execute(comando, [valor])
        linha = cursor.fetchone()
    if linha is None:
        return None
    return Credencial(*linha)


def por_email(email: str) -> Credencial | None:
    """Conta inexistente ou excluída: ``None``. O e-mail é comparado sem diferenciar maiúsculas."""
    return _buscar(_POR_EMAIL, (email or "").strip().lower())


def por_id(id_usuario: int) -> Credencial | None:
    return _buscar(_POR_ID, int(id_usuario))


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
