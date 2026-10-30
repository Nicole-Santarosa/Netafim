"""
Contexto de segurança da conexão com o banco (contrato com o RLS do banco do grupo).

As políticas de RLS leem duas configurações da transação, por ``set_config(..., true)``:

* ``app.current_user_id``: o ator da requisição (id do usuário autenticado);
* ``app.login_email``: só no login e no cadastro, quando ainda não existe ator, para a política
  ``usuario_select_login`` deixar a API enxergar a linha daquele e-mail.

Como o valor é local à transação, estas funções só valem DENTRO de ``transaction.atomic()``. Fora de
uma transação o PostgreSQL descartaria o valor no mesmo instante, e a API ficaria sem ator (o RLS
nega tudo, falha fechada). Por isso falham alto em vez de funcionar pela metade.
"""

from contextlib import contextmanager

from django.db import connection, transaction


def _definir(nome: str, valor: str) -> None:
    if not connection.in_atomic_block:
        raise RuntimeError(
            "O contexto do banco só vale dentro de uma transação (ATOMIC_REQUESTS ou "
            "transaction.atomic())."
        )
    with connection.cursor() as cursor:
        cursor.execute("select set_config(%s, %s, true)", [nome, valor])


def definir_ator(id_usuario: int | None) -> None:
    _definir("app.current_user_id", "" if id_usuario is None else str(int(id_usuario)))


def definir_email_login(email: str | None) -> None:
    _definir("app.login_email", (email or "").strip().lower())


def limpar() -> None:
    """Zera o ator e o e-mail de login (entrada de um serviço que define o próprio contexto)."""
    if not connection.in_atomic_block:
        raise RuntimeError("O contexto do banco só vale dentro de uma transação.")
    with connection.cursor() as cursor:
        cursor.execute(
            "select set_config('app.current_user_id', '', true), "
            "set_config('app.login_email', '', true)"
        )


@contextmanager
def como_ator(id_usuario: int | None):
    """Abre uma transação já com o ator informado."""
    with transaction.atomic():
        limpar()
        definir_ator(id_usuario)
        yield
