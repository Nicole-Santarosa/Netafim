# Dicionário de dados — Netafim

> **Fonte principal:** `MER e DER.pdf`, versão para apresentação de 29/09/2026, especialmente Anexo A (páginas 24–47).
>
> **Status:** documentação derivada do modelo conceitual/lógico apresentado. Não substitui a conferência do schema SQL efetivamente aplicado no banco.
>
> **Escopo:** 41 tabelas do banco central PostgreSQL. O banco local Room/SQLite está documentado separadamente em `modelo-banco-local-room.md`.

## Convenções

- **PK:** chave primária.
- **FK:** chave estrangeira.
- **PK/FK:** participa da chave primária e também referencia outra tabela.
- **Obrigatório:** campo não nulo no modelo.
- **Opcional:** campo aceita `NULL`.
- `INT IDENTITY` representa identificador inteiro gerado pelo banco.
- `UUID` representa identificador universal. Em atendimentos, relatórios, evidências e assinaturas, pode ser gerado no dispositivo.
- `TIMESTAMPTZ` corresponde a `TIMESTAMP WITH TIME ZONE`.
- `VCHAR` utilizado nas páginas-resumo do DER corresponde a `VARCHAR`.
- Colunas técnicas como `data_criacao`, `data_atualizacao`, `deletado_em` e `sync_seq` são descritas explicitamente quando previstas.

> Os tipos e nulabilidades abaixo reproduzem o documento MER/DER. Quando a documentação não informa uma regra mais específica, isso é indicado em vez de presumir uma constraint.

## 1. Acesso, perfis e organizações

### PERFIL

Representa os seis perfis da plataforma: Netafim, Distribuidor e Cliente, cada um nos níveis Administrador e Usuário.

| Campo | Tipo | Obrig. | Chave / observação |
|---|---|---:|---|
| id_perfil | INT IDENTITY | Sim | PK |
| nome_perfil | VARCHAR(50) | Sim | UNIQUE; nome do perfil |
| tipo_organizacao | VARCHAR(15) | Sim | `netafim`, `distribuidor` ou `cliente` |
| nivel | VARCHAR(10) | Sim | `admin` ou `usuario` |
| descricao | TEXT | Não | Descrição |

Sincronização: somente servidor. Dado pessoal: não.

### PERMISSAO

Permissões atômicas do sistema. O campo `codigo` corresponde ao codename de Permission do Django.

| Campo | Tipo | Obrig. | Chave / observação |
|---|---|---:|---|
| id_permissao | INT IDENTITY | Sim | PK |
| codigo | VARCHAR(50) | Sim | UNIQUE; ex.: `aprovar_cadastro`, `vincular_tecnico`, `encerrar_atendimento` |
| descricao | VARCHAR(150) | Sim | Descrição |

Sincronização: somente servidor. Dado pessoal: não.

### PERFIL_PERMISSAO

Tabela associativa da relação N:N entre PERFIL e PERMISSAO.

| Campo | Tipo | Obrig. | Chave / observação |
|---|---|---:|---|
| id_perfil | INT | Sim | PK/FK → PERFIL |
| id_permissao | INT | Sim | PK/FK → PERMISSAO |

PK composta: `(id_perfil, id_permissao)`. Sincronização: somente servidor.

### USUARIO

Conta de acesso. O cadastro nasce pendente, sem perfil nem organização; o Netafim Admin aprova e define o vínculo e o perfil.

| Campo | Tipo | Obrig. | Chave / observação |
|---|---|---:|---|
| id_usuario | INT IDENTITY | Sim | PK |
| nome | VARCHAR(150) | Sim | Nome |
| email | VARCHAR(150) | Sim | Único entre usuários não excluídos, sem diferenciar maiúsculas/minúsculas |
| telefone | VARCHAR(20) | Não | Contato; usado em envio por WhatsApp |
| password | VARCHAR(128) | Sim | Hash gerenciado pelo Django; aceita formato de hash ou senha inutilizada |
| last_login | TIMESTAMPTZ | Não | Último login |
| situacao | VARCHAR(15) | Sim | `pendente`, `ativo`, `recusado`, `inativo` ou `bloqueado` |
| falhas_login | SMALLINT | Sim | Tentativas consecutivas; zera após login bem-sucedido |
| bloqueado_ate | TIMESTAMPTZ | Não | Fim do bloqueio temporário |
| mfa_ativo | BOOLEAN | Sim | Indica MFA ativo; segredo fica fora desta tabela |
| senha_alterada_em | TIMESTAMPTZ | Não | Preenchido por gatilho quando o hash muda |
| exige_troca_senha | BOOLEAN | Sim | Exige troca quando senha foi entregue por administrador |
| tipo_organizacao | VARCHAR(15) | Não | `netafim`, `distribuidor` ou `cliente`; definido na aprovação |
| id_perfil | INT | Não | FK → PERFIL; nulo enquanto pendente/recusado |
| id_distribuidor | INT | Não | FK → DISTRIBUIDOR; usado para organização distribuidor |
| id_cliente | INT | Não | FK → CLIENTE; usado para organização cliente |
| id_aprovado_por | INT | Não | FK autorreferente → USUARIO; administrador que aprovou/recusou |
| data_aprovacao | TIMESTAMPTZ | Não | Data da decisão |
| consentimento_lgpd | BOOLEAN | Sim | Estado do consentimento |
| data_consentimento | TIMESTAMPTZ | Não | Data do aceite |
| versao_termo_consentimento | VARCHAR(20) | Não | Versão do aviso aceito |
| data_revogacao_consentimento | TIMESTAMPTZ | Não | Data de revogação |
| data_exclusao_solicitada | TIMESTAMPTZ | Não | Data da solicitação de exclusão |
| data_criacao | TIMESTAMPTZ | Sim | Coluna técnica |
| data_atualizacao | TIMESTAMPTZ | Sim | Atualizada por gatilho |
| deletado_em | TIMESTAMPTZ | Não | Exclusão lógica |

Regras principais: `id_cliente` e `id_distribuidor` não podem estar preenchidos simultaneamente; `tipo_organizacao` deve corresponder ao vínculo; `situacao = ativo` exige perfil e tipo de organização. Usuários Netafim e Distribuidor dependem de domínio corporativo ativo. Sincronização: somente servidor. Contém dados pessoais.

