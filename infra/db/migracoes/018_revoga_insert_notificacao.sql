-- =====================================================================
-- Migração 018: revoga o INSERT de app_django em notificacao
--
-- Autor: Rodrigo Almeida (Pessoa 1)
--
-- Número: 018, fechado com o Rafael (017 = gatilho de aprovação, 018 = esta revogação).
-- Ordem de aplicação: 015, 017, 018. Ordem para desfazer: 018, 017, 015.
--
-- Motivo: depois da 015, notificação só nasce por public.app_criar_notificacao(integer, text, uuid)
-- (SECURITY DEFINER, com as regras de ator, perfil e chave de idempotência). Enquanto app_django tiver
-- INSERT direto na tabela, qualquer código da API poderia gravar uma notificação sem passar por essas
-- regras. A revogação fecha esse caminho.
--
-- O que muda: só o privilégio. REVOKE INSERT ON public.notificacao FROM app_django. SELECT e UPDATE
-- continuam (a API lista e marca como lida). A política notificacao_insert (RLS, with check
-- app_usuario_ativo()) não é mexida: sem o privilégio ela não tem efeito para app_django, e deixá-la
-- mantém o desfazer reduzido a um GRANT.
--
-- Antes de aplicar (pedido do Rafael), o próprio script confere duas coisas e se recusa a rodar se falhar:
--   1. a 015 e a 017 estão aplicadas (app_criar_notificacao existe e fn_usuario_depois a chama);
--   2. nenhuma função do schema public que NÃO seja SECURITY DEFINER faz INSERT em notificacao (uma
--      assim rodaria como app_django e quebraria com a revogação). Conferido no banco real em
--      23/11/2026: nenhuma função do schema public faz INSERT em notificacao. A 014 do Douglas
--      (arquivo de 18/11/2026) não cita notificacao. A verificação do script cobre o ambiente onde ele
--      for rodado, inclusive o isolado do teste.
-- Também não achei uso de notificacao em nenhum código Python das branches remotas (conferido em 22/11/2026);
-- o que ainda não foi publicado pelo Douglas e pelo Rafael eu não vi.
--
-- Como desfazer (antes da 017 e da 015): GRANT INSERT ON public.notificacao TO app_django;
--
-- Pode ser executada mais de uma vez (REVOKE repetido não dá erro).
-- Quem aplica: a Pessoa 1, como postgres, SÓ depois do aceite conjunto de 015, 017 e 018.
--
-- Aplicado em: pendente. Teste: backend/db/testes/testar_migrations_017_018.sql (prova que o INSERT
-- direto de app_django falha e que app_criar_notificacao continua funcionando).
-- =====================================================================
begin;

do $$
declare
  v_lista text;
begin
  if to_regprocedure('public.app_criar_notificacao(integer,text,uuid)') is null then
    raise exception 'a migração 018 exige a 015 aplicada (public.app_criar_notificacao não existe)'
      using errcode = '55000';
  end if;

  if position('app_criar_notificacao' in
              (select prosrc from pg_proc where oid = 'public.fn_usuario_depois()'::regprocedure)) = 0 then
    raise exception 'a migração 018 exige a 017 aplicada (fn_usuario_depois não chama app_criar_notificacao)'
      using errcode = '55000';
  end if;

  select string_agg(p.oid::regprocedure::text, ', ')
    into v_lista
    from pg_proc p
   where p.pronamespace = 'public'::regnamespace
     and p.prokind in ('f', 'p')
     and not p.prosecdef
     and pg_get_functiondef(p.oid) ~* 'insert\s+into\s+(public\.)?notificacao\M';
  if v_lista is not null then
    raise exception 'funções que fazem INSERT em notificacao sem SECURITY DEFINER quebrariam com a revogação: %', v_lista
      using errcode = '55000';
  end if;
end
$$;

revoke insert on public.notificacao from app_django;

commit;
