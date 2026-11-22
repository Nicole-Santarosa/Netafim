# Modelo do banco local — Android Room / SQLite

> **Fonte principal:** `MER e DER.pdf`, páginas 16 e 45–47 (Anexo A.6), complementado pela `Documentação Técnica.pdf`, seções 36–48.
>
> **Escopo:** nove tabelas locais previstas para permitir que o técnico trabalhe sem conexão e sincronize posteriormente com a API.
>
> **Atenção ao status:** o MER/DER descreve o modelo local. A Documentação Técnica registra também decisões e propostas de implementação; nem todos os componentes citados nela devem ser considerados já implementados sem conferência no código.

## 1. Objetivo e princípios

O banco local Room/SQLite permite registrar e consultar dados necessários durante o atendimento em campo sem internet. Os registros são armazenados no aparelho e enviados ao servidor quando a conexão retorna.

Princípios documentados:

- UUID gerado no aparelho para registros locais que precisam de identidade antes de chegar ao servidor.
- O UUID de atendimento é armazenado como `TEXT` no SQLite/Room.
- O protocolo pode permanecer nulo enquanto o atendimento não sincroniza; o servidor atribui o número `OS-####`.
- O status local de sincronização não deve ser confundido com o status de negócio do atendimento no servidor.
- `SYNC_OUTBOX` mantém a fila de operações pendentes.
- `SYNC_ESTADO` guarda o cursor de sincronização por entidade.
- Evidências e assinaturas mantêm caminhos locais até que o envio seja confirmado.
- O banco local não substitui o banco central e não contém todas as tabelas do PostgreSQL.

## 2. Tabelas locais

### 2.1 ATENDIMENTO_LOCAL

Cópia local do atendimento em execução, com o mesmo UUID do servidor.

| Campo | Tipo SQLite/Room | Chave / nulabilidade | Descrição |
|---|---|---|---|
| id_atendimento | TEXT | PK, obrigatório | UUID do atendimento |
| numero_protocolo | TEXT | NULL permitido | Nulo até o servidor atribuir o protocolo |
| dados_json | TEXT | Obrigatório | Dados do atendimento em JSON |
| origem_registro | TEXT | Obrigatório | `local` ou `servidor` |
| status_local | TEXT | Obrigatório | `sincronizado`, `pendente_envio`, `conflito` ou `erro` |
| versao_base | INTEGER | Obrigatório no quadro do MER/DER | Última versão conhecida; em registro novo pode ser 0/nula conforme contrato de sincronização |
| sync_seq_servidor | INTEGER | NULL permitido | `sync_seq` do registro recebido do servidor |
| data_criacao_local | INTEGER | Obrigatório | Data/hora local |

**Observações complementares da Documentação Técnica:**

- `dados_json` deve armazenar o JSON do atendimento no formato devolvido pela API, preservando `snake_case`.
- A API inclui `versao`, utilizada para controle otimista de concorrência.
- `sync_seq_servidor` é o valor do registro; o cursor global do pull deve ser guardado separadamente em `SYNC_ESTADO` (ou mecanismo local equivalente definido pela implementação).
- Não misturar `status_local` com o status de negócio do atendimento (`aberto`, `destinado`, `agendado` etc.).

### 2.2 RELATORIO_LOCAL

Rascunho do relatório preenchido sem internet, com os especialistas participantes.

| Campo | Tipo SQLite/Room | Chave / nulabilidade | Descrição |
|---|---|---|---|
| id_relatorio | TEXT | PK, obrigatório | Identificador do relatório |
| id_atendimento | TEXT | FK → ATENDIMENTO_LOCAL, obrigatório | Atendimento relacionado |
| dados_json | TEXT | Obrigatório | Conteúdo do relatório em JSON |
| especialistas_json | TEXT | Obrigatório | Especialistas participantes em JSON |
| status_local | TEXT | Obrigatório | Estado local do relatório |
| versao_base | INTEGER | Obrigatório | Versão conhecida para controle de concorrência |
| data_atualizacao_local | INTEGER | Obrigatório | Última atualização local |

### 2.3 EVIDENCIA_LOCAL

Foto, vídeo ou documento mantido na área privada do aplicativo até a confirmação do envio.

| Campo | Tipo SQLite/Room | Chave / nulabilidade | Descrição |
|---|---|---|---|
| id_evidencia | TEXT | PK, obrigatório | UUID da evidência |
| id_atendimento | TEXT | FK → ATENDIMENTO_LOCAL, obrigatório | Atendimento relacionado |
| etapa | TEXT | Obrigatório | Etapa da evidência |
| caminho_arquivo_local | TEXT | Obrigatório | Caminho local do arquivo |
| hash_arquivo | TEXT | Obrigatório | Hash para integridade |
| status_upload | TEXT | Obrigatório | Estado do upload |
| latitude | REAL | NULL permitido | Coordenada opcional |
| longitude | REAL | NULL permitido | Coordenada opcional |

O binário fica no armazenamento privado do aplicativo; o banco guarda o caminho local e metadados.

### 2.4 ASSINATURA_LOCAL

