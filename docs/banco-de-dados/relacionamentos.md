# Relacionamentos do banco de dados — Netafim

> **Fonte principal:** `MER e DER.pdf`, versão de 29/09/2026, páginas 5–16 e Anexo A.
>
> Este documento descreve os vínculos conceituais e as chaves estrangeiras previstos no modelo. A cardinalidade deve ser conferida com o DER e com o schema SQL aplicado.

## 1. Visão geral

O modelo central possui 41 tabelas e organiza os dados em cinco grandes áreas: acesso e organizações, atendimento, relatório, formulário e privacidade. O banco local Android Room/SQLite possui outras nove tabelas, descritas em `modelo-banco-local-room.md`.

## 2. Acesso, perfis e organizações

### PERFIL — PERMISSAO — PERFIL_PERMISSAO

- `PERFIL` 1:N `PERFIL_PERMISSAO`.
- `PERMISSAO` 1:N `PERFIL_PERMISSAO`.
- `PERFIL` e `PERMISSAO` possuem relação N:N resolvida por `PERFIL_PERMISSAO`.
- A PK de `PERFIL_PERMISSAO` é composta por `(id_perfil, id_permissao)`.

### PERFIL — USUARIO

- Um perfil pode estar associado a vários usuários.
- `USUARIO.id_perfil` referencia `PERFIL.id_perfil`.
- O vínculo é opcional enquanto o cadastro estiver pendente ou recusado; usuário ativo exige perfil.

### USUARIO — USUARIO

- `USUARIO.id_aprovado_por` referencia `USUARIO.id_usuario`.
- Representa o administrador que aprovou ou recusou o cadastro.
- O campo é opcional porque nem toda conta possui decisão registrada.

### USUARIO — DISTRIBUIDOR / CLIENTE

- `USUARIO.id_distribuidor` referencia `DISTRIBUIDOR.id_distribuidor`.
- `USUARIO.id_cliente` referencia `CLIENTE.id_cliente`.
- Os dois vínculos não podem estar preenchidos simultaneamente.
- A organização e o perfil são definidos pelo Netafim Admin na aprovação.

### DISTRIBUIDOR — CLIENTE

- `CLIENTE.id_distribuidor` referencia `DISTRIBUIDOR.id_distribuidor`.
- Um distribuidor pode possuir vários clientes em sua carteira.
- O vínculo do cliente com distribuidor é opcional.

### CLIENTE — PROPRIEDADE

- `PROPRIEDADE.id_cliente` referencia `CLIENTE.id_cliente`.
- Um cliente pode possuir várias propriedades.
- Cada propriedade pertence a um cliente.
- O modelo prevê UNIQUE `(id_propriedade, id_cliente)` para permitir a referência composta feita por ATENDIMENTO.

### PROPRIEDADE — CULTURA

- Relação N:N resolvida por `PROPRIEDADE_CULTURA`.
- `PROPRIEDADE_CULTURA.id_propriedade` referencia PROPRIEDADE.
- `PROPRIEDADE_CULTURA.id_cultura` referencia CULTURA.
- Uma fazenda pode ter várias culturas e uma cultura pode estar presente em várias propriedades.

### USUARIO — DISPOSITIVO

- `DISPOSITIVO.id_usuario` referencia `USUARIO.id_usuario`.
- Um usuário pode possuir vários aparelhos autorizados.
- O dispositivo pode ser revogado sem excluir a conta.

### DISPOSITIVO — SYNC_CONFLITO

- `SYNC_CONFLITO.id_dispositivo` referencia `DISPOSITIVO.id_dispositivo`.
- O vínculo é opcional, pois a documentação permite conflito sem dispositivo identificado.
- `entidade_tipo` + `entidade_uuid` formam referência polimórfica controlada pela aplicação, não uma FK convencional.

### DOMINIO_EMAIL_CORPORATIVO — DISTRIBUIDOR

- `DOMINIO_EMAIL_CORPORATIVO.id_distribuidor` referencia DISTRIBUIDOR.
- É opcional para domínio Netafim e necessário quando o domínio é do tipo distribuidor.
- O domínio ativo é verificado no fluxo de aprovação.

## 3. Atendimento, agenda e classificação

### USUARIO — ATENDIMENTO

O atendimento possui vínculos com usuários em papéis diferentes:

