"""
Configuração do projeto Django da API Netafim.

PROVISÓRIO: esta base foi criada junto com o módulo de acesso para que ele pudesse
rodar e ser testado. Deve ser revisada e alinhada com o grupo antes do merge.

Toda configuração sensível vem de variáveis de ambiente (ver .env.example na raiz).
"""

import os
from datetime import timedelta
from pathlib import Path

import dj_database_url
from django.core.exceptions import ImproperlyConfigured

BASE_DIR = Path(__file__).resolve().parent.parent


def env(name: str, default: str | None = None, *, required: bool = False) -> str | None:
    value = os.environ.get(name, default)
    if required and not value:
        raise ImproperlyConfigured(f"Variável de ambiente obrigatória ausente: {name}")
    return value


def env_bool(name: str, default: bool = False) -> bool:
    return str(env(name, str(default))).strip().lower() in {"1", "true", "yes", "sim"}


def env_list(name: str, default: str = "") -> list[str]:
    return [item.strip() for item in str(env(name, default)).split(",") if item.strip()]


DEBUG = env_bool("DJANGO_DEBUG", False)
SECRET_KEY = env(
    "DJANGO_SECRET_KEY", "dev-inseguro-troque-em-producao" if DEBUG else None, required=not DEBUG
)
ALLOWED_HOSTS = env_list("DJANGO_ALLOWED_HOSTS", "localhost,127.0.0.1" if DEBUG else "")
CSRF_TRUSTED_ORIGINS = env_list("DJANGO_CSRF_TRUSTED_ORIGINS")

# API pura com JWT: sem admin, sessões, mensagens nem as tabelas do Django. O esquema (tabelas,
# gatilhos, funções e RLS) é do banco do grupo; o Django nunca roda migrate nele (ver
# apps/contas/models.py). django.contrib.auth e contenttypes ficam só porque o simplejwt e o
# AbstractBaseUser os importam.
INSTALLED_APPS = [
    "django.contrib.auth",
    "django.contrib.contenttypes",
    "django.contrib.staticfiles",
    "rest_framework",
    "drf_spectacular",
    "apps.contas",
]

MIDDLEWARE = [
    "django.middleware.security.SecurityMiddleware",
    "django.middleware.common.CommonMiddleware",
    "django.middleware.clickjacking.XFrameOptionsMiddleware",
]

ROOT_URLCONF = "config.urls"
WSGI_APPLICATION = "config.wsgi.application"

TEMPLATES = [
    {
        "BACKEND": "django.template.backends.django.DjangoTemplates",
        "DIRS": [],
        "APP_DIRS": True,
        "OPTIONS": {
            "context_processors": [
                "django.template.context_processors.request",
            ],
        },
    },
]

# Banco: PostgreSQL (Supabase em produção). O módulo depende de RLS, gatilhos e funções do
# PostgreSQL. A API conecta como app_django (sem BYPASSRLS), nunca como dono do esquema nem como
# postgres. No Supabase use o Session pooler (IPv4, porta 5432): usuário
# "app_django.<ref-do-projeto>".
DATABASES = {
    "default": dj_database_url.parse(
        env("DATABASE_URL", "postgres://app_django:app_django@localhost:5432/netafim"),
        conn_max_age=int(env("DATABASE_CONN_MAX_AGE", "60")),
        conn_health_checks=True,
    )
}
# Cada requisição é uma transação: o ator informado ao banco (set_config local) só vale dentro dela.
DATABASES["default"]["ATOMIC_REQUESTS"] = True
# Pooler do Supabase: sem cursor no servidor e sem prepared statements.
DATABASES["default"]["DISABLE_SERVER_SIDE_CURSORS"] = True
DATABASES["default"].setdefault("OPTIONS", {})["prepare_threshold"] = None

AUTH_USER_MODEL = "contas.Usuario"

# O e-mail é único só entre usuários não excluídos (índice único parcial ux_usuario_email, sem
# diferenciar maiúsculas), o que o Django não reconhece sozinho.
SILENCED_SYSTEM_CHECKS = ["auth.E003"]

PASSWORD_HASHERS = [
    "django.contrib.auth.hashers.Argon2PasswordHasher",
    "django.contrib.auth.hashers.PBKDF2PasswordHasher",
]

AUTH_PASSWORD_VALIDATORS = [
    {"NAME": "django.contrib.auth.password_validation.UserAttributeSimilarityValidator"},
    {
        "NAME": "django.contrib.auth.password_validation.MinimumLengthValidator",
        "OPTIONS": {"min_length": 8},
    },
    {"NAME": "django.contrib.auth.password_validation.CommonPasswordValidator"},
    {"NAME": "django.contrib.auth.password_validation.NumericPasswordValidator"},
]

LANGUAGE_CODE = "pt-br"
TIME_ZONE = "America/Sao_Paulo"
USE_I18N = True
USE_TZ = True

STATIC_URL = "static/"
STATIC_ROOT = BASE_DIR / "staticfiles"
DEFAULT_AUTO_FIELD = "django.db.models.BigAutoField"

