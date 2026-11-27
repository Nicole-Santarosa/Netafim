-- =====================================================================
-- MIGRAÇÃO 023: o BANCO passa a autorizar a anonimização de um titular
--
-- Autor: Rodrigo Aparecido de Almeida
-- Aplicado em: pendente
-- Depende de: 011 (o corpo substituído é o dela). Quem aplica: o responsável
-- pelo banco, como postgres, depois do "de acordo" do grupo.
--
-- Motivo (revisão do PR #10): fn_anonimizar_usuario é SECURITY DEFINER, liga
-- app.acao_sistema e tem EXECUTE para app_django, mas não conferia quem a
-- chamava: só a API impedia que outra pessoa anonimizasse uma conta.
--
-- O que muda: um bloco no início da função. Só o Netafim Admin identificado em
-- app.current_user_id executa, e não sobre a própria conta; senão 42501. O
-- resto do corpo é o da 011. Dono e privilégios não mudam (CREATE OR REPLACE).
--
-- Limite: o ator continua sendo informado pela própria conexão, como em todo o
-- RLS deste banco. Quem rodar a função pelo SQL Editor precisa antes, na mesma
-- transação: select set_config('app.current_user_id', '<id do admin>', true);
--
-- Guarda: recusa se o corpo em uso não for o da 011 nem o desta migração.
-- MD5 (sem CR) antes: 0eb73dc323a9a808f3391f71c8eb7ca6
-- MD5 (sem CR) depois: b147cb6cd2708635c0d2d6acac18573d
--
-- Pode ser executada mais de uma vez. Como desfazer: bloco comentado no fim.
-- =====================================================================
begin;
set local lock_timeout = '5s';

do $guarda$
declare
  v_md5 text;
begin
  select md5(replace(prosrc, chr(13), '')) into v_md5
    from pg_proc where oid = 'public.fn_anonimizar_usuario(integer)'::regprocedure;
  if v_md5 not in ('0eb73dc323a9a808f3391f71c8eb7ca6', 'b147cb6cd2708635c0d2d6acac18573d') then
    raise exception 'fn_anonimizar_usuario no banco é diferente da esperada (md5 %). Nada foi alterado.', v_md5;
  end if;
end
$guarda$;

create or replace function public.fn_anonimizar_usuario(p_id_usuario integer) returns void
language plpgsql security definer
set search_path = public, pg_temp
as $$
declare
  v_prev text := coalesce(current_setting('app.acao_sistema', true), '');
begin
  -- Quem pode: só o Netafim Admin, e nunca sobre a própria conta (separação de
  -- funções, a mesma regra da API). A checagem vem ANTES de app.acao_sistema.
  if not app_eh_admin_netafim() or p_id_usuario is not distinct from app_usuario_id() then
    raise exception 'sem permissão para anonimizar esta conta' using errcode = '42501';
  end if;

  -- roda como acao do sistema: as travas de linha nao se aplicam daqui em diante
  perform set_config('app.acao_sistema', 'on', true);
  if not exists (select 1 from usuario where id_usuario = p_id_usuario) then
    raise exception 'usuário não encontrado' using errcode = '23514';
  end if;

  -- Limpa o contato que o titular informou nos atendimentos que ele abriu.
  -- A coluna é not null, então não dá para gravar nulo: o valor fixo
  -- 'anonimizado' (11 caracteres, cabe no varchar(20)) ocupa o lugar do
  -- telefone. Só mexe no contato; nome do produtor, cidade e textos livres
  -- ficam como estão. O UPDATE passa pelo gatilho do atendimento, então a
  -- versao soma 1 e o sync_seq anda (o aparelho baixa o registro de novo),
  -- sem evento no histórico. O filtro "<> 'anonimizado'" faz a segunda
  -- chamada não encontrar nada para alterar, e assim não mexe em versao nem
  -- em sync_seq à toa.
  update atendimento
     set contato_solicitante = 'anonimizado'
   where id_solicitante = p_id_usuario
     and contato_solicitante <> 'anonimizado';

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

commit;

-- =====================================================================
-- COMO DESFAZER (volta ao corpo da 011). Copie, tire os "-- " e execute.
-- =====================================================================
-- begin;
-- do $guarda$
-- declare
--   v_md5 text;
-- begin
--   select md5(replace(prosrc, chr(13), '')) into v_md5
--     from pg_proc where oid = 'public.fn_anonimizar_usuario(integer)'::regprocedure;
--   if v_md5 not in ('0eb73dc323a9a808f3391f71c8eb7ca6', 'b147cb6cd2708635c0d2d6acac18573d') then
--     raise exception 'fn_anonimizar_usuario no banco é diferente da esperada (md5 %). Nada foi alterado.', v_md5;
--   end if;
-- end
-- $guarda$;
--
-- create or replace function public.fn_anonimizar_usuario(p_id_usuario integer) returns void
-- language plpgsql security definer
-- set search_path = public, pg_temp
-- as $$
-- declare
--   v_prev text := coalesce(current_setting('app.acao_sistema', true), '');
-- begin
--   -- roda como acao do sistema: anonimizar nao depende do perfil de quem chama
--   perform set_config('app.acao_sistema', 'on', true);
--   if not exists (select 1 from usuario where id_usuario = p_id_usuario) then
--     raise exception 'usuário não encontrado' using errcode = '23514';
--   end if;
--
--   -- Limpa o contato que o titular informou nos atendimentos que ele abriu.
--   -- A coluna é not null, então não dá para gravar nulo: o valor fixo
--   -- 'anonimizado' (11 caracteres, cabe no varchar(20)) ocupa o lugar do
--   -- telefone. Só mexe no contato; nome do produtor, cidade e textos livres
--   -- ficam como estão. O UPDATE passa pelo gatilho do atendimento, então a
--   -- versao soma 1 e o sync_seq anda (o aparelho baixa o registro de novo),
--   -- sem evento no histórico. O filtro "<> 'anonimizado'" faz a segunda
--   -- chamada não encontrar nada para alterar, e assim não mexe em versao nem
--   -- em sync_seq à toa.
--   update atendimento
--      set contato_solicitante = 'anonimizado'
--    where id_solicitante = p_id_usuario
--      and contato_solicitante <> 'anonimizado';
--
--   update dispositivo set revogado_em = coalesce(revogado_em, now()), modelo = null
--    where id_usuario = p_id_usuario;           -- o gatilho revoga os tokens
--
--   update usuario
--      set nome = 'Usuário anonimizado',
--          email = 'anonimizado-' || p_id_usuario || '@anonimizado.invalid',
--          telefone = null,
--          password = '!' || replace(gen_random_uuid()::text, '-', ''),
--          situacao = 'inativo',
--          mfa_ativo = false,
--          data_exclusao_solicitada = coalesce(data_exclusao_solicitada, now()),
--          deletado_em = coalesce(deletado_em, now())
--    where id_usuario = p_id_usuario;
--
--   delete from notificacao where id_usuario = p_id_usuario;
--
--   perform fn_audit('dados_anonimizados', 'sucesso', 'usuario', p_id_usuario::text);
--   perform set_config('app.acao_sistema', v_prev, true);
-- end
-- $$;
-- commit;
