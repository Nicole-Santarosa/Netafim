from collections import Counter

from rest_framework import serializers

from apps.atendimentos.models import (
    Atendimento,
    AtendimentoCultura,
    Cultura,
    DestinadoA,
    HistoricoAtendimento,
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
# com erro de chave estrangeira e o tradutor de erros responde 422.
#
# Além das colunas, aceita a lista opcional culturas (ids de cultura), que não é
# coluna do atendimento: o serviço grava cada id em atendimento_cultura.
#
# Chaves que não estão em fields (status, numero_protocolo, id_solicitante,
# snapshots etc.) são simplesmente ignoradas pelo DRF e não chegam ao
# validated_data.


class AberturaAtendimentoSerializer(serializers.ModelSerializer):
    # Ids das culturas do atendimento. Opcional; lista vazia é o mesmo que não
    # mandar. A validação de cada id fica em validate_culturas.
    culturas = serializers.ListField(
        child=serializers.IntegerField(), required=False, allow_empty=True
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
        # banco exige e não têm valor padrão. O resto aceita nulo.
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
        }

    def validate_culturas(self, ids):
        """Recusa ids repetidos e ids de cultura inexistente ou excluída, com o
        erro apontando o campo culturas."""
        if not ids:
            return ids

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

    def validate(self, dados):
        """Regras entre campos, iguais aos CHECKs do banco, só para o aplicativo
        receber o erro apontando o campo. O banco continua sendo a fonte da
        verdade: se algo passar daqui, ele barra do mesmo jeito.
        """
        # ck_atend_cliente_final: pedido de cliente final precisa do cliente.
        if dados.get("origem") == "cliente_final" and dados.get("id_cliente") is None:
            raise serializers.ValidationError(
                {"id_cliente": "Obrigatório quando a origem é cliente_final."}
            )
        # ck_atend_area_netafim: pedido da Netafim precisa do setor.
        if dados.get("origem") == "netafim" and not dados.get("area_setor"):
            raise serializers.ValidationError(
                {"area_setor": "Obrigatório quando a origem é netafim."}
            )
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
    outra peça.
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
    """Entrada do PATCH: a versão que o aplicativo conhece mais o que mudar.

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

    def validate(self, dados):
        # Um PATCH só com a versão não muda nada: pede ao menos uma alteração.
        if not any(coluna in dados for coluna in COLUNAS_ALTERAVEIS):
            raise serializers.ValidationError(
                "Informe ao menos uma alteração: " + ", ".join(COLUNAS_ALTERAVEIS) + "."
            )
        return dados