REST_FRAMEWORK = {
    # Sem sessão do Django não existe AnonymousUser: o anônimo é None.
    "UNAUTHENTICATED_USER": None,
    "UNAUTHENTICATED_TOKEN": None,
    "DEFAULT_AUTHENTICATION_CLASSES": [
        "apps.contas.autenticacao.AutenticacaoJWT",
    ],
    "DEFAULT_PERMISSION_CLASSES": ["rest_framework.permissions.IsAuthenticated"],
    "DEFAULT_PAGINATION_CLASS": "rest_framework.pagination.PageNumberPagination",
    "PAGE_SIZE": 25,
    "DEFAULT_SCHEMA_CLASS": "drf_spectacular.openapi.AutoSchema",
    "DEFAULT_THROTTLE_CLASSES": ["rest_framework.throttling.ScopedRateThrottle"],
    "DEFAULT_THROTTLE_RATES": {
        "login": env("THROTTLE_LOGIN", "10/min"),
        "cadastro": env("THROTTLE_CADASTRO", "5/hour"),
    },
}

# Token de acesso curto (JWT). O refresh token é opaco, guardado só como hash
# em TOKEN_ACESSO, com rotação e detecção de reuso (MER/DER, Anexo A.5).
SIMPLE_JWT = {
    "ACCESS_TOKEN_LIFETIME": timedelta(minutes=int(env("JWT_ACCESS_MINUTOS", "15"))),
    "ALGORITHM": "HS256",
    # "or": variável presente porém vazia (como no .env.example) cai na chave do Django.
    "SIGNING_KEY": env("JWT_SIGNING_KEY") or SECRET_KEY,
    "USER_ID_FIELD": "id_usuario",
    "USER_ID_CLAIM": "sub",
    "AUTH_HEADER_TYPES": ("Bearer",),
    "UPDATE_LAST_LOGIN": False,
}

CONTAS = {
    # Validades e bloqueio por tentativas são regras do BANCO (fn_emitir_token e
    # fn_registrar_login):
    # refresh 90 dias, convite 14 dias, redefinição 2 horas, bloqueio de 15 min após 5 falhas.
    # Estes dois valores só alimentam o texto dos e-mails.
    "CONVITE_DIAS": 14,
    "RESET_SENHA_HORAS": 2,
    "VERSAO_TERMO_VIGENTE": env("VERSAO_TERMO_VIGENTE", "1.0"),
    "URL_APP_CONVITE": env("URL_APP_CONVITE", "netafim://convite"),
}

SPECTACULAR_SETTINGS = {
    "TITLE": "API Netafim Pós-venda",
    "DESCRIPTION": "Contrato da API do aplicativo de atendimento técnico Netafim.",
    "VERSION": "0.1.0",
    "SERVE_INCLUDE_SCHEMA": False,
    "COMPONENT_SPLIT_REQUEST": True,
    "ENUM_NAME_OVERRIDES": {
        "TipoOrganizacaoEnum": "apps.contas.constantes.TipoOrganizacao",
        "SituacaoUsuarioEnum": "apps.contas.constantes.SituacaoUsuario",
        "NivelPerfilEnum": "apps.contas.constantes.NivelPerfil",
        "StatusOrganizacaoEnum": "apps.contas.constantes.StatusOrganizacao",
    },
}

EMAIL_BACKEND = env("EMAIL_BACKEND", "django.core.mail.backends.console.EmailBackend")
DEFAULT_FROM_EMAIL = env("DEFAULT_FROM_EMAIL", "nao-responda@netafim.local")
EMAIL_HOST = env("EMAIL_HOST", "localhost")
EMAIL_PORT = int(env("EMAIL_PORT", "587"))
EMAIL_HOST_USER = env("EMAIL_HOST_USER", "")
EMAIL_HOST_PASSWORD = env("EMAIL_HOST_PASSWORD", "")
EMAIL_USE_TLS = env_bool("EMAIL_USE_TLS", True)

if not DEBUG:
    SECURE_PROXY_SSL_HEADER = ("HTTP_X_FORWARDED_PROTO", "https")
    SECURE_SSL_REDIRECT = env_bool("DJANGO_SECURE_SSL_REDIRECT", True)
    SESSION_COOKIE_SECURE = True
    CSRF_COOKIE_SECURE = True
    SECURE_HSTS_SECONDS = int(env("DJANGO_HSTS_SECONDS", "31536000"))
    SECURE_HSTS_INCLUDE_SUBDOMAINS = True
    SECURE_CONTENT_TYPE_NOSNIFF = True

LOGGING = {
    "version": 1,
    "disable_existing_loggers": False,
    "formatters": {"padrao": {"format": "%(asctime)s %(levelname)s %(name)s %(message)s"}},
    "handlers": {"console": {"class": "logging.StreamHandler", "formatter": "padrao"}},
    "root": {"handlers": ["console"], "level": env("LOG_LEVEL", "INFO")},
}
