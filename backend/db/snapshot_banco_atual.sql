-- =====================================================================================
-- RETRATO do banco "Projeto Netafim" (Supabase) lido em 26/10/2026, somente leitura.
--
-- NÃO é a fonte da verdade: a fonte é o banco do grupo. Este arquivo reproduz, só para testes
-- locais e de CI, as tabelas e funções de que o módulo de acesso depende (usuários, perfis,
-- permissões, organizações, aparelhos, tokens e a trilha de auditoria), com as mesmas
-- constraints, gatilhos, funções, políticas de RLS e privilégios do papel app_django.
-- Se o banco mudar, regere este retrato e rode a suíte de testes.
--
-- Contrato com a aplicação (resumo):
--   * a API conecta como app_django (sem BYPASSRLS) e, em CADA transação, informa o ator:
--       select set_config('app.current_user_id', '<id>', true);
--     No login, antes de existir ator, informa o e-mail: set_config('app.login_email', ...).
--   * token_acesso não tem política nem privilégio para app_django: só as funções
--     fn_emitir_token, fn_rotacionar_token e fn_revogar_token (SECURITY DEFINER) a tocam.
--   * falhas de login e bloqueio só mudam por fn_registrar_login.
-- =====================================================================================

-- ---------- papel da aplicação ----------
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_django') THEN
    CREATE ROLE app_django LOGIN PASSWORD 'app_django_teste' NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;
  END IF;
END $$;

CREATE SEQUENCE seq_sync;

-- ---------- tabelas ----------
CREATE TABLE distribuidor (
  id_distribuidor integer GENERATED ALWAYS AS IDENTITY NOT NULL,
  nome character varying(150) NOT NULL,
  regiao character varying(100) NOT NULL,
  email character varying(150),
  telefone character varying(20),
  status character varying(20) DEFAULT 'ativo'::character varying NOT NULL,
  data_criacao timestamp with time zone DEFAULT now() NOT NULL,
  data_atualizacao timestamp with time zone DEFAULT now() NOT NULL,
  deletado_em timestamp with time zone,
  sync_seq bigint NOT NULL
);
CREATE TABLE cliente (
  id_cliente integer GENERATED ALWAYS AS IDENTITY NOT NULL,
  nome character varying(150) NOT NULL,
  email character varying(150),
  telefone character varying(20),
  id_distribuidor integer,
  status character varying(20) DEFAULT 'ativo'::character varying NOT NULL,
  data_criacao timestamp with time zone DEFAULT now() NOT NULL,
  data_atualizacao timestamp with time zone DEFAULT now() NOT NULL,
  deletado_em timestamp with time zone,
  sync_seq bigint NOT NULL
);
CREATE TABLE perfil (
  id_perfil integer GENERATED ALWAYS AS IDENTITY NOT NULL,
  nome_perfil character varying(50) NOT NULL,
  tipo_organizacao character varying(15) NOT NULL,
  nivel character varying(10) NOT NULL,
  descricao text
);
CREATE TABLE permissao (
  id_permissao integer GENERATED ALWAYS AS IDENTITY NOT NULL,
  codigo character varying(50) NOT NULL,
  descricao character varying(150) NOT NULL
);
CREATE TABLE perfil_permissao (
  id_perfil integer NOT NULL,
  id_permissao integer NOT NULL
);
CREATE TABLE dominio_email_corporativo (
  id_dominio integer GENERATED ALWAYS AS IDENTITY NOT NULL,
  dominio character varying(120) NOT NULL,
  tipo_organizacao character varying(15) NOT NULL,
  id_distribuidor integer,
  ativo boolean DEFAULT true NOT NULL,
  data_criacao timestamp with time zone DEFAULT now() NOT NULL
);
CREATE TABLE usuario (
  id_usuario integer GENERATED ALWAYS AS IDENTITY NOT NULL,
  nome character varying(150) NOT NULL,
  email character varying(150) NOT NULL,
  telefone character varying(20),
  password character varying(128) NOT NULL,
  last_login timestamp with time zone,
  situacao character varying(15) DEFAULT 'pendente'::character varying NOT NULL,
  falhas_login smallint DEFAULT 0 NOT NULL,
  bloqueado_ate timestamp with time zone,
  mfa_ativo boolean DEFAULT false NOT NULL,
  senha_alterada_em timestamp with time zone,
  exige_troca_senha boolean DEFAULT false NOT NULL,
  tipo_organizacao character varying(15),
  id_perfil integer,
  id_distribuidor integer,
  id_cliente integer,
  id_aprovado_por integer,
  data_aprovacao timestamp with time zone,
  consentimento_lgpd boolean DEFAULT false NOT NULL,
  data_consentimento timestamp with time zone,
  versao_termo_consentimento character varying(20),
  data_revogacao_consentimento timestamp with time zone,
  data_exclusao_solicitada timestamp with time zone,
  data_criacao timestamp with time zone DEFAULT now() NOT NULL,
  data_atualizacao timestamp with time zone DEFAULT now() NOT NULL,
  deletado_em timestamp with time zone
);
CREATE TABLE dispositivo (
  id_dispositivo uuid NOT NULL,
  id_usuario integer NOT NULL,
  modelo character varying(80),
  versao_app character varying(20),
  ultima_sincronizacao timestamp with time zone,
  revogado_em timestamp with time zone,
  limpeza_remota_solicitada_em timestamp with time zone,
  limpeza_remota_confirmada_em timestamp with time zone,
  data_criacao timestamp with time zone DEFAULT now() NOT NULL
);
CREATE TABLE token_acesso (
  id_token uuid DEFAULT gen_random_uuid() NOT NULL,
  id_usuario integer NOT NULL,
  id_dispositivo uuid,
  tipo character varying(15) NOT NULL,
  token_hash character(64) NOT NULL,
  criado_em timestamp with time zone DEFAULT now() NOT NULL,
  expira_em timestamp with time zone NOT NULL,
  usado_em timestamp with time zone,
  revogado_em timestamp with time zone,
  substituido_por uuid,
  ip inet
);
CREATE TABLE log_auditoria (
  id_evento bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
  data_evento timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
  id_usuario integer,
  id_dispositivo uuid,
  acao character varying(40) NOT NULL,
  resultado character varying(10) NOT NULL,
  entidade_tipo character varying(40),
  entidade_id character varying(64),
  ip inet,
  user_agent character varying(255),
  correlacao_id uuid,
  detalhes jsonb,
  hash_anterior character(64),
  hash_evento character(64) NOT NULL
);