### DISTRIBUIDOR

Parceiro comercial responsável por região e carteira de clientes.

| Campo | Tipo | Obrig. | Chave / observação |
|---|---|---:|---|
| id_distribuidor | INT IDENTITY | Sim | PK |
| nome | VARCHAR(150) | Sim | Nome |
| regiao | VARCHAR(100) | Sim | Região |
| email | VARCHAR(150) | Não | Contato |
| telefone | VARCHAR(20) | Não | Contato |
| status | VARCHAR(20) | Sim | `ativo` ou `inativo` |
| data_criacao | TIMESTAMPTZ | Sim | Recebido no servidor |
| data_atualizacao | TIMESTAMPTZ | Sim | Atualizada por gatilho |
| deletado_em | TIMESTAMPTZ | Não | Exclusão lógica |
| sync_seq | BIGINT | Sim | Cursor global atribuído por gatilho |

Sincronização: sim, por `sync_seq`.

### CLIENTE

Organização cliente (empresa ou produtor). A aprovação de acesso é da pessoa/USUARIO, não da empresa.

| Campo | Tipo | Obrig. | Chave / observação |
|---|---|---:|---|
| id_cliente | INT IDENTITY | Sim | PK |
| nome | VARCHAR(150) | Sim | Empresa ou produtor |
| email | VARCHAR(150) | Não | Contato |
| telefone | VARCHAR(20) | Não | Contato |
| id_distribuidor | INT | Não | FK → DISTRIBUIDOR; distribuidor da carteira, quando houver |
| status | VARCHAR(20) | Sim | `ativo` ou `inativo` |
| data_criacao | TIMESTAMPTZ | Sim | Recebido no servidor |
| data_atualizacao | TIMESTAMPTZ | Sim | Atualizada por gatilho |
| deletado_em | TIMESTAMPTZ | Não | Exclusão lógica |
| sync_seq | BIGINT | Sim | Cursor global atribuído por gatilho |

Sincronização: sim, por `sync_seq`.

### PROPRIEDADE

Fazenda pertencente a um cliente. Uma propriedade pode ter várias culturas por meio de PROPRIEDADE_CULTURA.

| Campo | Tipo | Obrig. | Chave / observação |
|---|---|---:|---|
| id_propriedade | INT IDENTITY | Sim | PK |
| id_cliente | INT | Sim | FK → CLIENTE |
| nome_fazenda | VARCHAR(150) | Sim | Nome |
| localizacao | VARCHAR(200) | Sim | Texto de localização |
| latitude | NUMERIC(9,6) | Não | Coordenada opcional |
| longitude | NUMERIC(9,6) | Não | Coordenada opcional |
| data_criacao | TIMESTAMPTZ | Sim | Recebido no servidor |
| data_atualizacao | TIMESTAMPTZ | Sim | Atualizada por gatilho |
| deletado_em | TIMESTAMPTZ | Não | Exclusão lógica |
| sync_seq | BIGINT | Sim | Cursor global atribuído por gatilho |

Regra: UNIQUE `(id_propriedade, id_cliente)`, usado pela FK composta de ATENDIMENTO para garantir que a propriedade pertença ao cliente informado.

### CULTURA

Catálogo de culturas agrícolas.

| Campo | Tipo | Obrig. | Chave / observação |
|---|---|---:|---|
| id_cultura | INT IDENTITY | Sim | PK |
| nome | VARCHAR(50) | Sim | Ex.: Cacau, Café, Cana-de-açúcar, Citrus, Grãos, Outros |
| data_criacao | TIMESTAMPTZ | Sim | Recebido no servidor |
| data_atualizacao | TIMESTAMPTZ | Sim | Atualizada por gatilho |
| deletado_em | TIMESTAMPTZ | Não | Exclusão lógica |
| sync_seq | BIGINT | Sim | Cursor global atribuído por gatilho |

### PROPRIEDADE_CULTURA

Associação N:N entre PROPRIEDADE e CULTURA.

| Campo | Tipo | Obrig. | Chave / observação |
|---|---|---:|---|
| id_propriedade | INT | Sim | PK/FK → PROPRIEDADE |
| id_cultura | INT | Sim | PK/FK → CULTURA |
| data_atualizacao | TIMESTAMPTZ | Sim | Atualizada por gatilho |
| deletado_em | TIMESTAMPTZ | Não | Exclusão lógica |
| sync_seq | BIGINT | Sim | Cursor global |

PK composta: `(id_propriedade, id_cultura)`.

### DISPOSITIVO

Aparelho Android autorizado de um usuário.

| Campo | Tipo | Obrig. | Chave / observação |
|---|---|---:|---|
| id_dispositivo | UUID | Sim | PK; gerado no aparelho na primeira execução |
| id_usuario | INT | Sim | FK → USUARIO |
| modelo | VARCHAR(80) | Não | Modelo do aparelho |
| versao_app | VARCHAR(20) | Não | Versão do aplicativo |
| ultima_sincronizacao | TIMESTAMPTZ | Não | Última sincronização |
| revogado_em | TIMESTAMPTZ | Não | Revogação do aparelho |
| limpeza_remota_solicitada_em | TIMESTAMPTZ | Não | Solicitação de limpeza remota |
| limpeza_remota_confirmada_em | TIMESTAMPTZ | Não | Confirmação da limpeza |
| data_criacao | TIMESTAMPTZ | Sim | Criação |

Sincronização: somente servidor.

### SYNC_CONFLITO

Armazena uma escrita recusada por versão antiga, preservando o payload enviado para resolução posterior.

| Campo | Tipo | Obrig. | Chave / observação |
|---|---|---:|---|
| id_conflito | UUID | Sim | PK |
| id_dispositivo | UUID | Não | FK → DISPOSITIVO |
| entidade_tipo | VARCHAR(30) | Sim | Tipo da entidade |
| entidade_uuid | UUID | Sim | Referência polimórfica; integridade controlada pela aplicação |
| versao_servidor | INT | Sim | Versão vigente no servidor |
| versao_cliente | INT | Sim | Versão conhecida pelo cliente |
| payload_cliente | JSONB | Sim | Conteúdo enviado pelo cliente |
| status | VARCHAR(20) | Sim | `aberto`, `resolvido_servidor`, `resolvido_cliente` ou `descartado` |
| id_resolvido_por | INT | Não | FK → USUARIO |
| data_criacao | TIMESTAMPTZ | Sim | Criação |
| resolvido_em | TIMESTAMPTZ | Não | Data da resolução |

