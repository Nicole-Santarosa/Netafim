-- =====================================================================
-- Teste transacional da migração 021 (policy atendimento_insert presa ao distribuidor do usuário).
-- Execute o arquivo inteiro, como postgres, em ambiente ISOLADO onde a 021 já foi aplicada. O script termina
-- em ROLLBACK: nenhum dado de teste permanece.
--
-- NÃO rode no banco real: cada tentativa de INSERT em atendimento, mesmo a que falha na policy, avança
-- seq_protocolo (o gatilho BEFORE roda antes do WITH CHECK) e a seq_sync, e ROLLBACK não restaura
-- sequências. No banco real o primeiro protocolo real deixaria de ser o OS-0001. Também podem avançar as
-- identities de usuario, distribuidor, cliente, dominio_email_corporativo e log_auditoria.
--
-- O que prova (todas as tentativas são feitas como app_django, com o usuário em app.current_user_id):
--   T1  o WITH CHECK da policy menciona id_distribuidor_origem e app_cliente_visivel (021 aplicada).
--   T2  Distribuidor Admin com id_distribuidor_origem do próprio distribuidor, sem cliente: INSERT com
--       RETURNING funciona (o caso que dava 403 por falta de origem).
--   T3  o mesmo, com cliente do próprio distribuidor: funciona.
--   T4  id_distribuidor_origem nulo: FALHA com 42501.
--   T5  id_distribuidor_origem de OUTRO distribuidor: FALHA com 42501 (o vazamento pedido pelo Rafael).
--   T6  origem própria, mas id_cliente de cliente de OUTRO distribuidor: FALHA com 42501.
--   T7  Netafim Admin continua abrindo atendimento sem origem e sem cliente (a regra não o alcança).
--
-- Cria dentro da transação: 2 distribuidores, 1 cliente de cada, 2 domínios de e-mail de teste, 1 usuário
-- ativo Distribuidor Admin do primeiro e 1 Netafim Admin. Depende dos perfis 1 (admin/netafim) e 3
-- (admin/distribuidor), ambos com a permissão abrir_atendimento (como no banco real).
--
-- T2 a T7 usam SET LOCAL ROLE app_django. Se o ambiente não deixar o postgres assumir esse papel, esses
-- testes aparecem como NAO EXECUTADO (nunca como OK): conceda o papel ao postgres no ambiente isolado.
--
-- Resultado: uma tabela com OK, FALHA ou NAO EXECUTADO por teste e uma linha RESUMO no fim. O aceite exige
-- RESUMO = OK (7 testes, zero FALHA e zero NAO EXECUTADO).
--
-- Este script NÃO foi executado de ponta a ponta por quem o escreveu: foi conferido só contra as definições
-- lidas do banco real. Foi validado no banco real, numa transação desfeita, apenas o ALTER POLICY e a
-- expressão da policy (tabela-verdade com o usuário 13). A primeira execução real é a do ambiente isolado.
-- =====================================================================
begin;

create temp table _t_021 (
  n serial primary key,
  teste text not null,
  resultado text not null,
  detalhe text
) on commit drop;

create function pg_temp.reg(p_teste text, p_resultado text, p_detalhe text default null)
returns void language sql as $$
  insert into pg_temp._t_021 (teste, resultado, detalhe) values (p_teste, p_resultado, p_detalhe)
$$;

do $$
declare
  v_sufixo     text := substr(md5(random()::text), 1, 8);
  v_dist_a     integer;
  v_dist_b     integer;
  v_cli_a      integer;
  v_cli_b      integer;
  v_u_dist     integer;
  v_u_net      integer;
  v_id         uuid;
  v_exp        text;
  v_pode_papel boolean := true;
  v_r2 text; v_r3 text; v_r4 text; v_r5 text; v_r6 text; v_r7 text;