-- ---------- chaves, unicidades e checks ----------
ALTER TABLE cliente ADD CONSTRAINT cliente_pkey PRIMARY KEY (id_cliente);
ALTER TABLE dispositivo ADD CONSTRAINT dispositivo_pkey PRIMARY KEY (id_dispositivo);
ALTER TABLE distribuidor ADD CONSTRAINT distribuidor_pkey PRIMARY KEY (id_distribuidor);
ALTER TABLE dominio_email_corporativo ADD CONSTRAINT dominio_email_corporativo_pkey PRIMARY KEY (id_dominio);
ALTER TABLE log_auditoria ADD CONSTRAINT log_auditoria_pkey PRIMARY KEY (id_evento);
ALTER TABLE perfil ADD CONSTRAINT perfil_pkey PRIMARY KEY (id_perfil);
ALTER TABLE perfil_permissao ADD CONSTRAINT perfil_permissao_pkey PRIMARY KEY (id_perfil, id_permissao);
ALTER TABLE permissao ADD CONSTRAINT permissao_pkey PRIMARY KEY (id_permissao);
ALTER TABLE token_acesso ADD CONSTRAINT token_acesso_pkey PRIMARY KEY (id_token);
ALTER TABLE usuario ADD CONSTRAINT usuario_pkey PRIMARY KEY (id_usuario);
ALTER TABLE dominio_email_corporativo ADD CONSTRAINT uq_dominio UNIQUE (dominio);
ALTER TABLE perfil ADD CONSTRAINT uq_perfil_id_tipo UNIQUE (id_perfil, tipo_organizacao);
ALTER TABLE perfil ADD CONSTRAINT uq_perfil_nome UNIQUE (nome_perfil);
ALTER TABLE perfil ADD CONSTRAINT uq_perfil_tipo_nivel UNIQUE (tipo_organizacao, nivel);
ALTER TABLE permissao ADD CONSTRAINT uq_permissao_codigo UNIQUE (codigo);
ALTER TABLE token_acesso ADD CONSTRAINT uq_token_hash UNIQUE (token_hash);
ALTER TABLE cliente ADD CONSTRAINT ck_cliente_status CHECK (((status)::text = ANY ((ARRAY['ativo'::character varying, 'inativo'::character varying])::text[])));
ALTER TABLE dispositivo ADD CONSTRAINT ck_dispositivo_limpeza CHECK (((limpeza_remota_confirmada_em IS NULL) OR (limpeza_remota_solicitada_em IS NOT NULL)));
ALTER TABLE distribuidor ADD CONSTRAINT ck_distribuidor_status CHECK (((status)::text = ANY ((ARRAY['ativo'::character varying, 'inativo'::character varying])::text[])));
ALTER TABLE dominio_email_corporativo ADD CONSTRAINT ck_dominio_distribuidor CHECK (((((tipo_organizacao)::text = 'distribuidor'::text) AND (id_distribuidor IS NOT NULL)) OR (((tipo_organizacao)::text = 'netafim'::text) AND (id_distribuidor IS NULL))));
ALTER TABLE dominio_email_corporativo ADD CONSTRAINT ck_dominio_formato CHECK ((((dominio)::text = lower((dominio)::text)) AND ((dominio)::text !~ '@'::text)));
ALTER TABLE dominio_email_corporativo ADD CONSTRAINT ck_dominio_tipo CHECK (((tipo_organizacao)::text = ANY ((ARRAY['netafim'::character varying, 'distribuidor'::character varying])::text[])));
ALTER TABLE log_auditoria ADD CONSTRAINT ck_audit_acao CHECK (((acao)::text = ANY ((ARRAY['login_sucesso'::character varying, 'login_falha'::character varying, 'logout'::character varying, 'conta_bloqueada'::character varying, 'senha_alterada'::character varying, 'mfa_alterado'::character varying, 'cadastro_solicitado'::character varying, 'cadastro_aprovado'::character varying, 'cadastro_recusado'::character varying, 'situacao_alterada'::character varying, 'perfil_alterado'::character varying, 'permissao_concedida'::character varying, 'permissao_revogada'::character varying, 'acesso_negado'::character varying, 'relatorio_editado_pos_finalizacao'::character varying, 'relatorio_baixado'::character varying, 'evidencia_baixada'::character varying, 'exportacao_dados'::character varying, 'dispositivo_revogado'::character varying, 'limpeza_remota'::character varying, 'dados_anonimizados'::character varying, 'solicitacao_titular'::character varying, 'incidente_registrado'::character varying, 'configuracao_alterada'::character varying])::text[])));
ALTER TABLE log_auditoria ADD CONSTRAINT ck_audit_hash CHECK ((hash_evento ~ '^[0-9a-f]{64}$'::text));
ALTER TABLE log_auditoria ADD CONSTRAINT ck_audit_hash_ant CHECK (((hash_anterior IS NULL) OR (hash_anterior ~ '^[0-9a-f]{64}$'::text)));
ALTER TABLE log_auditoria ADD CONSTRAINT ck_audit_resultado CHECK (((resultado)::text = ANY ((ARRAY['sucesso'::character varying, 'falha'::character varying, 'negado'::character varying])::text[])));
ALTER TABLE perfil ADD CONSTRAINT ck_perfil_nivel CHECK (((nivel)::text = ANY ((ARRAY['admin'::character varying, 'usuario'::character varying])::text[])));
ALTER TABLE perfil ADD CONSTRAINT ck_perfil_tipo CHECK (((tipo_organizacao)::text = ANY ((ARRAY['netafim'::character varying, 'distribuidor'::character varying, 'cliente'::character varying])::text[])));
ALTER TABLE token_acesso ADD CONSTRAINT ck_token_hash CHECK ((token_hash ~ '^[0-9a-f]{64}$'::text));
ALTER TABLE token_acesso ADD CONSTRAINT ck_token_tipo CHECK (((tipo)::text = ANY ((ARRAY['refresh'::character varying, 'convite'::character varying, 'reset_senha'::character varying])::text[])));
ALTER TABLE token_acesso ADD CONSTRAINT ck_token_validade CHECK (((expira_em > criado_em) AND (expira_em <= (criado_em + '90 days'::interval))));
ALTER TABLE usuario ADD CONSTRAINT ck_usuario_ativo_completo CHECK ((((situacao)::text <> 'ativo'::text) OR ((id_perfil IS NOT NULL) AND (tipo_organizacao IS NOT NULL))));
ALTER TABLE usuario ADD CONSTRAINT ck_usuario_consentimento CHECK (((NOT consentimento_lgpd) OR ((data_consentimento IS NOT NULL) AND (versao_termo_consentimento IS NOT NULL))));
ALTER TABLE usuario ADD CONSTRAINT ck_usuario_falhas CHECK ((falhas_login >= 0));
ALTER TABLE usuario ADD CONSTRAINT ck_usuario_organizacao CHECK ((((tipo_organizacao IS NULL) AND (id_cliente IS NULL) AND (id_distribuidor IS NULL)) OR (((tipo_organizacao)::text = 'netafim'::text) AND (id_cliente IS NULL) AND (id_distribuidor IS NULL)) OR (((tipo_organizacao)::text = 'distribuidor'::text) AND (id_cliente IS NULL) AND (id_distribuidor IS NOT NULL)) OR (((tipo_organizacao)::text = 'cliente'::text) AND (id_cliente IS NOT NULL) AND (id_distribuidor IS NULL))));
ALTER TABLE usuario ADD CONSTRAINT ck_usuario_password_formato CHECK ((((password)::text ~ '^[a-z0-9_]+\$.+'::text) OR ((password)::text ~ '^!'::text)));
ALTER TABLE usuario ADD CONSTRAINT ck_usuario_perfil_tipo CHECK (((id_perfil IS NULL) OR (tipo_organizacao IS NOT NULL)));
ALTER TABLE usuario ADD CONSTRAINT ck_usuario_revogacao CHECK (((data_revogacao_consentimento IS NULL) OR (consentimento_lgpd = false)));
ALTER TABLE usuario ADD CONSTRAINT ck_usuario_situacao CHECK (((situacao)::text = ANY ((ARRAY['pendente'::character varying, 'ativo'::character varying, 'recusado'::character varying, 'inativo'::character varying, 'bloqueado'::character varying])::text[])));
ALTER TABLE usuario ADD CONSTRAINT ck_usuario_tipo_org CHECK (((tipo_organizacao IS NULL) OR ((tipo_organizacao)::text = ANY ((ARRAY['netafim'::character varying, 'distribuidor'::character varying, 'cliente'::character varying])::text[]))));
ALTER TABLE cliente ADD CONSTRAINT cliente_id_distribuidor_fkey FOREIGN KEY (id_distribuidor) REFERENCES distribuidor(id_distribuidor);
ALTER TABLE dispositivo ADD CONSTRAINT dispositivo_id_usuario_fkey FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario);
ALTER TABLE dominio_email_corporativo ADD CONSTRAINT dominio_email_corporativo_id_distribuidor_fkey FOREIGN KEY (id_distribuidor) REFERENCES distribuidor(id_distribuidor);
ALTER TABLE log_auditoria ADD CONSTRAINT log_auditoria_id_dispositivo_fkey FOREIGN KEY (id_dispositivo) REFERENCES dispositivo(id_dispositivo);
ALTER TABLE log_auditoria ADD CONSTRAINT log_auditoria_id_usuario_fkey FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario);
ALTER TABLE perfil_permissao ADD CONSTRAINT perfil_permissao_id_perfil_fkey FOREIGN KEY (id_perfil) REFERENCES perfil(id_perfil);
ALTER TABLE perfil_permissao ADD CONSTRAINT perfil_permissao_id_permissao_fkey FOREIGN KEY (id_permissao) REFERENCES permissao(id_permissao);
ALTER TABLE token_acesso ADD CONSTRAINT token_acesso_id_dispositivo_fkey FOREIGN KEY (id_dispositivo) REFERENCES dispositivo(id_dispositivo);
ALTER TABLE token_acesso ADD CONSTRAINT token_acesso_id_usuario_fkey FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario);
ALTER TABLE token_acesso ADD CONSTRAINT token_acesso_substituido_por_fkey FOREIGN KEY (substituido_por) REFERENCES token_acesso(id_token) ON DELETE SET NULL;
ALTER TABLE usuario ADD CONSTRAINT fk_usuario_perfil FOREIGN KEY (id_perfil, tipo_organizacao) REFERENCES perfil(id_perfil, tipo_organizacao);
ALTER TABLE usuario ADD CONSTRAINT usuario_id_aprovado_por_fkey FOREIGN KEY (id_aprovado_por) REFERENCES usuario(id_usuario);
ALTER TABLE usuario ADD CONSTRAINT usuario_id_cliente_fkey FOREIGN KEY (id_cliente) REFERENCES cliente(id_cliente);
ALTER TABLE usuario ADD CONSTRAINT usuario_id_distribuidor_fkey FOREIGN KEY (id_distribuidor) REFERENCES distribuidor(id_distribuidor);

