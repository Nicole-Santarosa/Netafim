from rest_framework import serializers
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.atendimentos.push import LoteDoPushSerializer, processar_operacao
from apps.atendimentos.sincronizacao import LIMITE_MAXIMO, LIMITE_PADRAO, pull_atendimentos

# Maior valor que cabe no sync_seq (bigint). Um desde acima disso estouraria no
# banco, então a view recusa antes.
SYNC_SEQ_MAXIMO = 2**63 - 1


def ler_inteiro(request, campo, padrao, minimo, maximo):
    """Lê um parâmetro inteiro opcional da URL. Ausente devolve o padrão. Texto
    que não é inteiro ou valor fora da faixa responde 400 apontando o campo, no
    mesmo formato dos outros filtros da API."""
    bruto = request.query_params.get(campo)
    if bruto is None:
        return padrao
    try:
        valor = int(bruto)
    except ValueError:
        raise serializers.ValidationError({campo: ["Informe um número inteiro."]}) from None
    if not minimo <= valor <= maximo:
        raise serializers.ValidationError({campo: [f"Informe um valor de {minimo} a {maximo}."]})
    return valor


class PullView(APIView):
    """GET /sync/pull: os atendimentos alterados depois do cursor, para o app
    atualizar a cópia local. Quem decide o que cada perfil recebe é o RLS."""

    permission_classes = [IsAuthenticated]

    def get(self, request):
        desde = ler_inteiro(request, "desde", 0, 0, SYNC_SEQ_MAXIMO)
        limite = ler_inteiro(request, "limite", LIMITE_PADRAO, 1, LIMITE_MAXIMO)
        # O dicionário já tem só tipos de JSON; a view o devolve como está.
        return Response(pull_atendimentos(desde, limite))


class PushView(APIView):
    """POST /sync/push: executa um lote de operações do app e devolve um
    resultado por operação, na mesma ordem. A requisição responde 200 mesmo que
    operações falhem; o que cada uma teve está em resultados."""

    permission_classes = [IsAuthenticated]

    def post(self, request):
        # Lote malformado (vazio, acima de 50, id_operacao ruim): 400 no
        # formato padrão, e nenhuma operação é executada.
        lote = LoteDoPushSerializer(data=request.data)
        lote.is_valid(raise_exception=True)

        # O usuário é sempre o autenticado. Cada operação tem o seu savepoint
        # dentro de processar_operacao.
        resultados = [
            processar_operacao(request.user.pk, operacao)
            for operacao in lote.validated_data["operacoes"]
        ]
        return Response({"resultados": resultados})
