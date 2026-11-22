# Atendimentos: endpoints

> **Responsável pelo módulo:** Rafael. **Este arquivo é um resumo** montado a partir do contrato `docs/api/atendimentos-openapi.yaml` da branch `rafael-atendimentos-models`. **O contrato dele é a referência oficial**; em caso de diferença, vale o OpenAPI.
> **Autenticação:** [`autenticacao.md`](autenticacao.md) · **Erros:** [`erros-http.md`](erros-http.md)
> **Estado:** a API de atendimentos ainda **não foi mesclada** na branch principal.

---

## 1. Em 30 segundos

- O app gera o **UUID** do atendimento (`id_atendimento`) no aparelho. Ele é a chave de idempotência.
- O **servidor** decide: protocolo `OS-####`, `versao`, `sync_seq`, status inicial (sempre `aberto`) e prioridade. O que o app mandar nesses campos é ignorado.
- Quem solicita é sempre o **usuário autenticado**.
- Quem pode ver ou alterar é decisão do **banco** (RLS e gatilhos), não de filtros da API.

---

## 2. Endpoints existentes

| Método e rota | O que faz | Sucesso |
|---|---|---|
| `GET /api/v1/atendimentos` | Lista o que o usuário pode ver | 200, paginado |
| `POST /api/v1/atendimentos` | Abre um atendimento | 201 |
| `GET /api/v1/atendimentos/{id_atendimento}` | Detalhe com linha do tempo (`historico`) | 200 |
| `PATCH /api/v1/atendimentos/{id_atendimento}` | Altera status, destinação ou técnico | 200 |

### 2.1 Listar

`GET /api/v1/atendimentos?status=aberto&page=1`

- Filtro `status`: `aberto`, `destinado`, `agendado`, `em_atendimento`, `resolvido`, `encerrado`, `encerrado_sem_avaliacao`, `cancelado`. Valor diferente responde 400.
- Ordem: do mais novo para o mais antigo (`data_abertura`). **25 por página**, não configurável.
- Resposta: `{ "count", "next", "previous", "results": [ ... ] }`. Página inexistente → 404.
- Itens da lista: `id_atendimento`, `numero_protocolo`, `status`, `motivo`, `origem`, `nome_produtor`, `nome_fazenda_informado`, `cidade`, `data_abertura`, `data_atualizacao`, `versao`, `sync_seq`.

### 2.2 Abrir

`POST /api/v1/atendimentos`

Obrigatórios: `id_atendimento` (UUID), `origem`, `motivo`, `contato_solicitante` (até 20 caracteres).

| Campo | Valores / regra |
|---|---|
| `origem` | `netafim`, `distribuidor`, `cliente_final` |
| `motivo` | `digital_farming`, `duvidas`, `entrega_tecnica`, `pos_venda`, `programacao_feiras`, `treinamento` |
| `id_cliente` | **Obrigatório** quando a origem é `cliente_final` |
| `area_setor` | **Obrigatório** quando a origem é `netafim` (`comercial`, `engenharia`, `instalacao`, `marketing`, `pos_venda`) |
| `id_propriedade` | Exige `id_cliente` e deve pertencer a esse cliente |
| `id_distribuidor_origem` | Distribuidor escolhido na tela |
| `situacao_projeto` | `garantia`, `fora_garantia`, `instalacao` |
| `estado_projeto` | `operando`, `nao_operando` |
| `tipo_venda` | `projeto`, `venda_regular` |
| `tipo_atendimento` | `online`, `presencial` |
| Texto/datas opcionais | `nome_produtor`, `nome_fazenda_informado`, `cidade`, `numero_projeto_br`, `descricao_problema`, `data_inicio_problema`, `data_sugerida`, `necessidade`, `qtd_participantes_prevista` (≥ 1), `data_abertura` (relógio do aparelho) |

Resposta 201: `id_atendimento`, `numero_protocolo` (ex.: `OS-0001`), `status` (`aberto`), `prioridade`, `versao` (1), `sync_seq`, `data_abertura`.

`prioridade` (`baixa`, `media`, `alta`) é decidida pelo servidor e aparece nas respostas; o app não a envia.

**Campos ignorados se enviados:** status, protocolo, versão, `sync_seq`, prioridade, destinação, técnico, snapshots e `id_solicitante`.