Sincronização: somente servidor. O payload pode conter dados pessoais.

## 2. Atendimento, agenda e classificação

### ATENDIMENTO

Entidade central do sistema. O UUID é gerado no dispositivo. O protocolo é atribuído pelo servidor.

| Campo | Tipo | Obrig. | Chave / observação |
|---|---|---:|---|
| id_atendimento | UUID | Sim | PK |
| numero_protocolo | VARCHAR(20) | Sim | UNIQUE; `OS-####`, atribuído pelo servidor e imutável |
| origem | VARCHAR(20) | Sim | `netafim`, `distribuidor` ou `cliente_final` |
| motivo | VARCHAR(30) | Sim | `digital_farming`, `duvidas`, `entrega_tecnica`, `pos_venda`, `programacao_feiras`, `treinamento` |
| status | VARCHAR(25) | Sim | Máquina de estados documentada na política de integridade |
| prioridade | VARCHAR(10) | Sim | `baixa`, `media` ou `alta`; a confirmar com a Netafim |
| id_solicitante | INT | Sim | FK → USUARIO |
| id_cliente | INT | Não | FK → CLIENTE; obrigatório quando `origem = cliente_final` |
| id_propriedade | INT | Não | FK composta com id_cliente → PROPRIEDADE; opcional |
| nome_produtor | VARCHAR(150) | Não | Produtor informado em texto |
| nome_fazenda_informado | VARCHAR(150) | Não | Fazenda informada em texto |
| cidade | VARCHAR(100) | Não | Cidade informada |
| area_setor | VARCHAR(20) | Não | Obrigatório para origem Netafim; `comercial`, `engenharia`, `instalacao`, `marketing`, `pos_venda` |
| id_distribuidor_origem | INT | Não | FK → DISTRIBUIDOR |
| destinado_a | VARCHAR(15) | Não | `netafim` ou `distribuidor` |
| id_distribuidor | INT | Não | FK → DISTRIBUIDOR; obrigatório se destinado_a = distribuidor |
| id_tecnico_responsavel | INT | Não | FK → USUARIO; técnico alocado |
| contato_solicitante | VARCHAR(20) | Sim | Contato |
| numero_projeto_br | VARCHAR(30) | Não | Número do projeto |
| situacao_projeto | VARCHAR(20) | Não | `garantia`, `fora_garantia`, `instalacao` |
| estado_projeto | VARCHAR(20) | Não | `operando`, `nao_operando` |
| tipo_venda | VARCHAR(20) | Não | Digital Farming: `projeto`, `venda_regular` |
| tipo_atendimento | VARCHAR(15) | Não | `online`, `presencial` |
| descricao_problema | TEXT | Não | Descrição do problema/dúvida |
| data_inicio_problema | DATE | Não | Data de início |
| data_sugerida | DATE | Não | Data sugerida |
| necessidade | TEXT | Não | Necessidade descrita |
| qtd_participantes_prevista | SMALLINT | Não | Treinamento; maior que zero |
| snapshot_cliente | JSONB | Não | Dados do cliente congelados na abertura |
| snapshot_propriedade | JSONB | Não | Dados da propriedade congelados na abertura |
| data_abertura | TIMESTAMPTZ | Sim | Relógio do aparelho |
| data_conclusao | TIMESTAMPTZ | Não | Data da resolução |
| data_encerramento | TIMESTAMPTZ | Não | Data do encerramento |
| versao | INT | Sim | Concorrência otimista; incrementada por gatilho |
| id_atualizado_por | INT | Não | FK → USUARIO; nulo em alteração feita pelo sistema |
| data_criacao | TIMESTAMPTZ | Sim | Recebimento no servidor |
| data_atualizacao | TIMESTAMPTZ | Sim | Atualizada por gatilho |
| deletado_em | TIMESTAMPTZ | Não | Exclusão lógica |
| sync_seq | BIGINT | Sim | Cursor global atribuído por gatilho |

Regras: origem cliente_final exige cliente; origem Netafim exige área/setor válido; destinação distribuidor exige distribuidor; propriedade preenchida deve pertencer ao cliente; protocolo é gerado e imutável no servidor.

### HISTORICO_ATENDIMENTO

Trilha cronológica de eventos do atendimento. Somente inserção; gatilho impede edição e exclusão.

| Campo | Tipo | Obrig. | Chave / observação |
|---|---|---:|---|
| id_historico | BIGINT IDENTITY | Sim | PK |
| id_atendimento | UUID | Sim | FK → ATENDIMENTO |
| tipo_evento | VARCHAR(30) | Sim | `criado`, `protocolo_gerado`, `destinado`, `atribuido`, `agendado`, `reagendado`, `status_alterado`, `reclassificado`, `editado_pos_finalizacao`, `excluido` |
| origem_evento | VARCHAR(10) | Sim | `usuario` ou `sistema` |
| valor_anterior | VARCHAR(100) | Não | Valor anterior |
| valor_novo | VARCHAR(100) | Sim | Novo valor |
| id_usuario_responsavel | INT | Não | FK → USUARIO; obrigatório para origem usuario e nulo para sistema |
| data_evento | TIMESTAMPTZ | Sim | Data/hora |
| observacao | TEXT | Não | Observação |

### AGENDAMENTO

Cada reagendamento cria nova linha e cancela a anterior.

| Campo | Tipo | Obrig. | Chave / observação |
|---|---|---:|---|
| id_agendamento | INT IDENTITY | Sim | PK |
| id_atendimento | UUID | Sim | FK → ATENDIMENTO |
| data_hora_agendada | TIMESTAMPTZ | Sim | Data/hora marcada |
| status | VARCHAR(15) | Sim | `ativo`, `cancelado`, `realizado` |
| id_criado_por | INT | Sim | FK → USUARIO |
| data_criacao | TIMESTAMPTZ | Sim | Recebimento no servidor |
| data_atualizacao | TIMESTAMPTZ | Sim | Atualizada por gatilho |
| deletado_em | TIMESTAMPTZ | Não | Exclusão lógica |
| sync_seq | BIGINT | Sim | Cursor global |

