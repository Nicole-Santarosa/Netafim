"""Envio (push) das operações do aplicativo para o servidor, base de POST /sync/push.

O aplicativo guarda no aparelho as operações feitas offline (criar atendimento,
atualizar atendimento) e envia um lote delas. Este módulo valida o lote, executa
cada operação reaproveitando os mesmos serviços do POST e do PATCH isolados e
devolve um resultado por operação. Nenhuma regra de negócio é repetida aqui: a
validação por campo é dos serializers de sempre e a máquina de estados, as
permissões e o RLS continuam no banco.

Decisões do contrato (docs/api/sync.md) que este código cumpre:

- o lote tem de 1 a 50 operações, processadas na ordem em que chegaram;
- cada operação roda num savepoint próprio (transaction.atomic aninhado), então
  uma recusa, inclusive erro do banco, não derruba as outras nem deixa a
  transação da requisição abortada;
- a resposta é sempre 200 com um resultado por operação, na mesma ordem;
- criar é idempotente pelo UUID do aplicativo: se o id já existe, o servidor
  compara o conteúdo e responde "duplicado" (igual = sucesso, diferente =
  conflito);
- não existe tabela de operações processadas (migração 009 reservada, sem
  arquivo): reenviar uma atualização cuja resposta se perdeu volta como
  conflito_de_versao, e o registro_servidor deixa o app perceber que já foi
  aplicada;
- conflitos não são gravados em sync_conflito na v1;
- evidências ficam fora do push: a entidade só pode ser "atendimento".
"""

from django.db import transaction
from django.db.utils import Error as ErroDeBanco
from rest_framework import serializers
from rest_framework.exceptions import NotFound

from apps.atendimentos.excecoes import ConflitoDeVersao, SemPermissaoParaAlterar
from apps.atendimentos.models import Atendimento, AtendimentoCultura
from apps.atendimentos.serializers import (
    AberturaAtendimentoSerializer,
    AtendimentoAlteracaoSerializer,
)
from apps.atendimentos.servicos import abrir_atendimento, atualizar_atendimento
from apps.atendimentos.views import montar_detalhe

# Reaproveita o tradutor de erros do PostgreSQL do resto da API (23514 em 422,
# 23505 em 409, 42501 em 403 e assim por diante). O nome começa com sublinhado
# por ser de uso interno de apps/core/erros.py, mas o push precisa da mesma
# tradução por operação, e copiar a tabela abriria espaço para as duas
# divergirem.
from apps.core.erros import _traduzir

# Quantas operações cabem num lote.
LIMITE_DE_OPERACOES = 50

# Campos que a criação aceita do aplicativo, tirados do serializer de abertura
# (a lista fechada da decisão do duplicado). Ficam de fora o id_atendimento, que
# é a própria chave da busca, e as culturas, que não são coluna do atendimento e
# são comparadas à parte, como conjunto.
CAMPOS_COMPARADOS_NO_DUPLICADO = tuple(
    campo
    for campo in AberturaAtendimentoSerializer.Meta.fields
    if campo not in ("id_atendimento", "culturas")
)

MENSAGEM_DUPLICADO = "Já existe um registro com esses dados."


class LoteDoPushSerializer(serializers.Serializer):
    """Corpo da requisição: {"operacoes": [...]}. Só valida o que invalida o
    lote inteiro (formato, tamanho e o id_operacao de cada item). O conteúdo de
    cada operação é validado depois, uma a uma, porque uma operação inválida
    vira resultado "rejeitado" e não derruba as outras."""

    operacoes = serializers.ListField(
        child=serializers.DictField(),
        min_length=1,
        max_length=LIMITE_DE_OPERACOES,
        error_messages={
            "not_a_list": "Informe a lista de operações.",
            "empty": "Informe ao menos uma operação.",
            "min_length": "Informe ao menos uma operação.",
            "max_length": f"O lote aceita no máximo {LIMITE_DE_OPERACOES} operações.",
        },
    )

    def validate_operacoes(self, operacoes):
        """O id_operacao é o que o aplicativo usa para achar o resultado de cada
        operação. Sem ele (ou repetido) a resposta ficaria ambígua, então
        qualquer problema aqui invalida o lote, apontando a posição do item."""
        vistos = set()
        for posicao, operacao in enumerate(operacoes, start=1):
            bruto = operacao.get("id_operacao")
            try:
                if not isinstance(bruto, str):
                    raise serializers.ValidationError("não é texto")
                identificador = serializers.UUIDField().to_internal_value(bruto)
            except serializers.ValidationError:
                raise serializers.ValidationError(
                    f"Operação {posicao}: id_operacao ausente ou não é um UUID válido."
                ) from None
            if identificador in vistos:
                raise serializers.ValidationError(
                    f"Operação {posicao}: id_operacao repetido dentro do lote."
                )
            vistos.add(identificador)
        return operacoes


