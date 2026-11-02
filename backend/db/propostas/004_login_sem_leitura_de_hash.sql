-- =====================================================================================
-- PROPOSTA DT-002 (NÃO aplicada no banco do grupo): a API deixa de ler o hash da senha e a trilha de auditoria.
--
-- ATENÇÃO: aplicar SOMENTE junto com a mudança correspondente no Django (mesma entrega). Se o REVOKE
-- entrar antes do código novo, o login e a AutenticacaoJWT quebram: o ORM seleciona todas as colunas,
-- inclusive `password`.
--
-- Hoje: app_django tem SELECT em usuario (inclui password) e em log_auditoria. Quem conseguir executar
-- SQL pela API (injeção, credencial vazada) lista o hash de todos os usuários e lê a trilha inteira.
--
-- O que muda:
--   1. app_django perde o SELECT em log_auditoria (a API só GRAVA nela, por fn_audit) e passa a ter SELECT em
--      usuario somente nas colunas que não são `password`.
--   2. Nasce o papel app_auth, SEM nenhum privilégio de tabela, que só executa duas funções
--      SECURITY DEFINER: fn_credencial_por_email e fn_credencial_por_id. É uma conexão separada
--      (alias "auth" no Django), usada só para conferir a senha.
--
-- Importante, para ficar claro o limite: o Django precisa do hash para conferir a senha, então o hash
-- ainda chega ao processo de login. O ganho é que a conexão COMUM da API deixa de poder ler `password` por
-- consulta e não consegue chamar as funções de credencial.
--
-- Depois de aplicar:
--   * defina a senha de app_auth FORA do repositório:  ALTER ROLE app_auth WITH PASSWORD '<segredo>';
--     (no pooler do Supabase o usuário é "app_auth.<ref-do-projeto>", como em app_django);
--   * ao criar coluna nova em usuario, conceda também o SELECT dela a app_django (concessão por coluna
--     não é automática): GRANT SELECT (nova_coluna) ON public.usuario TO app_django;
--   * leitura da trilha passa a ser só pelo dono do esquema (fn_verifica_cadeia_auditoria e fn_ancora_auditoria).
--
-- Aplicar com o dono do esquema, depois do "de acordo" do grupo.
-- =====================================================================================

-- 1. app_django: sem log_auditoria e sem a coluna password --------------------------------------
REVOKE SELECT ON public.log_auditoria FROM app_django;

REVOKE SELECT ON public.usuario FROM app_django;
DO $$
BEGIN
  EXECUTE 'GRANT SELECT (' || (
    SELECT string_agg(quote_ident(attname), ', ' ORDER BY attnum)
      FROM pg_attribute
     WHERE attrelid = 'public.usuario'::regclass AND attnum > 0 AND NOT attisdropped
       AND attname <> 'password'
  ) || ') ON public.usuario TO app_django';
END $$;

-- 2. app_auth: o único papel que recebe o hash ----------------------------------------------------
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_auth') THEN
    CREATE ROLE app_auth LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;
  END IF;
  EXECUTE format('GRANT CONNECT ON DATABASE %I TO app_auth', current_database());
END $$;
GRANT USAGE ON SCHEMA public TO app_auth;
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM app_auth;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM app_auth;

-- Contrato (as duas funções devolvem as mesmas colunas, na mesma ordem):
--   id_usuario integer, password varchar, situacao varchar, falhas_login smallint, bloqueado_ate timestamptz
-- Só o hash é segredo; situação e contadores de login a API já lê pelo ORM. Vêm junto para o login
-- decidir com uma só consulta. Conta inexistente ou excluída: nenhuma linha (o Django gasta o mesmo
-- tempo de CPU de um hash real, para não revelar se o e-mail existe).
CREATE OR REPLACE FUNCTION public.fn_credencial_por_email(p_email text)
 RETURNS TABLE (id_usuario integer, password varchar, situacao varchar, falhas_login smallint, bloqueado_ate timestamptz)
 LANGUAGE sql
 STABLE
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
  SELECT u.id_usuario, u.password, u.situacao, u.falhas_login, u.bloqueado_ate
    FROM usuario u
   WHERE lower(u.email) = lower(p_email) AND u.deletado_em IS NULL
$function$;

-- Usada na troca de senha: o usuário já está autenticado pelo JWT e a API informa o id dele.
CREATE OR REPLACE FUNCTION public.fn_credencial_por_id(p_id_usuario integer)
 RETURNS TABLE (id_usuario integer, password varchar, situacao varchar, falhas_login smallint, bloqueado_ate timestamptz)
 LANGUAGE sql
 STABLE
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
  SELECT u.id_usuario, u.password, u.situacao, u.falhas_login, u.bloqueado_ate
    FROM usuario u
   WHERE u.id_usuario = p_id_usuario AND u.deletado_em IS NULL
$function$;

REVOKE ALL ON FUNCTION public.fn_credencial_por_email(text) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.fn_credencial_por_id(integer) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.fn_credencial_por_email(text) FROM app_django;
REVOKE ALL ON FUNCTION public.fn_credencial_por_id(integer) FROM app_django;
GRANT EXECUTE ON FUNCTION public.fn_credencial_por_email(text) TO app_auth;
GRANT EXECUTE ON FUNCTION public.fn_credencial_por_id(integer) TO app_auth;

COMMENT ON FUNCTION public.fn_credencial_por_email(text) IS
  'Hash da senha por e-mail. Só o papel app_auth executa; a conexão comum da API (app_django) não (DT-002).';
COMMENT ON FUNCTION public.fn_credencial_por_id(integer) IS
  'Hash da senha por id, para conferir a senha atual na troca de senha. Só app_auth executa (DT-002).';
