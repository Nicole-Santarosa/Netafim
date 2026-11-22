# Fluxo de sincronização — PUSH / PULL

> **Status:** proposta v0.1, pendente de confirmação do grupo.
> **Escopo proposto da v1:** `atendimento` e `evidencia`. Relatórios e agendamentos ficam previstos para uma versão posterior.
> Os endpoints descritos neste documento ainda não estão implementados, conforme o Contrato de Sincronização de 02/11/2026.

## 1. Objetivo

Definir como o aplicativo Android, usando Room, envia operações realizadas localmente e recebe atualizações do servidor, permitindo trabalho offline com rastreabilidade e tratamento explícito de falhas e conflitos.

## 2. Visão do fluxo

1. O usuário cria ou altera registros no aplicativo.
2. O aplicativo persiste os dados no Room e registra as operações em uma fila local.
3. Quando houver conectividade, o app envia as operações por **PUSH**.
4. A API processa cada operação de forma independente e devolve um resultado por operação.
5. O app atualiza sua base local com os registros confirmados pelo servidor.
6. Em seguida, o app executa **PULL**, usando o cursor global armazenado localmente.
7. O app aplica os itens recebidos de forma idempotente.
8. Após o envio dos metadados de evidências, os arquivos binários pendentes são enviados separadamente.

**Ordem proposta:** PUSH antes de PULL. Assim, o servidor pode devolver no PULL dados já atualizados, inclusive alterações feitas pelo próprio aparelho.

## 3. Autenticação e identificação do aparelho

As requisições usam o mecanismo de autenticação da API:

```http
Authorization: Bearer <access>
```

O aparelho é identificado pela sessão autenticada, e não por um identificador livre enviado no corpo. O contrato informa que a API verifica se o aparelho foi revogado.

## 4. PUSH — envio de operações

**Endpoint proposto:**

```http
POST /api/v1/sync/push
```

O lote contém até 50 operações por chamada, limite ainda sujeito à aprovação do grupo.

Cada operação inclui, conceitualmente:

| Campo | Finalidade |
|---|---|
| `id_operacao` | UUID único da operação, gerado no app e reutilizado em reenvios |
| `entidade` | Tipo do registro; na v1 proposta: `atendimento` ou `evidencia` |
| `tipo` | `criar` ou `atualizar` |
| `id` | UUID do registro |
| `versao_base` | Versão do servidor conhecida pelo app; nula em criação |
| `campos` | Dados da operação |
| `feito_em` | Horário informado pelo aparelho, apenas informativo |

O servidor não deve confiar no horário do aparelho para ordenar ou validar alterações.

### Regras de processamento

- As operações são processadas na ordem do array.
- A criação do atendimento deve preceder o envio das evidências associadas.
- Cada operação tem sua própria transação: a falha de uma não deve cancelar as demais.
- Em criação, campos controlados pelo servidor — como status, protocolo, versão, sequência de sincronização, destinação, técnico e snapshots — não devem ser aceitos como autoridade do cliente.
- Em atualização, somente os campos permitidos pelo contrato da API podem ser alterados. A lista completa de campos editáveis ainda precisa ser definida.

## 5. Resposta do PUSH

O contrato propõe `200 OK` quando o lote foi lido, mesmo que existam falhas em operações individuais. Erros HTTP ficam reservados a problemas do lote inteiro, como autenticação, formato inválido ou lote acima do limite.

Cada resultado pode ser:

| Resultado | Significado | Ação do app |
|---|---|---|
| `aplicado` | Operação gravada | Substituir o registro local pelo retornado e marcar como sincronizado |
| `duplicado` | Operação já aplicada anteriormente | Tratar como sucesso e atualizar com o registro atual retornado |
| `conflito` | Versão-base diferente da versão atual no servidor | Guardar a versão do servidor e preservar a intenção local para análise |
| `rejeitado` | Regra de negócio, permissão ou validação impediu a operação | Exibir o motivo; não reenviar sem correção ou descarte |

## 6. PULL — recebimento de atualizações

**Endpoint proposto:**

```http
GET /api/v1/sync/pull?desde=<sync_seq>&limite=200
```

O primeiro PULL pode começar em `desde=0` (ou com o parâmetro omitido), trazendo os registros que o usuário autenticado pode visualizar. O contrato atribui esse recorte às políticas de acesso do banco (RLS).

A resposta contém:

- `itens`: registros ou tombstones;
- `cursor`: novo cursor global;
- `tem_mais`: indica se há outra página.

Os itens devem ser entregues em ordem crescente de `sync_seq`. Enquanto `tem_mais` for verdadeiro, o app solicita a próxima página usando o cursor retornado.

### Aplicação local

- Guardar o cursor global separadamente, por exemplo no DataStore.
- Não confundir o cursor global com `syncSeqServidor`, que é um atributo de cada registro local.
- Aplicar um registro recebido somente se sua versão/sequência for mais recente que a versão local.
- Não sobrescrever um registro com `statusLocal = pendente_envio`; a operação local deve passar pelo PUSH e pelo fluxo de conflito.
- `removido = true` representa exclusão lógica (`deletado_em` preenchido).

## 7. Upload de evidências

O envio de metadados da evidência ocorre pelo PUSH. O arquivo binário é enviado depois, por endpoint proposto:

```http
PUT /api/v1/evidencias/{id}/arquivo
```

O servidor confere tamanho e SHA-256 em relação aos metadados antes de armazenar o arquivo no Object Storage.

O mecanismo de transferência — proxy da API ou URL pré-assinada — permanece pendente de decisão de infraestrutura.

## 8. Limitações e decisões pendentes

O contrato lista decisões A–I ainda abertas, incluindo: registro de operações processadas, política de conflitos, estratégia do cursor, campos editáveis, resolução administrativa de conflitos, mecanismo de upload, escopo da v1, limites e responsável pela implementação do backend.

A proposta de sobreposição de cursor em 1.000 posições é uma mitigação, não uma garantia formal contra commits fora de ordem. A alternativa de marca-d'água no servidor ainda precisa ser decidida.

## 9. Referência

Baseado no **Contrato de Sincronização — proposta v0.1, 02/11/2026**. Exemplos de endpoint e payload são propostas documentais, não evidência de implementação existente.
