"""
Regras de integridade do módulo de acesso garantidas pelo PostgreSQL
(MER/DER, seção 5.1). Valem para qualquer caminho de escrita: API, script ou painel.

1. Chave estrangeira composta usuario(id_perfil, tipo_organizacao): o perfil do
   usuário é sempre do mesmo tipo de organização dele.
2. Aprovação de usuário da Netafim ou de distribuidor exige domínio de e-mail
   ativo em DOMINIO_EMAIL_CORPORATIVO.
3. Inativar, bloquear, recusar ou excluir a conta revoga os tokens; revogar o
   aparelho revoga os tokens dele.
4. TOKEN_ACESSO: hash, dono, tipo, validade e revogação não voltam atrás.
5. sync_seq global atribuído pelo banco em DISTRIBUIDOR e CLIENTE.
"""

from django.db import migrations

SQL = r"""
-- 1. perfil coerente com a organização ---------------------------------------
ALTER TABLE usuario
    ADD CONSTRAINT usuario_perfil_mesma_organizacao_fk
    FOREIGN KEY (id_perfil, tipo_organizacao)
    REFERENCES perfil (id_perfil, tipo_organizacao);

-- 2. domínio corporativo na aprovação ----------------------------------------
CREATE OR REPLACE FUNCTION fn_usuario_valida_dominio() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    v_dominio text := lower(split_part(NEW.email, '@', 2));
BEGIN
    IF NEW.situacao <> 'ativo'
       OR NEW.deletado_em IS NOT NULL
       OR NEW.tipo_organizacao NOT IN ('netafim', 'distribuidor') THEN
        RETURN NEW;
    END IF;

    -- Só confere quando a conta passa a ativa ou muda e-mail/organização: uma
    -- conta já ativa não trava se o domínio for desativado depois.
    IF TG_OP = 'UPDATE'
       AND OLD.situacao = 'ativo'
       AND OLD.email = NEW.email
       AND OLD.tipo_organizacao IS NOT DISTINCT FROM NEW.tipo_organizacao
       AND OLD.id_distribuidor IS NOT DISTINCT FROM NEW.id_distribuidor THEN
        RETURN NEW;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM dominio_email_corporativo d
         WHERE d.dominio = v_dominio
           AND d.ativo
           AND d.tipo_organizacao = NEW.tipo_organizacao
           AND (NEW.tipo_organizacao = 'netafim' OR d.id_distribuidor = NEW.id_distribuidor)
    ) THEN
        RAISE EXCEPTION 'Domínio de e-mail "%" não autorizado para a organização do usuário.',
            v_dominio
            USING ERRCODE = 'check_violation',
                  CONSTRAINT = 'usuario_dominio_corporativo';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_usuario_valida_dominio
    BEFORE INSERT OR UPDATE ON usuario
    FOR EACH ROW EXECUTE FUNCTION fn_usuario_valida_dominio();

-- 3. revogação automática de tokens ------------------------------------------
CREATE OR REPLACE FUNCTION fn_usuario_revoga_tokens() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF (NEW.situacao IN ('inativo', 'bloqueado', 'recusado') AND NEW.situacao <> OLD.situacao)
       OR (NEW.deletado_em IS NOT NULL AND OLD.deletado_em IS NULL) THEN
        UPDATE token_acesso
           SET revogado_em = now()
         WHERE id_usuario = NEW.id_usuario
           AND revogado_em IS NULL;
    END IF;
    RETURN NULL;
END;
$$;

CREATE TRIGGER trg_usuario_revoga_tokens
    AFTER UPDATE OF situacao, deletado_em ON usuario
    FOR EACH ROW EXECUTE FUNCTION fn_usuario_revoga_tokens();

CREATE OR REPLACE FUNCTION fn_dispositivo_revoga_tokens() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.revogado_em IS NOT NULL AND OLD.revogado_em IS NULL THEN
        UPDATE token_acesso
           SET revogado_em = now()
         WHERE id_dispositivo = NEW.id_dispositivo
           AND revogado_em IS NULL;
    END IF;
    RETURN NULL;
END;
$$;

CREATE TRIGGER trg_dispositivo_revoga_tokens
    AFTER UPDATE OF revogado_em ON dispositivo
    FOR EACH ROW EXECUTE FUNCTION fn_dispositivo_revoga_tokens();

-- 4. token imutável nos campos de segurança ----------------------------------
CREATE OR REPLACE FUNCTION fn_token_acesso_imutavel() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.token_hash <> OLD.token_hash
       OR NEW.id_usuario <> OLD.id_usuario
       OR NEW.tipo <> OLD.tipo
       OR NEW.criado_em <> OLD.criado_em
       OR NEW.expira_em <> OLD.expira_em
       OR NEW.id_dispositivo IS DISTINCT FROM OLD.id_dispositivo
       OR (OLD.revogado_em IS NOT NULL AND NEW.revogado_em IS DISTINCT FROM OLD.revogado_em)
       OR (OLD.usado_em IS NOT NULL AND NEW.usado_em IS DISTINCT FROM OLD.usado_em)
       OR (OLD.substituido_por IS NOT NULL
           AND NEW.substituido_por IS DISTINCT FROM OLD.substituido_por) THEN
        RAISE EXCEPTION 'Campos de segurança do token não podem ser alterados.'
            USING ERRCODE = 'check_violation', CONSTRAINT = 'token_acesso_imutavel';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_token_acesso_imutavel
    BEFORE UPDATE ON token_acesso
    FOR EACH ROW EXECUTE FUNCTION fn_token_acesso_imutavel();

-- 5. cursor de sincronização --------------------------------------------------
CREATE SEQUENCE IF NOT EXISTS sync_seq_global AS bigint;

CREATE OR REPLACE FUNCTION fn_atribuir_sync_seq() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    NEW.sync_seq := nextval('sync_seq_global');
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_distribuidor_sync_seq
    BEFORE INSERT OR UPDATE ON distribuidor
    FOR EACH ROW EXECUTE FUNCTION fn_atribuir_sync_seq();

CREATE TRIGGER trg_cliente_sync_seq
    BEFORE INSERT OR UPDATE ON cliente
    FOR EACH ROW EXECUTE FUNCTION fn_atribuir_sync_seq();

CREATE INDEX distribuidor_sync_seq_idx ON distribuidor (sync_seq);
CREATE INDEX cliente_sync_seq_idx ON cliente (sync_seq);
"""

