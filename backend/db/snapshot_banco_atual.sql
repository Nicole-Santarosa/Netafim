-- =====================================================================================
-- BANCO COMPLETO DO GRUPO para testes locais e de CI (scripts 01 a 04, na ordem).
--
-- NAO e a fonte da verdade: a fonte e o banco do grupo (Supabase). Este arquivo e a concatenacao
-- dos scripts do grupo (01_schema, 02_funcoes_gatilhos, 03_seguranca_rls, 04_dados_iniciais), ja com
-- a correcao de visibilidade do atendimento (app_atendimento_visivel_linha, patch 07), mais a senha
-- de TESTE do papel app_django no final. Substitui o antigo retrato parcial (so tabelas de acesso).
-- Se os scripts do grupo mudarem, regere este arquivo e rode a suite de testes.
-- =====================================================================================

-- ---------- 01_schema.sql ----------
-- =====================================================================
-- 01_schema.sql
-- Netafim Pós-venda | Banco central (PostgreSQL / Supabase)
-- Modelo final: 41 tabelas, 66 chaves estrangeiras.
--
-- O QUE FAZ: cria sequências, tabelas, chaves, CHECKs e índices.
-- NÃO cria gatilhos nem policies (isso fica nos arquivos 02 e 03).
--
-- CONVENÇÕES DESTE ARQUIVO
--  * Nomes em minúsculo e sem aspas (usuario, atendimento...). No
--    Django, db_table = 'usuario' etc.
--  * Datas e horas sempre timestamptz (guardadas em UTC).
--  * PK uuid SEM default = gerada no aparelho (chave de idempotência).
--    PK uuid COM default = gerada no servidor.
--  * data_atualizacao e sync_seq são preenchidas por gatilho (02).
--  * Exclusão é sempre lógica (deletado_em); nenhuma FK usa CASCADE.
-- =====================================================================

-- Cursor global de sincronização: toda alteração em tabela que desce
-- ao celular recebe o próximo número (gatilho em 02).
create sequence seq_sync as bigint;

-- Numeração do protocolo OS-####.
create sequence seq_protocolo as bigint start 1;

-- =====================================================================
-- A.1  USUÁRIOS, ORGANIZAÇÕES, PERMISSÕES E DISPOSITIVOS
-- =====================================================================

-- ---------------------------------------------------------------- PERFIL
create table perfil (
  id_perfil         integer generated always as identity primary key,
  nome_perfil       varchar(50) not null,
  tipo_organizacao  varchar(15) not null,
  nivel             varchar(10) not null,
  descricao         text,
  constraint uq_perfil_nome        unique (nome_perfil),
  -- um perfil por combinação organização + nível
  constraint uq_perfil_tipo_nivel  unique (tipo_organizacao, nivel),
  -- usado pela FK composta de usuario (perfil coerente com a organização)
  constraint uq_perfil_id_tipo     unique (id_perfil, tipo_organizacao),
  constraint ck_perfil_tipo  check (tipo_organizacao in ('netafim','distribuidor','cliente')),
  constraint ck_perfil_nivel check (nivel in ('admin','usuario'))
);

-- ------------------------------------------------------------- PERMISSAO
create table permissao (
  id_permissao  integer generated always as identity primary key,
  codigo        varchar(50)  not null,
  descricao     varchar(150) not null,
  constraint uq_permissao_codigo unique (codigo)
);

-- ------------------------------------------------------- PERFIL_PERMISSAO
create table perfil_permissao (
  id_perfil     integer not null references perfil (id_perfil),
  id_permissao  integer not null references permissao (id_permissao),
  primary key (id_perfil, id_permissao)
);
create index ix_perfil_permissao_permissao on perfil_permissao (id_permissao);

-- ------------------------------------------------------- DISTRIBUIDOR
create table distribuidor (
  id_distribuidor   integer generated always as identity primary key,
  nome              varchar(150) not null,
  regiao            varchar(100) not null,
  email             varchar(150),
  telefone          varchar(20),
  status            varchar(20)  not null default 'ativo',
  data_criacao      timestamptz  not null default now(),
  data_atualizacao  timestamptz  not null default now(),
  deletado_em       timestamptz,
  sync_seq          bigint       not null,
  constraint ck_distribuidor_status check (status in ('ativo','inativo'))
);

-- ------------------------------------------------------------- CLIENTE
create table cliente (
  id_cliente        integer generated always as identity primary key,
  nome              varchar(150) not null,
  email             varchar(150),
  telefone          varchar(20),
  id_distribuidor   integer references distribuidor (id_distribuidor),
  status            varchar(20)  not null default 'ativo',
  data_criacao      timestamptz  not null default now(),
  data_atualizacao  timestamptz  not null default now(),
  deletado_em       timestamptz,
  sync_seq          bigint       not null,
  constraint ck_cliente_status check (status in ('ativo','inativo'))
);
create index ix_cliente_distribuidor on cliente (id_distribuidor);

-- ------------------------------------------------------------- USUARIO
create table usuario (
  id_usuario                    integer generated always as identity primary key,
  nome                          varchar(150) not null,
  email                         varchar(150) not null,
  telefone                      varchar(20),
  -- hash gerenciado pelo Django (campo herdado do AbstractBaseUser)
  password                      varchar(128) not null,
  last_login                    timestamptz,
  -- is_active do Django = (situacao = 'ativo')
  situacao                      varchar(15)  not null default 'pendente',
  falhas_login                  smallint     not null default 0,
  bloqueado_ate                 timestamptz,
  mfa_ativo                     boolean      not null default false,
  senha_alterada_em             timestamptz,
  exige_troca_senha             boolean      not null default false,
  tipo_organizacao              varchar(15),
  id_perfil                     integer,
  id_distribuidor               integer references distribuidor (id_distribuidor),
  id_cliente                    integer references cliente (id_cliente),
  id_aprovado_por               integer references usuario (id_usuario),
  data_aprovacao                timestamptz,
  consentimento_lgpd            boolean      not null default false,
  data_consentimento            timestamptz,
  versao_termo_consentimento    varchar(20),
  data_revogacao_consentimento  timestamptz,
  data_exclusao_solicitada      timestamptz,
  data_criacao                  timestamptz  not null default now(),
  data_atualizacao              timestamptz  not null default now(),
  deletado_em                   timestamptz,

  -- FK composta: o perfil precisa pertencer ao mesmo tipo de organização
  -- do usuário (ex.: "Netafim Admin" só em usuário do tipo netafim).
  -- Com id_perfil nulo (cadastro pendente) a FK não se aplica.
  constraint fk_usuario_perfil foreign key (id_perfil, tipo_organizacao)
    references perfil (id_perfil, tipo_organizacao),

  constraint ck_usuario_situacao check
    (situacao in ('pendente','ativo','recusado','inativo','bloqueado')),
  constraint ck_usuario_tipo_org check
    (tipo_organizacao is null or tipo_organizacao in ('netafim','distribuidor','cliente')),
  constraint ck_usuario_falhas check (falhas_login >= 0),
  -- só aceita hash do Django (algoritmo$...) ou senha inutilizada (!...)
  constraint ck_usuario_password_formato check
    (password ~ '^[a-z0-9_]+\$.+' or password ~ '^!'),
  -- uma organização por usuário e tipo coerente com a chave preenchida
  constraint ck_usuario_organizacao check (
       (tipo_organizacao is null        and id_cliente is null     and id_distribuidor is null)
    or (tipo_organizacao = 'netafim'    and id_cliente is null     and id_distribuidor is null)
    or (tipo_organizacao = 'distribuidor' and id_cliente is null   and id_distribuidor is not null)
    or (tipo_organizacao = 'cliente'    and id_cliente is not null and id_distribuidor is null)
  ),
  -- perfil sem tipo de organização escaparia da FK composta
  constraint ck_usuario_perfil_tipo check (id_perfil is null or tipo_organizacao is not null),
  -- conta ativa exige perfil e organização definidos
  constraint ck_usuario_ativo_completo check
    (situacao <> 'ativo' or (id_perfil is not null and tipo_organizacao is not null)),
  -- consentimento comprovável: aceite exige data e versão do termo
  constraint ck_usuario_consentimento check
    (not consentimento_lgpd or (data_consentimento is not null and versao_termo_consentimento is not null)),
  -- revogação devolve consentimento_lgpd para false
  constraint ck_usuario_revogacao check
    (data_revogacao_consentimento is null or consentimento_lgpd = false)
);
-- e-mail único entre usuários não excluídos, sem diferenciar maiúsculas
create unique index ux_usuario_email on usuario (lower(email)) where deletado_em is null;
create index ix_usuario_perfil          on usuario (id_perfil);
create index ix_usuario_distribuidor    on usuario (id_distribuidor);
create index ix_usuario_cliente         on usuario (id_cliente);
create index ix_usuario_aprovado_por    on usuario (id_aprovado_por);

-- ----------------------------------------------------------- PROPRIEDADE
create table propriedade (
  id_propriedade    integer generated always as identity primary key,
  id_cliente        integer not null references cliente (id_cliente),
  nome_fazenda      varchar(150) not null,
  localizacao       varchar(200) not null,
  latitude          numeric(9,6),
  longitude         numeric(9,6),
  data_criacao      timestamptz  not null default now(),
  data_atualizacao  timestamptz  not null default now(),
  deletado_em       timestamptz,
  sync_seq          bigint       not null,
  -- necessário para a FK composta de atendimento (propriedade do cliente)
  constraint uq_propriedade_id_cliente unique (id_propriedade, id_cliente),
  constraint ck_propriedade_lat check (latitude  is null or latitude  between -90  and 90),
  constraint ck_propriedade_lon check (longitude is null or longitude between -180 and 180),
  -- latitude e longitude andam juntas
  constraint ck_propriedade_coord check ((latitude is null) = (longitude is null))
);
create index ix_propriedade_cliente on propriedade (id_cliente);

-- --------------------------------------------------------------- CULTURA
create table cultura (
  id_cultura        integer generated always as identity primary key,
  nome              varchar(50) not null,
  data_criacao      timestamptz not null default now(),
  data_atualizacao  timestamptz not null default now(),
  deletado_em       timestamptz,
  sync_seq          bigint      not null
);
create unique index ux_cultura_nome on cultura (lower(nome)) where deletado_em is null;

-- ------------------------------------------------------ PROPRIEDADE_CULTURA
create table propriedade_cultura (
  id_propriedade    integer not null references propriedade (id_propriedade),
  id_cultura        integer not null references cultura (id_cultura),
  data_atualizacao  timestamptz not null default now(),
  deletado_em       timestamptz,
  sync_seq          bigint      not null,
  primary key (id_propriedade, id_cultura)
);
create index ix_propriedade_cultura_cultura on propriedade_cultura (id_cultura);

-- ----------------------------------------------------------- DISPOSITIVO
create table dispositivo (
  id_dispositivo                uuid primary key,   -- gerado no aparelho
  id_usuario                    integer not null references usuario (id_usuario),
  modelo                        varchar(80),
  versao_app                    varchar(20),
  ultima_sincronizacao          timestamptz,
  revogado_em                   timestamptz,
  limpeza_remota_solicitada_em  timestamptz,
  limpeza_remota_confirmada_em  timestamptz,
  data_criacao                  timestamptz not null default now(),
  -- só se confirma uma limpeza que foi solicitada
  constraint ck_dispositivo_limpeza check
    (limpeza_remota_confirmada_em is null or limpeza_remota_solicitada_em is not null)
);
create index ix_dispositivo_usuario on dispositivo (id_usuario);

-- --------------------------------------------------------- SYNC_CONFLITO
create table sync_conflito (
  id_conflito       uuid primary key default gen_random_uuid(),
  id_dispositivo    uuid references dispositivo (id_dispositivo),
  entidade_tipo     varchar(30) not null,
  -- referência polimórfica: integridade controlada pela aplicação
  entidade_uuid     uuid        not null,
  versao_servidor   integer     not null,
  versao_cliente    integer     not null,
  payload_cliente   jsonb       not null,
  status            varchar(20) not null default 'aberto',
  id_resolvido_por  integer references usuario (id_usuario),
  data_criacao      timestamptz not null default now(),
  resolvido_em      timestamptz,
  constraint ck_sync_conflito_status check
    (status in ('aberto','resolvido_servidor','resolvido_cliente','descartado')),
  -- conflito em aberto não tem data de resolução; os demais têm
  constraint ck_sync_conflito_resolucao check
    ((status = 'aberto') = (resolvido_em is null))
);
create index ix_sync_conflito_dispositivo  on sync_conflito (id_dispositivo);
create index ix_sync_conflito_resolvido_por on sync_conflito (id_resolvido_por);
create index ix_sync_conflito_entidade     on sync_conflito (entidade_tipo, entidade_uuid);

-- =====================================================================
-- A.2  ATENDIMENTO, AGENDA E CLASSIFICAÇÃO
-- =====================================================================

-- ---------------------------------------------------------- ATENDIMENTO
create table atendimento (
  id_atendimento              uuid primary key,   -- gerado no aparelho
  -- OS-####, atribuído pelo servidor (gatilho); o valor informado é ignorado
  numero_protocolo            varchar(20) not null,
  origem                      varchar(20) not null,
  motivo                      varchar(30) not null,
  status                      varchar(25) not null default 'aberto',
  prioridade                  varchar(10) not null default 'media',
  id_solicitante              integer not null references usuario (id_usuario),
  id_cliente                  integer references cliente (id_cliente),
  id_propriedade              integer,
  nome_produtor               varchar(150),
  nome_fazenda_informado      varchar(150),
  cidade                      varchar(100),
  area_setor                  varchar(20),
  id_distribuidor_origem      integer references distribuidor (id_distribuidor),
  destinado_a                 varchar(15),
  id_distribuidor             integer references distribuidor (id_distribuidor),
  id_tecnico_responsavel      integer references usuario (id_usuario),
  contato_solicitante         varchar(20) not null,
  numero_projeto_br           varchar(30),
  situacao_projeto            varchar(20),
  estado_projeto              varchar(20),
  tipo_venda                  varchar(20),
  tipo_atendimento            varchar(15),
  descricao_problema          text,
  data_inicio_problema        date,
  data_sugerida               date,
  necessidade                 text,
  qtd_participantes_prevista  smallint,
  snapshot_cliente            jsonb,
  snapshot_propriedade        jsonb,
  data_abertura               timestamptz not null default now(),
  data_conclusao              timestamptz,
  data_encerramento           timestamptz,
  versao                      integer     not null default 1,
  id_atualizado_por           integer references usuario (id_usuario),
  data_criacao                timestamptz not null default now(),
  data_atualizacao            timestamptz not null default now(),
  deletado_em                 timestamptz,
  sync_seq                    bigint      not null,

  constraint uq_atendimento_protocolo unique (numero_protocolo),

  -- FK composta: a propriedade tem de pertencer ao cliente do atendimento.
  -- Se id_propriedade ou id_cliente for nulo, a FK não se aplica.
  constraint fk_atendimento_propriedade_cliente foreign key (id_propriedade, id_cliente)
    references propriedade (id_propriedade, id_cliente),

  constraint ck_atend_protocolo   check (numero_protocolo ~ '^OS-[0-9]{4,}$'),
  constraint ck_atend_origem      check (origem in ('netafim','distribuidor','cliente_final')),
  constraint ck_atend_motivo      check (motivo in ('digital_farming','duvidas','entrega_tecnica','pos_venda','programacao_feiras','treinamento')),
  constraint ck_atend_status      check (status in ('aberto','destinado','agendado','em_atendimento','resolvido','encerrado','encerrado_sem_avaliacao','cancelado')),
  constraint ck_atend_prioridade  check (prioridade in ('baixa','media','alta')),
  constraint ck_atend_destinado_a check (destinado_a is null or destinado_a in ('netafim','distribuidor')),
  constraint ck_atend_situacao_projeto check (situacao_projeto is null or situacao_projeto in ('garantia','fora_garantia','instalacao')),
  constraint ck_atend_estado_projeto   check (estado_projeto   is null or estado_projeto   in ('operando','nao_operando')),
  constraint ck_atend_tipo_venda       check (tipo_venda       is null or tipo_venda       in ('projeto','venda_regular')),
  constraint ck_atend_tipo_atendimento check (tipo_atendimento is null or tipo_atendimento in ('online','presencial')),
  constraint ck_atend_area_setor       check (area_setor       is null or area_setor       in ('comercial','engenharia','instalacao','marketing','pos_venda')),

  -- regras de preenchimento condicional
  constraint ck_atend_cliente_final  check (origem <> 'cliente_final' or id_cliente is not null),
  constraint ck_atend_area_netafim   check (origem <> 'netafim' or area_setor is not null),
  constraint ck_atend_destino        check (destinado_a is distinct from 'distribuidor' or id_distribuidor is not null),
  constraint ck_atend_propriedade_cliente check (id_propriedade is null or id_cliente is not null),
  constraint ck_atend_qtd_participantes   check (qtd_participantes_prevista is null or qtd_participantes_prevista > 0),
  constraint ck_atend_versao         check (versao >= 1),

  -- coerência entre estado e datas (derivadas da máquina de estados)
  constraint ck_atend_status_destino check (status in ('aberto','cancelado') or destinado_a is not null),
  constraint ck_atend_conclusao      check (status not in ('resolvido','encerrado','encerrado_sem_avaliacao') or data_conclusao is not null),
  constraint ck_atend_encerramento   check (status not in ('encerrado','encerrado_sem_avaliacao') or data_encerramento is not null),
  constraint ck_atend_encerramento_so check (data_encerramento is null or status in ('encerrado','encerrado_sem_avaliacao'))
);
create index ix_atend_solicitante    on atendimento (id_solicitante);
create index ix_atend_cliente        on atendimento (id_cliente);
create index ix_atend_propriedade    on atendimento (id_propriedade);
create index ix_atend_dist_origem    on atendimento (id_distribuidor_origem);
create index ix_atend_distribuidor   on atendimento (id_distribuidor);
create index ix_atend_tecnico        on atendimento (id_tecnico_responsavel);
create index ix_atend_atualizado_por on atendimento (id_atualizado_por);
create index ix_atend_status         on atendimento (status) where deletado_em is null;
create unique index ux_atend_sync_seq on atendimento (sync_seq);

-- ------------------------------------------------- HISTORICO_ATENDIMENTO
create table historico_atendimento (
  id_historico           bigint generated always as identity primary key,
  id_atendimento         uuid         not null references atendimento (id_atendimento),
  tipo_evento            varchar(30)  not null,
  origem_evento          varchar(10)  not null,
  valor_anterior         varchar(100),
  valor_novo             varchar(100) not null,
  id_usuario_responsavel integer references usuario (id_usuario),
  data_evento            timestamptz  not null default now(),
  observacao             text,
  constraint ck_hist_tipo_evento check (tipo_evento in
    ('criado','protocolo_gerado','destinado','atribuido','agendado','reagendado',
     'status_alterado','reclassificado','editado_pos_finalizacao','excluido')),
  constraint ck_hist_origem check (origem_evento in ('usuario','sistema')),
  -- usuario exige o responsável; sistema deixa o campo nulo
  constraint ck_hist_responsavel check (
       (origem_evento = 'usuario' and id_usuario_responsavel is not null)
    or (origem_evento = 'sistema' and id_usuario_responsavel is null))
);
create index ix_hist_atendimento on historico_atendimento (id_atendimento, data_evento);
create index ix_hist_responsavel on historico_atendimento (id_usuario_responsavel);

-- ---------------------------------------------------------- AGENDAMENTO
create table agendamento (
  id_agendamento      integer generated always as identity primary key,
  id_atendimento      uuid        not null references atendimento (id_atendimento),
  data_hora_agendada  timestamptz not null,
  status              varchar(15) not null default 'ativo',
  id_criado_por       integer     not null references usuario (id_usuario),
  data_criacao        timestamptz not null default now(),
  data_atualizacao    timestamptz not null default now(),
  deletado_em         timestamptz,
  sync_seq            bigint      not null,
  constraint ck_agendamento_status check (status in ('ativo','cancelado','realizado'))
);
-- no máximo um agendamento ativo por atendimento
create unique index ux_agendamento_ativo on agendamento (id_atendimento)
  where status = 'ativo' and deletado_em is null;
create index ix_agendamento_atendimento on agendamento (id_atendimento);
create index ix_agendamento_criado_por  on agendamento (id_criado_por);

-- -------------------------------------------------- PROBLEMA_CATEGORIA
create table problema_categoria (
  id_categoria      integer generated always as identity primary key,
  nome              varchar(50) not null,
  aplicavel_a       varchar(20) not null,
  ordem             smallint    not null default 0,
  ativa             boolean     not null default true,
  data_criacao      timestamptz not null default now(),
  data_atualizacao  timestamptz not null default now(),
  deletado_em       timestamptz,
  sync_seq          bigint      not null,
  constraint ck_categoria_aplicavel check (aplicavel_a in ('digital_farming','pos_venda'))
);
create unique index ux_categoria_nome on problema_categoria (lower(nome), aplicavel_a) where deletado_em is null;