-- ---------- índices ----------
CREATE INDEX ix_cliente_distribuidor ON cliente USING btree (id_distribuidor);
CREATE UNIQUE INDEX ux_cliente_sync ON cliente USING btree (sync_seq);
CREATE INDEX ix_dispositivo_usuario ON dispositivo USING btree (id_usuario);
CREATE UNIQUE INDEX ux_distribuidor_sync ON distribuidor USING btree (sync_seq);
CREATE INDEX ix_dominio_distribuidor ON dominio_email_corporativo USING btree (id_distribuidor);
CREATE INDEX ix_audit_acao_data ON log_auditoria USING btree (acao, data_evento);
CREATE INDEX ix_audit_dispositivo ON log_auditoria USING btree (id_dispositivo);
CREATE INDEX ix_audit_usuario ON log_auditoria USING btree (id_usuario);
CREATE INDEX ix_perfil_permissao_permissao ON perfil_permissao USING btree (id_permissao);
CREATE INDEX ix_token_dispositivo ON token_acesso USING btree (id_dispositivo);
CREATE INDEX ix_token_substituido ON token_acesso USING btree (substituido_por);
CREATE INDEX ix_token_usuario ON token_acesso USING btree (id_usuario);
CREATE INDEX ix_usuario_aprovado_por ON usuario USING btree (id_aprovado_por);
CREATE INDEX ix_usuario_cliente ON usuario USING btree (id_cliente);
CREATE INDEX ix_usuario_distribuidor ON usuario USING btree (id_distribuidor);
CREATE INDEX ix_usuario_perfil ON usuario USING btree (id_perfil);
CREATE UNIQUE INDEX ux_usuario_email ON usuario USING btree (lower((email)::text)) WHERE (deletado_em IS NULL);

