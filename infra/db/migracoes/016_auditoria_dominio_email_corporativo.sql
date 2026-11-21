-- =====================================================================
-- Migração 016: auditoria das alterações em dominio_email_corporativo
--
-- Autor: Rodrigo Almeida (Pessoa 1)
--
-- Número da migração: 016 (reservado pela Pessoa 1; o grupo confirma antes de aplicar).
--
-- Motivo: quem cadastra ou altera um domínio de e-mail corporativo decide quem pode ser
-- aprovado como Netafim ou como distribuidor (RNF18). A API emite o evento
-- "configuracao_alterada" (já previsto no CHECK ck_audit_acao e no dicionário de dados),
-- mas só o escreve no log do servidor: a trilha log_auditoria é gravada pelo banco, por
-- gatilhos, e a tabela dominio_email_corporativo não tinha gatilho. Conferido no banco real
-- em 21/11/2026: criar, desativar e ativar um domínio pelo aplicativo não deixou nenhuma
-- linha em log_auditoria (só o login).
--
-- O que muda:
--   * função nova fn_dominio_depois() e gatilho novo trg_dominio_depois (AFTER INSERT, UPDATE
--     e DELETE, por linha) em dominio_email_corporativo. Nada mais é alterado.
--   * cada alteração grava um evento fn_audit('configuracao_alterada', 'sucesso',
--     'dominio_email_corporativo', <id_dominio>, <detalhes>), no mesmo vocabulário que a
--     API já usa em "detalhes": operacao = criar | alterar (e excluir, que a API não oferece
--     mas o banco permite ao postgres).
--   * detalhes: o domínio, o tipo, o distribuidor e o ativo (em "alterar": "de" e "para").
--     Domínio não tem "@" (CHECK ck_dominio_formato), então não esbarra na guarda de e-mail
--     de fn_audit_antes.
--   * UPDATE que não muda nenhum desses quatro campos não grava evento.
--
-- Ator: fn_audit usa coalesce(p_id_usuario, app_usuario_id()). Pela API, app_usuario_id() é o
-- Netafim Admin da requisição; no SQL Editor (postgres) fica nulo. Não há como o banco
-- conhecer IP e user agent, então esses campos ficam vazios, como nos demais gatilhos.
--
-- Efeito colateral: o gatilho passa a exigir a trava de fn_audit (pg_advisory_xact_lock em
-- 'log_auditoria') em toda alteração de domínio. Domínio é tabela de baixo volume.
--
-- Não existia função com esse nome (conferido), então não há corpo a comparar antes de aplicar.
--
-- Aplicado em: pendente
--
-- Como desfazer (não apaga eventos já gravados, a trilha é imutável):
--   drop trigger if exists trg_dominio_depois on dominio_email_corporativo;
--   drop function if exists fn_dominio_depois();
--
-- Pode ser executado mais de uma vez (create or replace + drop trigger if exists).
-- Quem aplica: a Pessoa 1, como postgres (dono das tabelas e de fn_audit), no SQL Editor.
-- =====================================================================
begin;

create or replace function fn_dominio_depois() returns trigger
language plpgsql security definer
set search_path = public, pg_temp
as $$
begin
  if tg_op = 'INSERT' then
    perform fn_audit('configuracao_alterada', 'sucesso', 'dominio_email_corporativo',
                     new.id_dominio::text,
                     jsonb_build_object('operacao', 'criar',
                                        'dominio', new.dominio,
                                        'tipo_organizacao', new.tipo_organizacao,
                                        'id_distribuidor', new.id_distribuidor,
                                        'ativo', new.ativo));
    return null;
  end if;

  if tg_op = 'DELETE' then
    perform fn_audit('configuracao_alterada', 'sucesso', 'dominio_email_corporativo',
                     old.id_dominio::text,
                     jsonb_build_object('operacao', 'excluir',
                                        'dominio', old.dominio,
                                        'tipo_organizacao', old.tipo_organizacao,
                                        'id_distribuidor', old.id_distribuidor,
                                        'ativo', old.ativo));
    return null;
  end if;

  -- UPDATE: só quando algo que importa para a aprovação de cadastro mudou
  if (new.dominio, new.tipo_organizacao, new.id_distribuidor, new.ativo)
     is distinct from (old.dominio, old.tipo_organizacao, old.id_distribuidor, old.ativo) then
    perform fn_audit('configuracao_alterada', 'sucesso', 'dominio_email_corporativo',
                     new.id_dominio::text,
                     jsonb_build_object(
                       'operacao', 'alterar',
                       'de', jsonb_build_object('dominio', old.dominio,
                                                'tipo_organizacao', old.tipo_organizacao,
                                                'id_distribuidor', old.id_distribuidor,
                                                'ativo', old.ativo),
                       'para', jsonb_build_object('dominio', new.dominio,
                                                  'tipo_organizacao', new.tipo_organizacao,
                                                  'id_distribuidor', new.id_distribuidor,
                                                  'ativo', new.ativo)));
  end if;
  return null;
end
$$;

drop trigger if exists trg_dominio_depois on dominio_email_corporativo;
create trigger trg_dominio_depois
  after insert or update or delete on dominio_email_corporativo
  for each row execute function fn_dominio_depois();

commit;
