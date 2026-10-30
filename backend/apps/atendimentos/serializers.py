from rest_framework import serializers

from apps.atendimentos.models import Atendimento

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
# Chaves que não estão em fields (status, numero_protocolo, id_solicitante,
# snapshots etc.) são simplesmente ignoradas pelo DRF e não chegam ao
# validated_data.


class AberturaAtendimentoSerializer(serializers.ModelSerializer):
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
