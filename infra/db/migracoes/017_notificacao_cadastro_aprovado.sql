-- =====================================================================
-- Migração 017: notifica o usuário quando o cadastro é aprovado (gatilho fn_usuario_depois)
--
-- Autor: Rodrigo Almeida (Pessoa 1)
--
-- Número: 017, fechado com o Rafael (017 = gatilho de aprovação, 018 = revogação do INSERT de
-- app_django em notificacao). Ordem de aplicação: 015, 017, 018. Ordem para desfazer: 018, 017, 015.
--
-- Depende da 015 (Douglas): public.app_criar_notificacao(integer, text, uuid). A 017 recusa rodar
-- sem ela (verificação no começo do script).
--
-- O que muda: só a função fn_usuario_depois(). O gatilho trg_usuario_depois (AFTER INSERT OR UPDATE,
-- por linha) continua o mesmo. O corpo é o que estava no banco real em 23/11/2026 (pg_get_functiondef;
-- MD5 do prosrc, sem CR: 7d3fe8e376c24bad6f96bf599f0bbc9f), mais UM bloco novo:
-- na transição pendente -> ativo, depois do fn_audit('cadastro_aprovado'), chama
--   app_criar_notificacao(new.id_usuario, 'cadastro_aprovado', null)
-- dentro de BEGIN ... EXCEPTION WHEN OTHERS. Nada do resto da função foi alterado.
--
-- Por que o bloco de exceção (pedido do Rafael): a app_criar_notificacao exige um ator (levanta 42501
-- "ator ausente ou inativo" sem ele), exige Netafim Admin com a permissão aprovar_cadastro e exige que
-- usuario.id_aprovado_por seja o ator. Uma aprovação feita sem ator (admin do Django, script, SQL Editor)
-- abortaria a transação inteira, e a aprovação não pode falhar por causa da notificação. Com o bloco,
-- a aprovação passa e a notificação simplesmente não é criada, com um aviso.
--   * O aviso (RAISE WARNING) leva só o SQLSTATE, sem e-mail, nome, id nem mensagem do erro (a mensagem
--     poderia carregar dado de linha). O evento de auditoria 'cadastro_aprovado' é gravado ANTES do bloco
--     e não é desfeito.
--   * O bloco abre uma subtransação por aprovação; o custo é desprezível (aprovação é rara).
--   * Todo erro cai no mesmo tratamento, inclusive 42501 (ator ausente ou sem permissão). Se o grupo
--     quiser tratar o 42501 à parte, é uma mudança nesta função.
--   * Quando a notificação é criada, a idempotência é da 015 (chave data_aprovacao em UTC): chamar de
--     novo não duplica.
--
-- Efeito para a API: na aprovação pela API o UPDATE já grava aprovado_por e data_aprovacao e a conexão
-- informa o ator (app.current_user_id), então as condições da 015 se cumprem e o usuário aprovado
-- recebe a notificação. Verificado no código: apps/contas/servicos/usuarios.py.
--
-- Conferido no banco real em 23/11/2026 (leitura): nenhuma função do schema public faz INSERT em
-- notificacao; fn_usuario_depois é SECURITY DEFINER (dono postgres, search_path fixo), então a chamada
-- roda como o dono; a app_criar_notificacao também é SECURITY DEFINER. Não existia ainda
-- app_criar_notificacao no banco (015 não aplicada).
--
-- Como desfazer (não apaga notificações já criadas): desfaça a 018 antes, depois restaure a função
-- original (o corpo está no bloco ROLLBACK, no fim deste arquivo). A 015 só pode ser desfeita depois.
--
-- Pode ser executada mais de uma vez (create or replace).
-- Quem aplica: a Pessoa 1, como postgres, SÓ depois do aceite conjunto de 015, 017 e 018 (o Douglas
-- roda 014, 015, 017 e 018 em ambiente isolado com o teste corrigido e o Rafael confere a saída).
--
-- Aplicado em: pendente. Teste: backend/db/testes/testar_migrations_017_018.sql.
-- =====================================================================
begin;

do $$
begin
  if to_regprocedure('public.app_criar_notificacao(integer,text,uuid)') is null then
    raise exception 'a migração 017 exige a 015 aplicada (public.app_criar_notificacao não existe)'
      using errcode = '55000';
  end if;
end
$$;

create or replace function fn_usuario_depois() returns trigger
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

    -- 017: notifica o usuário aprovado. A notificação nunca derruba a aprovação.
    if v_acao = 'cadastro_aprovado' then
      begin
        perform public.app_criar_notificacao(new.id_usuario, 'cadastro_aprovado', null::uuid);
      exception when others then
        raise warning 'fn_usuario_depois: notificação de aprovação não criada (sqlstate %)', sqlstate;
      end;
    end if;
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

commit;

-- =====================================================================
-- ROLLBACK (rode a 018 desfeita antes; este bloco só restaura o corpo anterior da função):
--
-- create or replace function fn_usuario_depois() returns trigger
-- language plpgsql security definer
-- set search_path = public, pg_temp
-- as $fn$
-- declare
--   v_acao text;
-- begin
--   if tg_op = 'INSERT' then
--     if new.situacao = 'pendente' then
--       perform fn_audit('cadastro_solicitado', 'sucesso', 'usuario', new.id_usuario::text,
--                        null, new.id_usuario);
--     end if;
--     return null;
--   end if;
--
--   -- situação mudou
--   if new.situacao is distinct from old.situacao then
--     v_acao := case
--       when old.situacao = 'pendente' and new.situacao = 'ativo'    then 'cadastro_aprovado'
--       when old.situacao = 'pendente' and new.situacao = 'recusado' then 'cadastro_recusado'
--       when new.situacao = 'bloqueado'                              then 'conta_bloqueada'
--       else 'situacao_alterada' end;
--     perform fn_audit(v_acao, 'sucesso', 'usuario', new.id_usuario::text,
--                      jsonb_build_object('de', old.situacao, 'para', new.situacao));
--   end if;
--   if new.id_perfil is distinct from old.id_perfil then
--     perform fn_audit('perfil_alterado', 'sucesso', 'usuario', new.id_usuario::text,
--                      jsonb_build_object('de', old.id_perfil, 'para', new.id_perfil));
--   end if;
--   if new.password is distinct from old.password then
--     perform fn_audit('senha_alterada', 'sucesso', 'usuario', new.id_usuario::text, null, new.id_usuario);
--   end if;
--   if new.mfa_ativo is distinct from old.mfa_ativo then
--     perform fn_audit('mfa_alterado', 'sucesso', 'usuario', new.id_usuario::text,
--                      jsonb_build_object('ativo', new.mfa_ativo), new.id_usuario);
--   end if;
--
--   -- conta inativa, bloqueada, recusada ou excluída: revoga todos os tokens
--   if (new.situacao in ('inativo','bloqueado','recusado') or new.deletado_em is not null)
--      and (new.situacao is distinct from old.situacao
--           or new.deletado_em is distinct from old.deletado_em) then
--     update token_acesso set revogado_em = now()
--      where id_usuario = new.id_usuario and revogado_em is null;
--   end if;
--   return null;
-- end
-- $fn$;
-- =====================================================================