Assinatura capturada na tela, preservando versão e hash do relatório assinado.

| Campo | Tipo SQLite/Room | Chave / nulabilidade | Descrição |
|---|---|---|---|
| id_assinatura | TEXT | PK, obrigatório | UUID da assinatura |
| id_atendimento | TEXT | FK → ATENDIMENTO_LOCAL, obrigatório | Atendimento relacionado |
| tipo_assinante | TEXT | Obrigatório | Tipo de assinante |
| forma_assinatura | TEXT | Obrigatório | Forma de assinatura |
| caminho_imagem_local | TEXT | NULL permitido | Caminho da imagem da assinatura |
| versao_relatorio_assinada | INTEGER | Obrigatório | Versão assinada |
| conteudo_hash | TEXT | Obrigatório | Hash do conteúdo assinado |

### 2.5 SYNC_OUTBOX

Fila local de operações a enviar ao servidor. `id_pai_outbox` permite controlar dependências, por exemplo, enviar o atendimento antes de sua evidência.

| Campo | Tipo SQLite/Room | Chave / nulabilidade | Descrição |
|---|---|---|---|
| id_outbox | INTEGER | PK, obrigatório | Identificador da operação na fila |
| entidade_tipo | TEXT | Obrigatório | Tipo da entidade |
| entidade_uuid | TEXT | Obrigatório | UUID do registro relacionado |
| operacao | TEXT | Obrigatório | Tipo da operação |
| payload_json | TEXT | Obrigatório | Conteúdo a enviar |
| versao_base | INTEGER | NULL permitido | Versão base conhecida |
| id_pai_outbox | INTEGER | NULL permitido | Dependência de outra operação da fila |
| status | TEXT | Obrigatório | Estado da operação |
| tentativas | INTEGER | Obrigatório | Número de tentativas |
| ultimo_erro | TEXT | NULL permitido | Último erro |
| data_criacao | INTEGER | Obrigatório | Criação local |
| data_ultima_tentativa | INTEGER | NULL permitido | Última tentativa |

**Integridade:** a referência `entidade_tipo` + `entidade_uuid` é polimórfica e controlada pela aplicação; não é FK convencional. `id_pai_outbox` representa a dependência entre operações.

A Documentação Técnica posterior também descreve uma proposta de identificador único de operação (`id_operacao`) para reenvio idempotente. Esse campo não aparece no quadro de nove tabelas do Anexo A.6; deve ser incluído no modelo Room somente após validar o contrato de sincronização aprovado.

### 2.6 SYNC_ESTADO

Guarda o último cursor recebido do servidor para cada entidade, permitindo pull incremental.

| Campo | Tipo SQLite/Room | Chave / nulabilidade | Descrição |
|---|---|---|---|
| entidade | TEXT | PK, obrigatório | Identificação da entidade |
| ultimo_sync_seq | INTEGER | Obrigatório | Último cursor confirmado |
| data_ultima_sync | INTEGER | Obrigatório | Data/hora da última sincronização |

O cursor global não deve ser confundido com `sync_seq_servidor` armazenado em cada registro de atendimento.

### 2.7 CATALOGO_LOCAL

Catálogos versionados disponíveis offline, como culturas, categorias e perguntas configuráveis.

| Campo | Tipo SQLite/Room | Chave / nulabilidade | Descrição |
|---|---|---|---|
| chave | TEXT | PK, obrigatório | Identificador do catálogo |
| versao | INTEGER | Obrigatório | Versão do catálogo |
| dados_json | TEXT | Obrigatório | Dados do catálogo em JSON |
| baixado_em | INTEGER | Obrigatório | Data/hora do download |

### 2.8 CLIENTE_LOCAL

Dados mínimos dos clientes da carteira do técnico para abertura e execução de atendimentos.

| Campo | Tipo SQLite/Room | Chave / nulabilidade | Descrição |
|---|---|---|---|
| id_cliente | INTEGER | PK, obrigatório | Identificador do cliente no servidor |
| nome | TEXT | Obrigatório | Nome |
| id_distribuidor | INTEGER | NULL permitido | Identificador do distribuidor |
| sync_seq | INTEGER | Obrigatório | Sequência de sincronização do registro |

### 2.9 PROPRIEDADE_LOCAL

Fazendas dos clientes da carteira do técnico, com culturas armazenadas em JSON.

| Campo | Tipo SQLite/Room | Chave / nulabilidade | Descrição |
|---|---|---|---|
| id_propriedade | INTEGER | PK, obrigatório | Identificador da propriedade no servidor |
| id_cliente | INTEGER | FK → CLIENTE_LOCAL, obrigatório | Cliente proprietário |
| nome_fazenda | TEXT | Obrigatório | Nome da fazenda |
| localizacao | TEXT | Obrigatório | Localização |
| culturas_json | TEXT | Obrigatório | Culturas em JSON |
| sync_seq | INTEGER | Obrigatório | Sequência de sincronização |

## 3. Relacionamentos locais

