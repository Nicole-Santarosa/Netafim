from rest_framework import status
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.atendimentos.serializers import (
    AberturaAtendimentoSerializer,
    AtendimentoCriadoSerializer,
)
from apps.atendimentos.servicos import abrir_atendimento


class AberturaAtendimentoView(APIView):
    """POST que abre um atendimento novo."""

    # Declarado aqui, e não só na configuração global, para a rota continuar
    # fechada mesmo se a configuração global mudar.
    permission_classes = [IsAuthenticated]

    # authentication_classes não é definido de propósito: vale a configuração
    # global do projeto. A autenticação real (JWT) é de outro módulo, que ainda
    # não está nesta branch. É ela quem informa ao banco quem é o usuário
    # (set_config) antes de esta view rodar, e o RLS depende disso.

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