-- ---------------------------------------------- PROBLEMA_SUBCATEGORIA
create table problema_subcategoria (
  id_subcategoria   integer generated always as identity primary key,
  id_categoria      integer      not null references problema_categoria (id_categoria),
  descricao         varchar(150) not null,
  ordem             smallint     not null default 0,
  ativa             boolean      not null default true,
  data_criacao      timestamptz  not null default now(),
  data_atualizacao  timestamptz  not null default now(),
  deletado_em       timestamptz,
  sync_seq          bigint       not null,
  -- necessário para a FK composta de atendimento_problema
  constraint uq_subcategoria_id_categoria unique (id_subcategoria, id_categoria)
);
create index ix_subcategoria_categoria on problema_subcategoria (id_categoria);

-- -------------------------------------------------- ATENDIMENTO_PROBLEMA
create table atendimento_problema (
  id_atendimento_problema  integer generated always as identity primary key,
  id_atendimento           uuid    not null references atendimento (id_atendimento),
  id_categoria             integer not null references problema_categoria (id_categoria),
  id_subcategoria          integer,
  data_criacao             timestamptz not null default now(),
  data_atualizacao         timestamptz not null default now(),
  deletado_em              timestamptz,
  sync_seq                 bigint      not null,
  -- quando há subcategoria, ela pertence à categoria informada
  constraint fk_atend_problema_subcat foreign key (id_subcategoria, id_categoria)
    references problema_subcategoria (id_subcategoria, id_categoria)
);
-- a combinação (atendimento, categoria, subcategoria) não se repete
create unique index ux_atend_problema on atendimento_problema
  (id_atendimento, id_categoria, coalesce(id_subcategoria, 0)) where deletado_em is null;
create index ix_atend_problema_categoria    on atendimento_problema (id_categoria);
create index ix_atend_problema_subcategoria on atendimento_problema (id_subcategoria);

-- --------------------------------------------------- ATENDIMENTO_CULTURA
create table atendimento_cultura (
  id_atendimento    uuid    not null references atendimento (id_atendimento),
  id_cultura        integer not null references cultura (id_cultura),
  data_atualizacao  timestamptz not null default now(),
  deletado_em       timestamptz,
  sync_seq          bigint      not null,
  primary key (id_atendimento, id_cultura)
);
create index ix_atend_cultura_cultura on atendimento_cultura (id_cultura);

-- -------------------------------------------------- TIPO_TREINAMENTO
create table tipo_treinamento (
  id_tipo_treinamento  integer generated always as identity primary key,
  nome                 varchar(50) not null,
  data_criacao         timestamptz not null default now(),
  data_atualizacao     timestamptz not null default now(),
  deletado_em          timestamptz,
  sync_seq             bigint      not null
);
create unique index ux_tipo_treinamento_nome on tipo_treinamento (lower(nome)) where deletado_em is null;

-- ------------------------------------------ ATENDIMENTO_TIPO_TREINAMENTO
create table atendimento_tipo_treinamento (
  id_atendimento       uuid    not null references atendimento (id_atendimento),
  id_tipo_treinamento  integer not null references tipo_treinamento (id_tipo_treinamento),
  data_atualizacao     timestamptz not null default now(),
  deletado_em          timestamptz,
  sync_seq             bigint      not null,
  primary key (id_atendimento, id_tipo_treinamento)
);
create index ix_atend_tipo_trein_tipo on atendimento_tipo_treinamento (id_tipo_treinamento);

-- =====================================================================
-- A.3  RELATÓRIO, EVIDÊNCIAS E COMUNICAÇÃO
-- =====================================================================

-- ------------------------------------------------- RELATORIO_ATENDIMENTO
create table relatorio_atendimento (
  id_relatorio            uuid primary key,   -- gerado no aparelho
  id_atendimento          uuid    not null references atendimento (id_atendimento),
  id_preenchido_por       integer not null references usuario (id_usuario),
  status                  varchar(15) not null default 'rascunho',
  data_finalizacao        timestamptz,
  descricao_constatacoes  text,
  atividades_executadas   text,
  plano_acao              text,
  conclusao_visita        text,
  local_treinamento       varchar(150),
  qtd_pessoas_treinadas   smallint,
  estacao_funcionando     boolean,
  versao                  integer not null default 1,
  id_atualizado_por       integer references usuario (id_usuario),
  data_criacao            timestamptz not null default now(),
  data_atualizacao        timestamptz not null default now(),
  deletado_em             timestamptz,
  sync_seq                bigint      not null,
  constraint ck_relatorio_status check (status in ('rascunho','finalizado')),
  constraint ck_relatorio_finalizacao check (status <> 'finalizado' or data_finalizacao is not null),
  constraint ck_relatorio_qtd check (qtd_pessoas_treinadas is null or qtd_pessoas_treinadas > 0),
  constraint ck_relatorio_versao check (versao >= 1)
);
-- um relatório por atendimento (entre não excluídos)
create unique index ux_relatorio_atendimento on relatorio_atendimento (id_atendimento) where deletado_em is null;
create index ix_relatorio_preenchido_por on relatorio_atendimento (id_preenchido_por);
create index ix_relatorio_atualizado_por on relatorio_atendimento (id_atualizado_por);

-- ------------------------------------------------ MOTIVO_CAMPO_RELATORIO
create table motivo_campo_relatorio (
  motivo            varchar(30) not null,
  campo             varchar(40) not null,
  obrigatorio       boolean     not null default true,
  data_atualizacao  timestamptz not null default now(),
  deletado_em       timestamptz,
  sync_seq          bigint      not null,
  primary key (motivo, campo),
  constraint ck_mcr_motivo check (motivo in ('digital_farming','duvidas','entrega_tecnica','pos_venda','programacao_feiras','treinamento')),
  constraint ck_mcr_campo check (campo in
    ('tipo_atendimento','tipo_treinamento','descricao_constatacoes','atividades_executadas',
     'plano_acao','conclusao_visita','local_treinamento','qtd_pessoas_treinadas',
     'evidencia_constatacao','evidencia_atividade','evidencia_geral',
     'assinatura_especialista','assinatura_cliente'))
);

-- ------------------------------------------------ RELATORIO_ESPECIALISTA
create table relatorio_especialista (
  id_relatorio      uuid    not null references relatorio_atendimento (id_relatorio),
  id_usuario        integer not null references usuario (id_usuario),
  papel             varchar(15) not null,
  data_atualizacao  timestamptz not null default now(),
  deletado_em       timestamptz,
  sync_seq          bigint      not null,
  primary key (id_relatorio, id_usuario),
  constraint ck_rel_esp_papel check (papel in ('responsavel','apoio'))
);
-- no máximo um responsável por relatório
create unique index ux_relatorio_um_responsavel on relatorio_especialista (id_relatorio)
  where papel = 'responsavel' and deletado_em is null;
create index ix_rel_esp_usuario on relatorio_especialista (id_usuario);

-- ---------------------------------------------------- RELATORIO_VERSAO
create table relatorio_versao (
  id_relatorio_versao     uuid primary key default gen_random_uuid(),
  id_relatorio            uuid    not null references relatorio_atendimento (id_relatorio),
  versao                  integer not null,
  descricao_constatacoes  text,
  atividades_executadas   text,
  plano_acao              text,
  conclusao_visita        text,
  local_treinamento       varchar(150),
  qtd_pessoas_treinadas   smallint,
  estacao_funcionando     boolean,
  id_editado_por          integer not null references usuario (id_usuario),
  data_criacao            timestamptz not null default now(),
  constraint uq_relatorio_versao unique (id_relatorio, versao)
);
create index ix_relatorio_versao_editado_por on relatorio_versao (id_editado_por);

-- ------------------------------------------------------- RELATORIO_PDF
create table relatorio_pdf (
  id_relatorio_pdf  integer generated always as identity primary key,
  id_relatorio      uuid         not null references relatorio_atendimento (id_relatorio),
  versao            integer      not null,
  storage_key       varchar(255) not null,
  hash_arquivo      char(64)     not null,
  tamanho_bytes     bigint       not null,
  data_geracao      timestamptz  not null default now(),
  constraint uq_relatorio_pdf_storage unique (storage_key),
  constraint ck_relatorio_pdf_hash    check (hash_arquivo ~ '^[0-9a-f]{64}$'),
  constraint ck_relatorio_pdf_tamanho check (tamanho_bytes > 0),
  constraint ck_relatorio_pdf_key     check (storage_key !~ '\.\.' and storage_key !~ '^/')
);
create index ix_relatorio_pdf_relatorio on relatorio_pdf (id_relatorio);

-- ------------------------------------------------- ENVIO_COMUNICACAO
create table envio_comunicacao (
  id_envio          integer generated always as identity primary key,
  id_atendimento    uuid        not null references atendimento (id_atendimento),
  tipo_comunicacao  varchar(15) not null,
  id_agendamento    integer references agendamento (id_agendamento),
  id_relatorio_pdf  integer references relatorio_pdf (id_relatorio_pdf),
  canal             varchar(15)  not null,
  destinatario      varchar(150) not null,
  status            varchar(15)  not null default 'pendente',
  tentativas        smallint     not null default 0,
  data_envio        timestamptz,
  erro_mensagem     varchar(255),
  data_criacao      timestamptz  not null default now(),
  constraint ck_envio_tipo   check (tipo_comunicacao in ('agendamento','relatorio')),
  constraint ck_envio_canal  check (canal in ('email','whatsapp')),
  constraint ck_envio_status check (status in ('pendente','enviado','falha')),
  constraint ck_envio_tentativas check (tentativas >= 0),
  -- exatamente uma referência, coerente com o tipo
  constraint ck_envio_referencia check (
       (tipo_comunicacao = 'agendamento' and id_agendamento is not null and id_relatorio_pdf is null)
    or (tipo_comunicacao = 'relatorio'   and id_relatorio_pdf is not null and id_agendamento is null))
);
create index ix_envio_atendimento   on envio_comunicacao (id_atendimento);
create index ix_envio_agendamento   on envio_comunicacao (id_agendamento);
create index ix_envio_relatorio_pdf on envio_comunicacao (id_relatorio_pdf);

-- ---------------------------------------------------------- EVIDENCIA
create table evidencia (
  id_evidencia       uuid primary key,   -- gerado no aparelho
  id_atendimento     uuid         not null references atendimento (id_atendimento),
  etapa              varchar(20)  not null,
  tipo_arquivo       varchar(10)  not null,
  storage_key        varchar(255) not null,
  mime_type          varchar(50)  not null,
  tamanho_bytes      bigint       not null,
  hash_arquivo       char(64)     not null,
  status_upload      varchar(15)  not null default 'pendente',
  varredura_status   varchar(15)  not null default 'pendente',
  tentativas_upload  smallint     not null default 0,
  origem_captura     varchar(10)  not null,
  data_captura       timestamptz  not null,
  data_upload        timestamptz,
  latitude           numeric(9,6),
  longitude          numeric(9,6),
  data_criacao       timestamptz  not null default now(),
  data_atualizacao   timestamptz  not null default now(),
  deletado_em        timestamptz,
  sync_seq           bigint       not null,
  constraint uq_evidencia_storage unique (storage_key),
  constraint ck_evid_etapa  check (etapa in ('solicitacao','constatacao','atividade','geral','anexo_extra')),
  constraint ck_evid_tipo   check (tipo_arquivo in ('foto','video','documento')),
  constraint ck_evid_status_upload check (status_upload in ('pendente','enviado','erro')),
  constraint ck_evid_varredura     check (varredura_status in ('pendente','limpo','infectado','erro')),
  constraint ck_evid_origem        check (origem_captura in ('camera_app','anexo')),
  constraint ck_evid_tentativas    check (tentativas_upload >= 0),
  constraint ck_evid_tamanho       check (tamanho_bytes between 1 and 1073741824),
  constraint ck_evid_hash          check (hash_arquivo ~ '^[0-9a-f]{64}$'),
  -- chave opaca: sem "../", sem barra inicial, só caracteres seguros
  constraint ck_evid_storage_key   check (storage_key ~ '^[A-Za-z0-9][A-Za-z0-9/_.-]*$' and storage_key !~ '\.\.'),
  -- foto e vídeo só nascem da câmera do aplicativo; documento só como anexo
  constraint ck_evid_origem_tipo   check (
       (tipo_arquivo in ('foto','video') and origem_captura = 'camera_app')
    or (tipo_arquivo = 'documento'       and origem_captura = 'anexo')),
  -- documento só na etapa de anexos extras e só em PDF
  constraint ck_evid_documento     check (
       tipo_arquivo <> 'documento'
    or (etapa = 'anexo_extra' and mime_type = 'application/pdf')),
  -- lista de MIME permitida por tipo de arquivo
  constraint ck_evid_mime          check (
       (tipo_arquivo = 'foto'      and mime_type in ('image/jpeg','image/png','image/webp','image/heic'))
    or (tipo_arquivo = 'video'     and mime_type in ('video/mp4','video/quicktime','video/3gpp'))
    or (tipo_arquivo = 'documento' and mime_type = 'application/pdf')),
  constraint ck_evid_upload_data   check (status_upload <> 'enviado' or data_upload is not null),
  constraint ck_evid_lat check (latitude  is null or latitude  between -90  and 90),
  constraint ck_evid_lon check (longitude is null or longitude between -180 and 180),
  constraint ck_evid_coord check ((latitude is null) = (longitude is null))
);
create index ix_evidencia_atendimento on evidencia (id_atendimento, etapa);

-- --------------------------------------------------------- ASSINATURA
create table assinatura (
  id_assinatura              uuid primary key,   -- gerado no aparelho
  id_atendimento             uuid         not null references atendimento (id_atendimento),
  tipo_assinante             varchar(15)  not null,
  id_usuario                 integer references usuario (id_usuario),
  nome_assinante             varchar(150) not null,
  -- CPF, quando informado: guardado CIFRADO pela aplicação (AES-256-GCM)
  documento_assinante        text,
  forma_assinatura           varchar(15)  not null,
  storage_key                varchar(255),
  -- calculados pelo banco no momento da assinatura (gatilho em 02)
  versao_relatorio_assinada  integer      not null,
  conteudo_hash              char(64)     not null,
  data_hora                  timestamptz  not null,
  data_criacao               timestamptz  not null default now(),
  data_atualizacao           timestamptz  not null default now(),
  deletado_em                timestamptz,
  sync_seq                   bigint       not null,
  constraint uq_assinatura_storage unique (storage_key),
  constraint ck_assin_tipo  check (tipo_assinante in ('cliente','especialista')),
  constraint ck_assin_forma check (forma_assinatura in ('desenho','nome_digitado')),
  -- tipo especialista exige id_usuario
  constraint ck_assin_especialista check (tipo_assinante <> 'especialista' or id_usuario is not null),
  -- desenho exige a imagem no armazenamento
  constraint ck_assin_desenho check (forma_assinatura <> 'desenho' or storage_key is not null),
  constraint ck_assin_versao check (versao_relatorio_assinada >= 1),
  constraint ck_assin_hash   check (conteudo_hash ~ '^[0-9a-f]{64}$'),
  constraint ck_assin_key    check (storage_key is null or (storage_key ~ '^[A-Za-z0-9][A-Za-z0-9/_.-]*$' and storage_key !~ '\.\.'))
);
-- uma assinatura de cada tipo por versão do relatório (entre não excluídas)
create unique index ux_assinatura_tipo_versao on assinatura
  (id_atendimento, tipo_assinante, versao_relatorio_assinada) where deletado_em is null;
create index ix_assinatura_atendimento on assinatura (id_atendimento);
create index ix_assinatura_usuario     on assinatura (id_usuario);

-- =====================================================================
-- A.4  FORMULÁRIO DINÂMICO, SATISFAÇÃO E NOTIFICAÇÕES
-- =====================================================================

-- ------------------------------------------------- PESQUISA_SATISFACAO
create table pesquisa_satisfacao (
  id_pesquisa             integer generated always as identity primary key,
  id_atendimento          uuid        not null references atendimento (id_atendimento),
  id_usuario_respondente  integer references usuario (id_usuario),
  nome_respondente        varchar(150),
  nota_geral              smallint,
  data_envio              timestamptz not null default now(),
  data_resposta           timestamptz,
  constraint uq_pesquisa_atendimento unique (id_atendimento),
  constraint ck_pesquisa_nota check (nota_geral is null or nota_geral between 0 and 10),
  -- respondida = tem nota e data de resposta, as duas juntas
  constraint ck_pesquisa_resposta check ((nota_geral is null) = (data_resposta is null))
);
create index ix_pesquisa_respondente on pesquisa_satisfacao (id_usuario_respondente);

-- ------------------------------------------------ PERGUNTA_SATISFACAO
create table pergunta_satisfacao (
  id_pergunta  integer generated always as identity primary key,
  texto        varchar(200) not null,
  ordem        smallint     not null default 0,
  ativa        boolean      not null default true
);

-- ------------------------------------------------ RESPOSTA_SATISFACAO
create table resposta_satisfacao (
  id_resposta  integer generated always as identity primary key,
  id_pesquisa  integer  not null references pesquisa_satisfacao (id_pesquisa),
  id_pergunta  integer  not null references pergunta_satisfacao (id_pergunta),
  nota         smallint not null,
  constraint uq_resposta_satisfacao unique (id_pesquisa, id_pergunta),
  constraint ck_resposta_satisfacao_nota check (nota between 0 and 10)
);
create index ix_resposta_satisfacao_pergunta on resposta_satisfacao (id_pergunta);

-- ------------------------------------------------ AVALIACAO_FORMULARIO
create table avaliacao_formulario (
  id_avaliacao      integer generated always as identity primary key,
  id_atendimento    uuid     not null references atendimento (id_atendimento),
  nota_estrelas     smallint not null,
  data_avaliacao    timestamptz not null default now(),
  data_atualizacao  timestamptz not null default now(),
  deletado_em       timestamptz,
  sync_seq          bigint      not null,
  constraint uq_avaliacao_atendimento unique (id_atendimento),
  constraint ck_avaliacao_estrelas check (nota_estrelas between 1 and 5)
);

-- --------------------------------------------------------- NOTIFICACAO
create table notificacao (
  id_notificacao  integer generated always as identity primary key,
  id_usuario      integer      not null references usuario (id_usuario),
  id_atendimento  uuid references atendimento (id_atendimento),
  tipo            varchar(30)  not null,
  mensagem        varchar(255) not null,
  lida            boolean      not null default false,
  data_criacao    timestamptz  not null default now(),
  constraint ck_notificacao_tipo check (tipo in
    ('novo_atendimento','atribuido','agendado','sincronizado','cadastro_aprovado','avaliacao_pendente')),
  -- sem endereços http/https na mensagem (proteção contra phishing)
  constraint ck_notificacao_mensagem check (mensagem !~* 'https?://')
);
create index ix_notificacao_usuario     on notificacao (id_usuario, lida);
create index ix_notificacao_atendimento on notificacao (id_atendimento);

-- ----------------------------------------------------- PERGUNTA_FORMULARIO
create table pergunta_formulario (
  id_pergunta_form   integer generated always as identity primary key,
  codigo             varchar(50)  not null,
  texto              varchar(200) not null,
  tipo_campo         varchar(20)  not null,
  opcoes             jsonb,
  regra_condicional  jsonb,
  motivo_aplicavel   varchar(30),
  obrigatoria        boolean  not null default false,
  ordem              smallint not null default 0,
  versao             integer  not null default 1,
  ativa              boolean  not null default true,
  data_criacao       timestamptz not null default now(),
  data_atualizacao   timestamptz not null default now(),
  deletado_em        timestamptz,
  sync_seq           bigint      not null,
  constraint uq_pergunta_codigo_versao unique (codigo, versao),
  constraint ck_pergunta_tipo check (tipo_campo in ('texto','numero','selecao','multisselecao','data')),
  -- opções só existem (e são obrigatórias) em selecao e multisselecao
  constraint ck_pergunta_opcoes check (
       (tipo_campo in ('selecao','multisselecao') and jsonb_typeof(opcoes) = 'array')
    or (tipo_campo not in ('selecao','multisselecao') and opcoes is null)),
  constraint ck_pergunta_motivo check (motivo_aplicavel is null or motivo_aplicavel in
    ('digital_farming','duvidas','entrega_tecnica','pos_venda','programacao_feiras','treinamento')),
  constraint ck_pergunta_versao check (versao >= 1)
);
-- uma única versão ativa por código
create unique index ux_pergunta_ativa on pergunta_formulario (codigo) where ativa and deletado_em is null;

-- ---------------------------------------- RESPOSTA_PERGUNTA_FORMULARIO
create table resposta_pergunta_formulario (
  id_resposta       integer generated always as identity primary key,
  id_atendimento    uuid    not null references atendimento (id_atendimento),
  id_pergunta_form  integer not null references pergunta_formulario (id_pergunta_form),
  valor_resposta    text,
  data_criacao      timestamptz not null default now(),
  data_atualizacao  timestamptz not null default now(),
  deletado_em       timestamptz,
  sync_seq          bigint      not null,
  constraint uq_resposta_form unique (id_atendimento, id_pergunta_form)
);
create index ix_resposta_form_pergunta on resposta_pergunta_formulario (id_pergunta_form);

