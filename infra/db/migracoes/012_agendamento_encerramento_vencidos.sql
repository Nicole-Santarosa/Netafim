-- =====================================================================
-- Proposta: agendar o encerramento automático dos atendimentos vencidos
--
-- Autor: Rafael Avelar
--
-- Número da migração: a definir pela Pessoa 1, na hora de aplicar.
--
-- Motivo: a função fn_encerrar_atendimentos_vencidos() já existe no banco
-- (02_funcoes_gatilhos.sql e base 001), mas ninguém a chama. Sem uma
-- chamada periódica, o atendimento resolvido cujo cliente não responde a
-- pesquisa de satisfação fica "resolvido" para sempre e nunca chega a
-- "encerrado_sem_avaliacao".
--
-- O que a função faz: com app.acao_sistema = 'on' (local à transação), passa
-- de "resolvido" para "encerrado_sem_avaliacao" todo atendimento não excluído
-- cuja data_conclusao mais o prazo já passou e que não tem pesquisa
-- respondida. O gatilho do atendimento grava data_encerramento e o evento
-- status_alterado no histórico, com origem "sistema". Devolve quantos
-- atendimentos encerrou.
--
-- Prazo: 7 dias, vindo de fn_prazo_avaliacao_dias(). O valor ainda precisa
-- ser confirmado com a Netafim. Se mudar, não é preciso mexer neste
-- agendamento: basta alter database ... set app.prazo_avaliacao_dias = 'N'.
--
-- O que esta proposta cria: a extensão pg_cron (se ainda não existir) e um
-- job diário chamado encerrar_atendimentos_vencidos, às 06:00 UTC (03:00 de
-- Brasília, horário de pouco uso). O pg_cron interpreta o horário em UTC.
--
-- Pré-requisito no Supabase: a extensão pg_cron precisa estar habilitada no
-- painel (Database, Extensions) antes de rodar este script. Quem aplica é a
-- Pessoa 1, com o dono do esquema, depois do "de acordo" do grupo.
--
-- Aplicado em: pendente
--
-- Registro da migração (padrão da 006): depois de aplicar, com o arquivo já
-- renomeado para infra/db/migracoes/NNN_agendamento_encerramento_vencidos.sql,
--   python infra/db/verificar_migracoes.py --checksums
--   select fn_registrar_migracao('migracoes/NNN_agendamento_encerramento_vencidos.sql', '<sha256>');
-- O registro fica fora deste arquivo de propósito: o checksum é calculado
-- sobre o próprio arquivo, então ele não pode conter a chamada com o valor.
--
-- Pode ser executado mais de uma vez: se o job já existir, ele é removido e
-- criado de novo, e nunca fica duplicado.
--
-- Como desfazer (rollback):
--   select cron.unschedule(jobid) from cron.job
--    where jobname = 'encerrar_atendimentos_vencidos';
-- A extensão pg_cron pode continuar instalada; removê-la (drop extension
-- pg_cron) só se nenhum outro job depender dela.
-- =====================================================================
begin;

-- No Supabase o create só funciona com a extensão já habilitada no painel;
-- com ela habilitada, o "if not exists" deixa a linha inofensiva.
create extension if not exists pg_cron;

-- Remove o job anterior com o mesmo nome, se houver. Selecionar a partir de
-- cron.job evita o erro que o cron.unschedule('nome') dá quando o job não
-- existe, e é isso que deixa o script ser aplicado duas vezes.
select cron.unschedule(jobid)
  from cron.job
 where jobname = 'encerrar_atendimentos_vencidos';

-- Todo dia às 06:00 UTC. A própria função liga app.acao_sistema e devolve o
-- total encerrado, que fica no histórico de execuções (cron.job_run_details).
select cron.schedule(
  'encerrar_atendimentos_vencidos',
  '0 6 * * *',
  'select fn_encerrar_atendimentos_vencidos()'
);

commit;
