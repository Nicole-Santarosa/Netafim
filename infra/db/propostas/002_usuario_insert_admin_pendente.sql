-- =====================================================================================
-- PROPOSTA DT-003 (NÃO aplicada no banco do grupo): admin de empresa só cria usuário PENDENTE e de nível Usuário.
--
-- Motivo: a política usuario_insert_admin deixa o admin de distribuidor ou de cliente inserir usuário já
-- ATIVO e com perfil ADMIN da própria organização. A API já bloqueia isso (servicos/usuarios.py cria
-- sempre pendente, com o perfil de nível Usuário), mas o banco não. A regra precisa valer nas duas camadas.
--
-- Regra nova, quando quem insere NÃO é o Netafim Admin:
--   * situação = pendente, sem aprovação gravada (id_aprovado_por e data_aprovacao nulos);
--   * perfil de nível 'usuario' e do mesmo tipo de organização do novo usuário;
--   * organização igual à do admin que insere (como antes).
-- O Netafim Admin continua podendo criar qualquer usuário, em qualquer organização.
--
-- Impacto: nenhum no fluxo atual da API. Quem ativa o usuário criado é o Netafim Admin (gatilho
-- fn_usuario_protege). Aplicar com o dono do esquema, depois do "de acordo" do grupo.
-- =====================================================================================
DROP POLICY IF EXISTS usuario_insert_admin ON public.usuario;

CREATE POLICY usuario_insert_admin ON public.usuario FOR INSERT TO app_django
  WITH CHECK (
    app_tem_permissao('criar_usuarios')
    AND (
      app_eh_admin_netafim()
      OR (
        app_nivel() = 'admin'
        AND situacao = 'pendente'
        AND id_aprovado_por IS NULL
        AND data_aprovacao IS NULL
        AND EXISTS (
          SELECT 1 FROM public.perfil p
           WHERE p.id_perfil = usuario.id_perfil
             AND p.nivel = 'usuario'
             AND p.tipo_organizacao = usuario.tipo_organizacao
        )
        AND (
          (tipo_organizacao = 'distribuidor' AND id_distribuidor = app_id_distribuidor())
          OR (tipo_organizacao = 'cliente' AND id_cliente = app_id_cliente())
        )
      )
    )
  );

COMMENT ON POLICY usuario_insert_admin ON public.usuario IS
  'Netafim Admin cria qualquer usuário; admin de distribuidor ou de cliente só cria usuário pendente, de nível Usuário, na própria organização (DT-003).';