-- ---------- funções ----------
CREATE OR REPLACE FUNCTION public.app_usuario_id()
 RETURNS integer
 LANGUAGE sql
 STABLE
AS $function$
  select nullif(current_setting('app.current_user_id', true), '')::integer
$function$;

CREATE OR REPLACE FUNCTION public.app_acao_sistema()
 RETURNS boolean
 LANGUAGE sql
 STABLE
AS $function$
  select coalesce(current_setting('app.acao_sistema', true), '') = 'on'
$function$;

CREATE OR REPLACE FUNCTION public.app_carga_inicial()
 RETURNS boolean
 LANGUAGE sql
 STABLE
AS $function$
  select coalesce(current_setting('app.carga_inicial', true), '') = 'on'
$function$;

CREATE OR REPLACE FUNCTION public.app_nivel()
 RETURNS text
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
  select p.nivel::text
    from usuario u join perfil p on p.id_perfil = u.id_perfil
   where u.id_usuario = app_usuario_id()
     and u.situacao = 'ativo' and u.deletado_em is null
$function$;

CREATE OR REPLACE FUNCTION public.app_tipo_org()
 RETURNS text
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
  select u.tipo_organizacao::text from usuario u
   where u.id_usuario = app_usuario_id()
     and u.situacao = 'ativo' and u.deletado_em is null
$function$;

