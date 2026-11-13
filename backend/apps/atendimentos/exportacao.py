"""Parte do módulo de atendimentos na exportação de dados do titular (LGPD:
acesso e portabilidade).

A rota e a junção com os dados de conta ficam no módulo de contas; ele chama
exportar_atendimentos(usuario) e junta o dicionário devolvido ao resto da
exportação. Esta função só lê: não grava auditoria nem muda nada no banco.

Como a leitura passa pela conexão da API (role app_django), o RLS continua
valendo com o usuário da sessão. O filtro pelo titular, porém, é feito aqui na
consulta: assim o Netafim Admin, que enxerga todos os atendimentos, exporta só
o que é do titular, e nunca o de outro usuário.
"""

from django.db.models import OuterRef, Q, Subquery

from apps.atendimentos.models import Atendimento, HistoricoAtendimento

# Dados do atendimento que descrevem o serviço e não identificam ninguém. Vão
# em todo atendimento ligado ao titular, seja qual for o papel dele.
CAMPOS_DO_SERVICO = (
    "id_atendimento",
    "numero_protocolo",
    "origem",
    "motivo",
    "status",
    "prioridade",
    "numero_projeto_br",
    "situacao_projeto",
    "estado_projeto",
    "tipo_venda",
    "tipo_atendimento",
    "data_abertura",
    "data_conclusao",
    "data_encerramento",
    "data_criacao",
    "data_atualizacao",
)

# O que quem abriu o atendimento digitou no pedido. Só sai quando o titular é o
# solicitante: é dado que ele mesmo informou. Para o técnico, por exemplo, isso
# é dado de outra pessoa (o contato e o problema relatados pelo cliente).
CAMPOS_INFORMADOS_PELO_SOLICITANTE = (
    "contato_solicitante",
    "nome_produtor",
    "nome_fazenda_informado",
    "cidade",
    "area_setor",
    "descricao_problema",
    "data_inicio_problema",
    "data_sugerida",
    "necessidade",
    "qtd_participantes_prevista",
)

# Ficam fora de propósito:
# - snapshot_cliente e snapshot_propriedade: cópia do cadastro do cliente e da
#   fazenda, montada pelo servidor. São dados do cliente, não do titular.
# - ids de outros usuários, do cliente, da propriedade e dos distribuidores:
#   no lugar deles a saída diz o papel do titular em cada atendimento.
# - versao e sync_seq: controle interno do app e da sincronização.
# - observacao do histórico: texto livre que pode citar terceiros.

# Colunas que guardam o usuário no atendimento e o nome do papel na saída.
PAPEIS = (
    ("id_solicitante", "solicitante"),
    ("id_tecnico_responsavel", "tecnico_responsavel"),
    ("id_atualizado_por", "ultimo_a_alterar"),
)


def exportar_atendimentos(usuario):
    """Devolve os atendimentos e os eventos do histórico ligados ao titular.

    usuario pode ser o objeto do usuário (qualquer objeto com pk, como o
    request.user que as views já usam) ou o id inteiro.

    A saída é um dicionário só com tipos de JSON (texto, número, nulo, listas e
    dicionários): UUID e datas já vêm como texto ISO 8601. Duas consultas ao
    banco, qualquer que seja o volume:

        {
          "id_usuario": 12,
          "atendimentos": [...],   # por data_abertura e numero_protocolo
          "historico": [...],      # por data_evento e id_historico
        }
    """
    id_usuario = _id_do_usuario(usuario)

    # Atendimentos em que o titular aparece em qualquer das colunas de usuário.
    # Os excluídos logicamente entram também (com excluido_em preenchido): o
    # registro continua guardado durante a retenção, então o titular tem direito
    # de saber que ele existe.
    vinculo = Q()
    for coluna, _ in PAPEIS:
        vinculo |= Q(**{coluna: id_usuario})
    colunas = (
        *CAMPOS_DO_SERVICO,
        *CAMPOS_INFORMADOS_PELO_SOLICITANTE,
        *(coluna for coluna, _ in PAPEIS),
        "deletado_em",
    )
    linhas = (
        Atendimento.objects.filter(vinculo)
        .order_by("data_abertura", "numero_protocolo")
        .values(*colunas)
    )
    atendimentos = [_atendimento_exportado(linha, id_usuario) for linha in linhas]

    # Eventos do histórico em que o titular foi o responsável. O protocolo vem
    # numa subconsulta, na mesma ida ao banco, para cada evento dizer de qual
    # atendimento é sem precisar de uma consulta por evento.
    protocolo = Atendimento.objects.filter(
        id_atendimento=OuterRef("id_atendimento")
    ).values("numero_protocolo")[:1]
    eventos = (
        HistoricoAtendimento.objects.filter(id_usuario_responsavel=id_usuario)
        .annotate(numero_protocolo=Subquery(protocolo))
        .order_by("data_evento", "id_historico")
        .values(
            "id_atendimento",
            "numero_protocolo",
            "tipo_evento",
            "valor_anterior",
            "valor_novo",
            "data_evento",
        )
    )
    historico = [
        {campo: _texto(valor) for campo, valor in evento.items()} for evento in eventos
    ]

    return {
        "id_usuario": id_usuario,
        "atendimentos": atendimentos,
        "historico": historico,
    }


def _id_do_usuario(usuario):
    """Aceita o objeto do usuário (usa o pk, como as views fazem com
    request.user) ou o id inteiro. bool é recusado de propósito: em Python ele é
    um int, e True viraria o usuário 1."""
    id_usuario = getattr(usuario, "pk", usuario)
    if isinstance(id_usuario, bool) or not isinstance(id_usuario, int):
        raise TypeError("Informe o usuário titular ou o id inteiro dele.")
    return id_usuario


def _atendimento_exportado(linha, id_usuario):
    """Monta um atendimento da saída a partir de uma linha do values()."""
    papeis = [nome for coluna, nome in PAPEIS if linha[coluna] == id_usuario]
    exportado = {campo: _texto(linha[campo]) for campo in CAMPOS_DO_SERVICO}
    exportado["papeis"] = papeis
    exportado["excluido_em"] = _texto(linha["deletado_em"])
    # O que foi digitado no pedido só vai para quem o digitou.
    if "solicitante" in papeis:
        exportado["dados_informados"] = {
            campo: _texto(linha[campo]) for campo in CAMPOS_INFORMADOS_PELO_SOLICITANTE
        }
    else:
        exportado["dados_informados"] = None
    return exportado


def _texto(valor):
    """Converte o que não é tipo de JSON: UUID vira texto e data ou data e hora
    vira texto ISO 8601. Os demais valores seguem como estão."""
    if valor is None or isinstance(valor, (str, int, float, bool)):
        return valor
    if hasattr(valor, "isoformat"):
        return valor.isoformat()
    return str(valor)