Regra: no máximo um agendamento ativo por atendimento (índice único parcial).

### PROBLEMA_CATEGORIA

Catálogo de categorias de problema.

| Campo | Tipo | Obrig. | Chave / observação |
|---|---|---:|---|
| id_categoria | INT IDENTITY | Sim | PK |
| nome | VARCHAR(50) | Sim | Nome da categoria |
| aplicavel_a | VARCHAR(20) | Sim | `digital_farming` ou `pos_venda` |
| ordem | SMALLINT | Sim | Ordem de exibição |
| ativa | BOOLEAN | Sim | Ativa/inativa |
| data_criacao | TIMESTAMPTZ | Sim | Criação |
| data_atualizacao | TIMESTAMPTZ | Sim | Atualizada por gatilho |
| deletado_em | TIMESTAMPTZ | Não | Exclusão lógica |
| sync_seq | BIGINT | Sim | Cursor global |

### PROBLEMA_SUBCATEGORIA

Itens específicos de cada categoria.

| Campo | Tipo | Obrig. | Chave / observação |
|---|---|---:|---|
| id_subcategoria | INT IDENTITY | Sim | PK |
| id_categoria | INT | Sim | FK → PROBLEMA_CATEGORIA |
| descricao | VARCHAR(150) | Sim | Descrição |
| ordem | SMALLINT | Sim | Ordem |
| ativa | BOOLEAN | Sim | Ativa/inativa |
| data_criacao | TIMESTAMPTZ | Sim | Criação |
| data_atualizacao | TIMESTAMPTZ | Sim | Atualizada por gatilho |
| deletado_em | TIMESTAMPTZ | Não | Exclusão lógica |
| sync_seq | BIGINT | Sim | Cursor global |

Regra: UNIQUE `(id_subcategoria, id_categoria)`, utilizado pela FK composta de ATENDIMENTO_PROBLEMA.

### ATENDIMENTO_PROBLEMA

Classificação multisseleção de categorias/subcategorias por atendimento.

| Campo | Tipo | Obrig. | Chave / observação |
|---|---|---:|---|
| id_atendimento_problema | INT IDENTITY | Sim | PK |
| id_atendimento | UUID | Sim | FK → ATENDIMENTO |
| id_categoria | INT | Sim | FK → PROBLEMA_CATEGORIA |
| id_subcategoria | INT | Não | FK composta → PROBLEMA_SUBCATEGORIA; nulo quando categoria não possui subitens |
| data_criacao | TIMESTAMPTZ | Sim | Recebimento no servidor |
| data_atualizacao | TIMESTAMPTZ | Sim | Atualizada por gatilho |
| deletado_em | TIMESTAMPTZ | Não | Exclusão lógica |
| sync_seq | BIGINT | Sim | Cursor global |

A subcategoria, quando preenchida, deve pertencer à categoria informada. A categoria deve ser compatível com o motivo do atendimento. A combinação atendimento/categoria/subcategoria não se repete.

### ATENDIMENTO_CULTURA

Associação N:N entre ATENDIMENTO e CULTURA.

| Campo | Tipo | Obrig. | Chave / observação |
|---|---|---:|---|
| id_atendimento | UUID | Sim | PK/FK → ATENDIMENTO |
| id_cultura | INT | Sim | PK/FK → CULTURA |
| data_atualizacao | TIMESTAMPTZ | Sim | Atualizada por gatilho |
| deletado_em | TIMESTAMPTZ | Não | Exclusão lógica |
| sync_seq | BIGINT | Sim | Cursor global |

PK composta: `(id_atendimento, id_cultura)`.

### TIPO_TREINAMENTO

Catálogo dos tipos de treinamento.

| Campo | Tipo | Obrig. | Chave / observação |
|---|---|---:|---|
| id_tipo_treinamento | INT IDENTITY | Sim | PK |
| nome | VARCHAR(50) | Sim | Ex.: Agronômico, Digital Farming, Manutenção Preventiva, Operação/Manutenção, Padronização de Instalação |
| data_criacao | TIMESTAMPTZ | Sim | Criação |
| data_atualizacao | TIMESTAMPTZ | Sim | Atualizada por gatilho |
| deletado_em | TIMESTAMPTZ | Não | Exclusão lógica |
| sync_seq | BIGINT | Sim | Cursor global |

### ATENDIMENTO_TIPO_TREINAMENTO

Associação N:N entre atendimento e tipo de treinamento.

| Campo | Tipo | Obrig. | Chave / observação |
|---|---|---:|---|
| id_atendimento | UUID | Sim | PK/FK → ATENDIMENTO |
| id_tipo_treinamento | INT | Sim | PK/FK → TIPO_TREINAMENTO |
| data_atualizacao | TIMESTAMPTZ | Sim | Atualizada por gatilho |
| deletado_em | TIMESTAMPTZ | Não | Exclusão lógica |
| sync_seq | BIGINT | Sim | Cursor global |

PK composta: `(id_atendimento, id_tipo_treinamento)`.

## 3. Relatórios, evidências e comunicação

### RELATORIO_ATENDIMENTO

Relatório de resolução. Nasce como rascunho e só é finalizado após validação dos campos, especialista, evidências e assinaturas exigidas.