| Origem | Destino | Cardinalidade / vínculo |
|---|---|---|
| ATENDIMENTO_LOCAL | RELATORIO_LOCAL | 1:N no DER; relatório referencia atendimento |
| ATENDIMENTO_LOCAL | EVIDENCIA_LOCAL | 1:N |
| ATENDIMENTO_LOCAL | ASSINATURA_LOCAL | 1:N |
| CLIENTE_LOCAL | PROPRIEDADE_LOCAL | 1:N |
| SYNC_OUTBOX | SYNC_OUTBOX | Autorrelacionamento lógico por `id_pai_outbox` |
| SYNC_OUTBOX | Entidade local | Referência polimórfica por tipo + UUID, controlada pela aplicação |
| SYNC_ESTADO | Entidade sincronizada | Guarda cursor por entidade, sem FK para cada tabela |
| CATALOGO_LOCAL | Catálogo remoto | Conteúdo versionado armazenado em JSON; não há FK descrita |

> O DER indica que SYNC_OUTBOX não possui FK convencional para a entidade porque sua referência é polimórfica. A aplicação deve validar a existência e a ordem das operações.

## 4. Estados locais e estados de negócio

A Documentação Técnica define os valores de `statusLocal` de atendimento:

- `sincronizado`
- `pendente_envio`
- `conflito`
- `erro`

Define também `origemRegistro`:

- `local`
- `servidor`

Esses campos descrevem a sincronização e a origem do registro. Não são o status de negócio do atendimento no servidor.

O status de negócio é mantido nos dados da API, com estados como `aberto`, `destinado`, `agendado`, `em_atendimento`, `resolvido`, `encerrado`, `encerrado_sem_avaliacao` e `cancelado`.

## 5. Fluxo operacional de sincronização descrito

1. O técnico cria ou altera o atendimento localmente.
2. A operação é registrada na fila local.
3. Operações dependentes respeitam a ordem: atendimento antes de evidências relacionadas.
4. Quando houver conexão, o aplicativo envia as operações à API.
5. O aplicativo interpreta o resultado de cada operação e atualiza o estado local.
6. O aplicativo consulta alterações do servidor por pull incremental.
7. O registro local é atualizado com a versão e a sequência recebidas.
8. Evidências binárias são enviadas separadamente, conforme o contrato técnico.

A proposta de contrato v0.1 descreve PUSH antes de PULL. O contrato também registra que endpoints de sincronização ainda precisavam ser implementados/confirmados naquela edição.

## 6. Controle de versão, conflitos e idempotência

- `versao_base` representa a versão que o aplicativo conhecia ao editar.
- A API compara a versão enviada com a versão atual no servidor.
- Versão divergente gera conflito, em vez de sobrescrever silenciosamente a versão do servidor.
- O conteúdo enviado pelo cliente deve ser preservado para permitir revisão/reaplicação.
- A proposta técnica recomenda reutilizar o mesmo identificador de operação em reenvios, evitando duplicação lógica.
- HTTP 409 não deve ser tratado automaticamente como sucesso: o campo `codigo` diferencia `conflito_de_versao` de `duplicado`.
- Para duplicidade em POST de criação, a proposta orienta confirmar o registro por GET antes de considerar o reenvio concluído.

A política exata de resolução de conflitos e o contrato definitivo devem seguir a versão aprovada pela equipe, não apenas a proposta v0.1.

## 7. Migrações do Room

A Documentação Técnica informa que o Room estava na versão 1 com `exportSchema = false` no momento registrado. Qualquer alteração estrutural exige:

- Incrementar a versão do banco.
- Implementar a migração correspondente.
- Preservar os dados existentes.
- Validar a migração em testes.

A proposta registrada era migrar da versão 1 para a versão 2 ao adicionar estruturas de fila e conflitos. Conferir o estado atual do código antes de aplicar essa indicação.

## 8. Cuidados de implementação

1. Não armazenar senhas, tokens de acesso ou segredos em texto claro no banco local.
2. Manter arquivos de evidência no armazenamento privado do aplicativo e guardar apenas o caminho e metadados no banco.
3. Preservar o JSON da API no formato `snake_case`, conforme confirmação posterior registrada na Documentação Técnica.
4. Não sobrescrever registros locais com alterações do servidor quando houver operação local pendente sem antes aplicar a política de conflito.
5. Atualizar `SYNC_ESTADO` somente após confirmar a aplicação dos itens recebidos no pull.
6. Tratar falhas e reenvios de forma idempotente.
7. Não confundir cursor global de pull com `sync_seq` individual de registro.
8. Conferir no código as entidades, índices, foreign keys, conversores e migrations realmente implementados.

## 9. Limites e pendências documentais

- O modelo de nove tabelas é o modelo local apresentado no MER/DER.
- A proposta técnica de sincronização menciona campos e estruturas adicionais, como `id_operacao` e cópia do registro do servidor em conflito. Esses itens não devem ser tratados como parte do esquema local aprovado sem validação.
- O documento de sincronização v0.1 lista decisões A–I pendentes, inclusive idempotência no servidor, política de conflito, cursor, campos editáveis, resolução administrativa, upload, escopo da versão e responsável pela implementação.
- Conferir a implementação atual antes de afirmar que qualquer endpoint ou mecanismo está concluído.
