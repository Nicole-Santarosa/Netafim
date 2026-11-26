-- =====================================================================
-- Teste transacional das migrações 017 e 018. Execute o arquivo inteiro, como postgres, em ambiente
-- ISOLADO onde já foram aplicadas 014, 015, 017 e 018 (nesta ordem). O script termina em ROLLBACK:
-- nenhum dado de teste permanece.
--
-- ATENÇÃO: ROLLBACK não restaura sequências. Podem avançar a identity de usuario, de notificacao e de
-- log_auditoria, e a seq_sync. Não há setval.
--
-- O que prova:
--   T1  app_django NÃO tem mais INSERT em notificacao e mantém SELECT e UPDATE (018).
--   T2  um INSERT direto em notificacao, como app_django, FALHA com 42501 (018).
--   T3  app_criar_notificacao continua funcionando: aprovar um cadastro com ator (Netafim Admin com
--       aprovar_cadastro) cria UMA notificação 'cadastro_aprovado' para o usuário aprovado (017 + 015).
--   T4  repetir a transição pendente -> ativo com a mesma data_aprovacao não duplica a notificação
--       (chave de idempotência da 015).
--   T5  aprovação SEM ator não aborta a transação (bloco BEGIN ... EXCEPTION da 017): o usuário fica
--       'ativo' e nenhuma notificação é criada.
--   T6  chamar app_criar_notificacao('cadastro_aprovado') direto, como app_django e fora de gatilho,
--       FALHA com 42501 (015).
--   T7  app_django tem EXECUTE em app_criar_notificacao e PUBLIC não tem (015).
--
-- Pré-requisitos (o script confere e para se faltar): 015, 017 e 018 aplicadas; pelo menos um Netafim
-- Admin ativo com a permissão aprovar_cadastro; pelo menos um cliente e um perfil de organização
-- 'cliente'. O usuário de teste é de organização 'cliente' para não depender da lista de domínios
-- corporativos (fn_usuario_antes só exige domínio para netafim e distribuidor).
--
-- T2 e T6 usam SET LOCAL ROLE app_django. Se o ambiente não deixar o postgres assumir esse papel, esses
-- testes aparecem como NAO EXECUTADO (nunca como OK): conceda o papel ao postgres no ambiente isolado e
-- rode de novo.
--
-- Resultado: uma tabela com OK, FALHA ou NAO EXECUTADO por teste e uma linha RESUMO no fim. O aceite
-- exige RESUMO = OK (zero FALHA e zero NAO EXECUTADO).
--
-- Este script NÃO foi executado por quem o escreveu (não havia PostgreSQL isolado com 015, 017 e 018):
-- foi conferido só contra as definições lidas do banco real e dos arquivos. A primeira execução real
-- é a do ambiente isolado.
-- =====================================================================
begin;

create temp table _t_017_018 (
  n serial primary key,
  teste text not null,
  resultado text not null,
  detalhe text
) on commit drop;

create function pg_temp.reg(p_teste text, p_resultado text, p_detalhe text default null)
returns void language sql as $$
  insert into pg_temp._t_017_018 (teste, resultado, detalhe) values (p_teste, p_resultado, p_detalhe)
$$;

do $$
declare
  v_admin      integer;
  v_perfil     integer;
  v_cliente    integer;
  v_novo1      integer;
  v_novo2      integer;
  v_n          integer;
  v_situacao   text;
  v_pode_papel boolean := true;
  v_r2         text;
  v_r6         text;
  r            record;