- `ATENDIMENTO.id_solicitante` → `USUARIO.id_usuario` (obrigatório).
- `ATENDIMENTO.id_tecnico_responsavel` → `USUARIO.id_usuario` (opcional até a alocação).
- `ATENDIMENTO.id_atualizado_por` → `USUARIO.id_usuario` (opcional; nulo em atualização automática).

Um usuário pode solicitar, ser responsável técnico ou realizar uma atualização em vários atendimentos.

### CLIENTE — ATENDIMENTO

- `ATENDIMENTO.id_cliente` referencia CLIENTE.
- O vínculo é opcional para origens Netafim e Distribuidor.
- É obrigatório quando `origem = cliente_final`.

### PROPRIEDADE — ATENDIMENTO

- `ATENDIMENTO.id_propriedade` e `id_cliente` formam referência composta para PROPRIEDADE.
- A propriedade é opcional.
- Quando informada, deve pertencer ao cliente associado ao atendimento.
- A combinação impede associar uma fazenda de um cliente a atendimento de outro.

### DISTRIBUIDOR — ATENDIMENTO

Existem dois papéis distintos:

- `ATENDIMENTO.id_distribuidor_origem` → DISTRIBUIDOR: distribuidor que originou o chamado; opcional.
- `ATENDIMENTO.id_distribuidor` → DISTRIBUIDOR: distribuidor que receberá o atendimento; opcional até a destinação.
- `destinado_a = distribuidor` exige `id_distribuidor`.

### ATENDIMENTO — HISTORICO_ATENDIMENTO

- `HISTORICO_ATENDIMENTO.id_atendimento` referencia ATENDIMENTO.
- Um atendimento possui vários eventos históricos.
- `id_usuario_responsavel` referencia USUARIO e é opcional na coluna, mas condicionado à origem do evento:
  - `origem_evento = usuario`: usuário responsável obrigatório.
  - `origem_evento = sistema`: usuário responsável nulo.
- Histórico é somente de inserção; não deve ser editado nem excluído.

### ATENDIMENTO — AGENDAMENTO

- `AGENDAMENTO.id_atendimento` referencia ATENDIMENTO.
- Um atendimento pode ter vários registros de agendamento ao longo do tempo.
- Cada reagendamento cria nova linha e cancela a anterior.
- Índice único parcial permite no máximo um agendamento com `status = ativo` por atendimento.
- `AGENDAMENTO.id_criado_por` referencia USUARIO.

### PROBLEMA_CATEGORIA — PROBLEMA_SUBCATEGORIA

- `PROBLEMA_SUBCATEGORIA.id_categoria` referencia PROBLEMA_CATEGORIA.
- Uma categoria pode possuir várias subcategorias.
- UNIQUE `(id_subcategoria, id_categoria)` permite a FK composta usada na classificação.

### ATENDIMENTO — PROBLEMA_CATEGORIA / PROBLEMA_SUBCATEGORIA

- A tabela `ATENDIMENTO_PROBLEMA` associa um atendimento a uma categoria e, opcionalmente, a uma subcategoria.
- `id_atendimento` → ATENDIMENTO.
- `id_categoria` → PROBLEMA_CATEGORIA.
- `(id_subcategoria, id_categoria)` → PROBLEMA_SUBCATEGORIA.
- A subcategoria precisa pertencer à categoria indicada.
- A categoria precisa ser compatível com o motivo do atendimento.
- A combinação atendimento/categoria/subcategoria não pode se repetir.

### ATENDIMENTO — CULTURA

- Relação N:N resolvida por `ATENDIMENTO_CULTURA`.
- `ATENDIMENTO_CULTURA.id_atendimento` → ATENDIMENTO.
- `ATENDIMENTO_CULTURA.id_cultura` → CULTURA.
- Permite múltiplas culturas em um atendimento.

### ATENDIMENTO — TIPO_TREINAMENTO

- Relação N:N resolvida por `ATENDIMENTO_TIPO_TREINAMENTO`.
- `id_atendimento` → ATENDIMENTO.
- `id_tipo_treinamento` → TIPO_TREINAMENTO.
- Permite selecionar vários tipos de treinamento.

## 4. Relatórios, evidências e comunicação

### ATENDIMENTO — RELATORIO_ATENDIMENTO

- `RELATORIO_ATENDIMENTO.id_atendimento` referencia ATENDIMENTO.
- O modelo prevê um relatório por atendimento entre registros não excluídos.
- `id_preenchido_por` referencia USUARIO e identifica quem preencheu.
- `id_atualizado_por` referencia USUARIO e identifica quem realizou edição posterior; é opcional.

