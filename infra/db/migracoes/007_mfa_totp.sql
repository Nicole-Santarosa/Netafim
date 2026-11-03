-- =====================================================================================
-- MIGRAÇÃO 007 (NÃO aplicada no banco do grupo): autenticação em dois fatores (TOTP, RFC 6238).
--
-- Depende da 004 (papel app_auth). Aplicar depois da 004 e antes de publicar o código com MFA.
--
-- Desenho (mesmo isolamento da DT-002 para o hash da senha):
--   * usuario_mfa guarda o segredo TOTP e usuario_mfa_recuperacao guarda só o HASH dos códigos de
--     recuperação. Nenhum privilégio para app_django: a conexão comum da API não lê segredo nem hash.
--   * só o papel app_auth executa as funções fn_mfa_* (SECURITY DEFINER, search_path fixo).
--   * usuario.mfa_ativo continua sendo a chave de ligar e desligar, alterada pela API como o próprio
--     titular (ou o Netafim Admin) para a auditoria de mfa_alterado do banco registrar o ator. Um gatilho
--     impede ativar sem segredo CONFIRMADO e apaga segredo e códigos quando o MFA é desligado, inclusive
--     na anonimização do titular (LGPD).
--   * ultimo_passo impede reutilizar o mesmo código (replay): cada passo de 30 s vale uma vez.
--
-- Limite: o segredo TOTP precisa ser reversível (o servidor o usa para calcular o código), então fica
-- legível por quem administra o banco e por quem tem a senha do app_auth. A proteção em repouso depende
-- do banco (criptografia de disco do provedor). Criptografia em nível de aplicação fica como evolução.
--
-- Idempotente. Aplicar com o dono do esquema, depois do "de acordo" do grupo.
-- =====================================================================================
CREATE TABLE IF NOT EXISTS public.usuario_mfa (
  id_usuario     integer      PRIMARY KEY REFERENCES public.usuario (id_usuario),
  segredo        varchar(64)  NOT NULL,
  confirmado_em  timestamptz,
  ultimo_passo   bigint       NOT NULL DEFAULT 0,
  criado_em      timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT ck_usuario_mfa_segredo CHECK (segredo ~ '^[A-Z2-7]{16,64}$')
);

CREATE TABLE IF NOT EXISTS public.usuario_mfa_recuperacao (
  id_codigo    bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_usuario   integer      NOT NULL REFERENCES public.usuario (id_usuario),
  codigo_hash  char(64)     NOT NULL,
  usado_em     timestamptz,
  criado_em    timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT uq_usuario_mfa_recuperacao UNIQUE (id_usuario, codigo_hash),
  CONSTRAINT ck_usuario_mfa_recuperacao_hash CHECK (codigo_hash ~ '^[0-9a-f]{64}$')
);

ALTER TABLE public.usuario_mfa ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.usuario_mfa_recuperacao ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE public.usuario_mfa, public.usuario_mfa_recuperacao FROM PUBLIC;
REVOKE ALL ON TABLE public.usuario_mfa, public.usuario_mfa_recuperacao FROM app_django;

-- ---------------------------------------------------------------------------------- funções
-- Guarda um segredo NOVO ainda não confirmado. Recusa se o MFA já está ativo. Se existia um segredo
-- nunca ativado (confirmado ou não), ele é substituído e os códigos de recuperação antigos são apagados.
CREATE OR REPLACE FUNCTION public.fn_mfa_guardar_segredo(p_id_usuario integer, p_segredo text)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
begin
  if exists (select 1 from usuario u where u.id_usuario = p_id_usuario and u.mfa_ativo) then
    raise exception 'mfa já ativo para este usuário' using errcode = '23514';
  end if;
  delete from usuario_mfa_recuperacao where id_usuario = p_id_usuario;
  insert into usuario_mfa (id_usuario, segredo) values (p_id_usuario, p_segredo)
  on conflict (id_usuario) do update
    set segredo = excluded.segredo, confirmado_em = null, ultimo_passo = 0, criado_em = now();
end
$function$;

-- Segredo, se confirmado e o último passo aceito. 0 ou 1 linha.
CREATE OR REPLACE FUNCTION public.fn_mfa_segredo(p_id_usuario integer)
 RETURNS TABLE (segredo text, confirmado boolean, ultimo_passo bigint)
 LANGUAGE sql
 STABLE
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
  select m.segredo::text, (m.confirmado_em is not null), m.ultimo_passo
    from usuario_mfa m where m.id_usuario = p_id_usuario
$function$;

-- Quantos códigos de recuperação ainda não foram usados.
CREATE OR REPLACE FUNCTION public.fn_mfa_recuperacao_restante(p_id_usuario integer)
 RETURNS integer
 LANGUAGE sql
 STABLE
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
  select count(*)::integer from usuario_mfa_recuperacao r
   where r.id_usuario = p_id_usuario and r.usado_em is null
$function$;

