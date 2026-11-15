-- =====================================================================
-- Proposta: só o Netafim Admin altera a situação do projeto depois da abertura
--
-- Autor: Rafael Avelar
--
-- Número da migração: a definir pela Pessoa 1, na hora de aplicar
-- (proposta 013).
--
-- Motivo: em fn_atendimento_antes nenhuma regra olha situacao_projeto no
-- UPDATE. Como o RLS de UPDATE é por linha (app_atendimento_operavel), todo
-- perfil que pode alterar o atendimento (Netafim, distribuidor e o cliente
-- que o abriu) conseguiria trocar a situação do projeto. A tela de edição do
-- Admin vai passar a alterar esse campo, e a regra do grupo é: depois da
-- abertura, só o Netafim Admin (ou o processo automático) altera. Na abertura
-- (INSERT) quem abre o atendimento continua podendo informá-la. O erro sai
-- como 23514 (a API responde 422 regra_de_negocio com a mensagem do gatilho).
--
-- O que muda: só o bloco "situação do projeto", no ramo de UPDATE, junto das
-- outras regras de quem pode alterar (logo depois da destinação e antes do
-- vínculo do técnico). Usa as mesmas checagens das regras vizinhas: v_sist
-- (app.acao_sistema ligado) e app_eh_admin_netafim(). O resto da função é
-- igual ao do 008 (agendado_para_em_atendimento.sql, revisão de 10/11/2026).
--
-- Depende do 008: esta função contém o bloco de iniciar do 008, então
-- aplicar depois do 008 mantém as duas regras. Aplicar esta antes do 008
-- também traz o bloco de iniciar; aplicar o 008 depois desta apagaria a
-- trava da situação do projeto.
--
-- Efeito conhecido: quem não é Netafim Admin e mandar um UPDATE que não muda
-- a situação do projeto (mesmo valor) não é barrado, porque is distinct from
-- compara o valor antigo com o novo.
--
-- Aplicado em: pendente
--
-- Como desfazer: reaplicar a função do 008
-- (backend/db/propostas/agendado_para_em_atendimento.sql), que é esta mesma
-- função sem o bloco da situação do projeto.
--
-- Pode ser executado mais de uma vez.
-- =====================================================================
begin;

create or replace function fn_atendimento_antes() returns trigger
language plpgsql security definer
set search_path = public, pg_temp
as $$
declare
  v_pesq  pesquisa_satisfacao%rowtype;
  v_uid   integer := app_usuario_id();
  v_sist  boolean := app_acao_sistema();
