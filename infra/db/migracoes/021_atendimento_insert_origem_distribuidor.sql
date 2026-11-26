-- =====================================================================
-- Migração 021: o INSERT de atendimento por usuário de distribuidor fica preso ao próprio distribuidor
--
-- Autor: Rodrigo Almeida (Pessoa 1)
--
-- Número: 021 (017 e 018 são minhas, 019 e 020 são do Douglas). Pedido do Rafael em 24/11/2026; o Rafael
-- deixou a critério do Rodrigo entrar na 018 ou numa migration nova, e ficou numa nova porque a 018 já está
-- fechada com o grupo como "REVOKE INSERT de app_django em notificacao" e vai para o teste isolado.
-- NÃO depende da 015, 017 nem 018 (outra tabela, outra policy): pode ser aplicada em qualquer ordem em
-- relação a elas.
--
-- Problema (conferido no banco real em 24/11/2026, leitura):
--   * A policy atendimento_insert só exige usuário ativo, permissão abrir_atendimento,
--     id_solicitante = app_usuario_id() e, para organização cliente, id_cliente = app_id_cliente().
--     Para organização distribuidor NADA restringe id_distribuidor_origem nem id_cliente.
--   * O gatilho fn_atendimento_antes bloqueia id_distribuidor (destino) no INSERT para quem não é Netafim
--     Admin, mas não valida id_distribuidor_origem nem a quem pertence o id_cliente (só há FK).
--   * Um usuário do distribuidor A podia gravar id_distribuidor_origem do distribuidor B, ou o id_cliente
--     de um cliente do B; o Distribuidor Admin de B passa a ver a linha (app_atendimento_visivel_linha usa
--     id_distribuidor, id_distribuidor_origem e cliente.id_distribuidor). É vazamento entre empresas.
--   * Sem origem e sem cliente, o Distribuidor Admin não consegue ler a própria linha no RETURNING (a
--     visibilidade dele não inclui id_solicitante), então o INSERT recebe 403.
--
-- O que muda: só o WITH CHECK da policy atendimento_insert (ALTER POLICY; papéis, comando e permissividade
-- continuam). Acrescenta, para tipo_org = 'distribuidor':
--     id_distribuidor_origem = app_id_distribuidor()
--     e (id_cliente is null ou app_cliente_visivel(id_cliente))
-- id_distribuidor_origem nulo reprova (comparação com null não é verdadeira). app_cliente_visivel já
-- existe (SECURITY DEFINER, usada na policy de SELECT de cliente): para distribuidor devolve verdadeiro só
-- se o cliente tem id_distribuidor igual ao do usuário. Netafim e cliente não mudam.
--
-- PONTO AINDA SEM "DE ACORDO" DO RAFAEL: a metade da origem foi pedida por ele; a metade do id_cliente
-- (app_cliente_visivel) foi proposta pelo Rodrigo em 24/11 e depende da confirmação dele. Se ele não
-- concordar, tire a linha do id_cliente da expressão (neste arquivo e no teste, T6).
--
-- Efeitos:
--   * Quem é de distribuidor (admin ou usuário) passa a precisar mandar id_distribuidor_origem = o próprio
--     distribuidor; a API (e o app, no envio offline) precisa enviá-lo, ou o back-end preenchê-lo.
--   * Não afeta linhas existentes (só vale para INSERT) nem atualizações. No banco real: 0 atendimentos.
--   * Cliente da lista com id_distribuidor nulo não é visível para distribuidor: o usuário do distribuidor
--     não abre atendimento para ele. Cliente excluído logicamente (deletado_em) não é filtrado por
--     app_cliente_visivel; isso já era assim na policy de SELECT de cliente.
--
-- Guarda: o script para se a policy não existir, se app_cliente_visivel(integer) não existir, ou se o
-- WITH CHECK atual não for reconhecido (nem o original, nem este já aplicado). Referência de MD5 do texto
-- devolvido por pg_get_expr no PostgreSQL 17 do banco real: original 5a175c24356cfff656e6121ebf3f7f28,
-- este 3b2a81ca64e793a2cd81e0672e81efb3 (a guarda compara o conteúdo, não o MD5, porque o texto muda
-- entre versões do PostgreSQL).
--
-- Conferido em 24/11/2026 numa transação desfeita no banco real (nada ficou gravado): o ALTER POLICY é
-- aceito e, com o usuário 13 (Distribuidor Admin do distribuidor 2), a expressão dá verdadeiro para
-- origem 2 sem cliente e para origem 2 com o cliente 2; nulo para origem nula; falso para origem 1 e para
-- origem 2 com o cliente 1 (de outro distribuidor).
--
-- Como desfazer: bloco ROLLBACK no fim (volta ao WITH CHECK original).
--
-- Pode ser executada mais de uma vez (ALTER POLICY repetido).
-- Quem aplica: a Pessoa 1, como postgres, depois do "de acordo" do Rafael e do teste isolado.
--
-- Aplicado em: pendente. Teste: backend/db/testes/testar_migration_021.sql.
-- =====================================================================
begin;

do $$
declare
  v_exp text;
begin
  select pg_get_expr(p.polwithcheck, p.polrelid) into v_exp
    from pg_policy p
   where p.polrelid = 'public.atendimento'::regclass and p.polname = 'atendimento_insert';
  if not found then
    raise exception 'a migração 021 exige a policy atendimento_insert em public.atendimento'
      using errcode = '55000';
  end if;
  if to_regprocedure('public.app_cliente_visivel(integer)') is null then
    raise exception 'a migração 021 exige public.app_cliente_visivel(integer)' using errcode = '55000';
  end if;
  if v_exp is null
     or position('abrir_atendimento' in v_exp) = 0
     or position('app_id_cliente' in v_exp) = 0 then
    raise exception 'a migração 021 cancelada: WITH CHECK inesperado em atendimento_insert: %', v_exp
      using errcode = '55000';
  end if;
end
$$;

alter policy atendimento_insert on public.atendimento
  with check (
    app_usuario_ativo()
    and app_tem_permissao('abrir_atendimento')
    and id_solicitante = app_usuario_id()
    and (app_tipo_org() <> 'cliente' or id_cliente = app_id_cliente())
    and (app_tipo_org() <> 'distribuidor'
         or (id_distribuidor_origem = app_id_distribuidor()
             and (id_cliente is null or app_cliente_visivel(id_cliente))))
  );

commit;

-- =====================================================================
-- ROLLBACK (volta ao WITH CHECK original, o que estava no banco em 24/11/2026):
--
-- alter policy atendimento_insert on public.atendimento
--   with check (
--     app_usuario_ativo()
--     and app_tem_permissao('abrir_atendimento')
--     and id_solicitante = app_usuario_id()
--     and (app_tipo_org() <> 'cliente' or id_cliente = app_id_cliente())
--   );
-- =====================================================================
