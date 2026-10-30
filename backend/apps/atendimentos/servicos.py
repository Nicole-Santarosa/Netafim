from apps.atendimentos.models import Atendimento

# Colunas que quem abre o atendimento pode informar. Qualquer chave fora desta
# lista é descartada antes do INSERT. Ficam de fora, por grupo:
#
# - Colunas que o banco preenche sozinho no gatilho fn_atendimento_antes:
#   numero_protocolo (o servidor gera o OS-####), versao (sempre 1 na abertura)
#   e sync_seq (cursor da sincronização, sobrescrito pelo gatilho).
# - status: o gatilho recusa qualquer valor diferente de "aberto" na abertura,
#   então a gente nem deixa passar e o banco usa o padrão.
# - Datas controladas pelo servidor: data_criacao e data_atualizacao (relógio
#   do servidor), data_conclusao e data_encerramento (o gatilho preenche quando
#   o status muda), e deletado_em (exclusão lógica, não existe na abertura).
# - Destinação e responsável: destinado_a, id_distribuidor e
#   id_tecnico_responsavel. Só o Netafim Admin destina e só quem tem a
#   permissão vincular_tecnico aloca o técnico, então não nascem no pedido.
# - id_solicitante: é sempre o usuário logado (o RLS exige). Por isso não vem
#   dos dados: a view passa o usuário autenticado como primeiro argumento da
#   função, e assim ninguém abre atendimento em nome de outra pessoa mandando
#   esse campo no corpo do pedido.
# - id_atualizado_por: o gatilho define a partir do usuário logado.
# - prioridade: não é escolha de quem abre; vale o padrão do banco (media).
COLUNAS_ACEITAS_NA_ABERTURA = (
    # O UUID vem do aparelho offline. Assim reenviar o mesmo atendimento é
    # reconhecível (a idempotência fica para outra peça).
    "id_atendimento",
    # Classificação do pedido.
    "origem",
    "motivo",
    # Para quem é o pedido (quem pediu entra como argumento da função).
    "id_cliente",
    "id_propriedade",
    "id_distribuidor_origem",
    "nome_produtor",
    "nome_fazenda_informado",
    "cidade",
    "area_setor",
    "contato_solicitante",
    # Dados do projeto e do atendimento solicitado.
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
    # Cópias do cliente e da propriedade no momento da abertura (a API monta).
    "snapshot_cliente",
    "snapshot_propriedade",
    # Relógio do aparelho, que pode estar offline quando o pedido é feito.
    "data_abertura",
)


def abrir_atendimento(id_solicitante: int, dados: dict) -> Atendimento:
    """Grava um novo atendimento e devolve o objeto já com o que o banco gerou.

    Recebe o id do usuário autenticado, que quem chama (a view) deve passar a
    partir de request.user, e os dados já validados (a validação por campo é do
    serializer). O solicitante é sempre o id recebido no argumento, mesmo que
    os dados tragam outro. Esta função só filtra as chaves e insere. As regras de negócio (protocolo,
    status inicial, versão, histórico) ficam no banco, e os erros dele não são
    capturados aqui: o tradutor de apps/core/erros.py converte em HTTP.
    """
    # Mantém só as colunas permitidas; o resto (status, numero_protocolo etc.)
    # é ignorado em silêncio.
    campos = {
        coluna: dados[coluna] for coluna in COLUNAS_ACEITAS_NA_ABERTURA if coluna in dados
    }
    # O solicitante vem do argumento, nunca dos dados: sobrescreve qualquer valor
    # que tenha escapado do filtro.
    campos["id_solicitante"] = id_solicitante

    # create() faz o INSERT direto. O save() tentaria um UPDATE antes, porque o
    # pk UUID não tem valor padrão e o Django não saberia que o registro é novo.
    atendimento = Atendimento.objects.create(**campos)

    # Relê a linha para trazer o que os gatilhos preencheram: numero_protocolo,
    # versao, sync_seq e status.
    atendimento.refresh_from_db()
    return atendimento