class OperacaoSerializer(serializers.Serializer):
    """Uma operação do lote. Cobre só o envelope (entidade, tipo, ids, versão e
    a presença do payload); o conteúdo do payload é validado pelo serializer de
    cada tipo."""

    id_operacao = serializers.UUIDField()
    # Só atendimento na v1. Outro valor vira operação rejeitada (400).
    entidade = serializers.ChoiceField(choices=["atendimento"])
    tipo = serializers.ChoiceField(choices=["criar", "atualizar"])
    id_atendimento = serializers.UUIDField()
    # Última versão que o app recebeu. Só importa em "atualizar" (a exigência
    # é conferida no validate); em "criar" a chave é descartada antes.
    versao_esperada = serializers.IntegerField(min_value=1, required=False)
    payload = serializers.DictField()

    def validate(self, dados):
        if dados["tipo"] == "atualizar" and "versao_esperada" not in dados:
            raise serializers.ValidationError(
                {"versao_esperada": ["Obrigatório quando o tipo é atualizar."]}
            )
        return dados


def processar_operacao(id_usuario, operacao):
    """Executa uma operação do lote e devolve o resultado dela, já no formato
    da resposta. Nunca levanta por erro de validação, de permissão ou de regra
    do banco: tudo isso vira um resultado. Só propaga erro de banco que a API
    não sabe traduzir (um defeito de verdade, que deve aparecer como 500)."""
    # Campos de correlação, devolvidos como vieram. O id_atendimento pode estar
    # ausente ou malformado numa operação inválida, e aí vai nulo.
    id_do_atendimento = operacao.get("id_atendimento")
    base = {
        "id_operacao": operacao["id_operacao"],
        "id_atendimento": id_do_atendimento if isinstance(id_do_atendimento, str) else None,
    }

    # Em "criar" a versao_esperada é ignorada: tira antes de validar, para um
    # valor sem sentido nela não rejeitar uma criação por causa de um campo que
    # não vale para o tipo.
    if operacao.get("tipo") == "criar":
        operacao = {c: v for c, v in operacao.items() if c != "versao_esperada"}

    envelope = OperacaoSerializer(data=operacao)
    if not envelope.is_valid():
        return {**base, **_rejeitado(400, _erro_de_campos(envelope.errors))}
    dados = envelope.validated_data

    if dados["tipo"] == "criar":
        return {**base, **_criar(id_usuario, dados)}
    return {**base, **_atualizar(dados)}


def _criar(id_usuario, operacao):
    # Mesma validação do POST /atendimentos: o payload é o corpo inteiro dele.
    serializer = AberturaAtendimentoSerializer(data=operacao["payload"])
    if not serializer.is_valid():
        return _rejeitado(400, _erro_de_campos(serializer.errors))
    dados = serializer.validated_data

    # O id do payload tem de ser o da operação. Se o app mandasse dois ids
    # diferentes, o servidor gravaria um e responderia sobre o outro.
    if dados["id_atendimento"] != operacao["id_atendimento"]:
        return _rejeitado(
            400,
            _erro_de_campos({"id_atendimento": ["Diferente do id_atendimento da operação."]}),
        )

    try:
        # Savepoint próprio: se o banco recusar, só esta operação é desfeita.
        with transaction.atomic():
            atendimento = abrir_atendimento(id_usuario, dados)
    except ErroDeBanco as erro:
        traduzido = _traduzir(erro.__cause__)
        if traduzido is None:
            # Erro que a API não sabe traduzir é defeito, não resposta de negócio.
            raise
        codigo_http, codigo, mensagem = traduzido
        if codigo == "duplicado":
            # 23505: o UUID já existe. O que responder depende do conteúdo.
            return _resolver_duplicado(dados)
        return _rejeitado(codigo_http, {"codigo": codigo, "detail": mensagem})

    # PONTO DE EXTENSÃO, NOTIFICAÇÕES (migração 015): ainda não implementado.
    # Quando a 015 existir, é aqui, depois do savepoint da criação confirmado,
    # que as funções de notificação devem ser chamadas, com falha só em log e
    # sem mudar o resultado devolvido abaixo. Este ponto fica de propósito fora
    # do bloco atomic acima: uma falha de notificação não pode desfazer a criação.

    return {"resultado": "criado", "status": 201, "registro": montar_detalhe(atendimento)}


