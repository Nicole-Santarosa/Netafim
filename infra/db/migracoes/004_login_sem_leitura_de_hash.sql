-- =====================================================================================
-- PROPOSTA DT-002, PARTE 1 DE 2 (NÃO aplicada no banco do grupo): papel app_auth e funções de credencial.
--
-- Esta parte é ADITIVA: só cria o papel app_auth e as funções fn_credencial_por_email e
-- fn_credencial_por_id. Não revoga nada. Por isso é compatível com o código Django ANTIGO e com o NOVO,
-- e pode ser aplicada antes, durante ou depois de cada colega atualizar o código. A parte 2 (005) é a que
-- revoga o SELECT de app_django em usuario.password e em log_auditoria.
--
-- Ordem de publicação sugerida:
--   1. aplicar esta 004 (aditiva) e definir a senha de app_auth fora do repositório;
--   2. cada pessoa atualiza o código e configura DB_AUTH_USER e DB_AUTH_PASSWORD;
--   3. conferir o modo seguro contra o banco real (login pela conexão "auth");
--   4. só então aplicar a 005 (REVOKE), quando TODOS estiverem no código novo.
--
-- O que ela cria: o papel app_auth, SEM nenhum privilégio de tabela, que só executa duas funções
-- SECURITY DEFINER (alias "auth" no Django), usadas só para conferir a senha.
--
-- Importante, para ficar claro o limite: o Django precisa do hash para conferir a senha, então o hash
-- ainda chega ao processo de login. O ganho (depois da 005) é que a conexão COMUM da API deixa de poder ler
-- `password` por consulta e não consegue chamar as funções de credencial.
--
-- Depois de aplicar:
--   * defina a senha de app_auth FORA do repositório:  ALTER ROLE app_auth WITH PASSWORD '<segredo>';
--     (no pooler do Supabase o usuário é "app_auth.<ref-do-projeto>", como em app_django).
--
-- Idempotente (CREATE ROLE com IF NOT EXISTS; GRANT, REVOKE e CREATE OR REPLACE repetem sem erro).
-- Aplicar com o dono do esquema, depois do "de acordo" do grupo.
-- =====================================================================================

-- app_auth: o único papel que recebe o hash ----------------------------------------------------
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
