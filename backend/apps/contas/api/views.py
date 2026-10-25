"""Gestão de usuários, organizações, perfis, domínios e aparelhos."""

from django.db import transaction
from django.db.models import Q
from django.shortcuts import get_object_or_404
from django.utils import timezone
from drf_spectacular.types import OpenApiTypes
from drf_spectacular.utils import OpenApiParameter, extend_schema, extend_schema_view
from rest_framework import mixins, status, viewsets
from rest_framework.decorators import action
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response

from ..constantes import Permissao as P
from ..constantes import TipoOrganizacao
from ..escopo import RequerPermissao, SomenteNetafimAdmin, pode_gerenciar, usuarios_visiveis
from ..excecoes import ErroNegocio, SemPermissao
from ..models import (
    Cliente,
    Dispositivo,
    Distribuidor,
    DominioEmailCorporativo,
    Perfil,
    PerfilPermissao,
    Permissao,
)
from ..servicos import auditoria, usuarios
from ..servicos.auditoria import Acao
from . import serializers as s

# --------------------------------------------------------------------------- usuários

FILTROS_USUARIO = [
    OpenApiParameter("situacao", OpenApiTypes.STR, description="pendente, ativo, ..."),
    OpenApiParameter("tipo_organizacao", OpenApiTypes.STR),
    OpenApiParameter("id_distribuidor", OpenApiTypes.INT),
    OpenApiParameter("id_cliente", OpenApiTypes.INT),
    OpenApiParameter("busca", OpenApiTypes.STR, description="Parte do nome ou do e-mail"),
]


@extend_schema_view(
    list=extend_schema(parameters=FILTROS_USUARIO),
    create=extend_schema(request=s.CriarUsuarioSerializer, responses={201: s.UsuarioSerializer}),
    partial_update=extend_schema(request=s.AtualizarDadosSerializer, responses=s.UsuarioSerializer),
)
@extend_schema(tags=["usuarios"])
class UsuarioViewSet(
    mixins.ListModelMixin,
    mixins.RetrieveModelMixin,
    mixins.DestroyModelMixin,
    viewsets.GenericViewSet,
):
    """
    Lista e gerencia usuários no escopo do ator: o Netafim Admin vê todos; admins de
    distribuidor e de cliente veem só a própria organização.
    """

    serializer_class = s.UsuarioSerializer
    lookup_field = "id_usuario"
    lookup_value_regex = r"\d+"
    permission_classes = [RequerPermissao.de(P.EDITAR_USUARIO, P.CRIAR_USUARIO, P.APROVAR_CADASTRO)]

    def get_queryset(self):
        qs = usuarios_visiveis(self.request.user).order_by("nome", "id_usuario")
        if self.action != "list":
            return qs
        params = self.request.query_params
        if situacao := params.get("situacao"):
            qs = qs.filter(situacao=situacao)
        if tipo := params.get("tipo_organizacao"):
            qs = qs.filter(tipo_organizacao=tipo)
        if id_distribuidor := params.get("id_distribuidor"):
            qs = qs.filter(distribuidor_id=id_distribuidor)
        if id_cliente := params.get("id_cliente"):
            qs = qs.filter(cliente_id=id_cliente)
        if busca := params.get("busca", "").strip():
            qs = qs.filter(Q(nome__icontains=busca) | Q(email__icontains=busca))
        return qs

    def _resposta(self, usuario, codigo=status.HTTP_200_OK):
        usuario = usuarios_visiveis(self.request.user).filter(pk=usuario.pk).first() or usuario
        return Response(s.UsuarioSerializer(usuario).data, status=codigo)

    def create(self, request):
        entrada = s.CriarUsuarioSerializer(data=request.data)
        entrada.is_valid(raise_exception=True)
        novo = usuarios.criar_por_admin(request.user, request=request, **entrada.validated_data)
        return self._resposta(novo, status.HTTP_201_CREATED)

    def partial_update(self, request, id_usuario=None):
        alvo = self.get_object()
        entrada = s.AtualizarDadosSerializer(data=request.data, partial=True)
        entrada.is_valid(raise_exception=True)
        usuarios.editar(request.user, alvo, request=request, **entrada.validated_data)
        return self._resposta(alvo)

    def destroy(self, request, id_usuario=None):
        usuarios.excluir(request.user, self.get_object(), request=request)
        return Response(status=status.HTTP_204_NO_CONTENT)

    @extend_schema(request=s.AprovarSerializer, responses=s.UsuarioSerializer)
    @action(detail=True, methods=["post"])
    def aprovar(self, request, id_usuario=None):
        entrada = s.AprovarSerializer(data=request.data)
        entrada.is_valid(raise_exception=True)
        alvo = usuarios.aprovar(
            request.user, self.get_object(), request=request, **entrada.validated_data
        )
        return self._resposta(alvo)

    @extend_schema(request=None, responses=s.UsuarioSerializer)
    @action(detail=True, methods=["post"])
    def recusar(self, request, id_usuario=None):
        alvo = usuarios.recusar(request.user, self.get_object(), request=request)
        return self._resposta(alvo)

    @extend_schema(request=s.SituacaoSerializer, responses=s.UsuarioSerializer)
    @action(detail=True, methods=["post"])
    def situacao(self, request, id_usuario=None):
        entrada = s.SituacaoSerializer(data=request.data)
        entrada.is_valid(raise_exception=True)
        alvo = usuarios.alterar_situacao(
            request.user, self.get_object(), entrada.validated_data["situacao"], request=request
        )
        return self._resposta(alvo)

    @extend_schema(request=s.DefinirPerfilSerializer, responses=s.UsuarioSerializer)
    @action(detail=True, methods=["post"])
    def perfil(self, request, id_usuario=None):
        entrada = s.DefinirPerfilSerializer(data=request.data)
        entrada.is_valid(raise_exception=True)
        alvo = usuarios.definir_perfil(
            request.user, self.get_object(), entrada.validated_data["id_perfil"], request=request
        )
        return self._resposta(alvo)

    @extend_schema(request=None, responses={204: None})
    @action(detail=True, methods=["post"], url_path="reenviar-convite")
    def reenviar_convite(self, request, id_usuario=None):
        usuarios.reenviar_convite(request.user, self.get_object(), request=request)
        return Response(status=status.HTTP_204_NO_CONTENT)

    @extend_schema(responses=s.DispositivoSerializer(many=True))
    @action(detail=True, methods=["get"])
    def dispositivos(self, request, id_usuario=None):
        alvo = self.get_object()
        if not (request.user.eh_netafim_admin or pode_gerenciar(request.user, alvo)):
            raise SemPermissao()
        return Response(s.DispositivoSerializer(alvo.dispositivos.all(), many=True).data)


