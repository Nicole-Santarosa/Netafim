"""Descida (pull) dos atendimentos para o aplicativo, base da sincronização.

Segue o rascunho do contrato de sincronização (docs/api/contrato-sincronizacao-
proposta.md, seção 6). O que esta função cumpre:

- devolve os atendimentos com sync_seq maior que o cursor recebido, em ordem
  crescente de sync_seq, no máximo "limite" por chamada, com "tem_mais" para o
  app pedir a próxima página;
- inclui os excluídos logicamente (deletado_em preenchido) como tombstone, com
  "removido": true, para o app apagar a cópia local;
- devolve o registro no formato do detalhe da API (GET /atendimentos/{id}),
  com a linha do tempo na chave "historico", que é o que o app guarda em
  dadosJson;
- não filtra por usuário: o que cada um recebe é decidido pelo RLS, porque a
  consulta passa pela conexão da API com o usuário da sessão.

O que não é desta função, e fica para quem implementar o contrato:

- a rota GET /sync/pull (ler desde e limite da URL, autenticação, resposta
  HTTP) e a junção com as outras entidades (evidencia, agendamento etc.);
- o push (POST /sync/push), os conflitos e a tabela de operações processadas
  (ponto A do contrato);
- o risco do cursor: o sync_seq é tirado na escrita, não no commit, então duas
  transações podem confirmar fora de ordem e um registro ficaria para trás. A
  mitigação combinada é do cliente: pedir com sobreposição (desde = cursor - 1000)
  e aplicar de forma idempotente. Por isso a função aceita qualquer desde maior
  ou igual a zero e não guarda estado nenhum.
"""

from django.db.models import OuterRef, Subquery
from rest_framework import serializers

from apps.atendimentos.models import (
    Atendimento,
    AtendimentoCultura,
    Cultura,
    HistoricoAtendimento,
)
from apps.atendimentos.serializers import (
    AtendimentoDetalheSerializer,
    HistoricoAtendimentoSerializer,
)

LIMITE_PADRAO = 200
LIMITE_MAXIMO = 500


class _RegistroDoPull(AtendimentoDetalheSerializer):
    """O detalhe, com as mesmas chaves e o mesmo formato, mas com as culturas e
    o histórico vindos de dicionários montados antes, uma consulta para cada um
    na página inteira. O get_culturas do detalhe faz uma consulta por
    atendimento, o que numa página de 200 viraria 200 consultas.

    O histórico vai dentro do registro do atendimento, na chave "historico",
    porque a tabela historico_atendimento não tem sync_seq próprio: o app não
    teria como pedir só os eventos novos. Cada atendimento que desce já leva a
    linha do tempo inteira, no mesmo formato do GET do detalhe.

    Limitação conhecida: três eventos (reagendado, reclassificado e editado
    pós-finalização) são gravados pelo banco sem nenhum UPDATE no atendimento,
    então o sync_seq dele não sobe. Por isso esses eventos só chegam no pull
    quando o atendimento mudar por outro motivo. O app também os recebe quando
    abre o detalhe online (GET), que sempre lê o histórico direto da tabela.
    """

    historico = serializers.SerializerMethodField()

    class Meta(AtendimentoDetalheSerializer.Meta):
        fields = (*AtendimentoDetalheSerializer.Meta.fields, "historico")

    def get_culturas(self, atendimento):
        return self.context["culturas"].get(atendimento.id_atendimento, [])

    def get_historico(self, atendimento):
        # Mesmo serializer do GET do detalhe, para o formato ser idêntico.
        eventos = self.context["historico"].get(atendimento.id_atendimento, [])
        return list(HistoricoAtendimentoSerializer(eventos, many=True).data)


