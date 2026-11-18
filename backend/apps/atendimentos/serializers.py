from collections import Counter

from rest_framework import serializers

from apps.atendimentos.models import (
    Atendimento,
    AtendimentoCultura,
    Cultura,
    DestinadoA,
    HistoricoAtendimento,
    SituacaoProjeto,
    Status,
)
from apps.atendimentos.servicos import COLUNAS_ALTERAVEIS

# Este serializer só recebe dados (a resposta fica para outra peça). Ele aceita
# as mesmas colunas de COLUNAS_ACEITAS_NA_ABERTURA (servicos.py), menos duas:
#
# - snapshot_cliente e snapshot_propriedade: são a cópia do cliente e da
#   propriedade no momento da abertura. Quem monta é o servidor, a partir do
#   cadastro; se o aplicativo pudesse enviar, alguém poderia gravar dados falsos.
#
# O id_distribuidor_origem entra, porque o usuário escolhe o distribuidor numa
# lista na tela. Não há validação extra aqui: se o id não existir, o banco recusa
# com erro de chave estrangeira e o tradutor de erros responde 422. Ele é sempre
# opcional (decisão da Netafim de 18/11/2026): o cliente final sem distribuidor
# padrão abre o atendimento sem ele, e o atendimento fica sem distribuidor de
# origem, direto na fila do Netafim Admin. O banco já aceita o campo nulo.
#
# Além das colunas, aceita a lista opcional culturas (ids de cultura), que não é
# coluna do atendimento: o serviço grava cada id em atendimento_cultura.
#
# Chaves que não estão em fields (status, numero_protocolo, id_solicitante,
# snapshots etc.) são simplesmente ignoradas pelo DRF e não chegam ao
# validated_data.
#
# Campos obrigatórios por origem, cultura obrigatória e os 5 motivos da
# abertura vêm do formulário de solicitação da Netafim. Essas regras são só
# da API, não do banco: deixei os CHECKs como estão e não criei migração. O
# banco continua aceitando, por exemplo, atendimento sem cidade ou com o
# motivo programacao_feiras (que o relatório de resolução usa); quem barra na
# abertura é este serializer.

# Campos que o formulário exige em cada origem, conferidos no validate. Dois
# deles já eram exigidos também pelos CHECKs do banco: id_cliente no
# cliente_final e area_setor na netafim. Os outros são só da API. A cidade vale
# nas três origens e o nome da fazenda só no cliente_final. O distribuidor de
# origem não aparece aqui: é opcional em todas as origens.
#
# O tipo_venda também não aparece, porque é obrigatório em todas as origens e
# por isso é declarado direto no campo (extra_kwargs), e não por origem.
# Texto único do erro do tipo_venda (ausente, nulo, vazio ou fora da lista).
MENSAGEM_TIPO_VENDA = "Informe o tipo de venda (projeto ou venda_regular)."

OBRIGATORIOS_POR_ORIGEM = {
    "cliente_final": ("id_cliente", "cidade", "nome_fazenda_informado"),
    "distribuidor": ("nome_produtor", "cidade"),
    "netafim": ("area_setor", "nome_produtor", "cidade"),
}


