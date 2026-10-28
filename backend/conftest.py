import pytest


@pytest.fixture(scope="session")
def django_db_setup():
    """Não faz nada de propósito.

    O padrão do pytest-django é criar um banco novo de teste e rodar as
    migrations nele. Aqui isso não serve: o banco local já existe, montado pelos
    scripts SQL de infra/db, e o Django nunca cria tabelas (os models são
    managed = False e não há migrations). Os testes usam esse banco como está.
    """
