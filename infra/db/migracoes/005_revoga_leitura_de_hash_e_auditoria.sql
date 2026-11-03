-- =====================================================================================
-- PROPOSTA DT-002, PARTE 2 DE 2 (NÃO aplicada no banco do grupo): a API deixa de ler o hash da senha e a trilha.
--
-- ATENÇÃO, ESTE É O PASSO QUE QUEBRA O CÓDIGO ANTIGO. Aplicar SOMENTE quando:
--   * a 004 já estiver aplicada e a senha de app_auth definida;
--   * TODAS as pessoas estiverem no código Django novo e com DB_AUTH_USER e DB_AUTH_PASSWORD configurados;
--   * o login pela conexão "auth" tiver sido conferido contra o banco real.
-- Depois desta parte, o código antigo (e o modo legado do código novo, sem DB_AUTH_PASSWORD) deixa de
-- conseguir ler `usuario.password`: o login e a AutenticacaoJWT falham com "permission denied".
--
-- Hoje: app_django tem SELECT em usuario (inclui password) e em log_auditoria. Quem conseguir executar
-- SQL pela API (injeção, credencial vazada) lista o hash de todos os usuários e lê a trilha inteira.
--
-- O que muda:
--   * app_django perde o SELECT em log_auditoria (a API só GRAVA nela, por fn_audit);
--   * app_django passa a ter SELECT em usuario somente nas colunas que não são `password`.
--   * leitura da trilha passa a ser só pelo dono do esquema (fn_verifica_cadeia_auditoria e fn_ancora_auditoria).
--
-- Ao criar coluna nova em usuario, conceda também o SELECT dela a app_django (concessão por coluna
-- não é automática): GRANT SELECT (nova_coluna) ON public.usuario TO app_django;
--
-- Para DESFAZER (se algo quebrar): GRANT SELECT ON public.usuario, public.log_auditoria TO app_django;
-- Idempotente. Aplicar com o dono do esquema, depois do "de acordo" do grupo.
-- =====================================================================================

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