def pull_atendimentos(desde=0, limite=LIMITE_PADRAO):
    """Devolve os atendimentos visíveis ao usuário da sessão com sync_seq maior
    que desde, do menor para o maior.

    Não recebo o usuário como parâmetro, diferente do rascunho do contrato
    (pull_atendimentos(usuario, desde, limite)): quem decide o que cada um vê é
    o RLS, pela conexão da API com o usuário informado ao banco na requisição.
    Um parâmetro usuario ficaria sem uso, ou pior, daria a impressão de que
    filtra alguma coisa.

    A saída só tem tipos de JSON (UUID e datas já em texto, como no detalhe):

        {
          "itens": [
            {"entidade": "atendimento", "removido": false, "registro": {...}},
            {"entidade": "atendimento", "removido": true,
             "registro": {"id_atendimento": "...", "versao": 3, "sync_seq": 905}},
          ],
          "cursor": 905,      # maior sync_seq devolvido, ou o próprio desde
          "tem_mais": false,
        }

    No máximo três consultas ao banco, qualquer que seja o tamanho da página:
    uma para os atendimentos, outra para as culturas e outra para o histórico
    dos que não foram removidos (as duas últimas não acontecem se a página só
    tiver tombstones ou vier vazia). O tombstone não leva histórico.
    """
    _validar_inteiro("desde", desde, minimo=0)
    _validar_inteiro("limite", limite, minimo=1, maximo=LIMITE_MAXIMO)

    # Peço um a mais que o limite: se ele vier, sei que existe outra página sem
    # precisar de um count() à parte. Sem filtro de deletado_em de propósito:
    # o excluído também desce, como tombstone.
    linhas = list(Atendimento.objects.filter(sync_seq__gt=desde).order_by("sync_seq")[: limite + 1])
    tem_mais = len(linhas) > limite
    linhas = linhas[:limite]

    ativos = [a for a in linhas if a.deletado_em is None]
    ids_ativos = [a.id_atendimento for a in ativos]
    contexto = {
        "culturas": _culturas_por_atendimento(ids_ativos),
        "historico": _historico_por_atendimento(ids_ativos),
    }
    registros = _RegistroDoPull(ativos, many=True, context=contexto).data
    registro_por_id = {a.id_atendimento: dict(r) for a, r in zip(ativos, registros, strict=True)}

    itens = []
    for atendimento in linhas:
        removido = atendimento.deletado_em is not None
        if removido:
            # O tombstone leva só o que o app precisa para achar e descartar a
            # cópia local; o resto do registro excluído não desce.
            registro = {
                "id_atendimento": str(atendimento.id_atendimento),
                "versao": atendimento.versao,
                "sync_seq": atendimento.sync_seq,
            }
        else:
            registro = registro_por_id[atendimento.id_atendimento]
        itens.append({"entidade": "atendimento", "removido": removido, "registro": registro})

    # O cursor é o último sync_seq da página. Sem nada novo, devolvo o próprio
    # desde, para o app guardar o mesmo valor e não andar para trás.
    cursor = linhas[-1].sync_seq if linhas else desde
    return {"itens": itens, "cursor": cursor, "tem_mais": tem_mais}


def _validar_inteiro(nome, valor, minimo, maximo=None):
    """Aceita só int dentro da faixa. bool é recusado de propósito: em Python
    ele é um int, e True viraria 1 sem ninguém perceber."""
    if isinstance(valor, bool) or not isinstance(valor, int):
        raise ValueError(f"{nome} precisa ser um número inteiro.")
    if valor < minimo or (maximo is not None and valor > maximo):
        faixa = f"de {minimo} a {maximo}" if maximo is not None else f"maior ou igual a {minimo}"
        raise ValueError(f"{nome} precisa estar {faixa}; recebi {valor}.")


def _culturas_por_atendimento(ids_atendimento):
    """Culturas de vários atendimentos numa consulta só, no formato do detalhe
    ([{id_cultura, nome}] em ordem de nome e id). Como no get_culturas do
    detalhe, vínculo e cultura excluídos logicamente ficam de fora: o nome vem
    de uma subconsulta que só acha cultura sem deletado_em, e o vínculo cuja
    cultura não foi achada é descartado."""
    if not ids_atendimento:
        return {}
    nome = Cultura.objects.filter(
        id_cultura=OuterRef("id_cultura"), deletado_em__isnull=True
    ).values("nome")[:1]
    vinculos = (
        AtendimentoCultura.objects.filter(
            id_atendimento__in=ids_atendimento, deletado_em__isnull=True
        )
        .annotate(nome=Subquery(nome))
        .filter(nome__isnull=False)
        .order_by("id_atendimento", "nome", "id_cultura")
        .values("id_atendimento", "id_cultura", "nome")
    )
    culturas = {}
    for vinculo in vinculos:
        culturas.setdefault(vinculo["id_atendimento"], []).append(
            {"id_cultura": vinculo["id_cultura"], "nome": vinculo["nome"]}
        )
    return culturas


def _historico_por_atendimento(ids_atendimento):
    """Eventos de vários atendimentos numa consulta só, agrupados por
    atendimento. Dentro de cada um a ordem é a do GET do detalhe: data_evento e,
    para eventos com a mesma data, id_historico (que é estável e serve de chave
    para o app não duplicar o evento). Quem enxerga o atendimento enxerga o
    histórico dele, pela policy da tabela, então não há filtro de usuário aqui.
    Atendimento sem evento simplesmente não aparece no dicionário."""
    if not ids_atendimento:
        return {}
    eventos = HistoricoAtendimento.objects.filter(id_atendimento__in=ids_atendimento).order_by(
        "id_atendimento", "data_evento", "id_historico"
    )
    historico = {}
    for evento in eventos:
        historico.setdefault(evento.id_atendimento, []).append(evento)
    return historico
