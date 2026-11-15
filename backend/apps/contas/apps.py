from django.apps import AppConfig
from django.conf import settings
from django.core import checks


def verificar_conexao_auth(app_configs, **kwargs):
    """Avisa enquanto o login lê o hash da senha pela conexão comum (modo legado, DT-002)."""
    if getattr(settings, "CONEXAO_AUTH_CONFIGURADA", False):
        return []
    return [
        checks.Warning(
            "A conexão 'auth' não está configurada (DB_AUTH_PASSWORD ausente): o login lê o hash "
            "da senha pela conexão comum (modo legado).",
            hint="Depois de aplicar a proposta 004 no banco, defina DB_AUTH_USER e "
            "DB_AUTH_PASSWORD (ver .env.example).",
            id="contas.W001",
        )
    ]


class ContasConfig(AppConfig):
    """Módulo de acesso: usuários, organizações, perfis, permissões e autenticação."""

    default_auto_field = "django.db.models.BigAutoField"
    name = "apps.contas"
    label = "contas"
    verbose_name = "Usuários e permissões"

    def ready(self):
        checks.register(verificar_conexao_auth)