-- Aceita um passo de tempo UMA vez: devolve false se já foi usado (ou um passo anterior).
CREATE OR REPLACE FUNCTION public.fn_mfa_registrar_passo(p_id_usuario integer, p_passo bigint)
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
begin
  update usuario_mfa set ultimo_passo = p_passo
   where id_usuario = p_id_usuario and confirmado_em is not null and ultimo_passo < p_passo;
  return found;
end
$function$;

-- Confirma o segredo (o titular provou que o aplicativo gera o código certo) e grava os HASHES dos
-- códigos de recuperação. O código em si só existe na resposta da API, uma vez.
CREATE OR REPLACE FUNCTION public.fn_mfa_confirmar(p_id_usuario integer, p_passo bigint, p_hashes text[])
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare h text;
begin
  update usuario_mfa set confirmado_em = now(), ultimo_passo = p_passo
   where id_usuario = p_id_usuario and confirmado_em is null;
  if not found then
    raise exception 'não há segredo de mfa pendente de confirmação' using errcode = '23514';
  end if;
  delete from usuario_mfa_recuperacao where id_usuario = p_id_usuario;
  foreach h in array p_hashes loop
    insert into usuario_mfa_recuperacao (id_usuario, codigo_hash) values (p_id_usuario, h);
  end loop;
end
$function$;

-- Consome um código de recuperação (uso único). false se não existe ou já foi usado.
CREATE OR REPLACE FUNCTION public.fn_mfa_consumir_recuperacao(p_id_usuario integer, p_hash text)
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
begin
  update usuario_mfa_recuperacao set usado_em = now()
   where id_usuario = p_id_usuario and codigo_hash = p_hash and usado_em is null;
  return found;
end
$function$;

-- ---------------------------------------------------------------------------------- gatilhos
-- Só liga o MFA com segredo confirmado; nasce sempre desligado.
CREATE OR REPLACE FUNCTION public.fn_usuario_mfa_coerente()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
begin
  if new.mfa_ativo and (tg_op = 'INSERT' or not old.mfa_ativo) then
    if tg_op = 'INSERT' or not exists (
         select 1 from usuario_mfa m where m.id_usuario = new.id_usuario and m.confirmado_em is not null) then
      raise exception 'mfa só pode ser ativado com segredo confirmado' using errcode = '23514';
    end if;
  end if;
  return new;
end
$function$;

-- Ao desligar o MFA (ou anonimizar o titular), segredo e códigos de recuperação são apagados.
CREATE OR REPLACE FUNCTION public.fn_usuario_mfa_apaga()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
begin
  if old.mfa_ativo and not new.mfa_ativo then
    delete from usuario_mfa_recuperacao where id_usuario = new.id_usuario;
    delete from usuario_mfa where id_usuario = new.id_usuario;
  end if;
  return null;
end
$function$;

DROP TRIGGER IF EXISTS trg_usuario_mfa_coerente ON public.usuario;
CREATE TRIGGER trg_usuario_mfa_coerente BEFORE INSERT OR UPDATE OF mfa_ativo ON public.usuario
  FOR EACH ROW EXECUTE FUNCTION public.fn_usuario_mfa_coerente();

DROP TRIGGER IF EXISTS trg_usuario_mfa_apaga ON public.usuario;
CREATE TRIGGER trg_usuario_mfa_apaga AFTER UPDATE OF mfa_ativo ON public.usuario
  FOR EACH ROW EXECUTE FUNCTION public.fn_usuario_mfa_apaga();

-- ---------------------------------------------------------------------------------- permissões
DO $$
DECLARE f text;
BEGIN
  FOREACH f IN ARRAY ARRAY[
    'fn_mfa_guardar_segredo(integer, text)', 'fn_mfa_segredo(integer)',
    'fn_mfa_recuperacao_restante(integer)', 'fn_mfa_registrar_passo(integer, bigint)',
    'fn_mfa_confirmar(integer, bigint, text[])', 'fn_mfa_consumir_recuperacao(integer, text)',
    'fn_usuario_mfa_coerente()', 'fn_usuario_mfa_apaga()'] LOOP
    EXECUTE format('REVOKE ALL ON FUNCTION public.%s FROM PUBLIC', f);
    EXECUTE format('REVOKE ALL ON FUNCTION public.%s FROM app_django', f);
  END LOOP;
  FOREACH f IN ARRAY ARRAY[
    'fn_mfa_guardar_segredo(integer, text)', 'fn_mfa_segredo(integer)',
    'fn_mfa_recuperacao_restante(integer)', 'fn_mfa_registrar_passo(integer, bigint)',
    'fn_mfa_confirmar(integer, bigint, text[])', 'fn_mfa_consumir_recuperacao(integer, text)'] LOOP
    EXECUTE format('GRANT EXECUTE ON FUNCTION public.%s TO app_auth', f);
  END LOOP;
END $$;

COMMENT ON TABLE public.usuario_mfa IS
  'Segredo TOTP do usuário. Sem privilégio para app_django; só as funções fn_mfa_* (app_auth) acessam (migração 007).';
COMMENT ON TABLE public.usuario_mfa_recuperacao IS
  'Hash SHA-256 dos códigos de recuperação do MFA, de uso único. Sem privilégio para app_django.';
