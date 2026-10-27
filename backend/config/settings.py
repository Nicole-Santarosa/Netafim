"""Configurações do projeto. Tudo que muda por ambiente vem de variáveis de ambiente."""
import os
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent


def carregar_env(caminho):
    """Lê o arquivo .env (CHAVE=valor por linha) e joga em os.environ.

    Variáveis que já existem no ambiente têm prioridade sobre o arquivo, assim
    um servidor ou o CI conseguem sobrescrever sem mexer no .env.
    """
    if not caminho.exists():
        return
    for linha in caminho.read_text(encoding="utf-8").splitlines():
        linha = linha.strip()
        if not linha or linha.startswith("#") or "=" not in linha:
            continue
        chave, valor = linha.split("=", 1)
        os.environ.setdefault(chave.strip(), valor.strip().strip("'\""))


carregar_env(BASE_DIR / ".env")

# Sem valor padrão de propósito: se faltar, o projeto não sobe com segredo fraco.
SECRET_KEY = os.environ["SECRET_KEY"]
DEBUG = os.environ.get("DEBUG", "false").lower() == "true"
ALLOWED_HOSTS = [h for h in os.environ.get("ALLOWED_HOSTS", "").split(",") if h]

# Só o rest_framework: admin, auth, contenttypes e sessions criariam tabelas,
# e as tabelas são todas dos scripts SQL em infra/db.
INSTALLED_APPS = [
    "rest_framework",
    "apps.atendimentos",
]

MIDDLEWARE = [
    "django.middleware.security.SecurityMiddleware",
    "django.middleware.common.CommonMiddleware",
]

ROOT_URLCONF = "config.urls"
WSGI_APPLICATION = "config.wsgi.application"

# A API conecta sempre como app_django (sem bypass de RLS), nunca como postgres.
DATABASES = {
    "default": {
        "ENGINE": "django.db.backends.postgresql",
        "NAME": os.environ.get("DB_NAME", "postgres"),
        "USER": os.environ.get("DB_USER", "app_django"),
        "PASSWORD": os.environ["DB_PASSWORD"],
        "HOST": os.environ["DB_HOST"],
        "PORT": os.environ.get("DB_PORT", "5432"),
        # Cada requisição é uma transação: o set_config local do usuário logado
        # só vale dentro dela.
        "ATOMIC_REQUESTS": True,
        # O pooler do Supabase em modo transação não aceita cursor no servidor
        # nem prepared statements.
        "DISABLE_SERVER_SIDE_CURSORS": True,
        "OPTIONS": {"prepare_threshold": None},
    }
}

LANGUAGE_CODE = "pt-br"
TIME_ZONE = "America/Sao_Paulo"
USE_TZ = True

DEFAULT_AUTO_FIELD = "django.db.models.BigAutoField"

REST_FRAMEWORK = {
    # Só JSON, na entrada e na saída.
    "DEFAULT_RENDERER_CLASSES": ["rest_framework.renderers.JSONRenderer"],
    "DEFAULT_PARSER_CLASSES": ["rest_framework.parsers.JSONParser"],
    # Sem django.contrib.auth não existe AnonymousUser, então o anônimo é None.
    "UNAUTHENTICATED_USER": None,
    "UNAUTHENTICATED_TOKEN": None,
    # Autenticação fica para a próxima etapa; sem isso o DRF tentaria sessão e Basic.
    "DEFAULT_AUTHENTICATION_CLASSES": [],
}