| Campo | Tipo | Obrig. | Chave / observação |
|---|---|---:|---|
| id_relatorio | UUID | Sim | PK |
| id_atendimento | UUID | Sim | FK → ATENDIMENTO; um relatório por atendimento entre não excluídos |
| id_preenchido_por | INT | Sim | FK → USUARIO |
| status | VARCHAR(15) | Sim | `rascunho` ou `finalizado` |
| data_finalizacao | TIMESTAMPTZ | Não | Data de finalização |
| descricao_constatacoes | TEXT | Não | Obrigatoriedade depende do motivo |
| atividades_executadas | TEXT | Não | Obrigatoriedade depende do motivo |
| plano_acao | TEXT | Não | Obrigatoriedade depende do motivo |
| conclusao_visita | TEXT | Não | Obrigatoriedade depende do motivo |
| local_treinamento | VARCHAR(150) | Não | Campo de treinamento |
| qtd_pessoas_treinadas | SMALLINT | Não | Treinamento; maior que zero |
| estacao_funcionando | BOOLEAN | Não | Digital Farming/Estação Meteorológica |
| versao | INT | Sim | Versão do conteúdo publicado |
| id_atualizado_por | INT | Não | FK → USUARIO; edição após finalização |
| data_criacao | TIMESTAMPTZ | Sim | Recebimento no servidor |
| data_atualizacao | TIMESTAMPTZ | Sim | Atualizada por gatilho |
| deletado_em | TIMESTAMPTZ | Não | Exclusão lógica |
| sync_seq | BIGINT | Sim | Cursor global |

### MOTIVO_CAMPO_RELATORIO

Configuração dos campos obrigatórios para cada motivo de atendimento.

| Campo | Tipo | Obrig. | Chave / observação |
|---|---|---:|---|
| motivo | VARCHAR(30) | Sim | PK; motivos de atendimento |
| campo | VARCHAR(40) | Sim | PK; ex.: `descricao_constatacoes`, `atividades_executadas`, `plano_acao`, `conclusao_visita`, `assinatura_cliente` |
| obrigatorio | BOOLEAN | Sim | Define obrigatoriedade |
| data_atualizacao | TIMESTAMPTZ | Sim | Atualizada por gatilho |
| deletado_em | TIMESTAMPTZ | Não | Exclusão lógica |
| sync_seq | BIGINT | Sim | Cursor global |

PK composta: `(motivo, campo)`.

### RELATORIO_ESPECIALISTA

Participantes do atendimento. Deve haver exatamente um especialista responsável na finalização; demais podem ser apoio.

| Campo | Tipo | Obrig. | Chave / observação |
|---|---|---:|---|
| id_relatorio | UUID | Sim | PK/FK → RELATORIO_ATENDIMENTO |
| id_usuario | INT | Sim | PK/FK → USUARIO |
| papel | VARCHAR(15) | Sim | `responsavel` ou `apoio` |
| data_atualizacao | TIMESTAMPTZ | Sim | Atualizada por gatilho |
| deletado_em | TIMESTAMPTZ | Não | Exclusão lógica |
| sync_seq | BIGINT | Sim | Cursor global |

PK composta: `(id_relatorio, id_usuario)`. Índice único parcial permite no máximo um responsável; constraint trigger adiado verifica pelo menos um na finalização.

### RELATORIO_VERSAO

Cópia do conteúdo publicado antes de cada edição pós-finalização. Registro somente de inserção.

| Campo | Tipo | Obrig. | Chave / observação |
|---|---|---:|---|
| id_relatorio_versao | UUID | Sim | PK; gerado no servidor |
| id_relatorio | UUID | Sim | FK → RELATORIO_ATENDIMENTO |
| versao | INT | Sim | Versão preservada |
| descricao_constatacoes | TEXT | Não | Conteúdo anterior |
| atividades_executadas | TEXT | Não | Conteúdo anterior |
| plano_acao | TEXT | Não | Conteúdo anterior |
| conclusao_visita | TEXT | Não | Conteúdo anterior |
| local_treinamento | VARCHAR(150) | Não | Conteúdo anterior |
| qtd_pessoas_treinadas | SMALLINT | Não | Conteúdo anterior |
| estacao_funcionando | BOOLEAN | Não | Conteúdo anterior |
| id_editado_por | INT | Sim | FK → USUARIO |
| data_criacao | TIMESTAMPTZ | Sim | Data de criação |

### RELATORIO_PDF

Arquivo PDF gerado para uma versão do relatório.

| Campo | Tipo | Obrig. | Chave / observação |
|---|---|---:|---|
| id_relatorio_pdf | INT IDENTITY | Sim | PK |
| id_relatorio | UUID | Sim | FK → RELATORIO_ATENDIMENTO |
| versao | INT | Sim | Versão que gerou o PDF |
| storage_key | VARCHAR(255) | Sim | UNIQUE; chave no Object Storage privado |
| hash_arquivo | CHAR(64) | Sim | SHA-256 |
| tamanho_bytes | BIGINT | Sim | Tamanho |
| data_geracao | TIMESTAMPTZ | Sim | Data de geração |

### ENVIO_COMUNICACAO

Rastreia envios por e-mail e WhatsApp para agendamento ou relatório.

| Campo | Tipo | Obrig. | Chave / observação |
|---|---|---:|---|
| id_envio | INT IDENTITY | Sim | PK |
| id_atendimento | UUID | Sim | FK → ATENDIMENTO |
| tipo_comunicacao | VARCHAR(15) | Sim | `agendamento` ou `relatorio` |
| id_agendamento | INT | Não | FK → AGENDAMENTO; preenchido para agendamento |
| id_relatorio_pdf | INT | Não | FK → RELATORIO_PDF; preenchido para relatório |
| canal | VARCHAR(15) | Sim | `email` ou `whatsapp` |
| destinatario | VARCHAR(150) | Sim | Destinatário |
| status | VARCHAR(15) | Sim | `pendente`, `enviado`, `falha` |
| tentativas | SMALLINT | Sim | Quantidade de tentativas |
| data_envio | TIMESTAMPTZ | Não | Data de envio |
| erro_mensagem | VARCHAR(255) | Não | Mensagem de erro |
| data_criacao | TIMESTAMPTZ | Sim | Criação |

Regra: exatamente uma entre `id_agendamento` e `id_relatorio_pdf` deve estar preenchida e deve corresponder a `tipo_comunicacao`.

### EVIDENCIA

Fotos, vídeos e documentos associados ao atendimento.