class AberturaAtendimentoSerializer(serializers.ModelSerializer):
    # Ids das culturas do atendimento. O formulário pede ao menos uma cultura em
    # todas as origens, então a lista é obrigatória e não pode vir vazia (o
    # próprio ListField responde 400 no campo culturas nos dois casos). A
    # validação de cada id fica em validate_culturas.
    culturas = serializers.ListField(
        child=serializers.IntegerField(), required=True, allow_empty=False
    )

    class Meta:
        model = Atendimento
        fields = (
            "id_atendimento",
            "origem",
            "motivo",
            "id_cliente",
            "id_propriedade",
            "id_distribuidor_origem",
            "nome_produtor",
            "nome_fazenda_informado",
            "cidade",
            "area_setor",
            "contato_solicitante",
            "numero_projeto_br",
            "situacao_projeto",
            "estado_projeto",
            "tipo_venda",
            "tipo_atendimento",
            "descricao_problema",
            "data_inicio_problema",
            "data_sugerida",
            "necessidade",
            "qtd_participantes_prevista",
            "data_abertura",
            "culturas",
        )
        # Os campos de lista fixa (origem, motivo, area_setor etc.) viram
        # ChoiceField sozinhos, porque o model usa TextChoices. Obrigatórios
        # ficam id_atendimento, origem, motivo e contato_solicitante, que o
        # banco exige e não têm valor padrão, e o tipo_venda, que o banco aceita
        # nulo mas a API exige (veja o bloco do tipo_venda mais abaixo). O resto
        # aceita nulo.
        extra_kwargs = {
            # O DRF criaria um validador de unicidade para o pk, que consulta o
            # banco. Aqui não: quem decide duplicado é o banco, e o tradutor de
            # erros responde 409.
            "id_atendimento": {"validators": []},
            # O banco tem valor padrão (agora). O DRF não enxerga o db_default e
            # marcaria o campo como obrigatório, então liberamos aqui.
            "data_abertura": {"required": False},
            # Mesma regra do CHECK ck_atend_qtd_participantes: maior que zero.
            "qtd_participantes_prevista": {"min_value": 1},
            # Lista fechada: o model tem blank=True nesses quatro campos e o DRF
            # copia isso para allow_blank, então "" passaria na validação e o
            # CHECK do banco ("nulo ou um dos valores") recusaria depois, com um
            # 422 genérico. Sem allow_blank, "" vira 400 apontando o campo. O
            # allow_null e o required continuam como o model definiu: nulo e
            # ausente seguem aceitos.
            "situacao_projeto": {"allow_blank": False},
            "estado_projeto": {"allow_blank": False},
            "tipo_atendimento": {"allow_blank": False},
            "area_setor": {"allow_blank": False},
            # O tipo de venda é obrigatório em todo atendimento, em todas as
            # origens (decisão da Netafim de 18/11/2026). Por isso, ao contrário
            # dos outros campos de lista, não aceita ausente, nulo nem "": cada
            # caso devolve 400 no campo, com mensagem em português. Os valores
            # aceitos continuam sendo os do model (projeto e venda_regular). O
            # PATCH não usa este serializer, então a edição parcial não muda.
            "tipo_venda": {
                "required": True,
                "allow_null": False,
                "allow_blank": False,
                # A mesma mensagem nos quatro casos. O "" cai em invalid_choice
                # (o ChoiceField do DRF só usa "blank" quando allow_blank é
                # verdadeiro) e um valor fora da lista também; para quem usa a
                # API não importa a diferença, o que falta é escolher um dos dois.
                "error_messages": {
                    "required": MENSAGEM_TIPO_VENDA,
                    "null": MENSAGEM_TIPO_VENDA,
                    "blank": MENSAGEM_TIPO_VENDA,
                    "invalid_choice": MENSAGEM_TIPO_VENDA,
                },
            },
        }

    def validate_culturas(self, ids):
        """Recusa ids repetidos e ids de cultura inexistente ou excluída, com o
        erro apontando o campo culturas. Lista ausente ou vazia já foi barrada
        pelo próprio campo antes de chegar aqui."""
        # Um id repetido violaria a chave composta (atendimento, cultura), e o
        # banco responderia 409, que o aplicativo lê como atendimento
        # duplicado. Por isso a repetição é barrada aqui.
        repetidos = sorted(i for i, vezes in Counter(ids).items() if vezes > 1)
        if repetidos:
            raise serializers.ValidationError(
                f"Culturas repetidas na lista: {', '.join(map(str, repetidos))}."
            )

        # A chave estrangeira só confere se o id existe; não barra cultura
        # excluída logicamente. Uma consulta só para a lista inteira.
        ativas = set(
            Cultura.objects.filter(id_cultura__in=ids, deletado_em__isnull=True).values_list(
                "id_cultura", flat=True
            )
        )
        fora_de_uso = [i for i in ids if i not in ativas]
        if fora_de_uso:
            raise serializers.ValidationError(
                f"Cultura inexistente ou fora de uso: {', '.join(map(str, fora_de_uso))}."
            )
        return ids

    def validate_motivo(self, motivo):
        # Na abertura só valem 5 motivos: o formulário de solicitação da Netafim
        # não lista Programação de Feiras, que só existe no relatório de
        # resolução. Quem garante isso é a API, porque o app não é barreira (dá
        # para chamar a rota direto). O banco continua aceitando os 6 valores no
        # CHECK, já que o relatório usa programacao_feiras; por isso a regra
        # fica aqui e não em migração.
        if motivo == "programacao_feiras":
            raise serializers.ValidationError(
                "programacao_feiras não é motivo de abertura; use um dos outros cinco."
            )
        return motivo

    def validate(self, dados):
        """Campos obrigatórios conforme a origem. Dois deles (id_cliente no
        cliente final e area_setor na Netafim) espelham CHECKs do banco; os
        outros são regra do formulário, só da API. Junto todos os que faltam
        numa resposta só, cada um apontando o campo, para o aplicativo marcar
        tudo de uma vez.
        """
        origem = dados.get("origem")
        faltando = {}
        for campo in OBRIGATORIOS_POR_ORIGEM.get(origem, ()):
            valor = dados.get(campo)
            # Ausente, nulo ou vazio contam igual. O "só espaços" também cai
            # aqui: o CharField do DRF já tira os espaços das pontas antes do
            # validate (trim_whitespace), então "   " chega como "".
            if valor is None or valor == "":
                faltando[campo] = f"Obrigatório quando a origem é {origem}."
        if faltando:
            raise serializers.ValidationError(faltando)
        return dados


