import pytest
from django.db import connection


@pytest.mark.django_db
def test_conexao_local_e_sem_bypass_de_rls():
    # O teste nunca pode estar falando com o Supabase.
    assert connection.settings_dict["HOST"] in ("127.0.0.1", "localhost")

    # A API conecta como app_django, que não ignora o RLS.
    with connection.cursor() as cursor:
        cursor.execute(
            "select current_user, rolbypassrls from pg_roles where rolname = current_user"
        )
        assert cursor.fetchone() == ("app_django", False)