| Campo | Tipo | Obrig. | Chave / observação |
|---|---|---:|---|
| id_evidencia | UUID | Sim | PK; gerado no dispositivo |
| id_atendimento | UUID | Sim | FK → ATENDIMENTO |
| etapa | VARCHAR(20) | Sim | `solicitacao`, `constatacao`, `atividade`, `geral`, `anexo_extra` |
| tipo_arquivo | VARCHAR(10) | Sim | `foto`, `video`, `documento` |
| storage_key | VARCHAR(255) | Sim | UNIQUE; chave opaca no Object Storage |
| mime_type | VARCHAR(50) | Sim | Tipo MIME permitido |
| tamanho_bytes | BIGINT | Sim | Entre 1 byte e 1 GiB |
| hash_arquivo | CHAR(64) | Sim | SHA-256 |
| status_upload | VARCHAR(15) | Sim | `pendente`, `enviado`, `erro` |
| varredura_status | VARCHAR(15) | Sim | `pendente`, `limpo`, `infectado`, `erro` |
| tentativas_upload | SMALLINT | Sim | Tentativas |
| origem_captura | VARCHAR(10) | Sim | `camera_app` ou `anexo` |
| data_captura | TIMESTAMPTZ | Sim | Relógio do aparelho |
| data_upload | TIMESTAMPTZ | Não | Data de upload |
| latitude | NUMERIC(9,6) | Não | GPS opcional |
| longitude | NUMERIC(9,6) | Não | GPS opcional |
| data_criacao | TIMESTAMPTZ | Sim | Recebimento no servidor |
| data_atualizacao | TIMESTAMPTZ | Sim | Atualizada por gatilho |
| deletado_em | TIMESTAMPTZ | Não | Exclusão lógica |
| sync_seq | BIGINT | Sim | Cursor global |

Regras: foto/vídeo apenas com origem declarada como câmera do app; documento somente PDF e na etapa `anexo_extra`; download liberado apenas após varredura `limpo`. O registro da origem declarada não prova tecnicamente a origem no servidor.

### ASSINATURA

Assinatura do cliente ou especialista, vinculada à versão e ao hash do relatório assinado.

| Campo | Tipo | Obrig. | Chave / observação |
|---|---|---:|---|
| id_assinatura | UUID | Sim | PK; gerado no dispositivo |
| id_atendimento | UUID | Sim | FK → ATENDIMENTO |
| tipo_assinante | VARCHAR(15) | Sim | `cliente` ou `especialista` |
| id_usuario | INT | Não | FK → USUARIO; obrigatório para especialista |
| nome_assinante | VARCHAR(150) | Sim | Nome |
| documento_assinante | TEXT | Não | CPF quando informado; cifrado pela aplicação |
| forma_assinatura | VARCHAR(15) | Sim | `desenho` ou `nome_digitado` |
| storage_key | VARCHAR(255) | Não | UNIQUE; imagem privada; obrigatória para desenho |
| versao_relatorio_assinada | INT | Sim | Calculada pelo banco |
| conteudo_hash | CHAR(64) | Sim | SHA-256 calculado pelo banco |
| data_hora | TIMESTAMPTZ | Sim | Relógio do aparelho |
| data_criacao | TIMESTAMPTZ | Sim | Recebimento no servidor |
| data_atualizacao | TIMESTAMPTZ | Sim | Atualizada por gatilho |
| deletado_em | TIMESTAMPTZ | Não | Exclusão lógica |
| sync_seq | BIGINT | Sim | Cursor global |

Regra: especialista que assina deve ser o responsável em RELATORIO_ESPECIALISTA. UNIQUE `(id_atendimento, tipo_assinante, versao_relatorio_assinada)`.

## 4. Formulários, satisfação e notificações

### PESQUISA_SATISFACAO

Pesquisa CSAT vinculada ao atendimento. O respondente pode não possuir conta no aplicativo.

| Campo | Tipo | Obrig. | Chave / observação |
|---|---|---:|---|
| id_pesquisa | INT IDENTITY | Sim | PK |
| id_atendimento | UUID | Sim | FK → ATENDIMENTO; UNIQUE |
| id_usuario_respondente | INT | Não | FK → USUARIO |
| nome_respondente | VARCHAR(150) | Não | Nome informado |
| nota_geral | SMALLINT | Não | CSAT de 0 a 10; nulo até resposta |
| data_envio | TIMESTAMPTZ | Sim | Envio |
| data_resposta | TIMESTAMPTZ | Não | Resposta |

Sincronização: somente servidor.

### PERGUNTA_SATISFACAO

Perguntas da pesquisa; máximo de cinco ativas.

| Campo | Tipo | Obrig. | Chave / observação |
|---|---|---:|---|
| id_pergunta | INT IDENTITY | Sim | PK |
| texto | VARCHAR(200) | Sim | Texto |
| ordem | SMALLINT | Sim | Ordem |
| ativa | BOOLEAN | Sim | Gatilho limita a cinco perguntas ativas |

Sincronização: somente servidor.

### RESPOSTA_SATISFACAO

Nota atribuída a cada pergunta da pesquisa.

| Campo | Tipo | Obrig. | Chave / observação |
|---|---|---:|---|
| id_resposta | INT IDENTITY | Sim | PK |
| id_pesquisa | INT | Sim | FK → PESQUISA_SATISFACAO |
| id_pergunta | INT | Sim | FK → PERGUNTA_SATISFACAO |
| nota | SMALLINT | Sim | De 0 a 10 |

Regra: UNIQUE `(id_pesquisa, id_pergunta)`. Sincronização: somente servidor.

### AVALIACAO_FORMULARIO

Avaliação por estrelas do formulário de abertura, distinta do CSAT.

| Campo | Tipo | Obrig. | Chave / observação |
|---|---|---:|---|
| id_avaliacao | INT IDENTITY | Sim | PK |
| id_atendimento | UUID | Sim | FK → ATENDIMENTO; UNIQUE |
| nota_estrelas | SMALLINT | Sim | De 1 a 5 |
| data_avaliacao | TIMESTAMPTZ | Sim | Data |
| data_atualizacao | TIMESTAMPTZ | Sim | Atualizada por gatilho |
| deletado_em | TIMESTAMPTZ | Não | Exclusão lógica |
| sync_seq | BIGINT | Sim | Cursor global |

### NOTIFICACAO

Avisos internos do aplicativo.

