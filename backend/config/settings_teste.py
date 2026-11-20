"""Configurações usadas só nos testes. Apontam para o PostgreSQL local em Docker,
nunca para o Supabase."""
import os

from django.core.exceptions import ImproperlyConfigured

# Valores do banco local de teste. Quem quiser outro banco local sobrescreve com
# as variáveis TESTE_DB_*. Como o settings.py usa setdefault ao ler o .env, o que
# for definido aqui em os.environ tem prioridade e o .env do Supabase não vale.
os.environ["SECRET_KEY"] = os.environ.get("TESTE_SECRET_KEY", "chave-so-para-testes")
os.environ["DB_HOST"] = os.environ.get("TESTE_DB_HOST", "127.0.0.1")
os.environ["DB_PORT"] = os.environ.get("TESTE_DB_PORT", "5544")
os.environ["DB_NAME"] = os.environ.get("TESTE_DB_NAME", "netafim_teste")
os.environ["DB_USER"] = os.environ.get("TESTE_DB_USER", "app_django")
os.environ["DB_PASSWORD"] = os.environ.get("TESTE_DB_PASSWORD", "senha_local_teste")

# Importa depois de definir as variáveis, porque o settings.py lê tudo na hora.
from .settings import *  # noqa: E402,F401,F403

# Trava de segurança: teste escreve e apaga dados, então só roda em banco local.
if DATABASES["default"]["HOST"] not in ("127.0.0.1", "localhost"):
    raise ImproperlyConfigured(
        "Os testes só podem rodar em um banco local (127.0.0.1 ou localhost), "
        f"mas o host configurado é {DATABASES['default']['HOST']!r}. "
        "Confira as variáveis TESTE_DB_* ."
    )
