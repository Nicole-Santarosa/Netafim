from django.db import models
from django.db.models.functions import Now


# Listas de valores permitidos. São iguais aos CHECKs da tabela atendimento
# em infra/db/01_schema.sql. O banco é quem barra um valor fora da lista; elas
# existem aqui para o resto do código não repetir texto solto.

class Origem(models.TextChoices):
    NETAFIM = "netafim"
    DISTRIBUIDOR = "distribuidor"
    CLIENTE_FINAL = "cliente_final"


class Motivo(models.TextChoices):
    DIGITAL_FARMING = "digital_farming"
    DUVIDAS = "duvidas"
    ENTREGA_TECNICA = "entrega_tecnica"
    POS_VENDA = "pos_venda"
    PROGRAMACAO_FEIRAS = "programacao_feiras"
    TREINAMENTO = "treinamento"


class Status(models.TextChoices):
    ABERTO = "aberto"
    DESTINADO = "destinado"
    AGENDADO = "agendado"
    EM_ATENDIMENTO = "em_atendimento"
    RESOLVIDO = "resolvido"
    ENCERRADO = "encerrado"
    ENCERRADO_SEM_AVALIACAO = "encerrado_sem_avaliacao"
    CANCELADO = "cancelado"


class Prioridade(models.TextChoices):
    BAIXA = "baixa"
    MEDIA = "media"
    ALTA = "alta"


class DestinadoA(models.TextChoices):
    NETAFIM = "netafim"
    DISTRIBUIDOR = "distribuidor"


class SituacaoProjeto(models.TextChoices):
    GARANTIA = "garantia"
    FORA_GARANTIA = "fora_garantia"
    INSTALACAO = "instalacao"


class EstadoProjeto(models.TextChoices):
    OPERANDO = "operando"
    NAO_OPERANDO = "nao_operando"


class TipoVenda(models.TextChoices):
    PROJETO = "projeto"
    VENDA_REGULAR = "venda_regular"


class TipoAtendimento(models.TextChoices):
    ONLINE = "online"
    PRESENCIAL = "presencial"


class AreaSetor(models.TextChoices):
    COMERCIAL = "comercial"
    ENGENHARIA = "engenharia"
    INSTALACAO = "instalacao"
    MARKETING = "marketing"
    POS_VENDA = "pos_venda"