CREATE OR REPLACE FUNCTION public.app_eh_admin_netafim()
 RETURNS boolean
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
  select coalesce(app_tipo_org() = 'netafim' and app_nivel() = 'admin', false)
$function$;

CREATE OR REPLACE FUNCTION public.app_id_cliente()
 RETURNS integer
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
  select u.id_cliente from usuario u
   where u.id_usuario = app_usuario_id()
     and u.situacao = 'ativo' and u.deletado_em is null
$function$;

CREATE OR REPLACE FUNCTION public.app_id_distribuidor()
 RETURNS integer
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
  select u.id_distribuidor from usuario u
   where u.id_usuario = app_usuario_id()
     and u.situacao = 'ativo' and u.deletado_em is null
$function$;

CREATE OR REPLACE FUNCTION public.app_usuario_ativo()
 RETURNS boolean
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
  select exists (
    select 1 from usuario u
     where u.id_usuario = app_usuario_id()
       and u.situacao = 'ativo'
       and u.deletado_em is null)
$function$;

CREATE OR REPLACE FUNCTION public.app_tem_permissao(p_codigo text)
 RETURNS boolean
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
  select exists (
    select 1
      from usuario u
      join perfil_permissao pp on pp.id_perfil = u.id_perfil
      join permissao pe on pe.id_permissao = pp.id_permissao
     where u.id_usuario = app_usuario_id()
       and u.situacao = 'ativo' and u.deletado_em is null
       and pe.codigo = p_codigo)
$function$;

CREATE OR REPLACE FUNCTION public.app_cliente_visivel(p_id_cliente integer)
 RETURNS boolean
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
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
$function$;

CREATE OR REPLACE FUNCTION public.fn_touch()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
begin
  new.data_atualizacao := now();
  return new;
end
$function$;

CREATE OR REPLACE FUNCTION public.fn_sync_meta()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
begin
  new.data_atualizacao := now();
  new.sync_seq := nextval('seq_sync');
  return new;
end
$function$;

CREATE OR REPLACE FUNCTION public.fn_somente_insercao()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
begin
  raise exception 'a tabela % é somente inserção: % não é permitido', tg_table_name, tg_op
    using errcode = '23514';
end
$function$;

CREATE OR REPLACE FUNCTION public.fn_audit_payload(r log_auditoria)
 RETURNS text
 LANGUAGE sql
 IMMUTABLE