begin
  -- pré-requisitos
  if to_regprocedure('public.app_criar_notificacao(integer,text,uuid)') is null then
    raise exception 'pré-requisito: 015 não aplicada (app_criar_notificacao não existe)';
  end if;
  if position('app_criar_notificacao' in
              (select prosrc from pg_proc where oid = 'public.fn_usuario_depois()'::regprocedure)) = 0 then
    raise exception 'pré-requisito: 017 não aplicada (fn_usuario_depois não chama app_criar_notificacao)';
  end if;
  if has_table_privilege('app_django', 'public.notificacao', 'INSERT') then
    raise exception 'pré-requisito: 018 não aplicada (app_django ainda tem INSERT em notificacao)';
  end if;

  -- um Netafim Admin ativo com aprovar_cadastro (o ator é lido de app.current_user_id)
  for r in select u.id_usuario from public.usuario u
            where u.situacao = 'ativo' and u.tipo_organizacao = 'netafim' and u.deletado_em is null
            order by u.id_usuario loop
    perform set_config('app.current_user_id', r.id_usuario::text, true);
    if public.app_eh_admin_netafim() is true and public.app_tem_permissao('aprovar_cadastro') is true then
      v_admin := r.id_usuario;
      exit;
    end if;
  end loop;
  perform set_config('app.current_user_id', '', true);
  if v_admin is null then
    raise exception 'pré-requisito: nenhum Netafim Admin ativo com a permissão aprovar_cadastro';
  end if;

  select p.id_perfil into v_perfil from public.perfil p where p.tipo_organizacao = 'cliente' order by p.id_perfil limit 1;
  select c.id_cliente into v_cliente from public.cliente c order by c.id_cliente limit 1;
  if v_perfil is null or v_cliente is null then
    raise exception 'pré-requisito: é preciso ao menos um perfil de organização cliente e um cliente';
  end if;

  -- T1: privilégios na tabela
  perform pg_temp.reg('T1 app_django sem INSERT em notificacao, com SELECT e UPDATE',
    case when not has_table_privilege('app_django', 'public.notificacao', 'INSERT')
          and has_table_privilege('app_django', 'public.notificacao', 'SELECT')
          and has_table_privilege('app_django', 'public.notificacao', 'UPDATE')
         then 'OK' else 'FALHA' end);

  -- T7: EXECUTE da função
  perform pg_temp.reg('T7 app_django com EXECUTE em app_criar_notificacao e PUBLIC sem',
    case when has_function_privilege('app_django', 'public.app_criar_notificacao(integer,text,uuid)', 'EXECUTE')
          and exists (select 1 from pg_proc p
                       where p.oid = 'public.app_criar_notificacao(integer,text,uuid)'::regprocedure
                         and p.proacl is not null)
          and not exists (select 1
                            from pg_proc p, lateral aclexplode(p.proacl) a
                           where p.oid = 'public.app_criar_notificacao(integer,text,uuid)'::regprocedure
                             and a.grantee = 0 and a.privilege_type = 'EXECUTE')
         then 'OK' else 'FALHA' end);

  -- usuário de teste 1 (pendente) e aprovação COM ator
  insert into public.usuario (nome, email, password, situacao)
  values ('Teste 017 A', 'teste017a_' || md5(random()::text) || '@teste.invalid', '!teste', 'pendente')
  returning id_usuario into v_novo1;

  perform set_config('app.current_user_id', v_admin::text, true);
  update public.usuario
     set situacao = 'ativo', tipo_organizacao = 'cliente', id_perfil = v_perfil, id_cliente = v_cliente,
         id_aprovado_por = v_admin, data_aprovacao = now()
   where id_usuario = v_novo1;

  select count(*) into v_n from public.notificacao where id_usuario = v_novo1 and tipo = 'cadastro_aprovado';
  perform pg_temp.reg('T3 aprovação com ator cria uma notificação cadastro_aprovado',
    case when v_n = 1 then 'OK' else 'FALHA' end, 'notificações encontradas: ' || v_n);

  -- T4: repetir a transição com a mesma data_aprovacao não duplica
  update public.usuario set situacao = 'pendente' where id_usuario = v_novo1;
  update public.usuario set situacao = 'ativo'    where id_usuario = v_novo1;
  select count(*) into v_n from public.notificacao where id_usuario = v_novo1 and tipo = 'cadastro_aprovado';
  perform pg_temp.reg('T4 repetir a aprovação com a mesma data não duplica a notificação',
    case when v_n = 1 then 'OK' else 'FALHA' end, 'notificações encontradas: ' || v_n);

  -- T5: aprovação SEM ator não pode abortar (bloco de exceção da 017)
  insert into public.usuario (nome, email, password, situacao)
  values ('Teste 017 B', 'teste017b_' || md5(random()::text) || '@teste.invalid', '!teste', 'pendente')
  returning id_usuario into v_novo2;

  perform set_config('app.current_user_id', '', true);
  begin
    update public.usuario
       set situacao = 'ativo', tipo_organizacao = 'cliente', id_perfil = v_perfil, id_cliente = v_cliente,
           id_aprovado_por = v_admin, data_aprovacao = now()
     where id_usuario = v_novo2;
    select u.situacao into v_situacao from public.usuario u where u.id_usuario = v_novo2;
    select count(*) into v_n from public.notificacao where id_usuario = v_novo2 and tipo = 'cadastro_aprovado';
    perform pg_temp.reg('T5 aprovação sem ator não aborta e não cria notificação',
      case when v_situacao = 'ativo' and v_n = 0 then 'OK' else 'FALHA' end,
      'situação: ' || coalesce(v_situacao, '?') || '; notificações: ' || v_n);
  exception when others then
    perform pg_temp.reg('T5 aprovação sem ator não aborta e não cria notificação', 'FALHA',
      'a aprovação sem ator levantou sqlstate ' || sqlstate);
  end;

  -- T2 e T6: precisam assumir o papel app_django. Os resultados ficam em variáveis e só são gravados
  -- depois do RESET ROLE (o app_django não escreve na tabela temporária do teste).
  begin
    set local role app_django;
  exception when others then
    v_pode_papel := false;
  end;

  if v_pode_papel then
    begin
      insert into public.notificacao (id_usuario, tipo, mensagem)
      values (v_novo1, 'cadastro_aprovado', 'teste 017/018');
      v_r2 := 'FALHA|o INSERT direto funcionou';
    exception
      when insufficient_privilege then v_r2 := 'OK|42501';
      when others then v_r2 := 'FALHA|sqlstate ' || sqlstate;
    end;

    begin
      perform set_config('app.current_user_id', v_admin::text, true);
      perform public.app_criar_notificacao(v_novo1, 'cadastro_aprovado', null::uuid);
      v_r6 := 'FALHA|a chamada direta fora de gatilho funcionou';
    exception
      when insufficient_privilege then v_r6 := 'OK|42501';
      when others then v_r6 := 'FALHA|sqlstate ' || sqlstate;
    end;

    reset role;

    perform pg_temp.reg('T2 INSERT direto em notificacao como app_django falha com 42501',
      split_part(v_r2, '|', 1), split_part(v_r2, '|', 2));
    perform pg_temp.reg('T6 app_criar_notificacao(cadastro_aprovado) direto, fora de gatilho, falha com 42501',
      split_part(v_r6, '|', 1), split_part(v_r6, '|', 2));
  else
    perform pg_temp.reg('T2 INSERT direto em notificacao como app_django falha com 42501', 'NAO EXECUTADO',
      'o postgres não pôde assumir o papel app_django (SET ROLE)');
    perform pg_temp.reg('T6 app_criar_notificacao(cadastro_aprovado) direto, fora de gatilho, falha com 42501',
      'NAO EXECUTADO', 'o postgres não pôde assumir o papel app_django (SET ROLE)');
  end if;

  perform set_config('app.current_user_id', '', true);
end
$$;

select n, teste, resultado, detalhe
  from _t_017_018
union all
select 999, 'RESUMO',
       case when count(*) filter (where resultado <> 'OK') = 0 and count(*) = 7 then 'OK' else 'FALHA' end,
       count(*) || ' teste(s); ' || count(*) filter (where resultado <> 'OK') || ' fora de OK (esperado: 7 testes)'
  from _t_017_018
order by n;

rollback;