| Campo | Tipo | Obrig. | Chave / observação |
|---|---|---:|---|
| id_notificacao | INT IDENTITY | Sim | PK |
| id_usuario | INT | Sim | FK → USUARIO; destinatário |
| id_atendimento | UUID | Não | FK → ATENDIMENTO; nulo em avisos de conta |
| tipo | VARCHAR(30) | Sim | `novo_atendimento`, `atribuido`, `agendado`, `sincronizado`, `cadastro_aprovado`, `avaliacao_pendente` |
| mensagem | VARCHAR(255) | Sim | Texto sem URLs |
| lida | BOOLEAN | Sim | Indicador de leitura |
| data_criacao | TIMESTAMPTZ | Sim | Criação |

Sincronização: somente servidor.

### PERGUNTA_FORMULARIO

Perguntas extras configuráveis pelo Administrador. Cada edição gera nova versão.

| Campo | Tipo | Obrig. | Chave / observação |
|---|---|---:|---|
| id_pergunta_form | INT IDENTITY | Sim | PK; versão específica |
| codigo | VARCHAR(50) | Sim | Identidade estável da pergunta |
| texto | VARCHAR(200) | Sim | Texto |
| tipo_campo | VARCHAR(20) | Sim | `texto`, `numero`, `selecao`, `multisselecao`, `data` |
| opcoes | JSONB | Não | Opções para seleção/multisseleção |
| regra_condicional | JSONB | Não | Regra de exibição condicional |
| motivo_aplicavel | VARCHAR(30) | Não | Motivo aplicável |
| obrigatoria | BOOLEAN | Sim | Obrigatoriedade |
| ordem | SMALLINT | Sim | Ordem |
| versao | INT | Sim | Versão |
| ativa | BOOLEAN | Sim | Uma versão ativa por código |
| data_criacao | TIMESTAMPTZ | Sim | Recebido no servidor |
| data_atualizacao | TIMESTAMPTZ | Sim | Atualizada por gatilho |
| deletado_em | TIMESTAMPTZ | Não | Exclusão lógica |
| sync_seq | BIGINT | Sim | Cursor global |

Regras: UNIQUE `(codigo, versao)` e índice único parcial por `codigo` para a versão ativa.

### RESPOSTA_PERGUNTA_FORMULARIO

Resposta de um atendimento a uma pergunta configurável.

| Campo | Tipo | Obrig. | Chave / observação |
|---|---|---:|---|
| id_resposta | INT IDENTITY | Sim | PK |
| id_atendimento | UUID | Sim | FK → ATENDIMENTO |
| id_pergunta_form | INT | Sim | FK → PERGUNTA_FORMULARIO |
| valor_resposta | TEXT | Não | Nulo em multisseleção; opções ficam em RESPOSTA_OPCAO |
| data_criacao | TIMESTAMPTZ | Sim | Recebido no servidor |
| data_atualizacao | TIMESTAMPTZ | Sim | Atualizada por gatilho |
| deletado_em | TIMESTAMPTZ | Não | Exclusão lógica |
| sync_seq | BIGINT | Sim | Cursor global |

Regra: UNIQUE `(id_atendimento, id_pergunta_form)`.

### RESPOSTA_OPCAO

Opções marcadas em resposta de multisseleção.

| Campo | Tipo | Obrig. | Chave / observação |
|---|---|---:|---|
| id_resposta | INT | Sim | PK/FK → RESPOSTA_PERGUNTA_FORMULARIO |
| opcao_selecionada | VARCHAR(100) | Sim | PK; opção marcada |
| data_atualizacao | TIMESTAMPTZ | Sim | Atualizada por gatilho |
| deletado_em | TIMESTAMPTZ | Não | Exclusão lógica |
| sync_seq | BIGINT | Sim | Cursor global |

PK composta: `(id_resposta, opcao_selecionada)`. Desmarcar opção é exclusão lógica.

## 5. Segurança, auditoria e privacidade

### LOG_AUDITORIA

Trilha de eventos de segurança e acesso a dados sensíveis. Somente inserção; o banco grava por função de auditoria.

| Campo | Tipo | Obrig. | Chave / observação |
|---|---|---:|---|
| id_evento | BIGINT IDENTITY | Sim | PK; atribuído sob trava, na ordem real de gravação |
| data_evento | TIMESTAMPTZ | Sim | Relógio do servidor |
| id_usuario | INT | Não | FK → USUARIO; nulo em evento anônimo |
| id_dispositivo | UUID | Não | FK → DISPOSITIVO |
| acao | VARCHAR(40) | Sim | Tipo do evento |
| resultado | VARCHAR(10) | Sim | `sucesso`, `falha`, `negado` |
| entidade_tipo | VARCHAR(40) | Não | Tipo da entidade |
| entidade_id | VARCHAR(64) | Não | Identificador da entidade, sem dado pessoal |
| ip | INET | Não | IP |
| user_agent | VARCHAR(255) | Não | Agente do cliente |
| correlacao_id | UUID | Não | Correlação da requisição |
| detalhes | JSONB | Não | Contexto técnico, sem dados pessoais/segredos |
| hash_anterior | CHAR(64) | Não | Hash anterior; nulo no primeiro |
| hash_evento | CHAR(64) | Sim | SHA-256 calculado por gatilho |

A cadeia de hashes permite detectar alterações e remoções intermediárias; âncora externa periódica é necessária para detectar remoção da cauda.

### TOKEN_ACESSO

Refresh tokens, convites e redefinições de senha. O token original nunca é armazenado; somente o hash SHA-256.

| Campo | Tipo | Obrig. | Chave / observação |
|---|---|---:|---|
| id_token | UUID | Sim | PK |
| id_usuario | INT | Sim | FK → USUARIO |
| id_dispositivo | UUID | Não | FK → DISPOSITIVO; nulo em convite/reset |
| tipo | VARCHAR(15) | Sim | `refresh`, `convite`, `reset_senha` |
| token_hash | CHAR(64) | Sim | UNIQUE; SHA-256 hexadecimal minúsculo |
| criado_em | TIMESTAMPTZ | Sim | Criação |
| expira_em | TIMESTAMPTZ | Sim | Posterior a criado_em; limite por tipo |
| usado_em | TIMESTAMPTZ | Não | Primeiro uso/consumo |
| revogado_em | TIMESTAMPTZ | Não | Revogação |
| substituido_por | UUID | Não | FK autorreferente → TOKEN_ACESSO |
| ip | INET | Não | IP |