### 2.3 Alterar

`PATCH /api/v1/atendimentos/{id_atendimento}`

```json
{ "versao": 3, "status": "agendado" }
```

- `versao` é **obrigatória**: a que o app conhece. Só é aplicada se for a atual (concorrência otimista).
- Informe ao menos um destes: `status`, `destinado_a` (+ `id_distribuidor`), `id_tecnico_responsavel` (`null` desvincula).
- Resposta 200: o mesmo formato do `GET` (detalhe), já com a versão nova.

**Quem pode (regras do banco):** destinar, cancelar e encerrar são do **Netafim Admin**; vincular técnico exige a permissão `vincular_tecnico` (o Distribuidor Admin só vincula técnicos do próprio distribuidor).

---

## 3. Máquina de estados

```
aberto ──► destinado ──► agendado ──► em_atendimento ──► resolvido ──► encerrado
  │            │             │               │               └──────► encerrado_sem_avaliacao
  └────────────┴─────────────┴───────────────┴──► cancelado
```

| Transição | Exige |
|---|---|
| `aberto → destinado` | Netafim Admin (ou processo automático) |
| `destinado → agendado` | Um agendamento ativo |
| `agendado → em_atendimento` | **Proposta em análise:** só o técnico responsável, o Netafim Admin, o Distribuidor Admin do atendimento destinado a ele ou o processo automático (migração `008`, ainda não aprovada pelo grupo) |
| `em_atendimento → resolvido` | Relatório finalizado |
| `resolvido → encerrado` | Pesquisa de satisfação respondida |
| `resolvido → encerrado_sem_avaliacao` | Prazo de espera vencido e pesquisa não respondida |
| `aberto`, `destinado`, `agendado` ou `em_atendimento` → `cancelado` | Netafim Admin (de `resolvido` não se cancela) |

Estados finais (`encerrado`, `encerrado_sem_avaliacao`, `cancelado`) só mudam pelo Netafim Admin ou pelo processo automático.

---

## 4. Erros específicos

| Status / `codigo` | Quando | O app faz |
|---|---|---|
| 400 | Campo ausente/inválido, `status` fora da lista, `versao` ausente | Mostrar no campo |
| 403 `sem_permissao` | Vê o atendimento mas o banco não deixa alterar | Mensagem de permissão |
| 404 | Não existe, foi excluído **ou a pessoa não vê** | "Atendimento não encontrado" |
| 409 `duplicado` | `id_atendimento` já existe | **GET** do id; se 200, tratar como sucesso; se 404, gerar novo UUID e reenviar |
| 409 `conflito_de_versao` | Versão velha; traz `versao_atual` | `GET`, mostrar o estado novo, tentar de novo |
| 422 `regra_de_negocio` | Transição não permitida, falta pré-requisito, perfil sem permissão | Mostrar o `detail` |
| 422 `referencia_invalida` | Cliente, propriedade, distribuidor ou técnico inexistente | Corrigir a seleção |

**Ordem de verificação do PATCH:** a `versao` é comparada **antes** da permissão. Com versão velha a resposta é 409, mesmo que a pessoa não tivesse permissão.

---

## 5. Pontos em aberto

| # | Assunto | Dono |
|---|---|---|
| 1 | Regra de quem **inicia** o atendimento (migração `008`) | Grupo decide; proposta do Rafael testada (8 casos) |
| 2 | Quais outros campos o app poderá **atualizar** (hoje o PATCH só aceita status, destinação e técnico) | Rafael |
| 3 | Snapshots de cliente e propriedade (hoje o servidor não preenche) | Rafael |
| 4 | Contrato de **sincronização offline** (`/sync/push` e `/sync/pull`) | Proposta em `Contrato_Sincronizacao_PROPOSTA.md`; grupo confirma |
| 5 | Agendamento, relatório, avaliação e evidências (anexos) | Ainda sem endpoints |

---

## 6. Dica para o Android

- Gere o `id_atendimento` **antes** de qualquer chamada e guarde-o: reenviar a mesma criação é seguro se você tratar o `409 duplicado` como descrito.
- Guarde `versao` de cada atendimento e envie-a no PATCH.
- `numero_protocolo` é do servidor: mostre "Aguardando sincronização" enquanto estiver vazio.
