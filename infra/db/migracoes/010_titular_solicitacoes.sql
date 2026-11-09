-- =====================================================================================
-- MIGRAÇÃO 010 (NÃO aplicada no banco do grupo): direitos do titular (LGPD, art. 18).
--
-- Problema: as policies de solicitacao_titular só deixam o Netafim Admin ler e gravar. A pessoa
-- comum (o próprio titular) não conseguia registrar nem acompanhar um pedido seu de acesso,
-- correção, anonimização, eliminação, portabilidade ou revogação de consentimento.
--
-- O que esta migração faz (sem afrouxar nenhuma policy):
--   * fn_titular_registrar_solicitacao(tipo, observacao): registra um pedido SEMPRE em nome do
--     usuário logado (app_usuario_id()), copiando nome e e-mail do cadastro. Grava na auditoria
--     ('solicitacao_titular'). Não aceita id_usuario de fora, então ninguém registra pedido por outro.
--   * fn_titular_minhas_solicitacoes(): devolve só os pedidos do usuário logado, sem o responsável
--     interno.
--   * ux_solic_aberta: no máximo UM pedido em aberto por titular e tipo (evita fila duplicada).
--
-- Quem atende os pedidos continua sendo o Netafim Admin, pelas policies que já existem.
-- Depende apenas do esquema base (001). Idempotente. Aplicar com o dono do esquema, depois do
-- "de acordo" do grupo.
--
-- Como desfazer: DROP FUNCTION fn_titular_registrar_solicitacao(text, text);
--                DROP FUNCTION fn_titular_minhas_solicitacoes();
--                DROP INDEX ux_solic_aberta;
-- =====================================================================================

CREATE UNIQUE INDEX IF NOT EXISTS ux_solic_aberta
  ON public.solicitacao_titular (id_usuario, tipo)
  WHERE status IN ('recebida', 'em_analise') AND id_usuario IS NOT NULL;

CREATE OR REPLACE FUNCTION public.fn_titular_registrar_solicitacao(
  p_tipo text, p_observacao text DEFAULT NULL)
 RETURNS integer
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  v_uid   integer := app_usuario_id();
  v_nome  text;
  v_email text;
  v_id    integer;
begin
  if v_uid is null then
    raise exception 'nenhum usuário identificado' using errcode = '42501';
  end if;
  select u.nome, u.email into v_nome, v_email
    from usuario u where u.id_usuario = v_uid and u.deletado_em is null;
  if not found then
    raise exception 'usuário não encontrado' using errcode = '23514';
  end if;

  insert into solicitacao_titular (id_usuario, nome_titular, email_contato, tipo, observacao)
  values (v_uid, left(v_nome, 150), left(v_email, 150), p_tipo, nullif(btrim(p_observacao), ''))
  returning id_solicitacao into v_id;

  -- sem e-mail nem texto livre nos detalhes: a auditoria recusa dado pessoal
  perform fn_audit('solicitacao_titular', 'sucesso', 'solicitacao_titular', v_id::text,
                   jsonb_build_object('tipo', p_tipo));
  return v_id;
end
$function$;

CREATE OR REPLACE FUNCTION public.fn_titular_minhas_solicitacoes()
 RETURNS TABLE (
   id_solicitacao         integer,
   tipo                   text,
   status                 text,
   observacao             text,
   data_solicitacao       timestamptz,
   data_prazo             timestamptz,
   data_conclusao         timestamptz,
   justificativa_negativa text)
 LANGUAGE sql
 STABLE
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
  select s.id_solicitacao, s.tipo::text, s.status::text, s.observacao,
         s.data_solicitacao, s.data_prazo, s.data_conclusao, s.justificativa_negativa
    from solicitacao_titular s
   where s.id_usuario = app_usuario_id()
   order by s.id_solicitacao desc
$function$;

REVOKE ALL ON FUNCTION public.fn_titular_registrar_solicitacao(text, text) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.fn_titular_minhas_solicitacoes() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.fn_titular_registrar_solicitacao(text, text) TO app_django;
GRANT EXECUTE ON FUNCTION public.fn_titular_minhas_solicitacoes() TO app_django;

COMMENT ON FUNCTION public.fn_titular_registrar_solicitacao(text, text) IS
  'Registra pedido LGPD em nome do usuário logado (migração 010). Não aceita id_usuario externo.';