# --------------------------------------------------------------------------- organizações


class SoftDeleteMixin:
    """DELETE marca deletado_em (tombstone propagado ao aplicativo pelo sync_seq)."""

    def perform_destroy(self, instancia):
        if instancia.usuarios.filter(deletado_em__isnull=True).exists():
            raise ErroNegocio(
                "Exclua ou mova os usuários desta organização antes.",
                "organizacao_com_usuarios",
                status_code=409,
            )
        instancia.deletado_em = timezone.now()
        instancia.save(update_fields=["deletado_em", "data_atualizacao"])


@extend_schema(tags=["organizacoes"])
class DistribuidorViewSet(SoftDeleteMixin, viewsets.ModelViewSet):
    """Netafim gerencia; usuários de distribuidor consultam o próprio."""

    serializer_class = s.DistribuidorSerializer
    queryset = Distribuidor.objects.all()
    lookup_field = "id_distribuidor"
    lookup_value_regex = r"\d+"
    http_method_names = ["get", "post", "patch", "delete", "head", "options"]

    def get_permissions(self):
        if self.action in {"list", "retrieve"}:
            return [IsAuthenticated()]
        return [SomenteNetafimAdmin()]

    def get_queryset(self):
        u = self.request.user
        qs = Distribuidor.objects.all()
        if u.tipo_organizacao == TipoOrganizacao.NETAFIM:
            return qs
        if u.tipo_organizacao == TipoOrganizacao.DISTRIBUIDOR:
            return qs.filter(pk=u.distribuidor_id)
        if u.tipo_organizacao == TipoOrganizacao.CLIENTE and u.cliente.distribuidor_id:
            return qs.filter(pk=u.cliente.distribuidor_id)
        return qs.none()


@extend_schema(tags=["organizacoes"])
class ClienteViewSet(SoftDeleteMixin, viewsets.ModelViewSet):
    """Netafim gerencia; distribuidores consultam a carteira; clientes, a própria empresa."""

    serializer_class = s.ClienteSerializer
    queryset = Cliente.objects.all()
    lookup_field = "id_cliente"
    lookup_value_regex = r"\d+"
    http_method_names = ["get", "post", "patch", "delete", "head", "options"]

    def get_permissions(self):
        if self.action in {"list", "retrieve"}:
            return [IsAuthenticated()]
        return [SomenteNetafimAdmin()]

    def get_queryset(self):
        u = self.request.user
        qs = Cliente.objects.select_related("distribuidor")
        if u.tipo_organizacao == TipoOrganizacao.NETAFIM:
            return qs
        if u.tipo_organizacao == TipoOrganizacao.DISTRIBUIDOR:
            return qs.filter(distribuidor_id=u.distribuidor_id)
        if u.tipo_organizacao == TipoOrganizacao.CLIENTE:
            return qs.filter(pk=u.cliente_id)
        return qs.none()


