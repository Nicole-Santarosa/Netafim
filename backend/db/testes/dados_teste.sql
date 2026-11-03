-- =====================================================================================
-- DADOS DE TESTE: SOMENTE PARA DESENVOLVIMENTO LOCAL. NUNCA rodar no banco compartilhado (Supabase) nem
-- em produção: este script cria usuários com SENHA CONHECIDA (publicada neste arquivo).
--
-- O que cria (tudo o que já existir é mantido; rodar de novo não duplica nada):
--   * domínios de e-mail corporativo: netafim.com e distribuidorteste.com.br
--   * Distribuidor Teste, Fazenda Teste (cliente do distribuidor) e uma propriedade da fazenda
--   * 3 usuários ATIVOS, com o mesmo consentimento (termo 1.0), todos com a mesma senha de teste:
--
--       Perfil                      E-mail                               Senha
--       Netafim Admin               admin.teste@netafim.com              Teste#Netafim2026
--       Técnico (Netafim Usuário)   tecnico.teste@netafim.com            Teste#Netafim2026
--       Cliente final (Cliente Usuário, Fazenda Teste)
--                                   cliente.teste@fazendateste.com.br    Teste#Netafim2026
--
-- PRÉ-REQUISITOS: o banco já tem o esquema e os dados iniciais (perfis e permissões). Rode como o DONO do
-- esquema (ou superusuário), nunca como app_django, porque o RLS exige um administrador como ator e este
-- script cria o primeiro. A senha é guardada como hash Argon2 do Django (o mesmo que a API usa).
--
-- COMO RODAR (banco local). A trava abaixo exige a confirmação na MESMA sessão:
--   psql "postgres://postgres:SENHA@localhost:5432/netafim" -v ON_ERROR_STOP=1 \
--        -c "SET netafim.dados_teste = 'sim'" -f backend/db/testes/dados_teste.sql
--
-- COMO TESTAR O LOGIN (a API local, na porta 8000):
--   curl -X POST http://localhost:8000/api/v1/auth/login -H "Content-Type: application/json" \
--     -d '{"email":"admin.teste@netafim.com","senha":"Teste#Netafim2026",
--          "dispositivo":{"id_dispositivo":"11111111-1111-4111-8111-111111111111","modelo":"Teste"}}'
--
-- Para limpar, recrie o banco local a partir do retrato (infra/db/base/001_schema_inicial.sql). Não há DELETE aqui
-- de propósito: o esquema do grupo usa exclusão lógica e trilha de auditoria.
-- =====================================================================================

-- Trava: só roda com a confirmação explícita, para não criar usuários de senha conhecida por engano.
DO $$
BEGIN
  IF coalesce(current_setting('netafim.dados_teste', true), '') <> 'sim' THEN
    RAISE EXCEPTION 'Este script cria usuários com SENHA CONHECIDA e é só para banco local. '
                    'Para confirmar, rode antes, na mesma sessão: SET netafim.dados_teste = ''sim'';'
      USING ERRCODE = 'P0001';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM perfil WHERE nome_perfil = 'Netafim Admin') THEN
    RAISE EXCEPTION 'Os dados iniciais (perfis e permissões) ainda não foram carregados.'
      USING ERRCODE = 'P0001';
  END IF;
END $$;

BEGIN;

-- Carga controlada: não enche a trilha de auditoria com eventos de configuração.
SELECT set_config('app.carga_inicial', 'on', true);

-- Domínios de e-mail corporativo (Netafim e distribuidor; o cliente final não precisa de domínio).
INSERT INTO dominio_email_corporativo (dominio, tipo_organizacao)
VALUES ('netafim.com', 'netafim')
ON CONFLICT (dominio) DO NOTHING;

INSERT INTO distribuidor (nome, regiao, email, telefone)
SELECT 'Distribuidor Teste', 'Ribeirão Preto - SP', 'contato@distribuidorteste.com.br', '+5516999990000'
 WHERE NOT EXISTS (SELECT 1 FROM distribuidor WHERE nome = 'Distribuidor Teste');

INSERT INTO dominio_email_corporativo (dominio, tipo_organizacao, id_distribuidor)
SELECT 'distribuidorteste.com.br', 'distribuidor', d.id_distribuidor
  FROM distribuidor d WHERE d.nome = 'Distribuidor Teste'
ON CONFLICT (dominio) DO NOTHING;

-- Cliente final (organização) e uma propriedade, para montar snapshots de atendimento.
INSERT INTO cliente (nome, email, telefone, id_distribuidor)
SELECT 'Fazenda Teste', 'contato@fazendateste.com.br', '+5516988880000', d.id_distribuidor
  FROM distribuidor d
 WHERE d.nome = 'Distribuidor Teste'
   AND NOT EXISTS (SELECT 1 FROM cliente WHERE nome = 'Fazenda Teste');