begin
  -- fixtures (como postgres; RLS não vale para o dono e não é forçado)
  insert into public.distribuidor (nome, regiao) values ('Teste 021 A', 'teste') returning id_distribuidor into v_dist_a;
  insert into public.distribuidor (nome, regiao) values ('Teste 021 B', 'teste') returning id_distribuidor into v_dist_b;
  insert into public.cliente (nome, id_distribuidor) values ('Cliente 021 A', v_dist_a) returning id_cliente into v_cli_a;
  insert into public.cliente (nome, id_distribuidor) values ('Cliente 021 B', v_dist_b) returning id_cliente into v_cli_b;

  insert into public.dominio_email_corporativo (dominio, tipo_organizacao, id_distribuidor, ativo)
  values ('teste021d' || v_sufixo || '.invalid', 'distribuidor', v_dist_a, true);
  insert into public.dominio_email_corporativo (dominio, tipo_organizacao, id_distribuidor, ativo)
  values ('teste021n' || v_sufixo || '.invalid', 'netafim', null, true);

  insert into public.usuario (nome, email, password, situacao)
  values ('Teste 021 Dist', 'u@teste021d' || v_sufixo || '.invalid', '!teste', 'pendente')
  returning id_usuario into v_u_dist;
  update public.usuario
     set situacao = 'ativo', tipo_organizacao = 'distribuidor', id_perfil = 3, id_distribuidor = v_dist_a
   where id_usuario = v_u_dist;

  insert into public.usuario (nome, email, password, situacao)
  values ('Teste 021 Net', 'u@teste021n' || v_sufixo || '.invalid', '!teste', 'pendente')
  returning id_usuario into v_u_net;
  update public.usuario
     set situacao = 'ativo', tipo_organizacao = 'netafim', id_perfil = 1
   where id_usuario = v_u_net;

  -- T1: a policy atual tem a regra nova
  select pg_get_expr(p.polwithcheck, p.polrelid) into v_exp
    from pg_policy p
   where p.polrelid = 'public.atendimento'::regclass and p.polname = 'atendimento_insert';
  perform pg_temp.reg('T1 WITH CHECK de atendimento_insert menciona id_distribuidor_origem e app_cliente_visivel',
    case when position('id_distribuidor_origem' in coalesce(v_exp, '')) > 0
          and position('app_cliente_visivel' in coalesce(v_exp, '')) > 0
         then 'OK' else 'FALHA' end,
    case when v_exp is null then 'policy não encontrada' else null end);

  -- T2 a T7: como app_django. Os resultados ficam em variáveis e só são gravados depois do RESET ROLE.
  begin
    set local role app_django;
  exception when others then
    v_pode_papel := false;
  end;

  if v_pode_papel then
    -- Distribuidor Admin do distribuidor A
    perform set_config('app.current_user_id', v_u_dist::text, true);

    begin  -- T2: origem própria, sem cliente, com RETURNING
      insert into public.atendimento (id_atendimento, origem, motivo, id_solicitante, contato_solicitante,
                                      id_distribuidor_origem, id_cliente)
      values (gen_random_uuid(), 'distribuidor', 'duvidas', v_u_dist, 'teste 021', v_dist_a, null)
      returning id_atendimento into v_id;
      v_r2 := case when v_id is not null then 'OK|inseriu e leu de volta' else 'FALHA|sem retorno' end;
    exception when others then v_r2 := 'FALHA|sqlstate ' || sqlstate;
    end;

    begin  -- T3: origem própria e cliente do próprio distribuidor
      insert into public.atendimento (id_atendimento, origem, motivo, id_solicitante, contato_solicitante,
                                      id_distribuidor_origem, id_cliente)
      values (gen_random_uuid(), 'distribuidor', 'duvidas', v_u_dist, 'teste 021', v_dist_a, v_cli_a)
      returning id_atendimento into v_id;
      v_r3 := case when v_id is not null then 'OK|inseriu e leu de volta' else 'FALHA|sem retorno' end;
    exception when others then v_r3 := 'FALHA|sqlstate ' || sqlstate;
    end;

    begin  -- T4: origem nula
      insert into public.atendimento (id_atendimento, origem, motivo, id_solicitante, contato_solicitante,
                                      id_distribuidor_origem, id_cliente)
      values (gen_random_uuid(), 'distribuidor', 'duvidas', v_u_dist, 'teste 021', null, null);
      v_r4 := 'FALHA|o INSERT sem origem funcionou';
    exception
      when insufficient_privilege then v_r4 := 'OK|42501';
      when others then v_r4 := 'FALHA|sqlstate ' || sqlstate;
    end;

    begin  -- T5: origem de outro distribuidor
      insert into public.atendimento (id_atendimento, origem, motivo, id_solicitante, contato_solicitante,
                                      id_distribuidor_origem, id_cliente)
      values (gen_random_uuid(), 'distribuidor', 'duvidas', v_u_dist, 'teste 021', v_dist_b, null);
      v_r5 := 'FALHA|o INSERT com origem de outro distribuidor funcionou';
    exception
      when insufficient_privilege then v_r5 := 'OK|42501';
      when others then v_r5 := 'FALHA|sqlstate ' || sqlstate;
    end;

    begin  -- T6: origem própria com cliente de outro distribuidor
      insert into public.atendimento (id_atendimento, origem, motivo, id_solicitante, contato_solicitante,
                                      id_distribuidor_origem, id_cliente)
      values (gen_random_uuid(), 'distribuidor', 'duvidas', v_u_dist, 'teste 021', v_dist_a, v_cli_b);
      v_r6 := 'FALHA|o INSERT com cliente de outro distribuidor funcionou';
    exception
      when insufficient_privilege then v_r6 := 'OK|42501';
      when others then v_r6 := 'FALHA|sqlstate ' || sqlstate;
    end;

    -- Netafim Admin: sem origem e sem cliente
    perform set_config('app.current_user_id', v_u_net::text, true);
    begin  -- T7
      insert into public.atendimento (id_atendimento, origem, motivo, id_solicitante, contato_solicitante,
                                      id_distribuidor_origem, id_cliente)
      values (gen_random_uuid(), 'distribuidor', 'duvidas', v_u_net, 'teste 021', null, null)
      returning id_atendimento into v_id;
      v_r7 := case when v_id is not null then 'OK|inseriu e leu de volta' else 'FALHA|sem retorno' end;
    exception when others then v_r7 := 'FALHA|sqlstate ' || sqlstate;
    end;

    reset role;

    perform pg_temp.reg('T2 Distribuidor Admin, origem própria, sem cliente: INSERT com RETURNING funciona',
      split_part(v_r2, '|', 1), split_part(v_r2, '|', 2));
    perform pg_temp.reg('T3 Distribuidor Admin, origem própria e cliente do próprio distribuidor: funciona',
      split_part(v_r3, '|', 1), split_part(v_r3, '|', 2));
    perform pg_temp.reg('T4 id_distribuidor_origem nulo falha com 42501',
      split_part(v_r4, '|', 1), split_part(v_r4, '|', 2));
    perform pg_temp.reg('T5 origem de outro distribuidor falha com 42501',
      split_part(v_r5, '|', 1), split_part(v_r5, '|', 2));
    perform pg_temp.reg('T6 cliente de outro distribuidor falha com 42501',
      split_part(v_r6, '|', 1), split_part(v_r6, '|', 2));
    perform pg_temp.reg('T7 Netafim Admin abre atendimento sem origem e sem cliente',
      split_part(v_r7, '|', 1), split_part(v_r7, '|', 2));
  else
    perform pg_temp.reg('T2 Distribuidor Admin, origem própria, sem cliente: INSERT com RETURNING funciona',
      'NAO EXECUTADO', 'o postgres não pôde assumir o papel app_django (SET ROLE)');
    perform pg_temp.reg('T3 Distribuidor Admin, origem própria e cliente do próprio distribuidor: funciona',
      'NAO EXECUTADO', 'o postgres não pôde assumir o papel app_django (SET ROLE)');
    perform pg_temp.reg('T4 id_distribuidor_origem nulo falha com 42501',
      'NAO EXECUTADO', 'o postgres não pôde assumir o papel app_django (SET ROLE)');
    perform pg_temp.reg('T5 origem de outro distribuidor falha com 42501',
      'NAO EXECUTADO', 'o postgres não pôde assumir o papel app_django (SET ROLE)');
    perform pg_temp.reg('T6 cliente de outro distribuidor falha com 42501',
      'NAO EXECUTADO', 'o postgres não pôde assumir o papel app_django (SET ROLE)');
    perform pg_temp.reg('T7 Netafim Admin abre atendimento sem origem e sem cliente',
      'NAO EXECUTADO', 'o postgres não pôde assumir o papel app_django (SET ROLE)');
  end if;

  perform set_config('app.current_user_id', '', true);
end
$$;

select n, teste, resultado, detalhe
  from _t_021
union all
select 999, 'RESUMO',
       case when count(*) filter (where resultado <> 'OK') = 0 and count(*) = 7 then 'OK' else 'FALHA' end,
       count(*) || ' teste(s); ' || count(*) filter (where resultado <> 'OK') || ' fora de OK (esperado: 7 testes)'
  from _t_021
order by n;

rollback;