### RELATORIO_ATENDIMENTO — RELATORIO_ESPECIALISTA — USUARIO

- `RELATORIO_ESPECIALISTA.id_relatorio` → RELATORIO_ATENDIMENTO.
- `RELATORIO_ESPECIALISTA.id_usuario` → USUARIO.
- Relação N:N entre relatórios e usuários especialistas.
- `papel` diferencia `responsavel` e `apoio`.
- Deve existir no máximo um responsável por relatório; pelo menos um é verificado na finalização.

### RELATORIO_ATENDIMENTO — RELATORIO_VERSAO

- `RELATORIO_VERSAO.id_relatorio` referencia RELATORIO_ATENDIMENTO.
- Um relatório pode possuir várias versões históricas.
- `id_editado_por` referencia USUARIO.
- As versões são cópias imutáveis do conteúdo anterior à edição.

### RELATORIO_ATENDIMENTO — RELATORIO_PDF

- `RELATORIO_PDF.id_relatorio` referencia RELATORIO_ATENDIMENTO.
- Um relatório pode gerar PDFs de versões diferentes.
- Cada PDF registra a versão, chave de armazenamento, hash e tamanho.

### ATENDIMENTO — EVIDENCIA

- `EVIDENCIA.id_atendimento` referencia ATENDIMENTO.
- Um atendimento pode possuir várias evidências.
- Evidência possui UUID próprio e upload independente do relatório.

### ATENDIMENTO — ASSINATURA

- `ASSINATURA.id_atendimento` referencia ATENDIMENTO.
- Um atendimento pode possuir assinaturas de cliente e especialista.
- `ASSINATURA.id_usuario` referencia USUARIO, mas é opcional para permitir assinatura de cliente sem conta.
- Para `tipo_assinante = especialista`, `id_usuario` é obrigatório e deve ser o especialista responsável do relatório.
- A assinatura preserva a versão do relatório e o hash do conteúdo assinado.

### ATENDIMENTO — ENVIO_COMUNICACAO

- `ENVIO_COMUNICACAO.id_atendimento` referencia ATENDIMENTO.
- O envio se relaciona a um agendamento ou a um PDF:
  - `id_agendamento` → AGENDAMENTO; ou
  - `id_relatorio_pdf` → RELATORIO_PDF.
- Exatamente uma das duas FKs deve estar preenchida, coerente com `tipo_comunicacao`.
- O canal é e-mail ou WhatsApp.

## 5. Formulários e satisfação

### ATENDIMENTO — PESQUISA_SATISFACAO

- `PESQUISA_SATISFACAO.id_atendimento` referencia ATENDIMENTO e é UNIQUE.
- Cada atendimento possui no máximo uma pesquisa.
- `id_usuario_respondente` → USUARIO é opcional, pois a resposta pode vir de link externo.

### PESQUISA_SATISFACAO — PERGUNTA_SATISFACAO — RESPOSTA_SATISFACAO

- `RESPOSTA_SATISFACAO.id_pesquisa` → PESQUISA_SATISFACAO.
- `RESPOSTA_SATISFACAO.id_pergunta` → PERGUNTA_SATISFACAO.
- Uma pesquisa possui respostas para perguntas cadastradas.
- UNIQUE `(id_pesquisa, id_pergunta)` impede resposta duplicada à mesma pergunta.

### ATENDIMENTO — AVALIACAO_FORMULARIO

- `AVALIACAO_FORMULARIO.id_atendimento` referencia ATENDIMENTO.
- A FK é UNIQUE: uma avaliação por atendimento.
- Essa avaliação de estrelas (1 a 5) é diferente da pesquisa CSAT (0 a 10).

### USUARIO / ATENDIMENTO — NOTIFICACAO

- `NOTIFICACAO.id_usuario` → USUARIO: destinatário obrigatório.
- `NOTIFICACAO.id_atendimento` → ATENDIMENTO: opcional para avisos de conta, como cadastro aprovado.
- Um usuário pode receber várias notificações.

### PERGUNTA_FORMULARIO — RESPOSTA_PERGUNTA_FORMULARIO — RESPOSTA_OPCAO