-- ------------------------------------------------------ RESPOSTA_OPCAO
create table resposta_opcao (
  id_resposta        integer      not null references resposta_pergunta_formulario (id_resposta),
  opcao_selecionada  varchar(100) not null,
  data_atualizacao   timestamptz  not null default now(),
  deletado_em        timestamptz,
  sync_seq           bigint       not null,
  primary key (id_resposta, opcao_selecionada)
);

-- =====================================================================
-- A.5  SEGURANÇA, AUDITORIA E PRIVACIDADE
-- =====================================================================

-- --------------------------------------------------------- LOG_AUDITORIA
create table log_auditoria (
  -- o gatilho reatribui o número sob trava, na ordem real de gravação
  id_evento      bigint generated always as identity primary key,
  data_evento    timestamptz  not null default clock_timestamp(),
  id_usuario     integer references usuario (id_usuario),
  id_dispositivo uuid references dispositivo (id_dispositivo),
  acao           varchar(40)  not null,
  resultado      varchar(10)  not null,
  entidade_tipo  varchar(40),
  entidade_id    varchar(64),
  ip             inet,
  user_agent     varchar(255),
  correlacao_id  uuid,
  detalhes       jsonb,
  hash_anterior  char(64),
  hash_evento    char(64)     not null,
  constraint ck_audit_acao check (acao in
    ('login_sucesso','login_falha','logout','conta_bloqueada','senha_alterada','mfa_alterado',
     'cadastro_solicitado','cadastro_aprovado','cadastro_recusado','situacao_alterada',
     'perfil_alterado','permissao_concedida','permissao_revogada','acesso_negado',
     'relatorio_editado_pos_finalizacao','relatorio_baixado','evidencia_baixada',
     'exportacao_dados','dispositivo_revogado','limpeza_remota','dados_anonimizados',
     'solicitacao_titular','incidente_registrado','configuracao_alterada')),
  constraint ck_audit_resultado check (resultado in ('sucesso','falha','negado')),
  constraint ck_audit_hash check (hash_evento ~ '^[0-9a-f]{64}$'),
  constraint ck_audit_hash_ant check (hash_anterior is null or hash_anterior ~ '^[0-9a-f]{64}$')
);
create index ix_audit_usuario     on log_auditoria (id_usuario);
create index ix_audit_dispositivo on log_auditoria (id_dispositivo);
create index ix_audit_acao_data   on log_auditoria (acao, data_evento);

-- ------------------------------------------------------- TOKEN_ACESSO
create table token_acesso (
  id_token        uuid primary key default gen_random_uuid(),
  id_usuario      integer     not null references usuario (id_usuario),
  id_dispositivo  uuid references dispositivo (id_dispositivo),
  tipo            varchar(15) not null,
  -- SHA-256 do token em hexadecimal minúsculo; o token nunca é gravado
  token_hash      char(64)    not null,
  criado_em       timestamptz not null default now(),
  expira_em       timestamptz not null,
  usado_em        timestamptz,
  revogado_em     timestamptz,
  -- ao apagar o token antigo na limpeza, o vínculo vira nulo
  substituido_por uuid references token_acesso (id_token) on delete set null,
  ip              inet,
  constraint uq_token_hash unique (token_hash),
  constraint ck_token_tipo check (tipo in ('refresh','convite','reset_senha')),
  constraint ck_token_hash check (token_hash ~ '^[0-9a-f]{64}$'),
  -- validade posterior à criação e no máximo 90 dias (teto por tipo em fn_emitir_token)
  constraint ck_token_validade check (expira_em > criado_em and expira_em <= criado_em + interval '90 days')
);
create index ix_token_usuario     on token_acesso (id_usuario);
create index ix_token_dispositivo on token_acesso (id_dispositivo);
create index ix_token_substituido on token_acesso (substituido_por);

-- ------------------------------------------------- SOLICITACAO_TITULAR
create table solicitacao_titular (
  id_solicitacao          integer generated always as identity primary key,
  id_usuario              integer references usuario (id_usuario),
  nome_titular            varchar(150),
  email_contato           varchar(150) not null,
  tipo                    varchar(30)  not null,
  status                  varchar(15)  not null default 'recebida',
  data_solicitacao        timestamptz  not null default now(),
  -- padrão de 15 dias (LGPD, art. 19, II); prazo a confirmar com o jurídico
  data_prazo              timestamptz  not null default (now() + interval '15 days'),
  data_conclusao          timestamptz,
  id_responsavel          integer references usuario (id_usuario),
  justificativa_negativa  text,
  observacao              text,
  constraint ck_solic_tipo check (tipo in
    ('confirmacao','acesso','correcao','anonimizacao','eliminacao','portabilidade','compartilhamento','revogacao_consentimento')),
  constraint ck_solic_status check (status in ('recebida','em_analise','atendida','negada')),
  constraint ck_solic_prazo  check (data_prazo >= data_solicitacao),
  constraint ck_solic_negada check (status <> 'negada' or (justificativa_negativa is not null and length(btrim(justificativa_negativa)) > 0)),
  constraint ck_solic_conclusao check ((status in ('atendida','negada')) = (data_conclusao is not null))
);
create index ix_solic_usuario     on solicitacao_titular (id_usuario);
create index ix_solic_responsavel on solicitacao_titular (id_responsavel);

-- ----------------------------------------------- INCIDENTE_SEGURANCA
create table incidente_seguranca (
  id_incidente              integer generated always as identity primary key,
  data_deteccao             timestamptz  not null,
  data_ocorrencia           timestamptz,
  severidade                varchar(10)  not null,
  categoria                 varchar(25)  not null,
  descricao                 text         not null,
  dados_afetados            text,
  status                    varchar(15)  not null default 'aberto',
  data_contencao            timestamptz,
  comunicado_autoridade_em  timestamptz,
  comunicado_titulares_em   timestamptz,
  id_registrado_por         integer      not null references usuario (id_usuario),
  id_responsavel            integer references usuario (id_usuario),
  licoes_aprendidas         text,
  data_criacao              timestamptz  not null default now(),
  data_atualizacao          timestamptz  not null default now(),
  constraint ck_incidente_severidade check (severidade in ('baixa','media','alta','critica')),
  constraint ck_incidente_categoria  check (categoria in
    ('acesso_indevido','vazamento','perda_dispositivo','malware','indisponibilidade','outro')),
  constraint ck_incidente_status check (status in ('aberto','contido','comunicado','encerrado')),
  constraint ck_incidente_ocorrencia check (data_ocorrencia is null or data_ocorrencia <= data_deteccao),
  constraint ck_incidente_contencao check (status = 'aberto' or data_contencao is not null)
);
create index ix_incidente_registrado_por on incidente_seguranca (id_registrado_por);
create index ix_incidente_responsavel    on incidente_seguranca (id_responsavel);

-- --------------------------------------------- DOMINIO_EMAIL_CORPORATIVO
create table dominio_email_corporativo (
  id_dominio        integer generated always as identity primary key,
  dominio           varchar(120) not null,
  tipo_organizacao  varchar(15)  not null,
  id_distribuidor   integer references distribuidor (id_distribuidor),
  ativo             boolean      not null default true,
  data_criacao      timestamptz  not null default now(),
  constraint uq_dominio unique (dominio),
  -- minúsculo e sem @
  constraint ck_dominio_formato check (dominio = lower(dominio) and dominio !~ '@'),
  constraint ck_dominio_tipo check (tipo_organizacao in ('netafim','distribuidor')),
  constraint ck_dominio_distribuidor check (
       (tipo_organizacao = 'distribuidor' and id_distribuidor is not null)
    or (tipo_organizacao = 'netafim'      and id_distribuidor is null))
);
create index ix_dominio_distribuidor on dominio_email_corporativo (id_distribuidor);

-- =====================================================================
-- Índices do cursor de sincronização (pull incremental por sync_seq)
-- =====================================================================
create unique index ux_distribuidor_sync   on distribuidor (sync_seq);
create unique index ux_cliente_sync        on cliente (sync_seq);
create unique index ux_propriedade_sync    on propriedade (sync_seq);
create unique index ux_cultura_sync        on cultura (sync_seq);
create unique index ux_prop_cultura_sync   on propriedade_cultura (sync_seq);
create unique index ux_agendamento_sync    on agendamento (sync_seq);
create unique index ux_categoria_sync      on problema_categoria (sync_seq);
create unique index ux_subcategoria_sync   on problema_subcategoria (sync_seq);
create unique index ux_atend_problema_sync on atendimento_problema (sync_seq);
create unique index ux_atend_cultura_sync  on atendimento_cultura (sync_seq);
create unique index ux_tipo_trein_sync     on tipo_treinamento (sync_seq);
create unique index ux_atend_tipo_trein_sync on atendimento_tipo_treinamento (sync_seq);
create unique index ux_relatorio_sync      on relatorio_atendimento (sync_seq);
create unique index ux_mcr_sync            on motivo_campo_relatorio (sync_seq);
create unique index ux_rel_esp_sync        on relatorio_especialista (sync_seq);
create unique index ux_evidencia_sync      on evidencia (sync_seq);
create unique index ux_assinatura_sync     on assinatura (sync_seq);
create unique index ux_avaliacao_sync      on avaliacao_formulario (sync_seq);
create unique index ux_pergunta_form_sync  on pergunta_formulario (sync_seq);
create unique index ux_resposta_form_sync  on resposta_pergunta_formulario (sync_seq);
create unique index ux_resposta_opcao_sync on resposta_opcao (sync_seq);

-- ---------- 02_funcoes_gatilhos.sql ----------
-- =====================================================================
-- 02_funcoes_gatilhos.sql
-- Netafim Pós-venda | Banco central (PostgreSQL / Supabase)
--
-- O QUE FAZ: cria as funções e os gatilhos (triggers) que fazem o BANCO
-- garantir sozinho as regras da seção 5.1 do documento: protocolo,
-- máquina de estados, relatório finalizado, assinatura, encerramento,
-- histórico imutável, auditoria encadeada, tokens, anonimização etc.
--
-- CONVENÇÕES
--  * Funções SECURITY DEFINER rodam com o dono do schema (postgres) e por
--    isso enxergam todas as tabelas; todas fixam o search_path para
--    impedir sequestro de função.
--  * O usuário logado chega ao banco pela variável de sessão
--    app.current_user_id (a API faz SET LOCAL a cada requisição).
--  * Quem age em nome do sistema (processo automático) liga a variável
--    app.acao_sistema = 'on' dentro da transação.
--  * Mensagens de erro de regra usam SQLSTATE 23514 (check_violation)
--    para a API conseguir traduzir em resposta HTTP 400/409.
-- =====================================================================

-- =====================================================================
-- 1. CONTEXTO DA SESSÃO (quem está logado)
-- =====================================================================

-- Id do usuário logado (nulo se a API não informou ninguém).
create function app_usuario_id() returns integer
language sql stable
as $$
  select nullif(current_setting('app.current_user_id', true), '')::integer
$$;

-- Verdadeiro quando o processo automático está agindo.
create function app_acao_sistema() returns boolean
language sql stable
as $$
  select coalesce(current_setting('app.acao_sistema', true), '') = 'on'
$$;

-- Verdadeiro durante a carga inicial (04_dados_iniciais.sql): evita
-- encher a auditoria com eventos de configuração.
create function app_carga_inicial() returns boolean
language sql stable
as $$
  select coalesce(current_setting('app.carga_inicial', true), '') = 'on'
$$;

-- Usuário logado está ativo? (existe, situacao = ativo, não excluído)
create function app_usuario_ativo() returns boolean
language sql stable security definer
set search_path = public, pg_temp
as $$
  select exists (
    select 1 from usuario u
     where u.id_usuario = app_usuario_id()
       and u.situacao = 'ativo'
       and u.deletado_em is null)
$$;

-- Tipo de organização do usuário logado (netafim | distribuidor | cliente).
create function app_tipo_org() returns text
language sql stable security definer
set search_path = public, pg_temp
as $$
  select u.tipo_organizacao::text from usuario u
   where u.id_usuario = app_usuario_id()
     and u.situacao = 'ativo' and u.deletado_em is null
$$;

-- Nível do perfil do usuário logado (admin | usuario).
create function app_nivel() returns text
language sql stable security definer
set search_path = public, pg_temp
as $$
  select p.nivel::text
    from usuario u join perfil p on p.id_perfil = u.id_perfil
   where u.id_usuario = app_usuario_id()
     and u.situacao = 'ativo' and u.deletado_em is null
$$;

-- Distribuidor do usuário logado (se for de distribuidor).
create function app_id_distribuidor() returns integer
language sql stable security definer
set search_path = public, pg_temp
as $$
  select u.id_distribuidor from usuario u
   where u.id_usuario = app_usuario_id()
     and u.situacao = 'ativo' and u.deletado_em is null
$$;

-- Cliente do usuário logado (se for de cliente).
create function app_id_cliente() returns integer
language sql stable security definer
set search_path = public, pg_temp
as $$
  select u.id_cliente from usuario u
   where u.id_usuario = app_usuario_id()
     and u.situacao = 'ativo' and u.deletado_em is null
$$;

-- O usuário logado é Netafim Admin?
create function app_eh_admin_netafim() returns boolean
language sql stable security definer
set search_path = public, pg_temp
as $$
  select coalesce(app_tipo_org() = 'netafim' and app_nivel() = 'admin', false)
$$;

-- O perfil do usuário logado tem esta permissão (matriz do Anexo B)?
create function app_tem_permissao(p_codigo text) returns boolean
language sql stable security definer
set search_path = public, pg_temp
as $$
  select exists (
    select 1
      from usuario u
      join perfil_permissao pp on pp.id_perfil = u.id_perfil
      join permissao pe on pe.id_permissao = pp.id_permissao
     where u.id_usuario = app_usuario_id()
       and u.situacao = 'ativo' and u.deletado_em is null
       and pe.codigo = p_codigo)
$$;

-- Um usuário (qualquer id) é Netafim Admin ativo? Usada nos gatilhos.
create function fn_usuario_e_admin_netafim(p_id integer) returns boolean
language sql stable security definer
set search_path = public, pg_temp
as $$
  select exists (
    select 1 from usuario u join perfil p on p.id_perfil = u.id_perfil
     where u.id_usuario = p_id
       and u.situacao = 'ativo' and u.deletado_em is null
       and p.tipo_organizacao = 'netafim' and p.nivel = 'admin')
$$;

-- A regra de visibilidade em si, calculada a partir das colunas da linha.
-- Existe separada porque a policy de leitura precisa avaliar a linha NOVA de
-- um INSERT ... RETURNING: nesse momento ela ainda não existe na tabela, então
-- uma função que relê o atendimento pelo id não a encontraria.
create function app_atendimento_visivel_linha(
  p_solicitante integer, p_tecnico integer, p_distribuidor integer,
  p_distribuidor_origem integer, p_cliente integer
) returns boolean
language plpgsql stable security definer
set search_path = public, pg_temp
as $$
declare
  v_uid   integer := app_usuario_id();
  v_tipo  text;
  v_nivel text;
  v_dist  integer;
  v_cli   integer;
begin
  if v_uid is null then return false; end if;

  select u.tipo_organizacao, p.nivel, u.id_distribuidor, u.id_cliente
    into v_tipo, v_nivel, v_dist, v_cli
    from usuario u join perfil p on p.id_perfil = u.id_perfil
   where u.id_usuario = v_uid and u.situacao = 'ativo' and u.deletado_em is null;
  if not found then return false; end if;

  if v_tipo = 'netafim' and v_nivel = 'admin' then
    return true;
  elsif v_nivel = 'usuario' and v_tipo in ('netafim','distribuidor') then
    return p_solicitante = v_uid or p_tecnico = v_uid;
  elsif v_tipo = 'distribuidor' and v_nivel = 'admin' then
    return p_distribuidor = v_dist
        or p_distribuidor_origem = v_dist
        or exists (select 1 from cliente c
                    where c.id_cliente = p_cliente and c.id_distribuidor = v_dist);
  elsif v_tipo = 'cliente' and v_nivel = 'admin' then
    return p_cliente = v_cli;
  elsif v_tipo = 'cliente' and v_nivel = 'usuario' then
    return p_solicitante = v_uid;
  end if;
  return false;
end
$$;

-- Seção 5.3: quem vê o quê. Recebe o id do atendimento.
--   Netafim Admin        todos
--   Netafim Usuário      os que abriu e os atribuídos a ele
--   Distribuidor Admin   destinados ao seu distribuidor, os de origem dele
--                        e os de clientes da sua carteira
--   Distribuidor Usuário os que abriu e os atribuídos a ele
--   Cliente Admin        todos da sua empresa
--   Cliente Usuário      somente os que abriu
create function app_atendimento_visivel(p_id uuid) returns boolean
language plpgsql stable security definer
set search_path = public, pg_temp
as $$
declare
  a atendimento%rowtype;
begin
  select * into a from atendimento where id_atendimento = p_id;
  if not found then return false; end if;
  return app_atendimento_visivel_linha(a.id_solicitante, a.id_tecnico_responsavel,
           a.id_distribuidor, a.id_distribuidor_origem, a.id_cliente);
end
$$;

-- Pode alterar o atendimento (e o que pende dele)? Perfis de Netafim e
-- distribuidor alteram o que enxergam; perfis de cliente só o que abriram.
create function app_atendimento_operavel(p_id uuid) returns boolean
language plpgsql stable security definer
set search_path = public, pg_temp
as $$
declare
  v_tipo text := app_tipo_org();
begin
  if not app_atendimento_visivel(p_id) then return false; end if;
  if v_tipo in ('netafim','distribuidor') then return true; end if;
  return exists (select 1 from atendimento a
                  where a.id_atendimento = p_id and a.id_solicitante = app_usuario_id());
end
$$;

-- Cliente visível para o usuário logado (usado nas policies de cliente,
-- propriedade e cultura da propriedade).
create function app_cliente_visivel(p_id_cliente integer) returns boolean
language plpgsql stable security definer
set search_path = public, pg_temp
as $$
declare
  v_tipo  text := app_tipo_org();
  v_nivel text := app_nivel();
begin
  if v_tipo is null then return false; end if;
  if v_tipo = 'netafim' then return true; end if;
  if v_tipo = 'distribuidor' then
    return exists (select 1 from cliente c
                    where c.id_cliente = p_id_cliente
                      and c.id_distribuidor = app_id_distribuidor());
  end if;
  return p_id_cliente = app_id_cliente();
end
$$;

-- Atendimento ao qual um relatório pertence (para policies dos filhos).
create function app_atendimento_do_relatorio(p_id_relatorio uuid) returns uuid
language sql stable security definer
set search_path = public, pg_temp
as $$
  select id_atendimento from relatorio_atendimento where id_relatorio = p_id_relatorio
$$;

-- Atendimento ao qual uma pesquisa pertence.
create function app_atendimento_da_pesquisa(p_id_pesquisa integer) returns uuid
language sql stable security definer
set search_path = public, pg_temp
as $$
  select id_atendimento from pesquisa_satisfacao where id_pesquisa = p_id_pesquisa
$$;

-- Atendimento ao qual uma resposta do formulário pertence.
create function app_atendimento_da_resposta(p_id_resposta integer) returns uuid
language sql stable security definer
set search_path = public, pg_temp
as $$
  select id_atendimento from resposta_pergunta_formulario where id_resposta = p_id_resposta
$$;

-- Prazo de espera pela avaliação do cliente, em dias. Padrão 7 (proposta
-- do grupo, pendente com a Netafim). Para mudar sem editar o código:
--   alter database postgres set app.prazo_avaliacao_dias = '10';
create function fn_prazo_avaliacao_dias() returns integer
language sql stable
as $$
  select coalesce(nullif(current_setting('app.prazo_avaliacao_dias', true), '')::integer, 7)
$$;

-- =====================================================================
-- 2. GATILHOS GENÉRICOS
-- =====================================================================

-- data_atualizacao = relógio do servidor (usuario e incidente_seguranca)
create function fn_touch() returns trigger
language plpgsql
as $$
begin
  new.data_atualizacao := now();
  return new;
end
$$;

-- data_atualizacao + sync_seq (todas as tabelas que descem ao celular).
-- O valor de sync_seq informado pelo cliente é SEMPRE ignorado.
create function fn_sync_meta() returns trigger
language plpgsql security definer
set search_path = public, pg_temp
as $$
begin
  new.data_atualizacao := now();
  new.sync_seq := nextval('seq_sync');
  return new;
end
$$;

-- Tabela somente inserção: bloqueia UPDATE, DELETE e TRUNCATE.
create function fn_somente_insercao() returns trigger
language plpgsql
as $$
begin
  raise exception 'a tabela % é somente inserção: % não é permitido', tg_table_name, tg_op
    using errcode = '23514';
end
$$;

-- =====================================================================
-- 3. AUDITORIA ENCADEADA (LOG_AUDITORIA)
-- =====================================================================