AS $function$
  select jsonb_build_array(
           r.id_evento,
           to_char(r.data_evento at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US'),
           r.id_usuario, r.id_dispositivo, r.acao, r.resultado,
           r.entidade_tipo, r.entidade_id, r.ip::text, r.user_agent,
           r.correlacao_id, r.detalhes, r.hash_anterior)::text
$function$;

CREATE OR REPLACE FUNCTION public.fn_audit_antes()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
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
$function$;

CREATE OR REPLACE FUNCTION public.fn_audit(p_acao text, p_resultado text DEFAULT 'sucesso'::text, p_entidade_tipo text DEFAULT NULL::text, p_entidade_id text DEFAULT NULL::text, p_detalhes jsonb DEFAULT NULL::jsonb, p_id_usuario integer DEFAULT NULL::integer, p_id_dispositivo uuid DEFAULT NULL::uuid, p_ip inet DEFAULT NULL::inet, p_user_agent text DEFAULT NULL::text, p_correlacao_id uuid DEFAULT NULL::uuid)
 RETURNS bigint
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
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
$function$;

CREATE OR REPLACE FUNCTION public.fn_usuario_antes()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
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
$function$;

CREATE OR REPLACE FUNCTION public.fn_usuario_depois()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
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
$function$;

CREATE OR REPLACE FUNCTION public.fn_usuario_protege()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
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
$function$;

CREATE OR REPLACE FUNCTION public.fn_token_antes()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
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
$function$;

CREATE OR REPLACE FUNCTION public.fn_dispositivo_antes()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
begin
  if new.id_usuario <> old.id_usuario then
    raise exception 'o aparelho não muda de dono' using errcode = '23514';
  end if;
  if old.revogado_em is not null and new.revogado_em is distinct from old.revogado_em then
    raise exception 'a revogação do aparelho não volta atrás' using errcode = '23514';
  end if;
  return new;
end
$function$;

CREATE OR REPLACE FUNCTION public.fn_dispositivo_depois()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
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
$function$;

CREATE OR REPLACE FUNCTION public.fn_perfil_permissao_depois()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
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
$function$;

CREATE OR REPLACE FUNCTION public.fn_registrar_login(p_id_usuario integer, p_sucesso boolean, p_ip inet DEFAULT NULL::inet, p_user_agent text DEFAULT NULL::text, p_id_dispositivo uuid DEFAULT NULL::uuid, p_correlacao_id uuid DEFAULT NULL::uuid)
 RETURNS TABLE(o_situacao text, o_falhas integer, o_bloqueado_ate timestamp with time zone)
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
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
$function$;

CREATE OR REPLACE FUNCTION public.fn_emitir_token(p_id_usuario integer, p_tipo text, p_token_hash text, p_id_dispositivo uuid DEFAULT NULL::uuid, p_ip inet DEFAULT NULL::inet)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
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
$function$;

CREATE OR REPLACE FUNCTION public.fn_revogar_token(p_hash text)
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  v_n integer;
begin
  update token_acesso set revogado_em = now()
   where token_hash = p_hash and revogado_em is null;
  get diagnostics v_n = row_count;
  return v_n > 0;
end
$function$;

CREATE OR REPLACE FUNCTION public.fn_rotacionar_token(p_hash_antigo text, p_hash_novo text DEFAULT NULL::text, p_ip inet DEFAULT NULL::inet)
 RETURNS TABLE(resultado text, id_usuario integer, id_dispositivo uuid, id_token_novo uuid)
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
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
$function$;

-- ---------- gatilhos ----------
CREATE TRIGGER trg_sync BEFORE INSERT OR UPDATE ON public.cliente FOR EACH ROW EXECUTE FUNCTION fn_sync_meta();
CREATE TRIGGER trg_sync BEFORE INSERT OR UPDATE ON public.distribuidor FOR EACH ROW EXECUTE FUNCTION fn_sync_meta();
CREATE TRIGGER trg_dispositivo_antes BEFORE UPDATE ON public.dispositivo FOR EACH ROW EXECUTE FUNCTION fn_dispositivo_antes();
CREATE TRIGGER trg_dispositivo_depois AFTER UPDATE ON public.dispositivo FOR EACH ROW EXECUTE FUNCTION fn_dispositivo_depois();
CREATE TRIGGER trg_perfil_permissao_depois AFTER INSERT OR DELETE ON public.perfil_permissao FOR EACH ROW EXECUTE FUNCTION fn_perfil_permissao_depois();
CREATE TRIGGER trg_token_antes BEFORE UPDATE ON public.token_acesso FOR EACH ROW EXECUTE FUNCTION fn_token_antes();
CREATE TRIGGER trg_usuario_antes BEFORE INSERT OR UPDATE ON public.usuario FOR EACH ROW EXECUTE FUNCTION fn_usuario_antes();
CREATE TRIGGER trg_usuario_depois AFTER INSERT OR UPDATE ON public.usuario FOR EACH ROW EXECUTE FUNCTION fn_usuario_depois();
CREATE TRIGGER trg_usuario_protege BEFORE UPDATE ON public.usuario FOR EACH ROW EXECUTE FUNCTION fn_usuario_protege();
CREATE TRIGGER trg_usuario_touch BEFORE INSERT OR UPDATE ON public.usuario FOR EACH ROW EXECUTE FUNCTION fn_touch();
CREATE TRIGGER trg_audit_antes BEFORE INSERT ON public.log_auditoria FOR EACH ROW EXECUTE FUNCTION fn_audit_antes();
CREATE TRIGGER trg_audit_imutavel BEFORE DELETE OR UPDATE ON public.log_auditoria FOR EACH ROW EXECUTE FUNCTION fn_somente_insercao();
CREATE TRIGGER trg_audit_truncate BEFORE TRUNCATE ON public.log_auditoria FOR EACH STATEMENT EXECUTE FUNCTION fn_somente_insercao();

-- ---------- RLS (ligada, sem FORCE; token_acesso fica sem política de propósito) ----------
ALTER TABLE perfil ENABLE ROW LEVEL SECURITY;
ALTER TABLE permissao ENABLE ROW LEVEL SECURITY;
ALTER TABLE perfil_permissao ENABLE ROW LEVEL SECURITY;
ALTER TABLE distribuidor ENABLE ROW LEVEL SECURITY;
ALTER TABLE cliente ENABLE ROW LEVEL SECURITY;
ALTER TABLE usuario ENABLE ROW LEVEL SECURITY;
ALTER TABLE dispositivo ENABLE ROW LEVEL SECURITY;
ALTER TABLE token_acesso ENABLE ROW LEVEL SECURITY;
ALTER TABLE dominio_email_corporativo ENABLE ROW LEVEL SECURITY;
ALTER TABLE log_auditoria ENABLE ROW LEVEL SECURITY;

CREATE POLICY cliente_insert ON cliente FOR INSERT TO app_django WITH CHECK (app_eh_admin_netafim());
CREATE POLICY cliente_select ON cliente FOR SELECT TO app_django USING (app_cliente_visivel(id_cliente));
CREATE POLICY cliente_update ON cliente FOR UPDATE TO app_django USING (app_eh_admin_netafim()) WITH CHECK (app_eh_admin_netafim());
CREATE POLICY dispositivo_insert ON dispositivo FOR INSERT TO app_django WITH CHECK ((id_usuario = app_usuario_id()));
CREATE POLICY dispositivo_select ON dispositivo FOR SELECT TO app_django USING (((id_usuario = app_usuario_id()) OR app_eh_admin_netafim()));
CREATE POLICY dispositivo_update ON dispositivo FOR UPDATE TO app_django USING (((id_usuario = app_usuario_id()) OR app_eh_admin_netafim())) WITH CHECK (((id_usuario = app_usuario_id()) OR app_eh_admin_netafim()));
CREATE POLICY distribuidor_insert ON distribuidor FOR INSERT TO app_django WITH CHECK (app_eh_admin_netafim());
CREATE POLICY distribuidor_select ON distribuidor FOR SELECT TO app_django USING (app_usuario_ativo());
CREATE POLICY distribuidor_update ON distribuidor FOR UPDATE TO app_django USING (app_eh_admin_netafim()) WITH CHECK (app_eh_admin_netafim());
CREATE POLICY dominio_email_corporativo_insert ON dominio_email_corporativo FOR INSERT TO app_django WITH CHECK (app_eh_admin_netafim());
CREATE POLICY dominio_email_corporativo_select ON dominio_email_corporativo FOR SELECT TO app_django USING (app_eh_admin_netafim());
CREATE POLICY dominio_email_corporativo_update ON dominio_email_corporativo FOR UPDATE TO app_django USING (app_eh_admin_netafim()) WITH CHECK (app_eh_admin_netafim());
CREATE POLICY log_auditoria_select ON log_auditoria FOR SELECT TO app_django USING (app_eh_admin_netafim());
CREATE POLICY perfil_insert ON perfil FOR INSERT TO app_django WITH CHECK (app_eh_admin_netafim());
CREATE POLICY perfil_select ON perfil FOR SELECT TO app_django USING (app_usuario_ativo());
CREATE POLICY perfil_update ON perfil FOR UPDATE TO app_django USING (app_eh_admin_netafim()) WITH CHECK (app_eh_admin_netafim());
CREATE POLICY perfil_permissao_delete ON perfil_permissao FOR DELETE TO app_django USING (app_eh_admin_netafim());
CREATE POLICY perfil_permissao_insert ON perfil_permissao FOR INSERT TO app_django WITH CHECK (app_eh_admin_netafim());
CREATE POLICY perfil_permissao_select ON perfil_permissao FOR SELECT TO app_django USING (app_usuario_ativo());
CREATE POLICY permissao_insert ON permissao FOR INSERT TO app_django WITH CHECK (app_eh_admin_netafim());
CREATE POLICY permissao_select ON permissao FOR SELECT TO app_django USING (app_usuario_ativo());
CREATE POLICY permissao_update ON permissao FOR UPDATE TO app_django USING (app_eh_admin_netafim()) WITH CHECK (app_eh_admin_netafim());
CREATE POLICY usuario_insert_admin ON usuario FOR INSERT TO app_django WITH CHECK ((app_tem_permissao('criar_usuarios'::text) AND (app_eh_admin_netafim() OR ((app_nivel() = 'admin'::text) AND ((((tipo_organizacao)::text = 'distribuidor'::text) AND (id_distribuidor = app_id_distribuidor())) OR (((tipo_organizacao)::text = 'cliente'::text) AND (id_cliente = app_id_cliente())))))));
CREATE POLICY usuario_insert_cadastro ON usuario FOR INSERT TO app_django WITH CHECK ((((situacao)::text = 'pendente'::text) AND (id_perfil IS NULL) AND (tipo_organizacao IS NULL) AND (id_cliente IS NULL) AND (id_distribuidor IS NULL) AND (id_aprovado_por IS NULL) AND (app_usuario_id() IS NULL)));
CREATE POLICY usuario_select ON usuario FOR SELECT TO app_django USING (((id_usuario = app_usuario_id()) OR app_eh_admin_netafim() OR ((app_nivel() = 'admin'::text) AND (((app_tipo_org() = 'distribuidor'::text) AND (id_distribuidor = app_id_distribuidor())) OR ((app_tipo_org() = 'cliente'::text) AND (id_cliente = app_id_cliente()))))));
CREATE POLICY usuario_select_login ON usuario FOR SELECT TO app_django USING ((lower((email)::text) = lower(NULLIF(current_setting('app.login_email'::text, true), ''::text))));
CREATE POLICY usuario_update ON usuario FOR UPDATE TO app_django USING (((id_usuario = app_usuario_id()) OR app_eh_admin_netafim() OR ((app_nivel() = 'admin'::text) AND (((app_tipo_org() = 'distribuidor'::text) AND (id_distribuidor = app_id_distribuidor())) OR ((app_tipo_org() = 'cliente'::text) AND (id_cliente = app_id_cliente()))) AND app_tem_permissao('editar_usuarios'::text)))) WITH CHECK (((id_usuario = app_usuario_id()) OR app_eh_admin_netafim() OR ((app_nivel() = 'admin'::text) AND (((app_tipo_org() = 'distribuidor'::text) AND (id_distribuidor = app_id_distribuidor())) OR ((app_tipo_org() = 'cliente'::text) AND (id_cliente = app_id_cliente()))) AND app_tem_permissao('editar_usuarios'::text))));

-- ---------- privilégios do app_django (as demais funções ficam sem EXECUTE para PUBLIC) ----------
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO app_django;
GRANT INSERT, SELECT, UPDATE ON cliente TO app_django;
GRANT INSERT, SELECT, UPDATE ON dispositivo TO app_django;
GRANT INSERT, SELECT, UPDATE ON distribuidor TO app_django;
GRANT INSERT, SELECT, UPDATE ON dominio_email_corporativo TO app_django;
GRANT SELECT ON log_auditoria TO app_django;
GRANT INSERT, SELECT, UPDATE ON perfil TO app_django;
GRANT DELETE, INSERT, SELECT ON perfil_permissao TO app_django;
GRANT INSERT, SELECT, UPDATE ON permissao TO app_django;
GRANT INSERT, SELECT, UPDATE ON usuario TO app_django;
GRANT EXECUTE ON FUNCTION app_usuario_id(), app_nivel(), app_tipo_org(), app_eh_admin_netafim(), app_id_cliente(),
      app_id_distribuidor(), app_usuario_ativo(), app_tem_permissao(text), app_cliente_visivel(integer),
      fn_audit(text, text, text, text, jsonb, integer, uuid, inet, text, uuid),
      fn_emitir_token(integer, text, text, uuid, inet), fn_registrar_login(integer, boolean, inet, text, uuid, uuid),
      fn_revogar_token(text), fn_rotacionar_token(text, text, inet) TO app_django;

-- ---------- dados de base (perfis, permissões e a matriz do Anexo B) ----------
SELECT set_config('app.carga_inicial', 'on', false);

INSERT INTO perfil (nome_perfil, tipo_organizacao, nivel, descricao) VALUES
('Netafim Admin','netafim','admin','Administrador da Netafim: aprova cadastros, destina, encerra e vê toda a plataforma'),
('Netafim Usuário','netafim','usuario','Funcionário da Netafim: abre e responde atendimentos'),
('Distribuidor Admin','distribuidor','admin','Administrador do distribuidor: gerencia usuários e técnicos do distribuidor'),
('Distribuidor Usuário','distribuidor','usuario','Técnico ou funcionário do distribuidor'),
('Cliente Admin','cliente','admin','Administrador da empresa cliente'),
('Cliente Usuário','cliente','usuario','Usuário da empresa cliente');

INSERT INTO permissao (codigo, descricao) VALUES
('aprovar_cadastro','Aprovar cadastros'),
('criar_usuarios','Criar usuários'),
('editar_usuarios','Editar usuários'),
('excluir_usuarios','Excluir usuários'),
('definir_perfil','Definir perfil / hierarquia'),
('abrir_atendimento','Abrir atendimento'),
('vincular_tecnico','Vincular técnico ao atendimento'),
('responder_atendimento','Responder atendimento'),
('encerrar_atendimento','Encerrar atendimento'),
('consultar_proprios','Consultar próprios atendimentos'),
('consultar_empresa','Consultar atendimentos da empresa'),
('consultar_clientes_vinculados','Consultar atendimentos de clientes vinculados'),
('consultar_todos','Consultar todos os atendimentos da plataforma'),
('dashboard_gerencial','Dashboard gerencial'),
('destinar_atendimento','Destinar atendimento (Netafim ou Distribuidor)'),
('reclassificar_atendimento','Reclassificar atendimento'),
('editar_atendimento_finalizado','Editar atendimento finalizado');

INSERT INTO perfil_permissao (id_perfil, id_permissao)
SELECT pf.id_perfil, pe.id_permissao
  FROM (VALUES
('Netafim Admin','aprovar_cadastro'),('Netafim Admin','criar_usuarios'),('Netafim Admin','editar_usuarios'),
('Netafim Admin','excluir_usuarios'),('Netafim Admin','definir_perfil'),('Netafim Admin','abrir_atendimento'),
('Netafim Admin','vincular_tecnico'),('Netafim Admin','responder_atendimento'),('Netafim Admin','encerrar_atendimento'),
('Netafim Admin','consultar_proprios'),('Netafim Admin','consultar_empresa'),('Netafim Admin','consultar_clientes_vinculados'),
('Netafim Admin','consultar_todos'),('Netafim Admin','dashboard_gerencial'),('Netafim Admin','destinar_atendimento'),
('Netafim Admin','reclassificar_atendimento'),('Netafim Admin','editar_atendimento_finalizado'),
('Netafim Usuário','abrir_atendimento'),('Netafim Usuário','responder_atendimento'),('Netafim Usuário','consultar_proprios'),
('Distribuidor Admin','criar_usuarios'),('Distribuidor Admin','editar_usuarios'),('Distribuidor Admin','excluir_usuarios'),
('Distribuidor Admin','abrir_atendimento'),('Distribuidor Admin','vincular_tecnico'),('Distribuidor Admin','responder_atendimento'),
('Distribuidor Admin','consultar_proprios'),('Distribuidor Admin','consultar_empresa'),('Distribuidor Admin','consultar_clientes_vinculados'),
('Distribuidor Admin','dashboard_gerencial'),
('Distribuidor Usuário','abrir_atendimento'),('Distribuidor Usuário','responder_atendimento'),('Distribuidor Usuário','consultar_proprios'),
('Cliente Admin','criar_usuarios'),('Cliente Admin','editar_usuarios'),('Cliente Admin','excluir_usuarios'),
('Cliente Admin','abrir_atendimento'),('Cliente Admin','responder_atendimento'),('Cliente Admin','consultar_proprios'),
('Cliente Admin','consultar_empresa'),('Cliente Admin','dashboard_gerencial'),
('Cliente Usuário','abrir_atendimento'),('Cliente Usuário','responder_atendimento'),('Cliente Usuário','consultar_proprios')
  ) AS v(perfil, permissao)
  JOIN perfil pf ON pf.nome_perfil = v.perfil
  JOIN permissao pe ON pe.codigo = v.permissao;

SELECT set_config('app.carga_inicial', '', false);