- `RESPOSTA_PERGUNTA_FORMULARIO.id_pergunta_form` → PERGUNTA_FORMULARIO.
- `RESPOSTA_PERGUNTA_FORMULARIO.id_atendimento` → ATENDIMENTO.
- UNIQUE `(id_atendimento, id_pergunta_form)` limita a uma resposta por pergunta em cada atendimento.
- `RESPOSTA_OPCAO.id_resposta` → RESPOSTA_PERGUNTA_FORMULARIO.
- RESPOSTA_OPCAO possui PK composta `(id_resposta, opcao_selecionada)`.

## 6. Segurança, auditoria e privacidade

### USUARIO / DISPOSITIVO — LOG_AUDITORIA

- `LOG_AUDITORIA.id_usuario` → USUARIO, opcional para eventos anônimos.
- `LOG_AUDITORIA.id_dispositivo` → DISPOSITIVO, opcional.
- A tabela é gravada pelo banco por função de auditoria; a aplicação não insere eventos diretamente.
- A cadeia de hashes liga cada evento ao anterior.

### USUARIO / DISPOSITIVO — TOKEN_ACESSO

- `TOKEN_ACESSO.id_usuario` → USUARIO, obrigatório.
- `TOKEN_ACESSO.id_dispositivo` → DISPOSITIVO, opcional para convite e redefinição de senha.
- `TOKEN_ACESSO.substituido_por` → TOKEN_ACESSO, autorrelacionamento opcional para rotação de refresh token.

### USUARIO — SOLICITACAO_TITULAR

- `SOLICITACAO_TITULAR.id_usuario` → USUARIO, opcional para titular sem conta.
- `id_responsavel` → USUARIO, opcional.

### USUARIO — INCIDENTE_SEGURANCA

- `INCIDENTE_SEGURANCA.id_registrado_por` → USUARIO, obrigatório.
- `id_responsavel` → USUARIO, opcional.

### USUARIO / DISTRIBUIDOR — DOMINIO_EMAIL_CORPORATIVO

- `DOMINIO_EMAIL_CORPORATIVO.id_distribuidor` → DISTRIBUIDOR, opcional conforme o tipo de organização.
- Domínios Netafim e de distribuidores são utilizados na validação de cadastro.

## 7. Integridade referencial e vínculos condicionais

Algumas regras não são expressas apenas pela presença de uma FK simples:

1. O usuário não pode estar vinculado simultaneamente a cliente e distribuidor.
2. A organização do usuário deve ser coerente com a FK preenchida.
3. Um atendimento de cliente final exige `id_cliente`.
4. Um atendimento Netafim exige `area_setor`.
5. Destinação a distribuidor exige `id_distribuidor`.
6. A propriedade do atendimento deve pertencer ao cliente do atendimento.
7. Subcategoria deve pertencer à categoria selecionada.
8. O especialista que assina deve ser o responsável pelo relatório.
9. Envio de comunicação deve apontar para exatamente um agendamento ou PDF.
10. Referências polimórficas, como `SYNC_CONFLITO.entidade_uuid`, não são FKs convencionais; sua consistência depende da aplicação e das regras do modelo.

## 8. Relações locais do Room/SQLite

O banco local possui relações próprias, sem replicar todo o modelo central:

- `ATENDIMENTO_LOCAL` 1:N `RELATORIO_LOCAL` (o relatório aponta para o atendimento).
- `ATENDIMENTO_LOCAL` 1:N `EVIDENCIA_LOCAL`.
- `ATENDIMENTO_LOCAL` 1:N `ASSINATURA_LOCAL`.
- `CLIENTE_LOCAL` 1:N `PROPRIEDADE_LOCAL`.
- `SYNC_OUTBOX.id_pai_outbox` estabelece dependência entre operações da fila.
- `SYNC_ESTADO` guarda o cursor por entidade.
- `CATALOGO_LOCAL` guarda catálogos versionados em JSON.
- `SYNC_OUTBOX.entidade_uuid` é uma referência polimórfica controlada pela aplicação, não uma FK convencional.

Consulte `modelo-banco-local-room.md` para os campos e o funcionamento offline.

## 9. Observação de manutenção

O DER representa a estrutura planejada. Antes de alterar ou implementar relacionamentos, comparar este documento com os scripts SQL e migrations efetivamente aplicados. Mudanças em PK, FK, cardinalidade ou nulabilidade devem ser refletidas no MER/DER, no dicionário e nos testes.