-- Texto canônico de um evento: tudo o que entra no hash. Usa horário em
-- UTC para o hash não depender do fuso da sessão.
create function fn_audit_payload(r log_auditoria) returns text
language sql immutable
as $$
  select jsonb_build_array(
           r.id_evento,
           to_char(r.data_evento at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US'),
           r.id_usuario, r.id_dispositivo, r.acao, r.resultado,
           r.entidade_tipo, r.entidade_id, r.ip::text, r.user_agent,
           r.correlacao_id, r.detalhes, r.hash_anterior)::text
$$;

-- Antes de gravar: trava, encadeia com o evento anterior e calcula o hash.
create function fn_audit_antes() returns trigger
language plpgsql security definer
set search_path = public, pg_temp
as $$
declare
  v_ant char(64);
begin
  -- uma trava para todo mundo: a ordem do número = a ordem da cadeia
  perform pg_advisory_xact_lock(hashtext('log_auditoria'));

  -- guarda contra dado pessoal e segredo dentro de "detalhes"
  if new.detalhes is not null then
    if new.detalhes::text ~* '[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}' then
      raise exception 'LOG_AUDITORIA.detalhes não pode conter e-mail' using errcode = '23514';
    end if;
    if new.detalhes::text ~* '"[^"]*(password|senha|token|secret|segredo)[^"]*"\s*:' then
      raise exception 'LOG_AUDITORIA.detalhes não pode conter segredos' using errcode = '23514';
    end if;
  end if;

  select hash_evento into v_ant from log_auditoria order by id_evento desc limit 1;

  -- id_evento ja vem preenchido pela identity (o padrao roda antes do
  -- gatilho); nao chamar nextval de novo, senao a numeracao fica com
  -- buracos que parecem eventos apagados.
  new.data_evento   := clock_timestamp();
  new.hash_anterior := v_ant;
  new.hash_evento   := encode(sha256(convert_to(fn_audit_payload(new), 'UTF8')), 'hex');
  return new;
end
$$;

-- Único caminho de escrita na auditoria. A API chama esta função.
create function fn_audit(
  p_acao           text,
  p_resultado      text    default 'sucesso',
  p_entidade_tipo  text    default null,
  p_entidade_id    text    default null,
  p_detalhes       jsonb   default null,
  p_id_usuario     integer default null,
  p_id_dispositivo uuid    default null,
  p_ip             inet    default null,
  p_user_agent     text    default null,
  p_correlacao_id  uuid    default null
) returns bigint
language plpgsql security definer
set search_path = public, pg_temp
as $$
declare
  v_id bigint;
begin
  -- a trava vem ANTES do insert, para o número ser gerado dentro dela
  perform pg_advisory_xact_lock(hashtext('log_auditoria'));
  insert into log_auditoria
         (id_usuario, id_dispositivo, acao, resultado, entidade_tipo, entidade_id,
          ip, user_agent, correlacao_id, detalhes, hash_evento)
  values (coalesce(p_id_usuario, app_usuario_id()), p_id_dispositivo, p_acao, p_resultado,
          p_entidade_tipo, left(p_entidade_id, 64), p_ip, left(p_user_agent, 255),
          p_correlacao_id, p_detalhes, repeat('0', 64))   -- o gatilho recalcula o hash
  returning id_evento into v_id;
  return v_id;
end
$$;

-- Percorre a cadeia inteira e confere cada elo. Devolve uma linha.
create function fn_verifica_cadeia_auditoria()
returns table (valido boolean, total_eventos bigint, primeiro_evento_invalido bigint, motivo text)
language plpgsql stable security definer
set search_path = public, pg_temp
as $$
declare
  r        log_auditoria%rowtype;
  v_prev   char(64) := null;
  v_total  bigint   := 0;
begin
  for r in select * from log_auditoria order by id_evento loop
    v_total := v_total + 1;
    if r.hash_anterior is distinct from v_prev then
      return query select false, v_total, r.id_evento,
        'elo quebrado: hash_anterior não confere com o evento anterior (evento removido ou alterado)'::text;
      return;
    end if;
    if r.hash_evento <> encode(sha256(convert_to(fn_audit_payload(r), 'UTF8')), 'hex') then
      return query select false, v_total, r.id_evento,
        'hash do evento não confere com o conteúdo (evento alterado)'::text;
      return;
    end if;
    v_prev := r.hash_evento;
  end loop;
  return query select true, v_total, null::bigint, null::text;
end
$$;

-- Âncora: último evento e seu hash. Exportar periodicamente para um
-- armazenamento imutável fora do banco; é o que permite detectar a
-- remoção dos eventos mais recentes.
create function fn_ancora_auditoria()
returns table (id_evento bigint, hash_evento char(64), total_eventos bigint, gerada_em timestamptz)
language sql stable security definer
set search_path = public, pg_temp
as $$
  select l.id_evento, l.hash_evento, (select count(*) from log_auditoria), now()
    from log_auditoria l order by l.id_evento desc limit 1
$$;

create trigger trg_audit_antes before insert on log_auditoria
  for each row execute function fn_audit_antes();
create trigger trg_audit_imutavel before update or delete on log_auditoria
  for each row execute function fn_somente_insercao();
create trigger trg_audit_truncate before truncate on log_auditoria
  for each statement execute function fn_somente_insercao();

-- =====================================================================
-- 4. HISTÓRICO DO ATENDIMENTO
-- =====================================================================

-- Grava um evento no histórico. A origem é "usuario" quando há alguém
-- logado e o processo automático não está agindo; senão "sistema".
create function fn_hist_registrar(
  p_id_atendimento uuid,
  p_tipo           text,
  p_anterior       text,
  p_novo           text,
  p_observacao     text    default null,
  p_forcar_sistema boolean default false
) returns void
language plpgsql security definer
set search_path = public, pg_temp
as $$
declare
  v_uid     integer := app_usuario_id();
  v_sistema boolean := p_forcar_sistema or app_acao_sistema() or v_uid is null;
begin
  insert into historico_atendimento
         (id_atendimento, tipo_evento, origem_evento, valor_anterior, valor_novo,
          id_usuario_responsavel, observacao)
  values (p_id_atendimento, p_tipo,
          case when v_sistema then 'sistema' else 'usuario' end,
          left(p_anterior, 100), left(coalesce(p_novo, ''), 100),
          case when v_sistema then null else v_uid end,
          p_observacao);
end
$$;

create trigger trg_hist_imutavel before update or delete on historico_atendimento
  for each row execute function fn_somente_insercao();
create trigger trg_hist_truncate before truncate on historico_atendimento
  for each statement execute function fn_somente_insercao();

-- =====================================================================
-- 5. ATENDIMENTO: protocolo, máquina de estados, encerramento
-- =====================================================================

-- Próximo protocolo OS-#### (4 dígitos com zeros; passa de 9999 sem cortar).
create function fn_proximo_protocolo() returns text
language plpgsql security definer
set search_path = public, pg_temp
as $$
declare
  v_n bigint := nextval('seq_protocolo');
begin
  return 'OS-' || case when v_n < 10000 then lpad(v_n::text, 4, '0') else v_n::text end;
end
$$;

create function fn_atendimento_antes() returns trigger
language plpgsql security definer
set search_path = public, pg_temp
as $$
declare
  v_pesq  pesquisa_satisfacao%rowtype;
  v_uid   integer := app_usuario_id();
  v_sist  boolean := app_acao_sistema();
begin
  if tg_op = 'INSERT' then
    -- o protocolo é sempre do servidor; o valor informado é ignorado
    new.numero_protocolo := fn_proximo_protocolo();
    if new.status <> 'aberto' then
      raise exception 'um atendimento novo precisa nascer com status aberto (recebido: %)', new.status
        using errcode = '23514';
    end if;
    new.versao := 1;
    -- destinação e técnico não nascem no pedido: o Netafim Admin destina e
    -- quem tem a permissão vincular_tecnico aloca o técnico
    if not (v_sist or app_eh_admin_netafim())
       and (new.destinado_a is not null or new.id_distribuidor is not null) then
      raise exception 'somente o Netafim Admin define a destinação do atendimento' using errcode = '23514';
    end if;
    if new.id_tecnico_responsavel is not null and not v_sist
       and not app_tem_permissao('vincular_tecnico') then
      raise exception 'seu perfil não pode vincular técnico ao atendimento' using errcode = '23514';
    end if;
    return new;
  end if;

  -- UPDATE ----------------------------------------------------------
  if new.id_atendimento <> old.id_atendimento then
    raise exception 'id_atendimento não pode mudar' using errcode = '23514';
  end if;
  new.numero_protocolo := old.numero_protocolo;   -- imutável
  new.versao := old.versao + 1;                    -- concorrência otimista

  -- estados finais (encerrado, encerrado sem avaliação e cancelado) não
  -- aceitam mais alteração, salvo do Netafim Admin ou do processo automático
  if old.status in ('encerrado','encerrado_sem_avaliacao','cancelado')
     and not (v_sist or app_eh_admin_netafim()) then
    raise exception 'atendimento em estado final (%) não pode mais ser alterado', old.status
      using errcode = '23514';
  end if;

  if new.status is distinct from old.status then
    -- transições permitidas (seção 5.2)
    if not ((old.status, new.status) in (
         ('aberto','destinado'),         ('aberto','cancelado'),
         ('destinado','agendado'),       ('destinado','cancelado'),
         ('agendado','em_atendimento'),  ('agendado','cancelado'),
         ('em_atendimento','resolvido'), ('em_atendimento','cancelado'),
         ('resolvido','encerrado'),      ('resolvido','encerrado_sem_avaliacao'))) then
      raise exception 'transição de status não permitida: % -> %', old.status, new.status
        using errcode = '23514';
    end if;

    -- quem pode fazer cada transição
    if new.status = 'destinado' and not (v_sist or app_eh_admin_netafim()) then
      raise exception 'somente o Netafim Admin destina o atendimento' using errcode = '23514';
    end if;
    if new.status in ('cancelado','encerrado','encerrado_sem_avaliacao')
       and not (v_sist or app_eh_admin_netafim()) then
      raise exception 'somente o Netafim Admin ou o processo automático pode mudar para %', new.status
        using errcode = '23514';
    end if;

    -- o que cada estado exige
    if new.status = 'agendado' then
      if not exists (select 1 from agendamento g
                      where g.id_atendimento = new.id_atendimento
                        and g.status = 'ativo' and g.deletado_em is null) then
        raise exception 'agendar exige um agendamento ativo' using errcode = '23514';
      end if;
    elsif new.status = 'resolvido' then
      if not exists (select 1 from relatorio_atendimento r
                      where r.id_atendimento = new.id_atendimento
                        and r.status = 'finalizado' and r.deletado_em is null) then
        raise exception 'resolver exige o relatório finalizado' using errcode = '23514';
      end if;
      new.data_conclusao := coalesce(new.data_conclusao, now());
    elsif new.status = 'encerrado' then
      select * into v_pesq from pesquisa_satisfacao where id_atendimento = new.id_atendimento;
      if not found or v_pesq.data_resposta is null then
        raise exception 'encerrar exige a pesquisa de satisfação respondida' using errcode = '23514';
      end if;
      new.data_encerramento := coalesce(new.data_encerramento, now());
    elsif new.status = 'encerrado_sem_avaliacao' then
      if now() < old.data_conclusao + make_interval(days => fn_prazo_avaliacao_dias()) then
        raise exception 'o prazo de espera pela avaliação (% dias) ainda não venceu', fn_prazo_avaliacao_dias()
          using errcode = '23514';
      end if;
      if exists (select 1 from pesquisa_satisfacao p
                  where p.id_atendimento = new.id_atendimento and p.data_resposta is not null) then
        raise exception 'a pesquisa já foi respondida: o encerramento é "encerrado"' using errcode = '23514';
      end if;
      new.data_encerramento := coalesce(new.data_encerramento, now());
    end if;
  end if;

  -- destinação (Netafim ou distribuidor): só o Netafim Admin (RN10)
  if (new.destinado_a, new.id_distribuidor) is distinct from (old.destinado_a, old.id_distribuidor)
     and not (v_sist or app_eh_admin_netafim()) then
    raise exception 'somente o Netafim Admin altera a destinação do atendimento' using errcode = '23514';
  end if;

  -- vínculo do técnico: exige a permissão vincular_tecnico; o Distribuidor
  -- Admin só vincula técnicos do próprio distribuidor (RN05)
  if new.id_tecnico_responsavel is distinct from old.id_tecnico_responsavel and not v_sist then
    if not app_tem_permissao('vincular_tecnico') then
      raise exception 'seu perfil não pode vincular técnico ao atendimento' using errcode = '23514';
    end if;
    if new.id_tecnico_responsavel is not null and app_tipo_org() = 'distribuidor'
       and not exists (select 1 from usuario t
                        where t.id_usuario = new.id_tecnico_responsavel
                          and t.id_distribuidor = app_id_distribuidor()) then
      raise exception 'o Distribuidor Admin só vincula técnicos do próprio distribuidor' using errcode = '23514';
    end if;
  end if;

  -- alteração feita pelo sistema fica sem autor
  if v_sist then new.id_atualizado_por := null;
  elsif v_uid is not null then new.id_atualizado_por := v_uid;
  end if;
  return new;
end
$$;

-- Depois de gravar: escreve os eventos no histórico.
create function fn_atendimento_depois() returns trigger
language plpgsql security definer
set search_path = public, pg_temp
as $$
begin
  if tg_op = 'INSERT' then
    perform fn_hist_registrar(new.id_atendimento, 'criado', null, new.status);
    perform fn_hist_registrar(new.id_atendimento, 'protocolo_gerado', null, new.numero_protocolo,
                              null, true);
    return null;
  end if;

  if new.status is distinct from old.status then
    perform fn_hist_registrar(new.id_atendimento, 'status_alterado', old.status, new.status);
  end if;
  if new.destinado_a is distinct from old.destinado_a
     or new.id_distribuidor is distinct from old.id_distribuidor then
    perform fn_hist_registrar(new.id_atendimento, 'destinado',
      concat_ws(':', old.destinado_a, old.id_distribuidor::text),
      concat_ws(':', new.destinado_a, new.id_distribuidor::text));
  end if;
  if new.id_tecnico_responsavel is distinct from old.id_tecnico_responsavel then
    perform fn_hist_registrar(new.id_atendimento, 'atribuido',
      old.id_tecnico_responsavel::text, coalesce(new.id_tecnico_responsavel::text, 'nenhum'));
  end if;
  if new.deletado_em is not null and old.deletado_em is null then
    perform fn_hist_registrar(new.id_atendimento, 'excluido', null, 'exclusao_logica');
  end if;
  return null;
end
$$;

create trigger trg_atendimento_antes before insert or update on atendimento
  for each row execute function fn_atendimento_antes();
create trigger trg_atendimento_depois after insert or update on atendimento
  for each row execute function fn_atendimento_depois();
create trigger trg_atendimento_sync before insert or update on atendimento
  for each row execute function fn_sync_meta();

-- ---------------------------------------------------------- AGENDAMENTO
-- Cada reagendamento cria uma linha nova e cancela a anterior.
create function fn_agendamento_antes() returns trigger
language plpgsql security definer
set search_path = public, pg_temp
as $$
begin
  if new.status = 'ativo' and new.deletado_em is null then
    update agendamento
       set status = 'cancelado'
     where id_atendimento = new.id_atendimento
       and status = 'ativo' and deletado_em is null
       and id_agendamento is distinct from new.id_agendamento;
  end if;
  return new;
end
$$;

create function fn_agendamento_depois() returns trigger
language plpgsql security definer
set search_path = public, pg_temp
as $$
declare
  v_reagendado boolean;
begin
  if new.status = 'ativo' then
    select exists (select 1 from agendamento g
                    where g.id_atendimento = new.id_atendimento
                      and g.id_agendamento <> new.id_agendamento
                      and g.status = 'cancelado' and g.deletado_em is null)
      into v_reagendado;
    perform fn_hist_registrar(new.id_atendimento,
      case when v_reagendado then 'reagendado' else 'agendado' end, null,
      to_char(new.data_hora_agendada at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI"Z"'));
  end if;
  return null;
end
$$;

create trigger trg_agendamento_antes before insert on agendamento
  for each row execute function fn_agendamento_antes();
create trigger trg_agendamento_depois after insert on agendamento
  for each row execute function fn_agendamento_depois();
create trigger trg_agendamento_sync before insert or update on agendamento
  for each row execute function fn_sync_meta();

-- ---------------------------------------------- ATENDIMENTO_PROBLEMA
-- A categoria precisa combinar com o motivo do atendimento (Digital
-- Farming ou Pós-venda).
create function fn_atend_problema_antes() returns trigger
language plpgsql security definer
set search_path = public, pg_temp
as $$
declare
  v_motivo text;
  v_aplic  text;
begin
  select motivo into v_motivo from atendimento where id_atendimento = new.id_atendimento;
  select aplicavel_a into v_aplic from problema_categoria where id_categoria = new.id_categoria;
  if v_motivo is distinct from v_aplic then
    raise exception 'a categoria só se aplica ao motivo %, mas o atendimento é de %', v_aplic, v_motivo
      using errcode = '23514';
  end if;
  return new;
end
$$;

-- Mudança de classificação depois de o atendimento sair de "aberto"
-- vira evento "reclassificado" no histórico.
create function fn_atend_problema_depois() returns trigger
language plpgsql security definer
set search_path = public, pg_temp
as $$
declare
  v_status text;
  v_nome   text;
begin
  select status into v_status from atendimento where id_atendimento = new.id_atendimento;
  if v_status <> 'aberto' then
    select c.nome || coalesce(' / ' || s.descricao, '')
      into v_nome
      from problema_categoria c
      left join problema_subcategoria s on s.id_subcategoria = new.id_subcategoria
     where c.id_categoria = new.id_categoria;
    if tg_op = 'INSERT' then
      perform fn_hist_registrar(new.id_atendimento, 'reclassificado', null, v_nome);
    elsif new.deletado_em is not null and old.deletado_em is null then
      perform fn_hist_registrar(new.id_atendimento, 'reclassificado', v_nome, 'removida');
    end if;
  end if;
  return null;
end
$$;

create trigger trg_atend_problema_antes before insert or update on atendimento_problema
  for each row execute function fn_atend_problema_antes();
create trigger trg_atend_problema_depois after insert or update on atendimento_problema
  for each row execute function fn_atend_problema_depois();

-- =====================================================================
-- 6. RELATÓRIO: conteúdo, finalização, edição protegida
-- =====================================================================

-- Hash SHA-256 do conteúdo canônico do relatório (o que o assinante vê).
create function fn_conteudo_hash(r relatorio_atendimento) returns char(64)
language sql immutable
as $$
  select encode(sha256(convert_to(jsonb_build_object(
           'id_relatorio',           r.id_relatorio,
           'versao',                 r.versao,
           'descricao_constatacoes', r.descricao_constatacoes,
           'atividades_executadas',  r.atividades_executadas,
           'plano_acao',             r.plano_acao,
           'conclusao_visita',       r.conclusao_visita,
           'local_treinamento',      r.local_treinamento,
           'qtd_pessoas_treinadas',  r.qtd_pessoas_treinadas,
           'estacao_funcionando',    r.estacao_funcionando)::text, 'UTF8')), 'hex')::char(64)
$$;

-- Versão que busca o relatório pelo id.
create function fn_conteudo_hash(p_id_relatorio uuid) returns char(64)
language sql stable security definer
set search_path = public, pg_temp
as $$
  select fn_conteudo_hash(r) from relatorio_atendimento r where r.id_relatorio = p_id_relatorio
$$;

-- Confere tudo o que a finalização exige e lista o que falta.
create function fn_valida_finalizacao(r relatorio_atendimento) returns void
language plpgsql stable security definer
set search_path = public, pg_temp
as $$
declare
  a      atendimento%rowtype;
  m      record;
  v_hash char(64) := fn_conteudo_hash(r);
  faltas text[]   := '{}';
begin
  select * into a from atendimento where id_atendimento = r.id_atendimento;

  -- exatamente um especialista responsável
  if not exists (select 1 from relatorio_especialista e
                  where e.id_relatorio = r.id_relatorio
                    and e.papel = 'responsavel' and e.deletado_em is null) then
    faltas := array_append(faltas, 'especialista responsável'::text);
  end if;

  -- obrigatoriedades do motivo (tabela de configuração)
  for m in select campo from motivo_campo_relatorio
            where motivo = a.motivo and obrigatorio and deletado_em is null
  loop
    case m.campo
      when 'tipo_atendimento' then
        if a.tipo_atendimento is null then faltas := array_append(faltas, 'tipo do atendimento'::text); end if;
      when 'tipo_treinamento' then
        if not exists (select 1 from atendimento_tipo_treinamento t
                        where t.id_atendimento = a.id_atendimento and t.deletado_em is null) then
          faltas := array_append(faltas, 'tipo de treinamento'::text); end if;
      when 'descricao_constatacoes' then
        if r.descricao_constatacoes is null or btrim(r.descricao_constatacoes) = '' then
          faltas := array_append(faltas, 'constatações'::text); end if;
      when 'atividades_executadas' then
        if r.atividades_executadas is null or btrim(r.atividades_executadas) = '' then
          faltas := array_append(faltas, 'atividades executadas'::text); end if;
      when 'plano_acao' then
        if r.plano_acao is null or btrim(r.plano_acao) = '' then
          faltas := array_append(faltas, 'plano de ação'::text); end if;
      when 'conclusao_visita' then
        if r.conclusao_visita is null or btrim(r.conclusao_visita) = '' then
          faltas := array_append(faltas, 'conclusão da visita'::text); end if;
      when 'local_treinamento' then
        if r.local_treinamento is null or btrim(r.local_treinamento) = '' then
          faltas := array_append(faltas, 'local do treinamento'::text); end if;
      when 'qtd_pessoas_treinadas' then
        if r.qtd_pessoas_treinadas is null then faltas := array_append(faltas, 'número de pessoas'::text); end if;
      when 'evidencia_constatacao' then
        if not exists (select 1 from evidencia e where e.id_atendimento = a.id_atendimento
                          and e.etapa = 'constatacao' and e.deletado_em is null) then
          faltas := array_append(faltas, 'foto/vídeo das constatações'::text); end if;
      when 'evidencia_atividade' then
        if not exists (select 1 from evidencia e where e.id_atendimento = a.id_atendimento
                          and e.etapa = 'atividade' and e.deletado_em is null) then
          faltas := array_append(faltas, 'foto/vídeo das atividades'::text); end if;
      when 'evidencia_geral' then
        if not exists (select 1 from evidencia e where e.id_atendimento = a.id_atendimento
                          and e.etapa = 'geral' and e.deletado_em is null) then
          faltas := array_append(faltas, 'foto/vídeo do atendimento'::text); end if;
      when 'assinatura_especialista' then
        if not exists (select 1 from assinatura s where s.id_atendimento = a.id_atendimento
                          and s.tipo_assinante = 'especialista' and s.deletado_em is null
                          and s.versao_relatorio_assinada = r.versao and s.conteudo_hash = v_hash) then
          faltas := array_append(faltas, 'assinatura do especialista (do conteúdo atual)'::text); end if;
      when 'assinatura_cliente' then
        if not exists (select 1 from assinatura s where s.id_atendimento = a.id_atendimento
                          and s.tipo_assinante = 'cliente' and s.deletado_em is null
                          and s.versao_relatorio_assinada = r.versao and s.conteudo_hash = v_hash) then
          faltas := array_append(faltas, 'assinatura do cliente (do conteúdo atual)'::text); end if;
    end case;
  end loop;

  -- Digital Farming, categoria Estação Meteorológica: "estação funcionando?"
  if a.motivo = 'digital_farming' and r.estacao_funcionando is null
     and exists (select 1 from atendimento_problema ap
                   join problema_categoria c on c.id_categoria = ap.id_categoria
                  where ap.id_atendimento = a.id_atendimento and ap.deletado_em is null
                    and c.nome = 'Estação Meteorológica') then
    faltas := array_append(faltas, 'estação está funcionando?'::text);
  end if;

  if array_length(faltas, 1) is not null then
    raise exception 'o relatório não pode ser finalizado, falta: %', array_to_string(faltas, '; ')
      using errcode = '23514';
  end if;
end
$$;

create function fn_relatorio_antes() returns trigger
language plpgsql security definer
set search_path = public, pg_temp
as $$
declare
  v_uid integer := app_usuario_id();
begin
  if tg_op = 'INSERT' then
    new.versao := 1;
    if new.status = 'finalizado' then
      perform fn_valida_finalizacao(new);
      new.data_finalizacao := coalesce(new.data_finalizacao, now());
    end if;
    return new;
  end if;

  -- UPDATE ----------------------------------------------------------
  if new.id_relatorio <> old.id_relatorio or new.id_atendimento <> old.id_atendimento then
    raise exception 'id_relatorio e id_atendimento não podem mudar' using errcode = '23514';
  end if;

  if old.status = 'finalizado' then
    if new.status <> 'finalizado' then
      raise exception 'relatório finalizado não volta a rascunho' using errcode = '23514';
    end if;
    new.data_finalizacao := old.data_finalizacao;

    if (old.descricao_constatacoes, old.atividades_executadas, old.plano_acao,
        old.conclusao_visita, old.local_treinamento, old.qtd_pessoas_treinadas,
        old.estacao_funcionando)
       is distinct from
       (new.descricao_constatacoes, new.atividades_executadas, new.plano_acao,
        new.conclusao_visita, new.local_treinamento, new.qtd_pessoas_treinadas,
        new.estacao_funcionando) then
      -- edição pós-finalização: só o Netafim Admin
      if v_uid is null or not fn_usuario_e_admin_netafim(v_uid) then
        raise exception 'relatório finalizado: somente o Administrador Netafim pode editar'
          using errcode = '23514';
      end if;
      -- guarda a versão publicada até agora
      insert into relatorio_versao
             (id_relatorio, versao, descricao_constatacoes, atividades_executadas, plano_acao,
              conclusao_visita, local_treinamento, qtd_pessoas_treinadas, estacao_funcionando,
              id_editado_por)
      values (old.id_relatorio, old.versao, old.descricao_constatacoes, old.atividades_executadas,
              old.plano_acao, old.conclusao_visita, old.local_treinamento,
              old.qtd_pessoas_treinadas, old.estacao_funcionando, v_uid);
      new.versao := old.versao + 1;
      new.id_atualizado_por := v_uid;
      perform fn_hist_registrar(new.id_atendimento, 'editado_pos_finalizacao',
                                'v' || old.versao, 'v' || new.versao);
      perform fn_audit('relatorio_editado_pos_finalizacao', 'sucesso', 'relatorio_atendimento',
                       new.id_relatorio::text,
                       jsonb_build_object('versao_anterior', old.versao, 'versao_nova', new.versao));
    else
      new.versao := old.versao;           -- versão só muda em edição de conteúdo
    end if;
    return new;
  end if;

  -- rascunho
  new.versao := old.versao;
  if new.status = 'finalizado' then
    perform fn_valida_finalizacao(new);
    new.data_finalizacao := coalesce(new.data_finalizacao, now());
  end if;
  return new;
end
$$;

create trigger trg_relatorio_antes before insert or update on relatorio_atendimento
  for each row execute function fn_relatorio_antes();
create trigger trg_relatorio_sync before insert or update on relatorio_atendimento
  for each row execute function fn_sync_meta();

create trigger trg_relatorio_versao_imutavel before update or delete on relatorio_versao
  for each row execute function fn_somente_insercao();
create trigger trg_relatorio_versao_truncate before truncate on relatorio_versao
  for each statement execute function fn_somente_insercao();

-- ------------------------------------------------ RELATORIO_ESPECIALISTA
-- Depois de finalizado, só o Netafim Admin mexe nos especialistas.
create function fn_rel_esp_antes() returns trigger
language plpgsql security definer
set search_path = public, pg_temp
as $$
declare
  v_id_rel uuid := case when tg_op = 'DELETE' then old.id_relatorio else new.id_relatorio end;
  v_uid    integer := app_usuario_id();
begin
  if exists (select 1 from relatorio_atendimento r
              where r.id_relatorio = v_id_rel and r.status = 'finalizado')
     and (v_uid is null or not fn_usuario_e_admin_netafim(v_uid)) then
    raise exception 'relatório finalizado: somente o Administrador Netafim altera os especialistas'
      using errcode = '23514';
  end if;
  if tg_op = 'DELETE' then return old; end if;
  return new;
end
$$;

-- "Pelo menos um responsável": constraint trigger ADIADO, conferido no
-- fim da transação. (O "no máximo um" é o índice único parcial.)
create function fn_chk_responsavel() returns trigger
language plpgsql security definer
set search_path = public, pg_temp
as $$
declare
  v_id_rel uuid;
begin
  v_id_rel := case when tg_op = 'DELETE' then old.id_relatorio else new.id_relatorio end;
  if exists (select 1 from relatorio_atendimento r
              where r.id_relatorio = v_id_rel and r.status = 'finalizado' and r.deletado_em is null)
     and not exists (select 1 from relatorio_especialista e
                      where e.id_relatorio = v_id_rel and e.papel = 'responsavel'
                        and e.deletado_em is null) then
    raise exception 'o relatório finalizado precisa ter um especialista responsável'
      using errcode = '23514';
  end if;
  return null;
end
$$;

create trigger trg_rel_esp_antes before insert or update or delete on relatorio_especialista
  for each row execute function fn_rel_esp_antes();
create trigger trg_rel_esp_sync before insert or update on relatorio_especialista
  for each row execute function fn_sync_meta();
create constraint trigger ctrg_relatorio_responsavel after insert or update on relatorio_atendimento
  deferrable initially deferred for each row execute function fn_chk_responsavel();
create constraint trigger ctrg_especialista_responsavel after insert or update or delete on relatorio_especialista
  deferrable initially deferred for each row execute function fn_chk_responsavel();

-- ------------------------------------------------------------ ASSINATURA
-- Versão e hash são do BANCO; o especialista que assina é o responsável.
create function fn_assinatura_antes() returns trigger
language plpgsql security definer
set search_path = public, pg_temp
as $$
declare
  r relatorio_atendimento%rowtype;
begin
  if tg_op = 'INSERT' then
    select * into r from relatorio_atendimento
     where id_atendimento = new.id_atendimento and deletado_em is null;
    if not found then
      raise exception 'não há relatório deste atendimento para assinar' using errcode = '23514';
    end if;
    new.versao_relatorio_assinada := r.versao;          -- o valor informado é ignorado
    new.conteudo_hash := fn_conteudo_hash(r);            -- o valor informado é ignorado
    if new.tipo_assinante = 'especialista' and not exists (
         select 1 from relatorio_especialista e
          where e.id_relatorio = r.id_relatorio and e.id_usuario = new.id_usuario
            and e.papel = 'responsavel' and e.deletado_em is null) then
      raise exception 'somente o especialista responsável pelo relatório assina' using errcode = '23514';
    end if;
    return new;
  end if;

  -- UPDATE: o que foi assinado não muda (só exclusão lógica e metadados)
  if (old.id_assinatura, old.id_atendimento, old.tipo_assinante, old.id_usuario, old.nome_assinante,
      old.documento_assinante, old.forma_assinatura, old.storage_key,
      old.versao_relatorio_assinada, old.conteudo_hash, old.data_hora)
     is distinct from
     (new.id_assinatura, new.id_atendimento, new.tipo_assinante, new.id_usuario, new.nome_assinante,
      new.documento_assinante, new.forma_assinatura, new.storage_key,
      new.versao_relatorio_assinada, new.conteudo_hash, new.data_hora) then
    raise exception 'uma assinatura registrada não pode ser alterada' using errcode = '23514';
  end if;
  return new;
end
$$;

create trigger trg_assinatura_antes before insert or update on assinatura
  for each row execute function fn_assinatura_antes();
create trigger trg_assinatura_sync before insert or update on assinatura
  for each row execute function fn_sync_meta();

-- =====================================================================
-- 7. SATISFAÇÃO E ENCERRAMENTO
-- =====================================================================

-- Pesquisa respondida encerra o atendimento resolvido (ação do sistema).
create function fn_pesquisa_depois() returns trigger
language plpgsql security definer
set search_path = public, pg_temp
as $$
declare
  v_prev text := coalesce(current_setting('app.acao_sistema', true), '');
begin
  if new.data_resposta is not null then
    perform set_config('app.acao_sistema', 'on', true);
    update atendimento
       set status = 'encerrado', data_encerramento = now()
     where id_atendimento = new.id_atendimento and status = 'resolvido';
    perform set_config('app.acao_sistema', v_prev, true);
  end if;
  return null;
end
$$;
create trigger trg_pesquisa_depois after insert or update on pesquisa_satisfacao
  for each row execute function fn_pesquisa_depois();

-- Registro da resposta da pesquisa (inclusive por link, sem login).
-- A API valida o link e chama esta função; ela grava as notas e o
-- gatilho acima encerra o atendimento.
--   p_notas: [{"id_pergunta": 1, "nota": 9}, ...]
create function fn_registrar_resposta_pesquisa(
  p_id_pesquisa      integer,
  p_nome_respondente text,
  p_nota_geral       smallint,
  p_notas            jsonb default '[]'::jsonb
) returns void
language plpgsql security definer
set search_path = public, pg_temp
as $$
declare
  v_pesq pesquisa_satisfacao%rowtype;
  v_item jsonb;
begin
  select * into v_pesq from pesquisa_satisfacao where id_pesquisa = p_id_pesquisa for update;
  if not found then
    raise exception 'pesquisa % não existe', p_id_pesquisa using errcode = '23514';
  end if;
  if v_pesq.data_resposta is not null then
    raise exception 'esta pesquisa já foi respondida' using errcode = '23514';
  end if;
  if jsonb_typeof(p_notas) is distinct from 'array' then
    raise exception 'p_notas deve ser uma lista' using errcode = '23514';
  end if;

  for v_item in select * from jsonb_array_elements(p_notas) loop
    insert into resposta_satisfacao (id_pesquisa, id_pergunta, nota)
    values (p_id_pesquisa, (v_item->>'id_pergunta')::integer, (v_item->>'nota')::smallint);
  end loop;

  update pesquisa_satisfacao
     set nota_geral = p_nota_geral,
         nome_respondente = coalesce(p_nome_respondente, nome_respondente),
         id_usuario_respondente = coalesce(app_usuario_id(), id_usuario_respondente),
         data_resposta = now()
   where id_pesquisa = p_id_pesquisa;
end
$$;

-- Rotina automática: encerra como "sem avaliação" os atendimentos
-- resolvidos cujo prazo de espera venceu. Agendar 1x ao dia (pg_cron
-- ou tarefa da API). Devolve quantos foram encerrados.
create function fn_encerrar_atendimentos_vencidos() returns integer
language plpgsql security definer
set search_path = public, pg_temp
as $$
declare
  v_prev text := coalesce(current_setting('app.acao_sistema', true), '');
  v_n    integer;
begin
  perform set_config('app.acao_sistema', 'on', true);
  update atendimento a
     set status = 'encerrado_sem_avaliacao', data_encerramento = now()
   where a.status = 'resolvido' and a.deletado_em is null
     and a.data_conclusao + make_interval(days => fn_prazo_avaliacao_dias()) <= now()
     and not exists (select 1 from pesquisa_satisfacao p
                      where p.id_atendimento = a.id_atendimento and p.data_resposta is not null);
  get diagnostics v_n = row_count;
  perform set_config('app.acao_sistema', v_prev, true);
  return v_n;
end
$$;

-- No máximo 5 perguntas ativas na pesquisa de satisfação.
create function fn_pergunta_satisfacao_max() returns trigger
language plpgsql security definer
set search_path = public, pg_temp
as $$
declare
  v_ativas integer;
begin
  if new.ativa then
    perform pg_advisory_xact_lock(hashtext('pergunta_satisfacao'));
    select count(*) into v_ativas from pergunta_satisfacao
     where ativa and id_pergunta <> new.id_pergunta;
    if v_ativas >= 5 then
      raise exception 'a pesquisa de satisfação aceita no máximo 5 perguntas ativas' using errcode = '23514';
    end if;
  end if;
  return new;
end
$$;
create trigger trg_pergunta_satisfacao_max before insert or update on pergunta_satisfacao
  for each row execute function fn_pergunta_satisfacao_max();

-- =====================================================================
-- 8. FORMULÁRIO DINÂMICO
-- =====================================================================

-- Versão de pergunta que já foi respondida não muda de conteúdo:
-- editar = criar nova versão (código igual, versão + 1).
create function fn_pergunta_form_antes() returns trigger
language plpgsql security definer
set search_path = public, pg_temp
as $$
begin
  if new.codigo <> old.codigo or new.versao <> old.versao then
    raise exception 'codigo e versao de uma pergunta não mudam: crie uma nova versão' using errcode = '23514';
  end if;
  if (new.texto, new.tipo_campo, new.opcoes, new.regra_condicional, new.motivo_aplicavel)
     is distinct from
     (old.texto, old.tipo_campo, old.opcoes, old.regra_condicional, old.motivo_aplicavel)
     and exists (select 1 from resposta_pergunta_formulario r
                  where r.id_pergunta_form = old.id_pergunta_form) then
    raise exception 'esta versão da pergunta já foi respondida: crie uma nova versão' using errcode = '23514';
  end if;
  return new;
end
$$;
create trigger trg_pergunta_form_antes before update on pergunta_formulario
  for each row execute function fn_pergunta_form_antes();
create trigger trg_pergunta_form_sync before insert or update on pergunta_formulario
  for each row execute function fn_sync_meta();

-- Em multisseleção o valor fica nulo (as opções vão em RESPOSTA_OPCAO).
create function fn_resposta_form_antes() returns trigger
language plpgsql security definer
set search_path = public, pg_temp
as $$
declare
  v_tipo text;
begin
  select tipo_campo into v_tipo from pergunta_formulario where id_pergunta_form = new.id_pergunta_form;
  if v_tipo = 'multisselecao' and new.valor_resposta is not null then
    raise exception 'em multisseleção valor_resposta fica nulo (use RESPOSTA_OPCAO)' using errcode = '23514';
  end if;
  return new;
end
$$;
create trigger trg_resposta_form_antes before insert or update on resposta_pergunta_formulario
  for each row execute function fn_resposta_form_antes();
create trigger trg_resposta_form_sync before insert or update on resposta_pergunta_formulario
  for each row execute function fn_sync_meta();

-- A opção marcada precisa existir na pergunta de multisseleção.
create function fn_resposta_opcao_antes() returns trigger
language plpgsql security definer
set search_path = public, pg_temp
as $$
declare
  v_tipo   text;
  v_opcoes jsonb;
begin
  select p.tipo_campo, p.opcoes into v_tipo, v_opcoes
    from resposta_pergunta_formulario r
    join pergunta_formulario p on p.id_pergunta_form = r.id_pergunta_form
   where r.id_resposta = new.id_resposta;
  if v_tipo is distinct from 'multisselecao' then
    raise exception 'RESPOSTA_OPCAO só vale para perguntas de multisseleção' using errcode = '23514';
  end if;
  if not (v_opcoes ? new.opcao_selecionada) then
    raise exception 'a opção "%" não existe nesta pergunta', new.opcao_selecionada using errcode = '23514';
  end if;
  return new;
end
$$;
create trigger trg_resposta_opcao_antes before insert or update on resposta_opcao
  for each row execute function fn_resposta_opcao_antes();
create trigger trg_resposta_opcao_sync before insert or update on resposta_opcao
  for each row execute function fn_sync_meta();

-- =====================================================================
-- 9. USUÁRIO, DISPOSITIVO E TOKENS
-- =====================================================================

create function fn_usuario_antes() returns trigger
language plpgsql security definer
set search_path = public, pg_temp
as $$
declare
  v_dom text;
begin
  if tg_op = 'UPDATE' and new.password is distinct from old.password then
    new.senha_alterada_em := now();
  end if;

  -- usuário de Netafim ou distribuidor só fica ativo com domínio de
  -- e-mail ativo na lista corporativa (confere ao ativar ou ao mudar
  -- e-mail/organização, para não travar contas já ativas)
  if new.situacao = 'ativo' and new.tipo_organizacao in ('netafim','distribuidor')
     and (tg_op = 'INSERT'
          or old.situacao <> 'ativo'
          or old.email is distinct from new.email
          or old.tipo_organizacao is distinct from new.tipo_organizacao
          or old.id_distribuidor is distinct from new.id_distribuidor) then
    v_dom := lower(split_part(new.email, '@', 2));
    if not exists (select 1 from dominio_email_corporativo d
                    where d.dominio = v_dom and d.ativo
                      and d.tipo_organizacao = new.tipo_organizacao
                      and (new.tipo_organizacao = 'netafim' or d.id_distribuidor = new.id_distribuidor)) then
      raise exception 'o domínio de e-mail "%" não está autorizado para esta organização', v_dom
        using errcode = '23514';
    end if;
  end if;
  return new;
end
$$;

create function fn_usuario_depois() returns trigger
language plpgsql security definer
set search_path = public, pg_temp
as $$
declare
  v_acao text;
begin
  if tg_op = 'INSERT' then
    if new.situacao = 'pendente' then
      perform fn_audit('cadastro_solicitado', 'sucesso', 'usuario', new.id_usuario::text,
                       null, new.id_usuario);
    end if;
    return null;
  end if;

  -- situação mudou
  if new.situacao is distinct from old.situacao then
    v_acao := case
      when old.situacao = 'pendente' and new.situacao = 'ativo'    then 'cadastro_aprovado'
      when old.situacao = 'pendente' and new.situacao = 'recusado' then 'cadastro_recusado'
      when new.situacao = 'bloqueado'                              then 'conta_bloqueada'
      else 'situacao_alterada' end;
    perform fn_audit(v_acao, 'sucesso', 'usuario', new.id_usuario::text,
                     jsonb_build_object('de', old.situacao, 'para', new.situacao));
  end if;
  if new.id_perfil is distinct from old.id_perfil then
    perform fn_audit('perfil_alterado', 'sucesso', 'usuario', new.id_usuario::text,
                     jsonb_build_object('de', old.id_perfil, 'para', new.id_perfil));
  end if;
  if new.password is distinct from old.password then
    perform fn_audit('senha_alterada', 'sucesso', 'usuario', new.id_usuario::text, null, new.id_usuario);
  end if;
  if new.mfa_ativo is distinct from old.mfa_ativo then
    perform fn_audit('mfa_alterado', 'sucesso', 'usuario', new.id_usuario::text,
                     jsonb_build_object('ativo', new.mfa_ativo), new.id_usuario);
  end if;

  -- conta inativa, bloqueada, recusada ou excluída: revoga todos os tokens
  if (new.situacao in ('inativo','bloqueado','recusado') or new.deletado_em is not null)
     and (new.situacao is distinct from old.situacao
          or new.deletado_em is distinct from old.deletado_em) then
    update token_acesso set revogado_em = now()
     where id_usuario = new.id_usuario and revogado_em is null;
  end if;
  return null;
end
$$;

create trigger trg_usuario_antes before insert or update on usuario
  for each row execute function fn_usuario_antes();
create trigger trg_usuario_touch before insert or update on usuario
  for each row execute function fn_touch();
create trigger trg_usuario_depois after insert or update on usuario
  for each row execute function fn_usuario_depois();
create trigger trg_incidente_touch before insert or update on incidente_seguranca
  for each row execute function fn_touch();

-- A API (role app_django) não pode se promover nem mexer nos contadores
-- de login. Quem define perfil, organização e aprovação é o Netafim Admin;
-- o admin de organização só ativa/inativa/exclui usuários da própria
-- organização (a policy já limita o escopo). Sessões que não são da API
-- (DBA no SQL Editor, migração) não passam por esta trava.
create function fn_usuario_protege() returns trigger
language plpgsql security definer
set search_path = public, pg_temp
as $$
declare
  v_uid integer := app_usuario_id();
begin
  if session_user <> 'app_django' or app_acao_sistema() or app_eh_admin_netafim() then
    return new;
  end if;

  if (new.id_perfil, new.tipo_organizacao, new.id_cliente, new.id_distribuidor,
      new.id_aprovado_por, new.data_aprovacao)
     is distinct from
     (old.id_perfil, old.tipo_organizacao, old.id_cliente, old.id_distribuidor,
      old.id_aprovado_por, old.data_aprovacao) then
    raise exception 'somente o Netafim Admin define perfil, organização e aprovação'
      using errcode = '23514';
  end if;

  if new.situacao is distinct from old.situacao then
    if not (old.situacao in ('ativo','inativo') and new.situacao in ('ativo','inativo')
            and v_uid is distinct from old.id_usuario
            and app_nivel() = 'admin' and app_tem_permissao('editar_usuarios')) then
      raise exception 'somente o Netafim Admin altera a situação da conta (admin de organização só ativa e inativa usuários da própria organização)'
        using errcode = '23514';
    end if;
  end if;

  if new.deletado_em is distinct from old.deletado_em then
    if not (v_uid is distinct from old.id_usuario and app_nivel() = 'admin'
            and app_tem_permissao('excluir_usuarios')) then
      raise exception 'seu perfil não pode excluir usuários' using errcode = '23514';
    end if;
  end if;

  if (new.falhas_login, new.bloqueado_ate) is distinct from (old.falhas_login, old.bloqueado_ate) then
    raise exception 'contadores de login só mudam por fn_registrar_login' using errcode = '23514';
  end if;
  return new;
end
$$;
create trigger trg_usuario_protege before update on usuario
  for each row execute function fn_usuario_protege();

-- Resultado de uma tentativa de login: zera ou conta falhas, bloqueia
-- temporariamente (5 falhas seguidas = 15 minutos; valores de trabalho) e
-- grava a auditoria. A API chama depois de conferir a senha.
create function fn_registrar_login(
  p_id_usuario     integer,
  p_sucesso        boolean,
  p_ip             inet    default null,
  p_user_agent     text    default null,
  p_id_dispositivo uuid    default null,
  p_correlacao_id  uuid    default null
) returns table (o_situacao text, o_falhas integer, o_bloqueado_ate timestamptz)
language plpgsql security definer
set search_path = public, pg_temp
as $$
declare
  u      usuario%rowtype;
  v_n    integer;
  v_prev text := coalesce(current_setting('app.acao_sistema', true), '');
begin
  select * into u from usuario where id_usuario = p_id_usuario and deletado_em is null for update;
  if not found then return; end if;

  perform set_config('app.acao_sistema', 'on', true);
  -- conta travada: nenhuma tentativa vale (nem a certa) e o contador nao
  -- anda, para o travamento nao ser prorrogado por quem ataca a conta
  if u.bloqueado_ate is not null and u.bloqueado_ate > now() then
    perform fn_audit('acesso_negado', 'negado', 'usuario', p_id_usuario::text,
                     jsonb_build_object('motivo', 'conta_bloqueada'),
                     p_id_usuario, p_id_dispositivo, p_ip, p_user_agent, p_correlacao_id);
  elsif p_sucesso then
    update usuario set falhas_login = 0, bloqueado_ate = null, last_login = now()
     where id_usuario = p_id_usuario;
    perform fn_audit('login_sucesso', 'sucesso', 'usuario', p_id_usuario::text, null,
                     p_id_usuario, p_id_dispositivo, p_ip, p_user_agent, p_correlacao_id);
  else
    v_n := u.falhas_login + 1;
    update usuario
       set falhas_login = v_n,
           bloqueado_ate = case when v_n >= 5 then now() + interval '15 minutes' else bloqueado_ate end
     where id_usuario = p_id_usuario;
    perform fn_audit('login_falha', 'falha', 'usuario', p_id_usuario::text, null,
                     p_id_usuario, p_id_dispositivo, p_ip, p_user_agent, p_correlacao_id);
    if v_n = 5 then
      perform fn_audit('conta_bloqueada', 'sucesso', 'usuario', p_id_usuario::text,
                       jsonb_build_object('motivo', 'excesso_de_falhas'),
                       p_id_usuario, p_id_dispositivo, p_ip, p_user_agent, p_correlacao_id);
    end if;
  end if;
  perform set_config('app.acao_sistema', v_prev, true);

  return query select x.situacao::text, x.falhas_login::integer, x.bloqueado_ate
                 from usuario x where x.id_usuario = p_id_usuario;
end
$$;

-- ------------------------------------------------------------ DISPOSITIVO
create function fn_dispositivo_antes() returns trigger
language plpgsql
as $$
begin
  if new.id_usuario <> old.id_usuario then
    raise exception 'o aparelho não muda de dono' using errcode = '23514';
  end if;
  if old.revogado_em is not null and new.revogado_em is distinct from old.revogado_em then
    raise exception 'a revogação do aparelho não volta atrás' using errcode = '23514';
  end if;
  return new;
end
$$;

create function fn_dispositivo_depois() returns trigger
language plpgsql security definer
set search_path = public, pg_temp
as $$
begin
  if new.revogado_em is not null and old.revogado_em is null then
    update token_acesso set revogado_em = now()
     where id_dispositivo = new.id_dispositivo and revogado_em is null;
    perform fn_audit('dispositivo_revogado', 'sucesso', 'dispositivo', new.id_dispositivo::text,
                     null, new.id_usuario, new.id_dispositivo);
  end if;
  if new.limpeza_remota_solicitada_em is not null and old.limpeza_remota_solicitada_em is null then
    perform fn_audit('limpeza_remota', 'sucesso', 'dispositivo', new.id_dispositivo::text,
                     null, null, new.id_dispositivo);
  end if;
  return null;
end
$$;
create trigger trg_dispositivo_antes before update on dispositivo
  for each row execute function fn_dispositivo_antes();
create trigger trg_dispositivo_depois after update on dispositivo
  for each row execute function fn_dispositivo_depois();

-- ------------------------------------------------- PERFIL_PERMISSAO / CONFIG
create function fn_perfil_permissao_depois() returns trigger
language plpgsql security definer
set search_path = public, pg_temp
as $$
begin
  if app_carga_inicial() then return null; end if;
  if tg_op = 'INSERT' then
    perform fn_audit('permissao_concedida', 'sucesso', 'perfil_permissao',
                     new.id_perfil || ':' || new.id_permissao);
  else
    perform fn_audit('permissao_revogada', 'sucesso', 'perfil_permissao',
                     old.id_perfil || ':' || old.id_permissao);
  end if;
  return null;
end
$$;
create trigger trg_perfil_permissao_depois after insert or delete on perfil_permissao
  for each row execute function fn_perfil_permissao_depois();

-- Mudança nas obrigatoriedades do relatório é configuração auditada.
create function fn_config_relatorio_depois() returns trigger
language plpgsql security definer
set search_path = public, pg_temp
as $$
begin
  if app_carga_inicial() then return null; end if;
  perform fn_audit('configuracao_alterada', 'sucesso', 'motivo_campo_relatorio',
                   new.motivo || ':' || new.campo,
                   jsonb_build_object('obrigatorio', new.obrigatorio));
  return null;
end
$$;
create trigger trg_config_relatorio_depois after insert or update on motivo_campo_relatorio
  for each row execute function fn_config_relatorio_depois();
create trigger trg_mcr_sync before insert or update on motivo_campo_relatorio
  for each row execute function fn_sync_meta();

-- ------------------------------------------------------------ TOKEN_ACESSO
-- Revogação, hash, dono e validade não voltam atrás.
create function fn_token_antes() returns trigger
language plpgsql
as $$
begin
  if (new.id_token, new.id_usuario, new.token_hash, new.tipo, new.criado_em, new.expira_em)
     is distinct from
     (old.id_token, old.id_usuario, old.token_hash, old.tipo, old.criado_em, old.expira_em)
     or new.id_dispositivo is distinct from old.id_dispositivo then
    raise exception 'token: hash, dono, tipo, aparelho e validade não mudam' using errcode = '23514';
  end if;
  if old.revogado_em is not null and new.revogado_em is distinct from old.revogado_em then
    raise exception 'token revogado não volta atrás' using errcode = '23514';
  end if;
  if old.usado_em is not null and new.usado_em is distinct from old.usado_em then
    raise exception 'o primeiro uso do token não muda' using errcode = '23514';
  end if;
  if old.substituido_por is not null and new.substituido_por is not null
     and new.substituido_por <> old.substituido_por then
    raise exception 'a rotação do token não muda' using errcode = '23514';
  end if;
  return new;
end
$$;
create trigger trg_token_antes before update on token_acesso
  for each row execute function fn_token_antes();

-- Emite um token (recebe só o HASH; o token em si nunca chega ao banco).
-- Teto de validade por tipo: refresh 90 dias, convite 14 dias, reset 2 horas.
create function fn_emitir_token(
  p_id_usuario     integer,
  p_tipo           text,
  p_token_hash     text,
  p_id_dispositivo uuid default null,
  p_ip             inet default null
) returns uuid
language plpgsql security definer
set search_path = public, pg_temp
as $$
declare
  v_sit      text;
  v_validade interval;
  v_id       uuid;
begin
  v_validade := case p_tipo
                  when 'refresh'     then interval '90 days'
                  when 'convite'     then interval '14 days'
                  when 'reset_senha' then interval '2 hours'
                  else null end;
  if v_validade is null then
    raise exception 'tipo de token inválido: %', p_tipo using errcode = '23514';
  end if;

  select situacao into v_sit from usuario where id_usuario = p_id_usuario and deletado_em is null;
  if not found then
    raise exception 'usuário não encontrado' using errcode = '23514';
  end if;

  if p_tipo = 'refresh' then
    if v_sit <> 'ativo' then
      raise exception 'só usuário ativo recebe sessão' using errcode = '23514';
    end if;
    if p_id_dispositivo is null or not exists (
         select 1 from dispositivo d where d.id_dispositivo = p_id_dispositivo
            and d.id_usuario = p_id_usuario and d.revogado_em is null) then
      raise exception 'refresh exige um aparelho do usuário, não revogado' using errcode = '23514';
    end if;
  elsif p_tipo = 'convite' then
    if v_sit <> 'pendente' then
      raise exception 'convite só para usuário pendente' using errcode = '23514';
    end if;
  else  -- reset_senha
    if v_sit not in ('ativo','pendente') then
      raise exception 'redefinição de senha indisponível para esta conta' using errcode = '23514';
    end if;
  end if;

  insert into token_acesso (id_usuario, id_dispositivo, tipo, token_hash, criado_em, expira_em, ip)
  values (p_id_usuario,
          case when p_tipo = 'refresh' then p_id_dispositivo else null end,
          p_tipo, p_token_hash, now(), now() + v_validade, p_ip)
  returning id_token into v_id;
  return v_id;
end
$$;

-- Usa um token. Refresh: emite o sucessor (p_hash_novo) e liga os dois.
-- Convite e reset: só consome (marca usado_em).
-- Reuso de refresh já usado = suspeita de roubo: revoga as sessões do aparelho.
-- Devolve sempre uma linha; "resultado" diz o que houve (a API traduz).
create function fn_rotacionar_token(
  p_hash_antigo text,
  p_hash_novo   text default null,
  p_ip          inet default null
) returns table (resultado text, id_usuario integer, id_dispositivo uuid, id_token_novo uuid)
language plpgsql security definer
set search_path = public, pg_temp
as $$
declare
  t      token_acesso%rowtype;
  v_sit  text;
  v_novo uuid;
begin
  select * into t from token_acesso where token_hash = p_hash_antigo for update;
  if not found then
    return query select 'invalido'::text, null::integer, null::uuid, null::uuid; return;
  end if;

  if t.usado_em is not null then
    if t.tipo = 'refresh' then
      -- reuso: derruba todas as sessões do aparelho (ou do usuário, sem aparelho)
      update token_acesso k set revogado_em = now()
       where k.id_usuario = t.id_usuario and k.tipo = 'refresh' and k.revogado_em is null
         and k.id_dispositivo is not distinct from t.id_dispositivo;
      perform fn_audit('acesso_negado', 'negado', 'token_acesso', t.id_token::text,
                       jsonb_build_object('motivo', 'reuso_refresh_token'),
                       t.id_usuario, t.id_dispositivo, p_ip);
      return query select 'reuso_detectado'::text, t.id_usuario, t.id_dispositivo, null::uuid; return;
    end if;
    return query select 'ja_utilizado'::text, t.id_usuario, t.id_dispositivo, null::uuid; return;
  end if;
  if t.revogado_em is not null then
    return query select 'revogado'::text, t.id_usuario, t.id_dispositivo, null::uuid; return;
  end if;
  if t.expira_em <= now() then
    return query select 'expirado'::text, t.id_usuario, t.id_dispositivo, null::uuid; return;
  end if;

  select situacao into v_sit from usuario where usuario.id_usuario = t.id_usuario and deletado_em is null;
  if t.tipo = 'refresh' and v_sit is distinct from 'ativo' then
    return query select 'usuario_inativo'::text, t.id_usuario, t.id_dispositivo, null::uuid; return;
  end if;

  if t.tipo = 'refresh' then
    if p_hash_novo is null then
      raise exception 'refresh exige o hash do novo token' using errcode = '23514';
    end if;
    v_novo := fn_emitir_token(t.id_usuario, 'refresh', p_hash_novo, t.id_dispositivo, p_ip);
    update token_acesso set usado_em = now(), substituido_por = v_novo where id_token = t.id_token;
  else
    update token_acesso set usado_em = now() where id_token = t.id_token;
  end if;
  return query select 'ok'::text, t.id_usuario, t.id_dispositivo, v_novo;
end
$$;

-- Revoga um token (logout). Devolve true se havia token ativo.
create function fn_revogar_token(p_hash text) returns boolean
language plpgsql security definer
set search_path = public, pg_temp
as $$
declare
  v_n integer;
begin
  update token_acesso set revogado_em = now()
   where token_hash = p_hash and revogado_em is null;
  get diagnostics v_n = row_count;
  return v_n > 0;
end
$$;

-- =====================================================================
-- 10. LGPD: anonimização e expurgo
-- =====================================================================

-- Anonimiza o usuário preservando os registros legais do atendimento
-- (o id continua, então histórico e assinaturas seguem íntegros).
create function fn_anonimizar_usuario(p_id_usuario integer) returns void
language plpgsql security definer
set search_path = public, pg_temp
as $$
declare
  v_prev text := coalesce(current_setting('app.acao_sistema', true), '');
begin
  -- roda como acao do sistema: anonimizar nao depende do perfil de quem chama
  perform set_config('app.acao_sistema', 'on', true);
  if not exists (select 1 from usuario where id_usuario = p_id_usuario) then
    raise exception 'usuário não encontrado' using errcode = '23514';
  end if;

  update dispositivo set revogado_em = coalesce(revogado_em, now()), modelo = null
   where id_usuario = p_id_usuario;           -- o gatilho revoga os tokens

  update usuario
     set nome = 'Usuário anonimizado',
         email = 'anonimizado-' || p_id_usuario || '@anonimizado.invalid',
         telefone = null,
         password = '!' || replace(gen_random_uuid()::text, '-', ''),
         situacao = 'inativo',
         mfa_ativo = false,
         data_exclusao_solicitada = coalesce(data_exclusao_solicitada, now()),
         deletado_em = coalesce(deletado_em, now())
   where id_usuario = p_id_usuario;

  delete from notificacao where id_usuario = p_id_usuario;

  perform fn_audit('dados_anonimizados', 'sucesso', 'usuario', p_id_usuario::text);
  perform set_config('app.acao_sistema', v_prev, true);
end
$$;

-- Anonimiza o cliente (produtor rural) e seus dados de localização,
-- preservando o atendimento, o relatório e as assinaturas.
create function fn_anonimizar_cliente(p_id_cliente integer) returns void
language plpgsql security definer
set search_path = public, pg_temp
as $$
declare
  v_prev text := coalesce(current_setting('app.acao_sistema', true), '');
begin
  -- roda como acao do sistema: anonimizar nao depende do perfil de quem chama
  perform set_config('app.acao_sistema', 'on', true);
  if not exists (select 1 from cliente where id_cliente = p_id_cliente) then
    raise exception 'cliente não encontrado' using errcode = '23514';
  end if;

  update cliente
     set nome = 'Cliente anonimizado', email = null, telefone = null,
         status = 'inativo', deletado_em = coalesce(deletado_em, now())
   where id_cliente = p_id_cliente;

  update propriedade
     set nome_fazenda = 'Fazenda anonimizada', localizacao = 'anonimizada',
         latitude = null, longitude = null
   where id_cliente = p_id_cliente;

  update atendimento
     set nome_produtor = null, nome_fazenda_informado = null,
         snapshot_cliente = case when snapshot_cliente is null then null
                                 else '{"anonimizado": true}'::jsonb end,
         snapshot_propriedade = null
   where id_cliente = p_id_cliente;

  perform fn_audit('dados_anonimizados', 'sucesso', 'cliente', p_id_cliente::text);
  perform set_config('app.acao_sistema', v_prev, true);
end
$$;

-- Rotina de manutenção (retenção do documento, seção 6.4). Agendar
-- 1x ao dia. Devolve quantas linhas apagou em cada tabela.
create function fn_expurgo_manutencao()
returns table (tabela text, apagados bigint)
language plpgsql security definer
set search_path = public, pg_temp
as $$
declare
  v_n bigint;
begin
  -- tokens: 30 dias depois de expirar ou ser revogado
  delete from token_acesso
   where least(expira_em, coalesce(revogado_em, expira_em)) < now() - interval '30 days';
  get diagnostics v_n = row_count;
  return query select 'token_acesso'::text, v_n;

  -- conflitos resolvidos há mais de 1 ano
  delete from sync_conflito
   where status <> 'aberto' and resolvido_em < now() - interval '1 year';
  get diagnostics v_n = row_count;
  return query select 'sync_conflito'::text, v_n;

  -- notificações lidas, 90 dias depois (aproximação: usa a data de criação)
  delete from notificacao where lida and data_criacao < now() - interval '90 days';
  get diagnostics v_n = row_count;
  return query select 'notificacao'::text, v_n;
end
$$;

-- =====================================================================
-- 11. GATILHOS DE data_atualizacao + sync_seq nas tabelas restantes
-- =====================================================================
create trigger trg_sync before insert or update on distribuidor
  for each row execute function fn_sync_meta();
create trigger trg_sync before insert or update on cliente
  for each row execute function fn_sync_meta();
create trigger trg_sync before insert or update on propriedade
  for each row execute function fn_sync_meta();
create trigger trg_sync before insert or update on cultura
  for each row execute function fn_sync_meta();
create trigger trg_sync before insert or update on propriedade_cultura
  for each row execute function fn_sync_meta();
create trigger trg_sync before insert or update on problema_categoria
  for each row execute function fn_sync_meta();
create trigger trg_sync before insert or update on problema_subcategoria
  for each row execute function fn_sync_meta();
create trigger trg_sync before insert or update on atendimento_problema
  for each row execute function fn_sync_meta();
create trigger trg_sync before insert or update on atendimento_cultura
  for each row execute function fn_sync_meta();
create trigger trg_sync before insert or update on tipo_treinamento
  for each row execute function fn_sync_meta();
create trigger trg_sync before insert or update on atendimento_tipo_treinamento
  for each row execute function fn_sync_meta();
create trigger trg_sync before insert or update on evidencia
  for each row execute function fn_sync_meta();
create trigger trg_sync before insert or update on avaliacao_formulario
  for each row execute function fn_sync_meta();

-- ---------- 03_seguranca_rls.sql ----------
-- =====================================================================
-- 03_seguranca_rls.sql
-- Netafim Pós-venda | Banco central (PostgreSQL / Supabase)
--
-- O QUE FAZ
--  1. Cria (ou confere) a role app_django: a única que a API Django usa.
--     Ela NÃO é dona das tabelas e NÃO ignora o RLS.
--  2. Tira qualquer acesso das roles públicas do Supabase (anon,
--     authenticated), para a API automática (PostgREST) não expor nada.
--  3. Dá a app_django só os privilégios mínimos de cada tabela.
--  4. Liga Row Level Security (RLS) nas 41 tabelas e cria as policies
--     da seção 5.3 (quem vê o quê).
--
-- COMO A API INFORMA QUEM ESTÁ LOGADO (a cada requisição, dentro da
-- transação; use ATOMIC_REQUESTS = True no Django):
--     SET LOCAL app.current_user_id = '<id_usuario>';
-- Login e cadastro, antes de existir usuário logado, liberam só a
-- linha do próprio e-mail:
--     SET LOCAL app.login_email = '<email>';
-- Processo automático (rotinas) liga: SET LOCAL app.acao_sistema = 'on';
--
-- A SENHA da role app_django NÃO está neste arquivo. Defina em outro
-- passo:  alter role app_django with password '...';
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. ROLE DA API
-- ---------------------------------------------------------------------
do $role$
begin
  if not exists (select 1 from pg_roles where rolname = 'app_django') then
    create role app_django login nosuperuser nocreatedb nocreaterole noinherit nobypassrls;
  end if;
end
$role$;

grant usage on schema public to app_django;
-- o Supabase exige que quem conecta enxergue o banco
do $conn$
begin
  execute format('grant connect on database %I to app_django', current_database());
end
$conn$;

-- ---------------------------------------------------------------------
-- 2. FECHAR O ACESSO PÚBLICO
-- ---------------------------------------------------------------------
revoke all on all tables    in schema public from public;
revoke all on all sequences in schema public from public;
revoke all on all functions in schema public from public;
alter default privileges in schema public revoke all on functions from public;

do $anon$
declare
  r text;
begin
  foreach r in array array['anon', 'authenticated'] loop
    if exists (select 1 from pg_roles where rolname = r) then
      execute format('revoke all on all tables    in schema public from %I', r);
      execute format('revoke all on all sequences in schema public from %I', r);
      execute format('revoke all on all functions in schema public from %I', r);
      execute format('alter default privileges in schema public revoke all on tables    from %I', r);
      execute format('alter default privileges in schema public revoke all on sequences from %I', r);
      execute format('alter default privileges in schema public revoke all on functions from %I', r);
    end if;
  end loop;
end
$anon$;

-- ---------------------------------------------------------------------
-- 3. PRIVILÉGIOS DE app_django (mínimo por tabela)
--    Nenhuma tabela recebe DELETE: a exclusão é lógica (deletado_em).
--    Exceção: perfil_permissao, que não tem coluna de exclusão lógica.
-- ---------------------------------------------------------------------

grant select on historico_atendimento to app_django;
grant select on relatorio_versao to app_django;
grant select on resposta_satisfacao to app_django;
grant select on log_auditoria to app_django;
grant select, insert on relatorio_pdf to app_django;
grant select, insert on pesquisa_satisfacao to app_django;
grant select, insert, delete on perfil_permissao to app_django;
grant select, insert, update on perfil to app_django;
grant select, insert, update on permissao to app_django;
grant select, insert, update on distribuidor to app_django;
grant select, insert, update on cliente to app_django;
grant select, insert, update on usuario to app_django;
grant select, insert, update on propriedade to app_django;
grant select, insert, update on cultura to app_django;
grant select, insert, update on propriedade_cultura to app_django;
grant select, insert, update on dispositivo to app_django;
grant select, insert, update on sync_conflito to app_django;
grant select, insert, update on atendimento to app_django;
grant select, insert, update on agendamento to app_django;
grant select, insert, update on problema_categoria to app_django;
grant select, insert, update on problema_subcategoria to app_django;
grant select, insert, update on atendimento_problema to app_django;
grant select, insert, update on atendimento_cultura to app_django;
grant select, insert, update on tipo_treinamento to app_django;
grant select, insert, update on atendimento_tipo_treinamento to app_django;
grant select, insert, update on relatorio_atendimento to app_django;
grant select, insert, update on motivo_campo_relatorio to app_django;
grant select, insert, update on relatorio_especialista to app_django;
grant select, insert, update on envio_comunicacao to app_django;
grant select, insert, update on evidencia to app_django;
grant select, insert, update on assinatura to app_django;
grant select, insert, update on pergunta_satisfacao to app_django;
grant select, insert, update on avaliacao_formulario to app_django;
grant select, insert, update on notificacao to app_django;
grant select, insert, update on pergunta_formulario to app_django;
grant select, insert, update on resposta_pergunta_formulario to app_django;
grant select, insert, update on resposta_opcao to app_django;
grant select, insert, update on solicitacao_titular to app_django;
grant select, insert, update on incidente_seguranca to app_django;
grant select, insert, update on dominio_email_corporativo to app_django;
-- token_acesso: nenhum privilégio. Só as funções fn_emitir_token,
-- fn_rotacionar_token e fn_revogar_token (SECURITY DEFINER) mexem nela.

-- Funções que a API pode chamar (as demais são internas dos gatilhos).
grant execute on function
  app_usuario_id(), app_usuario_ativo(), app_tipo_org(), app_nivel(),
  app_id_distribuidor(), app_id_cliente(), app_eh_admin_netafim(),
  app_tem_permissao(text), app_atendimento_visivel(uuid), app_atendimento_operavel(uuid),
  app_atendimento_visivel_linha(integer, integer, integer, integer, integer),
  app_cliente_visivel(integer), app_atendimento_do_relatorio(uuid),
  app_atendimento_da_pesquisa(integer), app_atendimento_da_resposta(integer),
  fn_prazo_avaliacao_dias(),
  fn_audit(text, text, text, text, jsonb, integer, uuid, inet, text, uuid),
  fn_registrar_login(integer, boolean, inet, text, uuid, uuid),
  fn_verifica_cadeia_auditoria(), fn_ancora_auditoria(),
  fn_emitir_token(integer, text, text, uuid, inet),
  fn_rotacionar_token(text, text, inet),
  fn_revogar_token(text),
  fn_registrar_resposta_pesquisa(integer, text, smallint, jsonb),
  fn_encerrar_atendimentos_vencidos(),
  fn_anonimizar_usuario(integer), fn_anonimizar_cliente(integer),
  fn_expurgo_manutencao(), fn_conteudo_hash(uuid)
to app_django;

-- ---------------------------------------------------------------------
-- 4. ROW LEVEL SECURITY
-- ---------------------------------------------------------------------

alter table perfil enable row level security;
alter table permissao enable row level security;
alter table perfil_permissao enable row level security;
alter table distribuidor enable row level security;
alter table cliente enable row level security;
alter table usuario enable row level security;
alter table propriedade enable row level security;
alter table cultura enable row level security;
alter table propriedade_cultura enable row level security;
alter table dispositivo enable row level security;
alter table sync_conflito enable row level security;
alter table atendimento enable row level security;
alter table historico_atendimento enable row level security;
alter table agendamento enable row level security;
alter table problema_categoria enable row level security;
alter table problema_subcategoria enable row level security;
alter table atendimento_problema enable row level security;
alter table atendimento_cultura enable row level security;
alter table tipo_treinamento enable row level security;
alter table atendimento_tipo_treinamento enable row level security;
alter table relatorio_atendimento enable row level security;
alter table motivo_campo_relatorio enable row level security;
alter table relatorio_especialista enable row level security;
alter table relatorio_versao enable row level security;
alter table relatorio_pdf enable row level security;
alter table envio_comunicacao enable row level security;
alter table evidencia enable row level security;
alter table assinatura enable row level security;
alter table pesquisa_satisfacao enable row level security;
alter table pergunta_satisfacao enable row level security;
alter table resposta_satisfacao enable row level security;
alter table avaliacao_formulario enable row level security;
alter table notificacao enable row level security;
alter table pergunta_formulario enable row level security;
alter table resposta_pergunta_formulario enable row level security;
alter table resposta_opcao enable row level security;
alter table log_auditoria enable row level security;
alter table token_acesso enable row level security;
alter table solicitacao_titular enable row level security;
alter table incidente_seguranca enable row level security;
alter table dominio_email_corporativo enable row level security;
-- token_acesso fica com RLS ligado e SEM policy: ninguém lê nem grava direto.

-- ---- Configuração e catálogos: qualquer usuário ativo lê; só o Netafim Admin altera
create policy perfil_select on perfil for select to app_django
  using (app_usuario_ativo());
create policy perfil_insert on perfil for insert to app_django
  with check (app_eh_admin_netafim());
create policy perfil_update on perfil for update to app_django
  using (app_eh_admin_netafim())
  with check (app_eh_admin_netafim());
create policy permissao_select on permissao for select to app_django
  using (app_usuario_ativo());
create policy permissao_insert on permissao for insert to app_django
  with check (app_eh_admin_netafim());
create policy permissao_update on permissao for update to app_django
  using (app_eh_admin_netafim())
  with check (app_eh_admin_netafim());
create policy cultura_select on cultura for select to app_django
  using (app_usuario_ativo());
create policy cultura_insert on cultura for insert to app_django
  with check (app_eh_admin_netafim());
create policy cultura_update on cultura for update to app_django
  using (app_eh_admin_netafim())
  with check (app_eh_admin_netafim());
create policy tipo_treinamento_select on tipo_treinamento for select to app_django
  using (app_usuario_ativo());
create policy tipo_treinamento_insert on tipo_treinamento for insert to app_django
  with check (app_eh_admin_netafim());
create policy tipo_treinamento_update on tipo_treinamento for update to app_django
  using (app_eh_admin_netafim())
  with check (app_eh_admin_netafim());
create policy problema_categoria_select on problema_categoria for select to app_django
  using (app_usuario_ativo());
create policy problema_categoria_insert on problema_categoria for insert to app_django
  with check (app_eh_admin_netafim());
create policy problema_categoria_update on problema_categoria for update to app_django
  using (app_eh_admin_netafim())
  with check (app_eh_admin_netafim());
create policy problema_subcategoria_select on problema_subcategoria for select to app_django
  using (app_usuario_ativo());
create policy problema_subcategoria_insert on problema_subcategoria for insert to app_django
  with check (app_eh_admin_netafim());
create policy problema_subcategoria_update on problema_subcategoria for update to app_django
  using (app_eh_admin_netafim())
  with check (app_eh_admin_netafim());
create policy motivo_campo_relatorio_select on motivo_campo_relatorio for select to app_django
  using (app_usuario_ativo());
create policy motivo_campo_relatorio_insert on motivo_campo_relatorio for insert to app_django
  with check (app_eh_admin_netafim());
create policy motivo_campo_relatorio_update on motivo_campo_relatorio for update to app_django
  using (app_eh_admin_netafim())
  with check (app_eh_admin_netafim());
create policy pergunta_formulario_select on pergunta_formulario for select to app_django
  using (app_usuario_ativo());
create policy pergunta_formulario_insert on pergunta_formulario for insert to app_django
  with check (app_eh_admin_netafim());
create policy pergunta_formulario_update on pergunta_formulario for update to app_django
  using (app_eh_admin_netafim())
  with check (app_eh_admin_netafim());
create policy pergunta_satisfacao_select on pergunta_satisfacao for select to app_django
  using (app_usuario_ativo());
create policy pergunta_satisfacao_insert on pergunta_satisfacao for insert to app_django
  with check (app_eh_admin_netafim());
create policy pergunta_satisfacao_update on pergunta_satisfacao for update to app_django
  using (app_eh_admin_netafim())
  with check (app_eh_admin_netafim());
create policy distribuidor_select on distribuidor for select to app_django
  using (app_usuario_ativo());
create policy distribuidor_insert on distribuidor for insert to app_django
  with check (app_eh_admin_netafim());
create policy distribuidor_update on distribuidor for update to app_django
  using (app_eh_admin_netafim())
  with check (app_eh_admin_netafim());
create policy perfil_permissao_select on perfil_permissao for select to app_django
  using (app_usuario_ativo());
create policy perfil_permissao_insert on perfil_permissao for insert to app_django
  with check (app_eh_admin_netafim());
create policy perfil_permissao_delete on perfil_permissao for delete to app_django
  using (app_eh_admin_netafim());

-- ---- Cliente e propriedade: cada um vê a própria carteira ou empresa
create policy cliente_select on cliente for select to app_django
  using (app_cliente_visivel(id_cliente));
create policy cliente_insert on cliente for insert to app_django
  with check (app_eh_admin_netafim());
create policy cliente_update on cliente for update to app_django
  using (app_eh_admin_netafim())
  with check (app_eh_admin_netafim());
create policy propriedade_select on propriedade for select to app_django
  using (app_cliente_visivel(id_cliente));
create policy propriedade_insert on propriedade for insert to app_django
  with check (app_cliente_visivel(id_cliente) and app_tem_permissao('abrir_atendimento'));
create policy propriedade_update on propriedade for update to app_django
  using (app_cliente_visivel(id_cliente) and app_tem_permissao('abrir_atendimento'))
  with check (app_cliente_visivel(id_cliente) and app_tem_permissao('abrir_atendimento'));
create policy propriedade_cultura_select on propriedade_cultura for select to app_django
  using (exists (select 1 from propriedade p where p.id_propriedade = propriedade_cultura.id_propriedade));
create policy propriedade_cultura_insert on propriedade_cultura for insert to app_django
  with check (exists (select 1 from propriedade p where p.id_propriedade = propriedade_cultura.id_propriedade));
create policy propriedade_cultura_update on propriedade_cultura for update to app_django
  using (exists (select 1 from propriedade p where p.id_propriedade = propriedade_cultura.id_propriedade))
  with check (exists (select 1 from propriedade p where p.id_propriedade = propriedade_cultura.id_propriedade));

-- ---- Usuário
-- Lê: a si mesmo, tudo (Netafim Admin) ou os usuários da própria organização (admin dela).
create policy usuario_select on usuario for select to app_django
  using (id_usuario = app_usuario_id() or app_eh_admin_netafim() or (app_nivel() = 'admin' and ((app_tipo_org() = 'distribuidor' and id_distribuidor = app_id_distribuidor()) or (app_tipo_org() = 'cliente' and id_cliente = app_id_cliente()))));
-- Login e cadastro: libera somente a linha do e-mail informado em app.login_email.
create policy usuario_select_login on usuario for select to app_django
  using (lower(email) = lower(nullif(current_setting('app.login_email', true), '')));
-- Autocadastro: sempre pendente, sem perfil nem organização, sem ninguém logado.
create policy usuario_insert_cadastro on usuario for insert to app_django
  with check (situacao = 'pendente' and id_perfil is null and tipo_organizacao is null and id_cliente is null and id_distribuidor is null and id_aprovado_por is null and app_usuario_id() is null);
-- Admin cria usuário: o Netafim Admin em qualquer organização; os demais admins só na própria.
create policy usuario_insert_admin on usuario for insert to app_django
  with check (app_tem_permissao('criar_usuarios') and (app_eh_admin_netafim() or (app_nivel() = 'admin' and ((tipo_organizacao = 'distribuidor' and id_distribuidor = app_id_distribuidor()) or (tipo_organizacao = 'cliente' and id_cliente = app_id_cliente())))));
-- Edita: a si mesmo, tudo (Netafim Admin) ou a própria organização. Campos sensíveis: gatilho fn_usuario_protege.
create policy usuario_update on usuario for update to app_django
  using (id_usuario = app_usuario_id() or (app_eh_admin_netafim() ) or ((app_nivel() = 'admin' and ((app_tipo_org() = 'distribuidor' and id_distribuidor = app_id_distribuidor()) or (app_tipo_org() = 'cliente' and id_cliente = app_id_cliente()))) and app_tem_permissao('editar_usuarios')))
  with check (id_usuario = app_usuario_id() or (app_eh_admin_netafim()) or ((app_nivel() = 'admin' and ((app_tipo_org() = 'distribuidor' and id_distribuidor = app_id_distribuidor()) or (app_tipo_org() = 'cliente' and id_cliente = app_id_cliente()))) and app_tem_permissao('editar_usuarios')));

-- ---- Dispositivo e conflitos de sincronização
create policy dispositivo_select on dispositivo for select to app_django
  using (id_usuario = app_usuario_id() or app_eh_admin_netafim());
create policy dispositivo_insert on dispositivo for insert to app_django
  with check (id_usuario = app_usuario_id());
create policy dispositivo_update on dispositivo for update to app_django
  using (id_usuario = app_usuario_id() or app_eh_admin_netafim())
  with check (id_usuario = app_usuario_id() or app_eh_admin_netafim());
create policy sync_conflito_select on sync_conflito for select to app_django
  using (app_eh_admin_netafim() or id_dispositivo in (select d.id_dispositivo from dispositivo d where d.id_usuario = app_usuario_id()));
create policy sync_conflito_insert on sync_conflito for insert to app_django
  with check (app_usuario_ativo());
create policy sync_conflito_update on sync_conflito for update to app_django
  using (app_eh_admin_netafim())
  with check (app_eh_admin_netafim());

-- ---- Atendimento (seção 5.3) e tudo o que pende dele
-- Usa as colunas da própria linha (e não relê a tabela): assim vale também para
-- a linha nova de um INSERT ... RETURNING, que ainda não existe na tabela.
create policy atendimento_select on atendimento for select to app_django
  using (app_atendimento_visivel_linha(id_solicitante, id_tecnico_responsavel,
         id_distribuidor, id_distribuidor_origem, id_cliente));
-- Abrir: perfil com a permissão, em nome de si mesmo; usuário de cliente só para a própria empresa.
create policy atendimento_insert on atendimento for insert to app_django
  with check (app_usuario_ativo() and app_tem_permissao('abrir_atendimento') and id_solicitante = app_usuario_id() and (app_tipo_org() <> 'cliente' or id_cliente = app_id_cliente()));
create policy atendimento_update on atendimento for update to app_django
  using (app_atendimento_operavel(id_atendimento))
  with check (app_atendimento_operavel(id_atendimento));
create policy historico_atendimento_select on historico_atendimento for select to app_django
  using (app_atendimento_visivel(id_atendimento));
create policy agendamento_select on agendamento for select to app_django
  using (app_atendimento_visivel(id_atendimento));
create policy agendamento_insert on agendamento for insert to app_django
  with check (app_atendimento_operavel(id_atendimento));
create policy agendamento_update on agendamento for update to app_django
  using (app_atendimento_operavel(id_atendimento))
  with check (app_atendimento_operavel(id_atendimento));
create policy atendimento_problema_select on atendimento_problema for select to app_django
  using (app_atendimento_visivel(id_atendimento));
create policy atendimento_problema_insert on atendimento_problema for insert to app_django
  with check (app_atendimento_operavel(id_atendimento));
create policy atendimento_problema_update on atendimento_problema for update to app_django
  using (app_atendimento_operavel(id_atendimento))
  with check (app_atendimento_operavel(id_atendimento));
create policy atendimento_cultura_select on atendimento_cultura for select to app_django
  using (app_atendimento_visivel(id_atendimento));
create policy atendimento_cultura_insert on atendimento_cultura for insert to app_django
  with check (app_atendimento_operavel(id_atendimento));
create policy atendimento_cultura_update on atendimento_cultura for update to app_django
  using (app_atendimento_operavel(id_atendimento))
  with check (app_atendimento_operavel(id_atendimento));
create policy atendimento_tipo_treinamento_select on atendimento_tipo_treinamento for select to app_django
  using (app_atendimento_visivel(id_atendimento));
create policy atendimento_tipo_treinamento_insert on atendimento_tipo_treinamento for insert to app_django
  with check (app_atendimento_operavel(id_atendimento));
create policy atendimento_tipo_treinamento_update on atendimento_tipo_treinamento for update to app_django
  using (app_atendimento_operavel(id_atendimento))
  with check (app_atendimento_operavel(id_atendimento));
create policy evidencia_select on evidencia for select to app_django
  using (app_atendimento_visivel(id_atendimento));
create policy evidencia_insert on evidencia for insert to app_django
  with check (app_atendimento_operavel(id_atendimento));
create policy evidencia_update on evidencia for update to app_django
  using (app_atendimento_operavel(id_atendimento))
  with check (app_atendimento_operavel(id_atendimento));
create policy assinatura_select on assinatura for select to app_django
  using (app_atendimento_visivel(id_atendimento));
create policy assinatura_insert on assinatura for insert to app_django
  with check (app_atendimento_operavel(id_atendimento));
create policy assinatura_update on assinatura for update to app_django
  using (app_atendimento_operavel(id_atendimento))
  with check (app_atendimento_operavel(id_atendimento));
create policy avaliacao_formulario_select on avaliacao_formulario for select to app_django
  using (app_atendimento_visivel(id_atendimento));
create policy avaliacao_formulario_insert on avaliacao_formulario for insert to app_django
  with check (app_atendimento_operavel(id_atendimento));
create policy avaliacao_formulario_update on avaliacao_formulario for update to app_django
  using (app_atendimento_operavel(id_atendimento))
  with check (app_atendimento_operavel(id_atendimento));
create policy resposta_pergunta_formulario_select on resposta_pergunta_formulario for select to app_django
  using (app_atendimento_visivel(id_atendimento));
create policy resposta_pergunta_formulario_insert on resposta_pergunta_formulario for insert to app_django
  with check (app_atendimento_operavel(id_atendimento));
create policy resposta_pergunta_formulario_update on resposta_pergunta_formulario for update to app_django
  using (app_atendimento_operavel(id_atendimento))
  with check (app_atendimento_operavel(id_atendimento));
create policy envio_comunicacao_select on envio_comunicacao for select to app_django
  using (app_atendimento_visivel(id_atendimento));
create policy envio_comunicacao_insert on envio_comunicacao for insert to app_django
  with check (app_atendimento_operavel(id_atendimento));
create policy envio_comunicacao_update on envio_comunicacao for update to app_django
  using (app_atendimento_operavel(id_atendimento))
  with check (app_atendimento_operavel(id_atendimento));

-- ---- Relatório
create policy relatorio_atendimento_select on relatorio_atendimento for select to app_django
  using (app_atendimento_visivel(id_atendimento));
create policy relatorio_atendimento_insert on relatorio_atendimento for insert to app_django
  with check (app_atendimento_operavel(id_atendimento) and app_tem_permissao('responder_atendimento') and id_preenchido_por = app_usuario_id());
create policy relatorio_atendimento_update on relatorio_atendimento for update to app_django
  using (app_atendimento_operavel(id_atendimento))
  with check (app_atendimento_operavel(id_atendimento));
create policy relatorio_especialista_select on relatorio_especialista for select to app_django
  using (app_atendimento_visivel(app_atendimento_do_relatorio(id_relatorio)));
create policy relatorio_especialista_insert on relatorio_especialista for insert to app_django
  with check (app_atendimento_operavel(app_atendimento_do_relatorio(id_relatorio)));
create policy relatorio_especialista_update on relatorio_especialista for update to app_django
  using (app_atendimento_operavel(app_atendimento_do_relatorio(id_relatorio)))
  with check (app_atendimento_operavel(app_atendimento_do_relatorio(id_relatorio)));
create policy relatorio_versao_select on relatorio_versao for select to app_django
  using (app_atendimento_visivel(app_atendimento_do_relatorio(id_relatorio)));
create policy relatorio_pdf_select on relatorio_pdf for select to app_django
  using (app_atendimento_visivel(app_atendimento_do_relatorio(id_relatorio)));
create policy relatorio_pdf_insert on relatorio_pdf for insert to app_django
  with check (app_atendimento_operavel(app_atendimento_do_relatorio(id_relatorio)));
create policy resposta_opcao_select on resposta_opcao for select to app_django
  using (app_atendimento_visivel(app_atendimento_da_resposta(id_resposta)));
create policy resposta_opcao_insert on resposta_opcao for insert to app_django
  with check (app_atendimento_operavel(app_atendimento_da_resposta(id_resposta)));
create policy resposta_opcao_update on resposta_opcao for update to app_django
  using (app_atendimento_operavel(app_atendimento_da_resposta(id_resposta)))
  with check (app_atendimento_operavel(app_atendimento_da_resposta(id_resposta)));

-- ---- Satisfação (a resposta entra por fn_registrar_resposta_pesquisa)
create policy pesquisa_satisfacao_select on pesquisa_satisfacao for select to app_django
  using (app_atendimento_visivel(id_atendimento));
create policy pesquisa_satisfacao_insert on pesquisa_satisfacao for insert to app_django
  with check (app_atendimento_operavel(id_atendimento));
create policy resposta_satisfacao_select on resposta_satisfacao for select to app_django
  using (app_atendimento_visivel(app_atendimento_da_pesquisa(id_pesquisa)));

-- ---- Notificações: cada um vê e marca as suas
create policy notificacao_select on notificacao for select to app_django
  using (id_usuario = app_usuario_id());
create policy notificacao_insert on notificacao for insert to app_django
  with check (app_usuario_ativo());
create policy notificacao_update on notificacao for update to app_django
  using (id_usuario = app_usuario_id())
  with check (id_usuario = app_usuario_id());

-- ---- Auditoria, LGPD e domínios: somente o Netafim Admin
create policy log_auditoria_select on log_auditoria for select to app_django
  using (app_eh_admin_netafim());
create policy solicitacao_titular_select on solicitacao_titular for select to app_django
  using (app_eh_admin_netafim());
create policy solicitacao_titular_insert on solicitacao_titular for insert to app_django
  with check (app_eh_admin_netafim());
create policy solicitacao_titular_update on solicitacao_titular for update to app_django
  using (app_eh_admin_netafim())
  with check (app_eh_admin_netafim());
create policy incidente_seguranca_select on incidente_seguranca for select to app_django
  using (app_eh_admin_netafim());
create policy incidente_seguranca_insert on incidente_seguranca for insert to app_django
  with check (app_eh_admin_netafim());
create policy incidente_seguranca_update on incidente_seguranca for update to app_django
  using (app_eh_admin_netafim())
  with check (app_eh_admin_netafim());
create policy dominio_email_corporativo_select on dominio_email_corporativo for select to app_django
  using (app_eh_admin_netafim());
create policy dominio_email_corporativo_insert on dominio_email_corporativo for insert to app_django
  with check (app_eh_admin_netafim());
create policy dominio_email_corporativo_update on dominio_email_corporativo for update to app_django
  using (app_eh_admin_netafim())
  with check (app_eh_admin_netafim());

-- ---------- 04_dados_iniciais.sql ----------
-- =====================================================================
-- 04_dados_iniciais.sql
-- Netafim Pós-venda | Banco central (PostgreSQL / Supabase)
--
-- O QUE FAZ: carga inicial das tabelas de configuração, conforme o
-- documento: perfis, permissões (matriz do Anexo B), culturas, tipos de
-- treinamento, categorias e subcategorias de problema (formulário da
-- Netafim) e a matriz de obrigatoriedade do relatório por motivo.
--
-- NÃO cria: usuários, distribuidores, clientes, domínios de e-mail nem
-- perguntas de satisfação (dependem de dados que a Netafim ainda não
-- passou). Pode rodar de novo: não duplica (usa ON CONFLICT / WHERE NOT EXISTS).
-- =====================================================================

-- Evita encher a auditoria com eventos de configuração da carga inicial.
do $$ begin perform set_config('app.carga_inicial', 'on', false); end $$;

-- ---------------------------------------------------------------- PERFIL
insert into perfil (nome_perfil, tipo_organizacao, nivel, descricao) values
  ('Netafim Admin',       'netafim',      'admin',   'Administrador da Netafim: aprova cadastros, destina, encerra e vê toda a plataforma'),
  ('Netafim Usuário',     'netafim',      'usuario', 'Funcionário da Netafim: abre e responde atendimentos'),
  ('Distribuidor Admin',  'distribuidor', 'admin',   'Administrador do distribuidor: gerencia usuários e técnicos do distribuidor'),
  ('Distribuidor Usuário','distribuidor', 'usuario', 'Técnico ou funcionário do distribuidor'),
  ('Cliente Admin',       'cliente',      'admin',   'Administrador da empresa cliente'),
  ('Cliente Usuário',     'cliente',      'usuario', 'Usuário da empresa cliente')
on conflict (nome_perfil) do nothing;

-- ------------------------------------------------------------- PERMISSAO
insert into permissao (codigo, descricao) values

  ('aprovar_cadastro', 'Aprovar cadastros'),
  ('criar_usuarios', 'Criar usuários'),
  ('editar_usuarios', 'Editar usuários'),
  ('excluir_usuarios', 'Excluir usuários'),
  ('definir_perfil', 'Definir perfil / hierarquia'),
  ('abrir_atendimento', 'Abrir atendimento'),
  ('vincular_tecnico', 'Vincular técnico ao atendimento'),
  ('responder_atendimento', 'Responder atendimento'),
  ('encerrar_atendimento', 'Encerrar atendimento'),
  ('consultar_proprios', 'Consultar próprios atendimentos'),
  ('consultar_empresa', 'Consultar atendimentos da empresa'),
  ('consultar_clientes_vinculados', 'Consultar atendimentos de clientes vinculados'),
  ('consultar_todos', 'Consultar todos os atendimentos da plataforma'),
  ('dashboard_gerencial', 'Dashboard gerencial'),
  ('destinar_atendimento', 'Destinar atendimento (Netafim ou Distribuidor)'),
  ('reclassificar_atendimento', 'Reclassificar atendimento'),
  ('editar_atendimento_finalizado', 'Editar atendimento finalizado')
on conflict (codigo) do nothing;

-- Matriz de permissões (Anexo B): cada marca é uma linha.
insert into perfil_permissao (id_perfil, id_permissao)
select p.id_perfil, pe.id_permissao
  from (values
    ('Netafim Admin', 'aprovar_cadastro'),
    ('Netafim Admin', 'criar_usuarios'),
    ('Distribuidor Admin', 'criar_usuarios'),
    ('Cliente Admin', 'criar_usuarios'),
    ('Netafim Admin', 'editar_usuarios'),
    ('Distribuidor Admin', 'editar_usuarios'),
    ('Cliente Admin', 'editar_usuarios'),
    ('Netafim Admin', 'excluir_usuarios'),
    ('Distribuidor Admin', 'excluir_usuarios'),
    ('Cliente Admin', 'excluir_usuarios'),
    ('Netafim Admin', 'definir_perfil'),
    ('Netafim Admin', 'abrir_atendimento'),
    ('Netafim Usuário', 'abrir_atendimento'),
    ('Distribuidor Admin', 'abrir_atendimento'),
    ('Distribuidor Usuário', 'abrir_atendimento'),
    ('Cliente Admin', 'abrir_atendimento'),
    ('Cliente Usuário', 'abrir_atendimento'),
    ('Netafim Admin', 'vincular_tecnico'),
    ('Distribuidor Admin', 'vincular_tecnico'),
    ('Netafim Admin', 'responder_atendimento'),
    ('Netafim Usuário', 'responder_atendimento'),
    ('Distribuidor Admin', 'responder_atendimento'),
    ('Distribuidor Usuário', 'responder_atendimento'),
    ('Cliente Admin', 'responder_atendimento'),
    ('Cliente Usuário', 'responder_atendimento'),
    ('Netafim Admin', 'encerrar_atendimento'),
    ('Netafim Admin', 'consultar_proprios'),
    ('Netafim Usuário', 'consultar_proprios'),
    ('Distribuidor Admin', 'consultar_proprios'),
    ('Distribuidor Usuário', 'consultar_proprios'),
    ('Cliente Admin', 'consultar_proprios'),
    ('Cliente Usuário', 'consultar_proprios'),
    ('Netafim Admin', 'consultar_empresa'),
    ('Distribuidor Admin', 'consultar_empresa'),
    ('Cliente Admin', 'consultar_empresa'),
    ('Netafim Admin', 'consultar_clientes_vinculados'),
    ('Distribuidor Admin', 'consultar_clientes_vinculados'),
    ('Netafim Admin', 'consultar_todos'),
    ('Netafim Admin', 'dashboard_gerencial'),
    ('Distribuidor Admin', 'dashboard_gerencial'),
    ('Cliente Admin', 'dashboard_gerencial'),
    ('Netafim Admin', 'destinar_atendimento'),
    ('Netafim Admin', 'reclassificar_atendimento'),
    ('Netafim Admin', 'editar_atendimento_finalizado')
  ) as m (nome_perfil, codigo)
  join perfil p     on p.nome_perfil = m.nome_perfil
  join permissao pe on pe.codigo = m.codigo
on conflict do nothing;

-- --------------------------------------------------------------- CULTURA
insert into cultura (nome)
select v.nome from (values ('Cacau'), ('Café'), ('Cana-de-açúcar'), ('Citrus'), ('Grãos'), ('Outros')) as v (nome)
where not exists (select 1 from cultura c where lower(c.nome) = lower(v.nome) and c.deletado_em is null);

-- ------------------------------------------------------ TIPO_TREINAMENTO
insert into tipo_treinamento (nome)
select v.nome from (values ('Agronômico'), ('Digital Farming'), ('Manutenção Preventiva'),
                           ('Operação/Manutenção'), ('Padronização de Instalação')) as v (nome)
where not exists (select 1 from tipo_treinamento t where lower(t.nome) = lower(v.nome) and t.deletado_em is null);

-- --------------------------------------------- PROBLEMA_CATEGORIA e SUBCATEGORIA
-- Categorias sem subitens (Chave de partida, Cronograma de Garantia) ficam sem linhas em
-- problema_subcategoria. "Avaliação de sistema" aparece só na Estruturação do Formulário:
-- está comentada abaixo até a Netafim confirmar (pendência 6).
insert into problema_categoria (nome, aplicavel_a, ordem)
select v.nome, v.aplicavel_a, v.ordem from (values
  ('Automação', 'pos_venda', 1),
  ('Bombeamento', 'pos_venda', 2),
  ('Cavalete', 'pos_venda', 3),
  ('Chave de partida', 'pos_venda', 4),
  ('Cronograma de Garantia', 'pos_venda', 5),
  ('Emissores', 'pos_venda', 6),
  ('Fertirrigação', 'pos_venda', 7),
  ('Filtragem', 'pos_venda', 8),
  ('Tubulação', 'pos_venda', 9),
  ('Controlador', 'digital_farming', 1),
  ('Estação Meteorológica', 'digital_farming', 2),
  ('GS One', 'digital_farming', 3),
  ('Plataforma', 'digital_farming', 4),
  ('RadioNet/NETRTU', 'digital_farming', 5)
) as v (nome, aplicavel_a, ordem)
where not exists (select 1 from problema_categoria c
                   where lower(c.nome) = lower(v.nome) and c.aplicavel_a = v.aplicavel_a and c.deletado_em is null);

-- insert into problema_categoria (nome, aplicavel_a, ordem) values ('Avaliação de sistema', 'pos_venda', 10);

insert into problema_subcategoria (id_categoria, descricao, ordem)
select c.id_categoria, v.descricao, v.ordem from (values
  ('Automação', 'pos_venda', 'Aquative rompida', 1),
  ('Automação', 'pos_venda', 'Microtubo sem comando', 2),
  ('Automação', 'pos_venda', 'Problema no Controlador', 3),
  ('Automação', 'pos_venda', 'Régua do aquative', 4),
  ('Automação', 'pos_venda', 'Solenóide queimando', 5),
  ('Bombeamento', 'pos_venda', 'Falta de pressão', 1),
  ('Bombeamento', 'pos_venda', 'Ligação de pressão', 2),
  ('Bombeamento', 'pos_venda', 'Regulagem da antecipadora de onda', 3),
  ('Bombeamento', 'pos_venda', 'Sucção', 4),
  ('Bombeamento', 'pos_venda', 'Válvula de ar', 5),
  ('Cavalete', 'pos_venda', 'Piloto drenando', 1),
  ('Cavalete', 'pos_venda', 'Problema na válvula', 2),
  ('Cavalete', 'pos_venda', 'Vazamento na estrutura', 3),
  ('Emissores', 'pos_venda', 'Chicote dobrando', 1),
  ('Emissores', 'pos_venda', 'Entupido', 2),
  ('Emissores', 'pos_venda', 'Esguichando', 3),
  ('Emissores', 'pos_venda', 'Tubogotejador rasgado', 4),
  ('Emissores', 'pos_venda', 'Vazão diferente da nominal', 5),
  ('Fertirrigação', 'pos_venda', 'Booster da fertirrigação', 1),
  ('Fertirrigação', 'pos_venda', 'Fertkit não liga', 2),
  ('Fertirrigação', 'pos_venda', 'Falta de vazão de injeção', 3),
  ('Filtragem', 'pos_venda', 'Automação da retrolavagem', 1),
  ('Filtragem', 'pos_venda', 'Hidrômetro', 2),
  ('Filtragem', 'pos_venda', 'Válvula de alívio', 3),
  ('Filtragem', 'pos_venda', 'Válvula de retrolavagem', 4),
  ('Filtragem', 'pos_venda', 'Vazamentos', 5),
  ('Tubulação', 'pos_venda', 'Rompimento adutora', 1),
  ('Tubulação', 'pos_venda', 'Rompimento secundária ou ramal', 2),
  ('Tubulação', 'pos_venda', 'Válvula de ar drenando', 3),
  ('Tubulação', 'pos_venda', 'Vazamento junta elástica', 4),
  ('Tubulação', 'pos_venda', 'Vazamento junta soldável', 5),
  ('Controlador', 'digital_farming', 'Bomba não liga', 1),
  ('Controlador', 'digital_farming', 'Canal de injeção não funciona', 2),
  ('Controlador', 'digital_farming', 'Programação', 3),
  ('Controlador', 'digital_farming', 'Válvula não abre', 4),
  ('Estação Meteorológica', 'digital_farming', 'Falha de conexão', 1),
  ('Estação Meteorológica', 'digital_farming', 'Valores incorretos', 2),
  ('GS One', 'digital_farming', 'Desconexão', 1),
  ('GS One', 'digital_farming', 'Problemas nos sensores', 2),
  ('Plataforma', 'digital_farming', 'Configuração', 1),
  ('Plataforma', 'digital_farming', 'Criação de login', 2),
  ('Plataforma', 'digital_farming', 'Erro no sistema', 3),
  ('Plataforma', 'digital_farming', 'Extração de relatórios e gráficos', 4),
  ('RadioNet/NETRTU', 'digital_farming', 'Bateria baixa', 1),
  ('RadioNet/NETRTU', 'digital_farming', 'Sinal oscilando', 2)
) as v (categoria, aplicavel_a, descricao, ordem)
join problema_categoria c on lower(c.nome) = lower(v.categoria) and c.aplicavel_a = v.aplicavel_a and c.deletado_em is null
where not exists (select 1 from problema_subcategoria s
                   where s.id_categoria = c.id_categoria and lower(s.descricao) = lower(v.descricao) and s.deletado_em is null);

-- ------------------------------------------------ MOTIVO_CAMPO_RELATORIO
-- O que é obrigatório para finalizar o relatório de cada motivo (formulário "Relatório de
-- Resolução do Atendimento"). Alterável sem migração: basta mudar a coluna obrigatorio.
-- Assinatura do cliente: obrigatória em todos os motivos, exceto Programação de Feiras
-- (a confirmar com a Netafim).
insert into motivo_campo_relatorio (motivo, campo, obrigatorio) values
  ('digital_farming', 'tipo_atendimento', true),
  ('digital_farming', 'descricao_constatacoes', true),
  ('digital_farming', 'evidencia_constatacao', true),
  ('digital_farming', 'atividades_executadas', true),
  ('digital_farming', 'evidencia_atividade', true),
  ('digital_farming', 'plano_acao', true),
  ('digital_farming', 'conclusao_visita', true),
  ('digital_farming', 'assinatura_especialista', true),
  ('digital_farming', 'assinatura_cliente', true),
  ('duvidas', 'tipo_atendimento', true),
  ('duvidas', 'descricao_constatacoes', true),
  ('duvidas', 'evidencia_constatacao', true),
  ('duvidas', 'conclusao_visita', true),
  ('duvidas', 'assinatura_especialista', true),
  ('duvidas', 'assinatura_cliente', true),
  ('entrega_tecnica', 'atividades_executadas', true),
  ('entrega_tecnica', 'evidencia_atividade', true),
  ('entrega_tecnica', 'plano_acao', true),
  ('entrega_tecnica', 'conclusao_visita', true),
  ('entrega_tecnica', 'assinatura_especialista', true),
  ('entrega_tecnica', 'assinatura_cliente', true),
  ('pos_venda', 'descricao_constatacoes', true),
  ('pos_venda', 'evidencia_constatacao', true),
  ('pos_venda', 'atividades_executadas', true),
  ('pos_venda', 'evidencia_atividade', true),
  ('pos_venda', 'plano_acao', true),
  ('pos_venda', 'conclusao_visita', true),
  ('pos_venda', 'assinatura_especialista', true),
  ('pos_venda', 'assinatura_cliente', true),
  ('programacao_feiras', 'evidencia_geral', true),
  ('programacao_feiras', 'conclusao_visita', true),
  ('programacao_feiras', 'assinatura_especialista', true),
  ('treinamento', 'tipo_treinamento', true),
  ('treinamento', 'local_treinamento', true),
  ('treinamento', 'qtd_pessoas_treinadas', true),
  ('treinamento', 'evidencia_geral', true),
  ('treinamento', 'conclusao_visita', true),
  ('treinamento', 'assinatura_especialista', true),
  ('treinamento', 'assinatura_cliente', true)
on conflict (motivo, campo) do nothing;

do $$ begin perform set_config('app.carga_inicial', '', false); end $$;

-- ---------- senha de teste do app_django (so para testes locais; nunca usar em producao) ----------
alter role app_django with password 'app_django_teste';