begin
  if tg_op = 'INSERT' then
    -- o protocolo é sempre do servidor; o valor informado é ignorado
    new.numero_protocolo := fn_proximo_protocolo();
    if new.status <> 'aberto' then
      raise exception 'um atendimento novo precisa nascer com status aberto (recebido: %)', new.status
        using errcode = '23514';
    end if;
    new.versao := 1;
    -- destinação e técnico não nascem no pedido: o Netafim Admin destina e
    -- quem tem a permissão vincular_tecnico aloca o técnico
    if not (v_sist or app_eh_admin_netafim())
       and (new.destinado_a is not null or new.id_distribuidor is not null) then
      raise exception 'somente o Netafim Admin define a destinação do atendimento' using errcode = '23514';
    end if;
    if new.id_tecnico_responsavel is not null and not v_sist
       and not app_tem_permissao('vincular_tecnico') then
      raise exception 'seu perfil não pode vincular técnico ao atendimento' using errcode = '23514';
    end if;
    return new;
  end if;

  -- UPDATE ----------------------------------------------------------
  if new.id_atendimento <> old.id_atendimento then
    raise exception 'id_atendimento não pode mudar' using errcode = '23514';
  end if;
  new.numero_protocolo := old.numero_protocolo;   -- imutável
  new.versao := old.versao + 1;                    -- concorrência otimista

  -- estados finais (encerrado, encerrado sem avaliação e cancelado) não
  -- aceitam mais alteração, salvo do Netafim Admin ou do processo automático
  if old.status in ('encerrado','encerrado_sem_avaliacao','cancelado')
     and not (v_sist or app_eh_admin_netafim()) then
    raise exception 'atendimento em estado final (%) não pode mais ser alterado', old.status
      using errcode = '23514';
  end if;

  if new.status is distinct from old.status then
    -- transições permitidas (seção 5.2)
    if not ((old.status, new.status) in (
         ('aberto','destinado'),         ('aberto','cancelado'),
         ('destinado','agendado'),       ('destinado','cancelado'),
         ('agendado','em_atendimento'),  ('agendado','cancelado'),
         ('em_atendimento','resolvido'), ('em_atendimento','cancelado'),
         ('resolvido','encerrado'),      ('resolvido','encerrado_sem_avaliacao'))) then
      raise exception 'transição de status não permitida: % -> %', old.status, new.status
        using errcode = '23514';
    end if;

    -- quem pode fazer cada transição
    if new.status = 'destinado' and not (v_sist or app_eh_admin_netafim()) then
      raise exception 'somente o Netafim Admin destina o atendimento' using errcode = '23514';
    end if;
    if new.status in ('cancelado','encerrado','encerrado_sem_avaliacao')
       and not (v_sist or app_eh_admin_netafim()) then
      raise exception 'somente o Netafim Admin ou o processo automático pode mudar para %', new.status
        using errcode = '23514';
    end if;

    -- iniciar o atendimento: o técnico responsável, o Netafim Admin ou o
    -- Distribuidor Admin (só se o atendimento estiver destinado ao distribuidor
    -- dele). O processo automático fica de fora de propósito: v_sist não entra
    -- aqui, então uma rotina com app.acao_sistema ligado também é recusada.
    if old.status = 'agendado' and new.status = 'em_atendimento'
       and not (app_eh_admin_netafim()
                or coalesce(app_tipo_org() = 'distribuidor' and app_nivel() = 'admin'
                            and old.id_distribuidor = app_id_distribuidor(), false)
                or coalesce(old.id_tecnico_responsavel = v_uid, false)) then
      raise exception 'somente o técnico responsável ou um administrador inicia o atendimento'
        using errcode = '23514';
    end if;

    -- o que cada estado exige
    if new.status = 'agendado' then
      if not exists (select 1 from agendamento g
                      where g.id_atendimento = new.id_atendimento
                        and g.status = 'ativo' and g.deletado_em is null) then
        raise exception 'agendar exige um agendamento ativo' using errcode = '23514';
      end if;
    elsif new.status = 'resolvido' then
      if not exists (select 1 from relatorio_atendimento r
                      where r.id_atendimento = new.id_atendimento
                        and r.status = 'finalizado' and r.deletado_em is null) then
        raise exception 'resolver exige o relatório finalizado' using errcode = '23514';
      end if;
      new.data_conclusao := coalesce(new.data_conclusao, now());
    elsif new.status = 'encerrado' then
      select * into v_pesq from pesquisa_satisfacao where id_atendimento = new.id_atendimento;
      if not found or v_pesq.data_resposta is null then
        raise exception 'encerrar exige a pesquisa de satisfação respondida' using errcode = '23514';
      end if;
      new.data_encerramento := coalesce(new.data_encerramento, now());
    elsif new.status = 'encerrado_sem_avaliacao' then
      if now() < old.data_conclusao + make_interval(days => fn_prazo_avaliacao_dias()) then
        raise exception 'o prazo de espera pela avaliação (% dias) ainda não venceu', fn_prazo_avaliacao_dias()
          using errcode = '23514';
      end if;
      if exists (select 1 from pesquisa_satisfacao p
                  where p.id_atendimento = new.id_atendimento and p.data_resposta is not null) then
        raise exception 'a pesquisa já foi respondida: o encerramento é "encerrado"' using errcode = '23514';
      end if;
      new.data_encerramento := coalesce(new.data_encerramento, now());
    end if;
  end if;

  -- destinação (Netafim ou distribuidor): só o Netafim Admin (RN10)
  if (new.destinado_a, new.id_distribuidor) is distinct from (old.destinado_a, old.id_distribuidor)
     and not (v_sist or app_eh_admin_netafim()) then
    raise exception 'somente o Netafim Admin altera a destinação do atendimento' using errcode = '23514';
  end if;

  -- situação do projeto: informada por quem abre o atendimento (o INSERT não
  -- passa por aqui); depois da abertura, só o Netafim Admin ou o processo
  -- automático altera. Vale para qualquer mudança, inclusive de nulo para um
  -- valor e de um valor para nulo, por isso o is distinct from.
  if new.situacao_projeto is distinct from old.situacao_projeto
     and not (v_sist or app_eh_admin_netafim()) then
    raise exception 'somente o Netafim Admin altera a situação do projeto' using errcode = '23514';
  end if;

  -- vínculo do técnico: exige a permissão vincular_tecnico; o Distribuidor
  -- Admin só vincula técnicos do próprio distribuidor (RN05)
  if new.id_tecnico_responsavel is distinct from old.id_tecnico_responsavel and not v_sist then
    if not app_tem_permissao('vincular_tecnico') then
      raise exception 'seu perfil não pode vincular técnico ao atendimento' using errcode = '23514';
    end if;
    if new.id_tecnico_responsavel is not null and app_tipo_org() = 'distribuidor'
       and not exists (select 1 from usuario t
                        where t.id_usuario = new.id_tecnico_responsavel
                          and t.id_distribuidor = app_id_distribuidor()) then
      raise exception 'o Distribuidor Admin só vincula técnicos do próprio distribuidor' using errcode = '23514';
    end if;
  end if;

  -- alteração feita pelo sistema fica sem autor
  if v_sist then new.id_atualizado_por := null;
  elsif v_uid is not null then new.id_atualizado_por := v_uid;
  end if;
  return new;
end
$$;

commit;