SQL_REVERSO = r"""
DROP INDEX IF EXISTS cliente_sync_seq_idx;
DROP INDEX IF EXISTS distribuidor_sync_seq_idx;
DROP TRIGGER IF EXISTS trg_cliente_sync_seq ON cliente;
DROP TRIGGER IF EXISTS trg_distribuidor_sync_seq ON distribuidor;
-- fn_atribuir_sync_seq e sync_seq_global podem ser usadas por outros módulos.
DROP TRIGGER IF EXISTS trg_token_acesso_imutavel ON token_acesso;
DROP FUNCTION IF EXISTS fn_token_acesso_imutavel();
DROP TRIGGER IF EXISTS trg_dispositivo_revoga_tokens ON dispositivo;
DROP FUNCTION IF EXISTS fn_dispositivo_revoga_tokens();
DROP TRIGGER IF EXISTS trg_usuario_revoga_tokens ON usuario;
DROP FUNCTION IF EXISTS fn_usuario_revoga_tokens();
DROP TRIGGER IF EXISTS trg_usuario_valida_dominio ON usuario;
DROP FUNCTION IF EXISTS fn_usuario_valida_dominio();
ALTER TABLE usuario DROP CONSTRAINT IF EXISTS usuario_perfil_mesma_organizacao_fk;
"""


class Migration(migrations.Migration):
    dependencies = [("contas", "0001_inicial")]

    operations = [migrations.RunSQL(SQL, SQL_REVERSO)]