def _atualizar(operacao):
    # Reaproveita o serializer do PATCH. A versão vem da operação, e uma
    # "versao" que o app tenha colocado dentro do payload é ignorada (a chave da
    # operação vem por último e sobrescreve).
    serializer = AtendimentoAlteracaoSerializer(
        data={**operacao["payload"], "versao": operacao["versao_esperada"]}
    )
    if not serializer.is_valid():
        return _rejeitado(400, _erro_de_campos(serializer.errors))

    mudancas = dict(serializer.validated_data)
    versao_esperada = mudancas.pop("versao")
    id_atendimento = operacao["id_atendimento"]

    try:
        with transaction.atomic():
            atendimento = atualizar_atendimento(id_atendimento, versao_esperada, mudancas)
    except NotFound:
        # Inexistente, excluído ou invisível pelo RLS: a mesma resposta nos três
        # casos, para não revelar que o id existe. No push o 404 leva codigo
        # (o PATCH isolado continua sem).
        return _rejeitado(404, {"codigo": "nao_encontrado", "detail": "Não encontrado."})
    except ConflitoDeVersao as erro:
        # Devolve também o registro atual do servidor, para o app não precisar
        # de um GET extra para mostrar o conflito (só no push, o PATCH isolado
        # continua devolvendo apenas a versao_atual).
        atual = Atendimento.objects.filter(
            id_atendimento=id_atendimento, deletado_em__isnull=True
        ).first()
        if atual is None:
            # Sumiu entre o UPDATE e esta leitura (excluído por outra transação).
            return _rejeitado(404, {"codigo": "nao_encontrado", "detail": "Não encontrado."})
        return {
            "resultado": "conflito_de_versao",
            "status": 409,
            "erro": erro.detail,
            "registro_servidor": montar_detalhe(atual),
        }
    except SemPermissaoParaAlterar as erro:
        return _rejeitado(403, erro.detail)
    except ErroDeBanco as erro:
        traduzido = _traduzir(erro.__cause__)
        if traduzido is None:
            raise
        codigo_http, codigo, mensagem = traduzido
        return _rejeitado(codigo_http, {"codigo": codigo, "detail": mensagem})

    return {"resultado": "atualizado", "status": 200, "registro": montar_detalhe(atendimento)}


def _resolver_duplicado(dados):
    """O INSERT bateu no UUID de um atendimento que já existe. Relê pelo id e
    decide: invisível ao usuário, conteúdo igual ou conteúdo diferente."""
    # A leitura passa pelo RLS. Excluído logicamente conta como não visível,
    # como em todas as leituras da API.
    atual = Atendimento.objects.filter(
        id_atendimento=dados["id_atendimento"], deletado_em__isnull=True
    ).first()

    if atual is None:
        # O id existe, mas este usuário não enxerga o atendimento. Devolver o
        # registro (ou dizer se é igual) revelaria que ele existe, então é uma
        # rejeição comum, sem registro. O app gera outro id e reenvia.
        return _rejeitado(409, {"codigo": "duplicado", "detail": MENSAGEM_DUPLICADO})

    if _conteudo_igual(dados, atual):
        # Reenvio de uma criação que já tinha dado certo (resposta perdida):
        # vale como sucesso, com o registro atual do servidor.
        return {
            "resultado": "duplicado",
            "status": 200,
            "comparacao": "igual",
            "registro": montar_detalhe(atual),
        }

    return {
        "resultado": "duplicado",
        "status": 409,
        "comparacao": "diferente",
        "erro": {"codigo": "duplicado", "detail": MENSAGEM_DUPLICADO},
        "registro_servidor": montar_detalhe(atual),
    }


def _conteudo_igual(dados, atual):
    """Compara o que o app enviou com o que está gravado.

    Só entram os campos que o serializer de criação aceita (lista fechada em
    CAMPOS_COMPARADOS_NO_DUPLICADO). O que o servidor gera (protocolo, versao,
    sync_seq, histórico, datas geradas, solicitante) fica de fora.

    - Chave ausente conta como nulo ou vazio: é igual se o gravado for nulo ou
      texto vazio, e diferente se o gravado tiver valor.
    - data_abertura só entra se o app a enviou (sem ela o banco usa now()). As
      duas datas têm fuso, e o Python compara datetimes com fuso pelo instante
      em UTC, então fusos diferentes para o mesmo instante dão igual.
    - culturas são comparadas como conjunto de ids, sem ordem.
    """
    for campo in CAMPOS_COMPARADOS_NO_DUPLICADO:
        if campo == "data_abertura" and campo not in dados:
            continue
        if _sem_vazio(dados.get(campo)) != _sem_vazio(getattr(atual, campo)):
            return False

    gravadas = set(
        AtendimentoCultura.objects.filter(
            id_atendimento=atual.id_atendimento, deletado_em__isnull=True
        ).values_list("id_cultura", flat=True)
    )
    return set(dados["culturas"]) == gravadas


def _sem_vazio(valor):
    """Trata texto vazio como nulo, para ausente, nulo e "" compararem iguais."""
    return None if valor == "" else valor


def _rejeitado(status_http, erro):
    return {"resultado": "rejeitado", "status": status_http, "erro": erro}


def _erro_de_campos(campos):
    """Erro de validação por campo, no formato do contrato: detail genérico e o
    dicionário campo para lista de mensagens, sem codigo (como o 400 do DRF)."""
    return {"detail": "Dados inválidos.", "campos": campos}
