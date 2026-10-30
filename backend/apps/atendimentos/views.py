from rest_framework import serializers, status
from rest_framework.pagination import PageNumberPagination
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.atendimentos.models import Atendimento, Status
from apps.atendimentos.serializers import (
    AberturaAtendimentoSerializer,
    AtendimentoCriadoSerializer,
    AtendimentoListaSerializer,
)
from apps.atendimentos.servicos import abrir_atendimento


class PaginacaoAtendimentos(PageNumberPagination):
    """Paginação da lista: 25 por página. Fica aqui, e não na configuração
    global, para a view não depender dela. A resposta segue o formato padrão do
    DRF: count, next, previous e results."""

    page_size = 25


class AtendimentosView(APIView):
    """GET lista os atendimentos visíveis ao usuário; POST abre um novo."""

    # Declarado aqui, e não só na configuração global, para a rota continuar
    # fechada mesmo se a configuração global mudar.
    permission_classes = [IsAuthenticated]

    # authentication_classes não é definido de propósito: vale a configuração
    # global do projeto. A autenticação real (JWT) é de outro módulo, que ainda
    # não está nesta branch. É ela quem informa ao banco quem é o usuário
    # (set_config) antes de esta view rodar, e o RLS depende disso.

    def get(self, request):
        # Filtro opcional ?status=valor. Só aceita os valores do model; qualquer
        # outro responde 400 com o erro apontando o campo status.
        status_filtro = request.query_params.get("status")
        if status_filtro is not None and status_filtro not in Status.values:
            raise serializers.ValidationError(
                {"status": f"Valor inválido. Use um destes: {', '.join(Status.values)}."}
            )

        # Não há filtro por usuário nem por perfil de propósito: quem decide o
        # que cada pessoa enxerga é o RLS do banco. Aqui só tiramos os excluídos
        # logicamente e ordenamos do mais novo para o mais antigo, com o
        # id_atendimento para desempatar e a paginação ficar estável.
        consulta = Atendimento.objects.filter(deletado_em__isnull=True)
        if status_filtro is not None:
            consulta = consulta.filter(status=status_filtro)
        consulta = consulta.order_by("-data_abertura", "id_atendimento")

        paginador = PaginacaoAtendimentos()
        pagina = paginador.paginate_queryset(consulta, request, view=self)
        return paginador.get_paginated_response(
            AtendimentoListaSerializer(pagina, many=True).data
        )

    def post(self, request):
        # Valida o corpo. Se estiver errado, o DRF responde 400 com o erro por
        # campo e a função termina aqui.
        serializer = AberturaAtendimentoSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        # O solicitante é sempre o usuário autenticado, nunca algo do corpo.
        # Erros do banco não são capturados: o tradutor de apps/core/erros.py
        # converte em resposta HTTP.
        atendimento = abrir_atendimento(request.user.pk, serializer.validated_data)

        return Response(
            AtendimentoCriadoSerializer(atendimento).data,
            status=status.HTTP_201_CREATED,
        )