class AtendimentoCriadoSerializer(serializers.ModelSerializer):
    """Resposta da abertura: só leitura. Devolve o que o aplicativo precisa
    guardar depois de abrir o atendimento, que é o protocolo gerado pelo banco e
    os campos de sincronização. Não é o serializer de detalhe, que virá em outra
    peça.
    """

    class Meta:
        model = Atendimento
        fields = (
            "id_atendimento",
            "numero_protocolo",
            "status",
            "prioridade",
            "versao",
            "sync_seq",
            "data_abertura",
        )
        # Nenhum campo é aceito na entrada: este serializer só mostra dados.
        read_only_fields = fields


class AtendimentoListaSerializer(serializers.ModelSerializer):
    """Item da lista de atendimentos: só leitura.

    A tela calcula o "Aberto há X dias" a partir de data_abertura, então o
    servidor não manda esse número pronto. O nome do produtor só vem preenchido
    quando o atendimento o informou; o nome do cliente virá do snapshot, em
    outra peça. O id_tecnico_responsavel vem nulo enquanto ninguém foi atribuído
    e deixa a fila do técnico saber, sem abrir o detalhe, de quem é cada pedido.
    """

    class Meta:
        model = Atendimento
        fields = (
            "id_atendimento",
            "numero_protocolo",
            "status",
            "motivo",
            "origem",
            "nome_produtor",
            "nome_fazenda_informado",
            "cidade",
            "id_tecnico_responsavel",
            "data_abertura",
            "data_atualizacao",
            "versao",
            "sync_seq",
        )
        # Nenhum campo é aceito na entrada: este serializer só mostra dados.
        read_only_fields = fields


