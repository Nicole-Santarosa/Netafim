"""
Quem pode ver e gerenciar quem, e classes de permissão do DRF.

Este é o ponto de integração para os outros módulos: use ``RequerPermissao`` nas
views e ``usuario.tem_permissao(Permissao.X)`` nas regras, com os códigos de
``apps.contas.constantes.Permissao``.
"""

from django.db.models import QuerySet
from rest_framework.permissions import BasePermission

from .constantes import NivelPerfil, TipoOrganizacao
from .models import Usuario


def usuarios_visiveis(ator: Usuario) -> QuerySet[Usuario]:
    """Netafim Admin vê todos; admins de organização veem a própria; demais, só a si."""
    base = Usuario.objects.select_related("perfil", "distribuidor", "cliente")
    if ator.eh_netafim_admin:
        return base
    if ator.eh_admin and ator.tipo_organizacao == TipoOrganizacao.DISTRIBUIDOR:
        return base.filter(
            tipo_organizacao=ator.tipo_organizacao, distribuidor_id=ator.distribuidor_id
        )
    if ator.eh_admin and ator.tipo_organizacao == TipoOrganizacao.CLIENTE:
        return base.filter(tipo_organizacao=ator.tipo_organizacao, cliente_id=ator.cliente_id)
    return base.filter(pk=ator.pk)


def pode_gerenciar(ator: Usuario, alvo: Usuario) -> bool:
    """
    Gestão de usuários "dentro da própria organização" (Anexo B). O Netafim Admin
    gerencia todos. Admin de distribuidor ou de cliente gerencia só os usuários de
    nível Usuário da própria organização: mudar hierarquia é exclusivo da Netafim.
    """
    if ator.pk == alvo.pk:
        return False
    if ator.eh_netafim_admin:
        return True
    return (
        ator.eh_admin
        and ator.tipo_organizacao != TipoOrganizacao.NETAFIM
        and ator.mesma_organizacao(alvo)
        and (alvo.perfil_id is None or alvo.perfil.nivel == NivelPerfil.USUARIO)
    )


class RequerPermissao(BasePermission):
    """
    Uso: ``permission_classes = [RequerPermissao.de(Permissao.CRIAR_USUARIO)]``.
    Com vários códigos, basta ter um deles.
    """

    codigos: tuple[str, ...] = ()
    message = "Você não tem permissão para esta ação."

    @classmethod
    def de(cls, *codigos: str) -> type["RequerPermissao"]:
        return type(f"RequerPermissao_{'_'.join(codigos)}", (cls,), {"codigos": codigos})

    def has_permission(self, request, view) -> bool:
        usuario = request.user
        return bool(
            usuario
            and usuario.is_authenticated
            and any(usuario.tem_permissao(codigo) for codigo in self.codigos)
        )


class SomenteNetafimAdmin(BasePermission):
    message = "Ação restrita ao Administrador Netafim."

    def has_permission(self, request, view) -> bool:
        usuario = request.user
        return bool(usuario and usuario.is_authenticated and usuario.eh_netafim_admin)
