from django.apps import AppConfig


class ContasConfig(AppConfig):
    """Módulo de acesso: usuários, organizações, perfis, permissões e autenticação."""

    default_auto_field = "django.db.models.BigAutoField"
    name = "apps.contas"
    label = "contas"
    verbose_name = "Usuários e permissões"