class AtendimentoDetalheSerializer(serializers.ModelSerializer):
    """Detalhe de um atendimento: só leitura. A lista de campos é explícita
    (sem exclude) para uma coluna nova do banco não aparecer na API por acidente.
    deletado_em fica de fora: o atendimento excluído nem chega aqui.
    """

    # Culturas ligadas ao atendimento, como lista de {id_cultura, nome}.
    culturas = serializers.SerializerMethodField()

    class Meta:
        model = Atendimento
        fields = (
            "id_atendimento",
            "numero_protocolo",
            "origem",
            "motivo",
            "status",
            "prioridade",
            "id_solicitante",
            "id_cliente",
            "id_propriedade",
            "id_distribuidor_origem",
            "nome_produtor",
            "nome_fazenda_informado",
            "cidade",
            "area_setor",
            "destinado_a",
            "id_distribuidor",
            "id_tecnico_responsavel",
            "contato_solicitante",
            "numero_projeto_br",
            "situacao_projeto",
            "estado_projeto",
            "tipo_venda",
            "tipo_atendimento",
            "descricao_problema",
            "data_inicio_problema",
            "data_sugerida",
            "necessidade",
            "qtd_participantes_prevista",
            "snapshot_cliente",
            "snapshot_propriedade",
            "data_abertura",
            "data_conclusao",
            "data_encerramento",
            "versao",
            # Cursor da sincronização. Vem no detalhe (e na resposta do PATCH, que usa
            # o mesmo formato) para o app guardar o registro inteiro sem outra ida à
            # lista; o pull da sincronização devolve este mesmo formato.
            "sync_seq",
            "data_criacao",
            "data_atualizacao",
            "culturas",
        )
        read_only_fields = fields

    def get_culturas(self, atendimento):
        # Uma consulta só: os vínculos do atendimento entram como subconsulta
        # (o values("id_cultura") dentro do __in não é executado à parte).
        # O RLS já limita os vínculos a quem pode ver o atendimento, mas não
        # esconde linhas excluídas logicamente; por isso o deletado_em é filtrado
        # nas duas tabelas. Ordem por nome, com o id para desempatar. Sem
        # cultura, a lista sai vazia.
        vinculos = AtendimentoCultura.objects.filter(
            id_atendimento=atendimento.id_atendimento, deletado_em__isnull=True
        ).values("id_cultura")
        return list(
            Cultura.objects.filter(id_cultura__in=vinculos, deletado_em__isnull=True)
            .order_by("nome", "id_cultura")
            .values("id_cultura", "nome")
        )


class CulturaSerializer(serializers.ModelSerializer):
    """Uma cultura do catálogo, para o formulário de abertura: só leitura, com
    o id que vai na lista culturas do POST e o nome que aparece na tela."""

    class Meta:
        model = Cultura
        fields = ("id_cultura", "nome")
        read_only_fields = fields


class HistoricoAtendimentoSerializer(serializers.ModelSerializer):
    """Um evento da linha do tempo do atendimento: só leitura. A observação e o
    responsável ficam de fora por enquanto, porque a observação é texto livre e
    pode ter dado pessoal.
    """

    class Meta:
        model = HistoricoAtendimento
        fields = (
            "id_historico",
            "tipo_evento",
            "origem_evento",
            "valor_anterior",
            "valor_novo",
            "data_evento",
        )
        read_only_fields = fields


class AtendimentoAlteracaoSerializer(serializers.Serializer):
    """Entrada do PATCH: a versão que o aplicativo conhece mais o que mudar
    (status, destinação, técnico ou situação do projeto).

    Só valida formato e lista de valores. As regras de transição de status e de
    quem pode destinar, cancelar ou trocar o técnico são do banco (gatilho), e
    por isso não se repetem aqui. Chaves desconhecidas são ignoradas e não
    chegam ao validated_data.
    """

    # Versão que o aplicativo tem; a API compara com a do banco no UPDATE.
    versao = serializers.IntegerField(min_value=1)

    # As escolhas vêm dos TextChoices do model.
    status = serializers.ChoiceField(choices=Status.choices, required=False)
    destinado_a = serializers.ChoiceField(choices=DestinadoA.choices, required=False)
    id_distribuidor = serializers.IntegerField(required=False)
    # Aceita nulo para desvincular o técnico.
    id_tecnico_responsavel = serializers.IntegerField(required=False, allow_null=True)
    # Mesma lista e mesma anulabilidade do campo no POST de abertura (as escolhas
    # vêm do mesmo TextChoices do model). Ausente não muda nada; nulo limpa a
    # situação; valor fora da lista responde 400 apontando o campo. Quem pode
    # alterar é regra do gatilho do banco, por isso não há checagem de perfil aqui.
    situacao_projeto = serializers.ChoiceField(
        choices=SituacaoProjeto.choices, required=False, allow_null=True
    )

    def validate(self, dados):
        # Um PATCH só com a versão não muda nada: pede ao menos uma alteração.
        if not any(coluna in dados for coluna in COLUNAS_ALTERAVEIS):
            raise serializers.ValidationError(
                "Informe ao menos uma alteração: " + ", ".join(COLUNAS_ALTERAVEIS) + "."
            )
        return dados