INSERT INTO propriedade (id_cliente, nome_fazenda, localizacao, latitude, longitude)
SELECT c.id_cliente, 'Sítio Boa Vista', 'Franca - SP', -20.538600, -47.400800
  FROM cliente c
 WHERE c.nome = 'Fazenda Teste'
   AND NOT EXISTS (SELECT 1 FROM propriedade p WHERE p.id_cliente = c.id_cliente AND p.nome_fazenda = 'Sítio Boa Vista');

-- Usuários. Hash Argon2 do Django para a senha de teste (ver o quadro acima).
-- 1) Netafim Admin (o primeiro; ninguém o aprova).
INSERT INTO usuario (nome, email, telefone, password, senha_alterada_em, situacao, tipo_organizacao, id_perfil,
                     consentimento_lgpd, data_consentimento, versao_termo_consentimento)
SELECT 'Admin Netafim (teste)', 'admin.teste@netafim.com', '+5516900000001',
       'argon2$argon2id$v=19$m=102400,t=2,p=8$WEYyZ1lQbmhLdFhxV1VHZGdIVjZIdQ$Qqnun/BGAKXJybSPLVpdI2uwOERogdct9lb9nbI6AJ8', now(),
       'ativo', 'netafim', p.id_perfil, true, now(), '1.0'
  FROM perfil p
 WHERE p.nome_perfil = 'Netafim Admin'
   AND NOT EXISTS (SELECT 1 FROM usuario WHERE lower(email) = 'admin.teste@netafim.com' AND deletado_em IS NULL);

-- 2) Técnico (Netafim Usuário), aprovado pelo Admin Netafim de teste.
INSERT INTO usuario (nome, email, telefone, password, senha_alterada_em, situacao, tipo_organizacao, id_perfil,
                     id_aprovado_por, data_aprovacao,
                     consentimento_lgpd, data_consentimento, versao_termo_consentimento)
SELECT 'Técnico Netafim (teste)', 'tecnico.teste@netafim.com', '+5516900000002',
       'argon2$argon2id$v=19$m=102400,t=2,p=8$WEYyZ1lQbmhLdFhxV1VHZGdIVjZIdQ$Qqnun/BGAKXJybSPLVpdI2uwOERogdct9lb9nbI6AJ8', now(),
       'ativo', 'netafim', p.id_perfil,
       (SELECT id_usuario FROM usuario WHERE lower(email) = 'admin.teste@netafim.com' AND deletado_em IS NULL), now(),
       true, now(), '1.0'
  FROM perfil p
 WHERE p.nome_perfil = 'Netafim Usuário'
   AND NOT EXISTS (SELECT 1 FROM usuario WHERE lower(email) = 'tecnico.teste@netafim.com' AND deletado_em IS NULL);

-- 3) Cliente final (Cliente Usuário da Fazenda Teste), aprovado pelo Admin Netafim de teste.
INSERT INTO usuario (nome, email, telefone, password, senha_alterada_em, situacao, tipo_organizacao, id_perfil, id_cliente,
                     id_aprovado_por, data_aprovacao,
                     consentimento_lgpd, data_consentimento, versao_termo_consentimento)
SELECT 'Cliente Final (teste)', 'cliente.teste@fazendateste.com.br', '+5516900000003',
       'argon2$argon2id$v=19$m=102400,t=2,p=8$WEYyZ1lQbmhLdFhxV1VHZGdIVjZIdQ$Qqnun/BGAKXJybSPLVpdI2uwOERogdct9lb9nbI6AJ8', now(),
       'ativo', 'cliente', p.id_perfil, c.id_cliente,
       (SELECT id_usuario FROM usuario WHERE lower(email) = 'admin.teste@netafim.com' AND deletado_em IS NULL), now(),
       true, now(), '1.0'
  FROM perfil p, cliente c
 WHERE p.nome_perfil = 'Cliente Usuário' AND c.nome = 'Fazenda Teste'
   AND NOT EXISTS (SELECT 1 FROM usuario WHERE lower(email) = 'cliente.teste@fazendateste.com.br' AND deletado_em IS NULL);

COMMIT;

-- Conferência
SELECT u.id_usuario, u.email, p.nome_perfil, u.situacao
  FROM usuario u JOIN perfil p USING (id_perfil)
 WHERE lower(u.email) IN ('admin.teste@netafim.com', 'tecnico.teste@netafim.com', 'cliente.teste@fazendateste.com.br')
 ORDER BY u.id_usuario;
