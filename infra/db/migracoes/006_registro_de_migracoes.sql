-- =====================================================================================
-- MIGRAÇÃO 006 (NÃO aplicada no banco do grupo): registro central das migrações aplicadas.
--
-- Motivo (proposta da Nicole): saber, no próprio banco, quais scripts já foram aplicados, com identificação
-- do arquivo, checksum e data, para evitar execução duplicada e alteração esquecida de um script já aplicado.
--
-- O que cria:
--   * tabela migracao_aplicada (arquivo, checksum SHA-256, data, quem aplicou);
--   * função fn_registrar_migracao(arquivo, checksum), que insere o registro e RECUSA:
--       - o mesmo arquivo com o mesmo checksum (execução duplicada);
--       - o mesmo arquivo com checksum diferente (arquivo alterado depois de aplicado).
--   Sem nenhum privilégio para app_django (a API não lê nem escreve no registro).
--
-- Como usar (somente quem aplica no banco, a Pessoa 1, depois do "de acordo" do grupo):
--   python infra/db/verificar_migracoes.py --checksums            -- arquivo  sha256
--   select fn_registrar_migracao('migracoes/004_login_sem_leitura_de_hash.sql', '<sha256>');
--
-- Os scripts que já estavam aplicados antes desta migração (a base) e a própria 006 precisam ser registrados
-- à mão uma vez, por quem tem acesso ao banco e conhece o estado real.
--
-- Idempotente (CREATE ... IF NOT EXISTS, CREATE OR REPLACE). Aplicar com o dono do esquema.
-- =====================================================================================
CREATE TABLE IF NOT EXISTS public.migracao_aplicada (
  arquivo      varchar(200) PRIMARY KEY,
  checksum     char(64)     NOT NULL,
  aplicada_em  timestamptz  NOT NULL DEFAULT now(),
  aplicada_por text         NOT NULL DEFAULT session_user,
  CONSTRAINT ck_migracao_checksum CHECK (checksum ~ '^[0-9a-f]{64}$')
);

REVOKE ALL ON TABLE public.migracao_aplicada FROM PUBLIC;
REVOKE ALL ON TABLE public.migracao_aplicada FROM app_django;

CREATE OR REPLACE FUNCTION public.fn_registrar_migracao(p_arquivo text, p_checksum text)
 RETURNS void
 LANGUAGE plpgsql
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  v_checksum text := lower(btrim(p_checksum));
  v_atual    text;
begin
  if coalesce(btrim(p_arquivo), '') = '' then
    raise exception 'informe o arquivo da migração' using errcode = '22023';
  end if;
  if v_checksum !~ '^[0-9a-f]{64}$' then
    raise exception 'checksum inválido: esperado SHA-256 em hexadecimal (64 caracteres)' using errcode = '22023';
  end if;

  select m.checksum into v_atual from migracao_aplicada m where m.arquivo = btrim(p_arquivo);
  if found then
    if v_atual = v_checksum then
      raise exception 'migração já registrada como aplicada: %', btrim(p_arquivo) using errcode = '23505';
    end if;
    raise exception 'arquivo alterado depois de aplicado: % (checksum registrado %, informado %)',
      btrim(p_arquivo), v_atual, v_checksum using errcode = '23514';
  end if;

  insert into migracao_aplicada (arquivo, checksum) values (btrim(p_arquivo), v_checksum);
end
$function$;

REVOKE ALL ON FUNCTION public.fn_registrar_migracao(text, text) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.fn_registrar_migracao(text, text) FROM app_django;

COMMENT ON TABLE public.migracao_aplicada IS
  'Registro das migrações aplicadas no banco: arquivo, checksum SHA-256, data e quem aplicou (infra/db/README.md).';
COMMENT ON FUNCTION public.fn_registrar_migracao(text, text) IS
  'Registra uma migração aplicada; recusa duplicada e arquivo alterado depois de aplicado.';
