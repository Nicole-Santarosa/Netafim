-- =====================================================================
-- Proposta: limpar o contato do solicitante ao anonimizar o titular
--
-- Autor: Rafael Avelar
--
-- Número da migração: a definir pela Pessoa 1, na hora de aplicar.
--
-- Motivo: fn_anonimizar_usuario descaracteriza nome, e-mail e telefone da
-- conta, mas não toca em atendimento. O telefone que a pessoa digitou ao
-- abrir cada atendimento (contato_solicitante) continuava guardado. Pela
-- seção 56 da Documentação Técnica, ao anonimizar o titular o contato dele
-- deve ser limpo nos atendimentos em que ele é o solicitante; nome do
-- produtor, cidade e textos livres são preservados (sem limpeza
-- indiscriminada dos demais dados).
--
-- O que muda: só um bloco novo dentro de fn_anonimizar_usuario, logo depois
-- da checagem de que o usuário existe. O resto da função é igual ao da base
-- (infra/db/base/001_schema_inicial.sql) e ao do 02_funcoes_gatilhos.sql.
--
-- Valor fixo: contato_solicitante é varchar(20) not null, então não dá para
-- gravar nulo. A limpeza grava 'anonimizado'. Não há migração da coluna.
--
-- Efeitos colaterais conhecidos (são do gatilho do atendimento, não deste
-- bloco): cada atendimento atingido soma 1 em versao, ganha um sync_seq novo
-- (o aparelho baixa a linha de novo) e fica com id_atualizado_por nulo,
-- porque a função roda como ação do sistema. Não gera evento no histórico do
-- atendimento; o rastro é a auditoria da própria função (fn_audit).
-- A trava de estado final de fn_atendimento_antes não atrapalha, pelo mesmo
-- motivo: app.acao_sistema está ligado.
--
-- Repetir a chamada é inofensivo: o UPDATE só pega as linhas que ainda não
-- foram anonimizadas, então a segunda execução não mexe em versao nem em
-- sync_seq.
--
-- ATENÇÃO, este script substitui a função INTEIRA. Quem aplica deve, antes,
-- comparar o corpo abaixo com a função em uso no banco:
--   select pg_get_functiondef('fn_anonimizar_usuario(integer)'::regprocedure);
-- e conferir o dono dela (\df+ fn_anonimizar_usuario). O dono importa: como
-- as tabelas não usam force row level security, é por a função ser dona
-- delas que o UPDATE em atendimento passa pelo RLS. Se o banco tiver a função
-- diferente da base, ajustar o corpo antes de aplicar, para não desfazer uma
-- alteração feita por outra migração.
--
-- Aplicado em: pendente
--
-- Como desfazer: reaplicar a função original da base
-- (create or replace function fn_anonimizar_usuario, sem o bloco de
-- atendimento). Os contatos já anonimizados não voltam; isso é intencional.
--
-- Pode ser executado mais de uma vez.
--
-- Registro da migração (padrão da 006): depois de aplicar, com o arquivo já
-- renomeado para infra/db/migracoes/NNN_anonimizar_contato_solicitante.sql,
--   python infra/db/verificar_migracoes.py --checksums
--   select fn_registrar_migracao('migracoes/NNN_anonimizar_contato_solicitante.sql', '<sha256>');
-- O registro fica fora deste arquivo de propósito: o checksum é calculado
-- sobre o próprio arquivo, então ele não pode conter a chamada com o valor.
-- =====================================================================
begin;

-- Anonimiza o usuário preservando os registros legais do atendimento
-- (o id continua, então histórico e assinaturas seguem íntegros).
create or replace function fn_anonimizar_usuario(p_id_usuario integer) returns void
language plpgsql security definer
set search_path = public, pg_temp
as $$
declare
  v_prev text := coalesce(current_setting('app.acao_sistema', true), '');
begin
  -- roda como acao do sistema: anonimizar nao depende do perfil de quem chama
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
