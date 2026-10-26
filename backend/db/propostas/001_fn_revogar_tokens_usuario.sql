-- =====================================================================================
-- PROPOSTA (NÃO aplicada no banco do grupo): encerrar todas as sessões de um usuário.
--
-- Motivo: ao trocar ou redefinir a senha, as sessões antigas (refresh tokens) devem acabar. O papel da API
-- (app_django) não tem privilégio em token_acesso, e o banco só oferece fn_revogar_token(hash), que exige
-- o token. Sem esta função a API registra um aviso e as sessões antigas valem até expirar (90 dias).
--
-- Segurança: SECURITY DEFINER com search_path fixo; só o próprio titular (ator = p_id_usuario) ou o
-- Netafim Admin revogam; sem EXECUTE para PUBLIC. Aplicar com o dono do esquema, depois de alinhar com o grupo.
-- A API já chama esta função e tolera a ausência dela (ver apps/contas/servicos/tokens.py).
-- =====================================================================================
CREATE OR REPLACE FUNCTION public.fn_revogar_tokens_usuario(p_id_usuario integer, p_tipo text DEFAULT NULL::text)
 RETURNS integer
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  v_n integer;
begin
  if app_usuario_id() is distinct from p_id_usuario and not app_eh_admin_netafim() then
    raise exception 'sem permissão para encerrar as sessões de outro usuário' using errcode = '42501';
  end if;
  if p_tipo is not null and p_tipo not in ('refresh', 'convite', 'reset_senha') then
    raise exception 'tipo de token inválido: %', p_tipo using errcode = '23514';
  end if;

  update token_acesso set revogado_em = now()
   where id_usuario = p_id_usuario and revogado_em is null
     and (p_tipo is null or tipo = p_tipo);
  get diagnostics v_n = row_count;
  return v_n;
end
$function$;

REVOKE ALL ON FUNCTION public.fn_revogar_tokens_usuario(integer, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.fn_revogar_tokens_usuario(integer, text) TO app_django;