# --------------------------------------------------------------------------- perfis


@extend_schema(tags=["perfis"])
class PerfilViewSet(viewsets.ReadOnlyModelViewSet):
    serializer_class = s.PerfilSerializer
    queryset = Perfil.objects.prefetch_related("permissoes")
    lookup_field = "id_perfil"
    lookup_value_regex = r"\d+"
    pagination_class = None

    @extend_schema(request=s.PermissoesDoPerfilSerializer, responses=s.PerfilSerializer)
    @action(detail=True, methods=["put"], permission_classes=[SomenteNetafimAdmin])
    def permissoes(self, request, id_perfil=None):
        """Substitui as permissões do perfil (RF20 – gerenciar permissões)."""
        perfil = self.get_object()
        entrada = s.PermissoesDoPerfilSerializer(data=request.data)
        entrada.is_valid(raise_exception=True)
        novas = set(entrada.validated_data["permissoes"])
        atuais = set(perfil.permissoes.values_list("codigo", flat=True))
        if perfil.eh_admin and perfil.tipo_organizacao == TipoOrganizacao.NETAFIM:
            essenciais = {P.APROVAR_CADASTRO, P.DEFINIR_PERFIL}
            if not essenciais <= novas:
                raise ErroNegocio(
                    "O Netafim Admin não pode perder aprovar cadastros e definir perfis.",
                    "permissao_essencial",
                )
        with transaction.atomic():
            PerfilPermissao.objects.filter(
                perfil=perfil, permissao__codigo__in=atuais - novas
            ).delete()
            PerfilPermissao.objects.bulk_create(
                [
                    PerfilPermissao(perfil=perfil, permissao=p)
                    for p in Permissao.objects.filter(codigo__in=novas - atuais)
                ]
            )
        for codigo in sorted(novas - atuais):
            auditoria.registrar(
                Acao.PERMISSAO_CONCEDIDA,
                usuario=request.user,
                request=request,
                entidade=perfil,
                permissao=codigo,
            )
        for codigo in sorted(atuais - novas):
            auditoria.registrar(
                Acao.PERMISSAO_REVOGADA,
                usuario=request.user,
                request=request,
                entidade=perfil,
                permissao=codigo,
            )
        return Response(s.PerfilSerializer(Perfil.objects.get(pk=perfil.pk)).data)


@extend_schema(tags=["perfis"])
class PermissaoViewSet(viewsets.ReadOnlyModelViewSet):
    serializer_class = s.PermissaoSerializer
    queryset = Permissao.objects.all()
    lookup_field = "codigo"
    pagination_class = None


# --------------------------------------------------------------------------- configuração


@extend_schema(tags=["configuracao"])
class DominioViewSet(viewsets.ModelViewSet):
    serializer_class = s.DominioSerializer
    queryset = DominioEmailCorporativo.objects.select_related("distribuidor")
    lookup_field = "id_dominio"
    lookup_value_regex = r"\d+"
    permission_classes = [SomenteNetafimAdmin]
    http_method_names = ["get", "post", "patch", "delete", "head", "options"]

    def perform_create(self, serializer):
        dominio = serializer.save()
        auditoria.registrar(
            Acao.CONFIGURACAO_ALTERADA,
            usuario=self.request.user,
            request=self.request,
            entidade=dominio,
            operacao="criar",
        )

    def perform_update(self, serializer):
        dominio = serializer.save()
        auditoria.registrar(
            Acao.CONFIGURACAO_ALTERADA,
            usuario=self.request.user,
            request=self.request,
            entidade=dominio,
            operacao="alterar",
        )


@extend_schema(tags=["configuracao"])
class DispositivoViewSet(viewsets.GenericViewSet):
    queryset = Dispositivo.objects.all()
    lookup_field = "id_dispositivo"
    permission_classes = [SomenteNetafimAdmin]

    @extend_schema(request=None, responses=s.DispositivoSerializer)
    @action(detail=True, methods=["post"])
    def revogar(self, request, id_dispositivo=None):
        """Aparelho perdido ou roubado: revoga o acesso e os tokens dele."""
        aparelho = get_object_or_404(Dispositivo, pk=id_dispositivo)
        if aparelho.revogado_em is None:
            aparelho.revogado_em = timezone.now()
            aparelho.save(update_fields=["revogado_em"])
            auditoria.registrar(
                Acao.DISPOSITIVO_REVOGADO,
                usuario=request.user,
                request=request,
                entidade=aparelho,
                id_dispositivo=aparelho.pk,
            )
        return Response(s.DispositivoSerializer(aparelho).data)
