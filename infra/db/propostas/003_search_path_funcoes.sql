-- =====================================================================================
-- PROPOSTA DT-004 (NÃO aplicada no banco do grupo): fixar search_path nas funções que ainda não têm.
--
-- Motivo: 54 das 64 funções do esquema public já usam `SET search_path = public, pg_temp`. Estas 10 não
-- usam. Sem isso, um objeto de outro esquema (ou em pg_temp) pode ser resolvido no lugar do esperado.
-- Formato igual ao das demais funções do banco e do patch de atendimento: public, pg_temp.
--
-- Nenhuma delas é SECURITY DEFINER, então o risco prático é baixo; o ganho é uniformidade e defesa
-- em profundidade. Efeito colateral conhecido: funções SQL simples com SET deixam de ser "inlined" pelo
-- planejador (app_usuario_id, app_acao_sistema, app_carga_inicial). O custo é de microssegundos por chamada.
--
-- ALTER FUNCTION ... SET não altera o corpo da função nem exige reescrever gatilhos. Pode ser reaplicado
-- sem efeito colateral. Aplicar com o dono do esquema, depois do "de acordo" do grupo.
-- =====================================================================================
ALTER FUNCTION public.app_acao_sistema()                       SET search_path = public, pg_temp;
ALTER FUNCTION public.app_carga_inicial()                      SET search_path = public, pg_temp;
ALTER FUNCTION public.app_usuario_id()                         SET search_path = public, pg_temp;
ALTER FUNCTION public.fn_audit_payload(public.log_auditoria)   SET search_path = public, pg_temp;
ALTER FUNCTION public.fn_conteudo_hash(public.relatorio_atendimento) SET search_path = public, pg_temp;
ALTER FUNCTION public.fn_dispositivo_antes()                   SET search_path = public, pg_temp;
ALTER FUNCTION public.fn_prazo_avaliacao_dias()                SET search_path = public, pg_temp;
ALTER FUNCTION public.fn_somente_insercao()                    SET search_path = public, pg_temp;
ALTER FUNCTION public.fn_token_antes()                         SET search_path = public, pg_temp;
ALTER FUNCTION public.fn_touch()                               SET search_path = public, pg_temp;

-- Conferência (deve devolver 0 linhas depois de aplicar):
--   SELECT p.oid::regprocedure FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
--    WHERE n.nspname = 'public' AND p.prokind = 'f'
--      AND NOT EXISTS (SELECT 1 FROM unnest(coalesce(p.proconfig, '{}')) c WHERE c LIKE 'search_path=%');
