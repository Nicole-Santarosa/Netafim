"""
Painel administrativo do Django (apoio para a equipe técnica).

Mudanças de situação, perfil e aprovação devem passar pela API, que aplica as
regras de negócio e gera os eventos de auditoria; por isso esses campos ficam
somente leitura aqui.
"""

from django.contrib import admin

from .models import (
    Cliente,
    Dispositivo,
    Distribuidor,
    DominioEmailCorporativo,
    Perfil,
    Permissao,
    Usuario,
)


@admin.register(Usuario)
class UsuarioAdmin(admin.ModelAdmin):
    list_display = ["id_usuario", "nome", "email", "situacao", "tipo_organizacao", "perfil"]
    list_filter = ["situacao", "tipo_organizacao", "perfil"]
    search_fields = ["nome", "email"]
    fields = [
        "nome",
        "email",
        "telefone",
        "situacao",
        "tipo_organizacao",
        "perfil",
        "distribuidor",
        "cliente",
        "aprovado_por",
        "data_aprovacao",
        "last_login",
        "falhas_login",
        "bloqueado_ate",
        "consentimento_lgpd",
        "versao_termo_consentimento",
        "data_consentimento",
        "data_criacao",
    ]
    readonly_fields = [f for f in fields if f not in {"nome", "telefone"}]

    def has_add_permission(self, request):
        return False

    def has_delete_permission(self, request, obj=None):
        return False


@admin.register(Distribuidor)
class DistribuidorAdmin(admin.ModelAdmin):
    list_display = ["id_distribuidor", "nome", "regiao", "status"]
    search_fields = ["nome", "regiao"]
    readonly_fields = ["sync_seq", "data_criacao", "data_atualizacao"]


@admin.register(Cliente)
class ClienteAdmin(admin.ModelAdmin):
    list_display = ["id_cliente", "nome", "distribuidor", "status"]
    list_filter = ["status", "distribuidor"]
    search_fields = ["nome"]
    readonly_fields = ["sync_seq", "data_criacao", "data_atualizacao"]


@admin.register(DominioEmailCorporativo)
class DominioAdmin(admin.ModelAdmin):
    list_display = ["dominio", "tipo_organizacao", "distribuidor", "ativo"]
    list_filter = ["tipo_organizacao", "ativo"]


@admin.register(Perfil)
class PerfilAdmin(admin.ModelAdmin):
    list_display = ["id_perfil", "nome_perfil", "tipo_organizacao", "nivel"]

    def has_change_permission(self, request, obj=None):
        return False


@admin.register(Permissao)
class PermissaoAdmin(admin.ModelAdmin):
    list_display = ["codigo", "descricao"]

    def has_change_permission(self, request, obj=None):
        return False


@admin.register(Dispositivo)
class DispositivoAdmin(admin.ModelAdmin):
    list_display = ["id_dispositivo", "usuario", "modelo", "versao_app", "revogado_em"]
    readonly_fields = ["id_dispositivo", "usuario", "data_criacao"]