class Atendimento(models.Model):
    """Espelho da tabela atendimento. O Django não cria nem altera a tabela:
    ela vem dos scripts SQL e as regras de negócio ficam em gatilhos e CHECKs.
    """

    # Identificação. O UUID é gerado no aparelho (sem default aqui), assim
    # reenviar o mesmo atendimento não duplica o registro.
    id_atendimento = models.UUIDField(primary_key=True)

    # O banco troca esse valor por OS-#### no gatilho, antes do INSERT. Por isso
    # o campo aceita vazio: o que o aplicativo mandar é ignorado de qualquer forma.
    numero_protocolo = models.CharField(max_length=20, blank=True, default="")

    # Classificação do pedido. status e prioridade têm valor padrão no banco
    # (o db_default repete aqui o padrão que já existe na coluna).
    origem = models.CharField(max_length=20, choices=Origem.choices)
    motivo = models.CharField(max_length=30, choices=Motivo.choices)
    status = models.CharField(
        max_length=25, choices=Status.choices, db_default=Status.ABERTO
    )
    prioridade = models.CharField(
        max_length=10, choices=Prioridade.choices, db_default=Prioridade.MEDIA
    )

    # Quem pediu e para quem. Ficam como número simples, sem ForeignKey, porque
    # os models de usuario, cliente, propriedade e distribuidor são de outras
    # pessoas do grupo. A integridade continua garantida pelas FKs do banco.
    id_solicitante = models.IntegerField()
    id_cliente = models.IntegerField(null=True, blank=True)
    id_propriedade = models.IntegerField(null=True, blank=True)
    nome_produtor = models.CharField(max_length=150, null=True, blank=True)
    nome_fazenda_informado = models.CharField(max_length=150, null=True, blank=True)
    cidade = models.CharField(max_length=100, null=True, blank=True)
    area_setor = models.CharField(
        max_length=20, choices=AreaSetor.choices, null=True, blank=True
    )
    contato_solicitante = models.CharField(max_length=20)

    # Destinação e responsável. Não nascem no pedido: só o Netafim Admin destina
    # e o técnico exige a permissão vincular_tecnico.
    id_distribuidor_origem = models.IntegerField(null=True, blank=True)
    destinado_a = models.CharField(
        max_length=15, choices=DestinadoA.choices, null=True, blank=True
    )
    id_distribuidor = models.IntegerField(null=True, blank=True)
    id_tecnico_responsavel = models.IntegerField(null=True, blank=True)

    # Dados do projeto e do atendimento solicitado.
    numero_projeto_br = models.CharField(max_length=30, null=True, blank=True)
    situacao_projeto = models.CharField(
        max_length=20, choices=SituacaoProjeto.choices, null=True, blank=True
    )
    estado_projeto = models.CharField(
        max_length=20, choices=EstadoProjeto.choices, null=True, blank=True
    )
    tipo_venda = models.CharField(
        max_length=20, choices=TipoVenda.choices, null=True, blank=True
    )
    tipo_atendimento = models.CharField(
        max_length=15, choices=TipoAtendimento.choices, null=True, blank=True
    )
    descricao_problema = models.TextField(null=True, blank=True)
    data_inicio_problema = models.DateField(null=True, blank=True)
    data_sugerida = models.DateField(null=True, blank=True)
    necessidade = models.TextField(null=True, blank=True)
    qtd_participantes_prevista = models.SmallIntegerField(null=True, blank=True)

    # Cópia dos dados do cliente e da propriedade no momento da abertura, para o
    # atendimento continuar legível mesmo se o cadastro mudar depois. A API
    # preenche na abertura.
    snapshot_cliente = models.JSONField(null=True, blank=True)
    snapshot_propriedade = models.JSONField(null=True, blank=True)

    # Datas do ciclo de vida. data_abertura é o relógio do aparelho. As datas de
    # conclusão e encerramento são preenchidas pelo gatilho quando o status muda.
    data_abertura = models.DateTimeField(db_default=Now())
    data_conclusao = models.DateTimeField(null=True, blank=True)
    data_encerramento = models.DateTimeField(null=True, blank=True)

    # Controle preenchido pelo banco por gatilho:
    # versao (concorrência otimista, começa em 1 e soma 1 a cada alteração),
    # id_atualizado_por, data_criacao e data_atualizacao (relógio do servidor).
    versao = models.IntegerField(db_default=1)
    id_atualizado_por = models.IntegerField(null=True, blank=True)
    data_criacao = models.DateTimeField(db_default=Now())
    data_atualizacao = models.DateTimeField(db_default=Now())

    # Exclusão lógica: o registro nunca é apagado, só marcado.
    deletado_em = models.DateTimeField(null=True, blank=True)

    # Cursor da sincronização do aplicativo. O gatilho sempre sobrescreve o valor
    # enviado. Na coluna é NOT NULL, mas aqui aceita nulo para o INSERT poder
    # seguir sem número; o gatilho preenche antes de o banco conferir.
    sync_seq = models.BigIntegerField(null=True, blank=True)

    class Meta:
        managed = False
        db_table = "atendimento"


class AtendimentoProblema(models.Model):
    """Espelho da tabela atendimento_problema: a categoria (e, se houver, a
    subcategoria) do problema informado no atendimento. Um atendimento pode ter
    várias linhas, desde que a combinação não se repita entre as não excluídas.
    """

    # Identity do banco: o Django não envia esse valor, ele vem no retorno do INSERT.
    id_atendimento_problema = models.AutoField(primary_key=True)

    # Ligações guardadas como número simples, sem ForeignKey. A subcategoria é
    # opcional e, quando existe, o banco exige que ela pertença à categoria.
    id_atendimento = models.UUIDField()
    id_categoria = models.IntegerField()
    id_subcategoria = models.IntegerField(null=True, blank=True)

    data_criacao = models.DateTimeField(db_default=Now())
    data_atualizacao = models.DateTimeField(db_default=Now())
    deletado_em = models.DateTimeField(null=True, blank=True)

    # Preenchido por gatilho; aceita nulo aqui só para o INSERT poder seguir.
    sync_seq = models.BigIntegerField(null=True, blank=True)

    class Meta:
        managed = False
        db_table = "atendimento_problema"