Emissão, rotação e revogação são feitas pelas funções do serviço de login descritas no MER/DER.

### SOLICITACAO_TITULAR

Registro de pedidos de titulares de dados.

| Campo | Tipo | Obrig. | Chave / observação |
|---|---|---:|---|
| id_solicitacao | INT IDENTITY | Sim | PK |
| id_usuario | INT | Não | FK → USUARIO; nulo para titular sem conta |
| nome_titular | VARCHAR(150) | Não | Nome |
| email_contato | VARCHAR(150) | Sim | Canal de resposta |
| tipo | VARCHAR(30) | Sim | `confirmacao`, `acesso`, `correcao`, `anonimizacao`, `eliminacao`, `portabilidade`, `compartilhamento`, `revogacao_consentimento` |
| status | VARCHAR(15) | Sim | `recebida`, `em_analise`, `atendida`, `negada` |
| data_solicitacao | TIMESTAMPTZ | Sim | Recebimento |
| data_prazo | TIMESTAMPTZ | Sim | Prazo proposto de 15 dias, a confirmar com jurídico |
| data_conclusao | TIMESTAMPTZ | Não | Conclusão |
| id_responsavel | INT | Não | FK → USUARIO |
| justificativa_negativa | TEXT | Não | Justificativa quando negada |
| observacao | TEXT | Não | Observação |

### INCIDENTE_SEGURANCA

Registro de incidentes de segurança.

| Campo | Tipo | Obrig. | Chave / observação |
|---|---|---:|---|
| id_incidente | INT IDENTITY | Sim | PK |
| data_deteccao | TIMESTAMPTZ | Sim | Detecção |
| data_ocorrencia | TIMESTAMPTZ | Não | Ocorrência, se conhecida |
| severidade | VARCHAR(10) | Sim | `baixa`, `media`, `alta`, `critica` |
| categoria | VARCHAR(25) | Sim | `acesso_indevido`, `vazamento`, `perda_dispositivo`, `malware`, `indisponibilidade`, `outro` |
| descricao | TEXT | Sim | Descrição |
| dados_afetados | TEXT | Não | Categorias/volume aproximado, sem reproduzir dados |
| status | VARCHAR(15) | Sim | `aberto`, `contido`, `comunicado`, `encerrado` |
| data_contencao | TIMESTAMPTZ | Não | Contenção |
| comunicado_autoridade_em | TIMESTAMPTZ | Não | Comunicação à autoridade, quando aplicável |
| comunicado_titulares_em | TIMESTAMPTZ | Não | Comunicação aos titulares |
| id_registrado_por | INT | Sim | FK → USUARIO |
| id_responsavel | INT | Não | FK → USUARIO |
| licoes_aprendidas | TEXT | Não | Lições |
| data_criacao | TIMESTAMPTZ | Sim | Criação |
| data_atualizacao | TIMESTAMPTZ | Sim | Atualização |

### DOMINIO_EMAIL_CORPORATIVO

Domínios autorizados para contas Netafim e Distribuidor.

| Campo | Tipo | Obrig. | Chave / observação |
|---|---|---:|---|
| id_dominio | INT IDENTITY | Sim | PK |
| dominio | VARCHAR(120) | Sim | UNIQUE; minúsculo, sem `@` |
| tipo_organizacao | VARCHAR(15) | Sim | `netafim` ou `distribuidor` |
| id_distribuidor | INT | Não | FK → DISTRIBUIDOR; obrigatório para tipo distribuidor |
| ativo | BOOLEAN | Sim | Ativo/inativo |
| data_criacao | TIMESTAMPTZ | Sim | Criação |

Clientes finais não utilizam essa lista; passam por aprovação do Administrador Netafim.

## 6. Resumo das tabelas

| Área | Tabelas |
|---|---|
| Acesso e organização | PERFIL, PERMISSAO, PERFIL_PERMISSAO, USUARIO, DISTRIBUIDOR, CLIENTE, PROPRIEDADE, CULTURA, PROPRIEDADE_CULTURA, DISPOSITIVO, DOMINIO_EMAIL_CORPORATIVO |
| Atendimento e classificação | ATENDIMENTO, HISTORICO_ATENDIMENTO, AGENDAMENTO, PROBLEMA_CATEGORIA, PROBLEMA_SUBCATEGORIA, ATENDIMENTO_PROBLEMA, ATENDIMENTO_CULTURA, TIPO_TREINAMENTO, ATENDIMENTO_TIPO_TREINAMENTO |
| Relatórios e evidências | RELATORIO_ATENDIMENTO, MOTIVO_CAMPO_RELATORIO, RELATORIO_ESPECIALISTA, RELATORIO_VERSAO, RELATORIO_PDF, ENVIO_COMUNICACAO, EVIDENCIA, ASSINATURA |
| Formulários e satisfação | PESQUISA_SATISFACAO, PERGUNTA_SATISFACAO, RESPOSTA_SATISFACAO, AVALIACAO_FORMULARIO, NOTIFICACAO, PERGUNTA_FORMULARIO, RESPOSTA_PERGUNTA_FORMULARIO, RESPOSTA_OPCAO |
| Segurança e privacidade | LOG_AUDITORIA, TOKEN_ACESSO, SOLICITACAO_TITULAR, INCIDENTE_SEGURANCA, SYNC_CONFLITO |

## 7. Pontos que exigem conferência antes de tratar como schema definitivo

1. Conferir todos os tipos, constraints, índices, nomes e nulabilidades contra os scripts SQL efetivamente aplicados.
2. O MER/DER é uma especificação de modelo; não prova que todas as 41 tabelas e gatilhos já estejam implantados.
3. A documentação de Room está no Anexo A.6 e não deve ser confundida com as tabelas do PostgreSQL.
4. Regras e decisões pendentes com a Netafim permanecem pendentes até validação formal.
5. Prazos de retenção indicados no MER/DER são propostas de trabalho e dependem de validação jurídica/corporativa.