class AtendimentoCultura(models.Model):
    """Espelho da tabela atendimento_cultura: as culturas ligadas ao atendimento.
    A chave primária é o par (atendimento, cultura), sem coluna de id própria.
    """

    pk = models.CompositePrimaryKey("id_atendimento", "id_cultura")

    id_atendimento = models.UUIDField()
    id_cultura = models.IntegerField()

    # Esta tabela não tem data_criacao, só data_atualizacao.
    data_atualizacao = models.DateTimeField(db_default=Now())
    deletado_em = models.DateTimeField(null=True, blank=True)

    # Preenchido por gatilho; aceita nulo aqui só para o INSERT poder seguir.
    sync_seq = models.BigIntegerField(null=True, blank=True)

    class Meta:
        managed = False
        db_table = "atendimento_cultura"


class AtendimentoTipoTreinamento(models.Model):
    """Espelho da tabela atendimento_tipo_treinamento: os tipos de treinamento
    pedidos no atendimento. Chave primária composta, como atendimento_cultura.
    """

    pk = models.CompositePrimaryKey("id_atendimento", "id_tipo_treinamento")

    id_atendimento = models.UUIDField()
    id_tipo_treinamento = models.IntegerField()

    data_atualizacao = models.DateTimeField(db_default=Now())
    deletado_em = models.DateTimeField(null=True, blank=True)

    # Preenchido por gatilho; aceita nulo aqui só para o INSERT poder seguir.
    sync_seq = models.BigIntegerField(null=True, blank=True)

    class Meta:
        managed = False
        db_table = "atendimento_tipo_treinamento"


# Listas de valores permitidos do histórico, iguais aos CHECKs de
# historico_atendimento em infra/db/01_schema.sql.

class TipoEvento(models.TextChoices):
    CRIADO = "criado"
    PROTOCOLO_GERADO = "protocolo_gerado"
    DESTINADO = "destinado"
    ATRIBUIDO = "atribuido"
    AGENDADO = "agendado"
    REAGENDADO = "reagendado"
    STATUS_ALTERADO = "status_alterado"
    RECLASSIFICADO = "reclassificado"
    EDITADO_POS_FINALIZACAO = "editado_pos_finalizacao"
    EXCLUIDO = "excluido"


class OrigemEvento(models.TextChoices):
    USUARIO = "usuario"
    SISTEMA = "sistema"


class HistoricoAtendimento(models.Model):
    """Espelho da tabela historico_atendimento: a linha do tempo do atendimento.

    Somente leitura. Quem grava é o banco, pelos gatilhos de atendimento e
    agendamento, e um gatilho bloqueia UPDATE, DELETE e TRUNCATE. A API só
    consulta; nunca deve criar, alterar nem apagar linhas por aqui.
    """

    # Identity do banco.
    id_historico = models.BigAutoField(primary_key=True)

    id_atendimento = models.UUIDField()
    tipo_evento = models.CharField(max_length=30, choices=TipoEvento.choices)
    origem_evento = models.CharField(max_length=10, choices=OrigemEvento.choices)

    # Os dois valores são texto curto (até 100 caracteres) do que mudou, como
    # status antigo e novo. O anterior é nulo quando não há valor de antes.
    valor_anterior = models.CharField(max_length=100, null=True, blank=True)
    valor_novo = models.CharField(max_length=100)

    # Pelo CHECK do banco: origem usuario exige o responsável; origem sistema
    # deixa o campo nulo.
    id_usuario_responsavel = models.IntegerField(null=True, blank=True)

    data_evento = models.DateTimeField(db_default=Now())
    observacao = models.TextField(null=True, blank=True)

    class Meta:
        managed = False
        db_table = "historico_atendimento"
