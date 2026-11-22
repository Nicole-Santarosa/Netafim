# Contrato de sincronização Android ⇄ API (PROPOSTA v0.1)

Resposta à pergunta da Nicole: *o que o Android envia, o que recebe e como o servidor identifica conflitos.*

**Status:** proposta para o grupo confirmar. **Nenhum destes endpoints existe hoje** (a API de atendimentos do Rafael tem só `GET/POST /atendimentos` e `GET/PATCH /atendimentos/{id}`).
**Data:** 02/11/2026 · **Autor:** Rodrigo (Pessoa 1)

> **Estado em 15/11/2026:** continua sendo **proposta**. Nenhum endpoint de sincronização foi implementado. Em aberto, para o grupo decidir: **quem implementa `GET /sync/pull` e `POST /sync/push` no back-end** (a função `pull_atendimentos(desde, limite)` do Rafael existe na branch `rafael-atendimentos-models` (PR #3, ainda não mesclado), sem a rota; é a base do pull) e o prazo para o app (Pessoa 4) consumir. Este arquivo entra no repositório para ter uma fonte versionada; o conteúdo abaixo é o da proposta original, sem alteração.

> Legenda: **[BANCO]** = já existe no snapshot `infra/db/base/001_schema_inicial.sql` (conferido) · **[API]** = já existe no contrato do Rafael (`origin/rafael-atendimentos-models`) · **[NOVO]** = proposta, precisa ser construída/aprovada.

---

## 1. O que o banco já nos dá (não vamos inventar do zero)

| Peça | Onde | Para que serve |
|---|---|---|
| UUID gerado no aparelho como chave primária (`atendimento`, `evidencia`, …) | [BANCO] | **Idempotência**: reenviar a mesma criação não duplica |
| `versao` (integer, +1 a cada UPDATE, gatilho) | [BANCO] em `atendimento`, `relatorio_atendimento` e outras | **Detecção de conflito** (concorrência otimista) |
| `sync_seq` (sequência global `seq_sync`, gatilho `fn_sync_meta`) | [BANCO] em mais de 20 tabelas (`atendimento`, `evidencia`, `agendamento`, `relatorio_atendimento`, `cliente`, ...) | **Cursor de descida**: "me dê tudo com `sync_seq` maior que N" |
| `deletado_em` (exclusão lógica) | [BANCO] | **Tombstone**: o app fica sabendo que o registro saiu |
| `sync_conflito` (versao_servidor, versao_cliente, payload_cliente jsonb, status) | [BANCO] | Guarda o conflito para resolução posterior |
| `numero_protocolo` OS-#### atribuído pelo servidor | [BANCO] | Atendimento criado offline não tem protocolo até sincronizar |
| `evidencia.status_upload`, `hash_arquivo`, `tentativas_upload` | [BANCO] | Arquivo sobe separado do cadastro |
| RLS + gatilhos aplicam as regras **como o usuário logado** | [BANCO] | O servidor não precisa reimplementar permissões na sincronização |

O que **não** existe: endpoint de sync, e uma tabela para lembrar operações já processadas (ver §9, ponto A).

---

## 2. Visão geral do fluxo

```
Android (Room)                                   API
─────────────                                    ───
1. grava local + fila de operações
2. POST /sync/push  (lote de operações) ───────► aplica uma a uma, como o usuário
                    ◄─────────────────────────── resultado POR operação
3. GET /sync/pull?desde=<sync_seq> ────────────► registros visíveis ao usuário
                    ◄─────────────────────────── itens + novo cursor
4. envia arquivos pendentes (PUT por evidência)
```

Sempre **push antes de pull** (o servidor devolve o que o próprio app acabou de gravar já com `versao`, `sync_seq` e protocolo).

Autenticação: `Authorization: Bearer <access>` (igual ao resto). O aparelho é identificado **pela sessão**, não pelo corpo (a API já confere a cada requisição que o aparelho não foi revogado).

---

## 3. PUSH — o que o Android envia

`POST /api/v1/sync/push` — até **50 operações** por chamada.

```json
{
  "operacoes": [
    {
      "id_operacao": "0b6f...-uuid",          // gerado no app, único por operação
      "entidade": "atendimento",              // v1: atendimento | evidencia
      "tipo": "criar",                        // criar | atualizar
      "id": "7c1e...-uuid",                   // UUID do registro (PK do banco)
      "versao_base": null,                    // criar: null | atualizar: versao que o app viu por último
      "campos": { "origem": "cliente_final", "motivo": "duvidas", "...": "..." },
      "feito_em": "2026-11-02T14:03:11-03:00" // hora no aparelho (informativa; o servidor não confia nela)
    }
  ]
}
```

Regras:
- **`campos` de criar**: os mesmos campos de `AberturaAtendimento` do contrato do Rafael [API]. Campos que o servidor decide (status, protocolo, versao, sync_seq, destinação, técnico, snapshots) são **ignorados** — comportamento que já existe no POST.
- **`campos` de atualizar**: só o que mudou, dentro do que o PATCH atual aceita [API]: `status`, `destinado_a`/`id_distribuidor`, `id_tecnico_responsavel`. Para outros campos editáveis do atendimento, a lista precisa ser definida pelo Rafael (ver §9, ponto D).
- **Ordem**: o servidor processa **na ordem do array**. O app deve enviar a criação do atendimento antes das evidências dele.
- **Cada operação é independente** (transação própria): uma falha não derruba as outras.

---

## 4. PUSH — o que o Android recebe

`200 OK` sempre que o lote foi lido (mesmo com falhas dentro). Erro HTTP só para problema do lote inteiro (401, 400 de formato, 413 se > 50 operações).

```json
{
  "resultados": [
    {
      "id_operacao": "0b6f...",
      "resultado": "aplicado",
      "registro": { "id_atendimento": "7c1e...", "versao": 1, "sync_seq": 90412, "numero_protocolo": "OS-0042", "...": "..." }
    },
    {
      "id_operacao": "9aa1...",
      "resultado": "conflito",
      "registro_servidor": { "id_atendimento": "55d2...", "versao": 7, "status": "agendado", "...": "..." },
      "id_conflito": "c3f0...-uuid"
    },
    {
      "id_operacao": "1d4e...",
      "resultado": "rejeitado",
      "erro": { "codigo": "regra_de_negocio", "detail": "somente o técnico responsável ou um administrador inicia o atendimento", "status": 422 }
    }
  ]
}
```

| `resultado` | Significa | O que o app faz |
|---|---|---|
| `aplicado` | Gravado. Vem o registro **como ficou** (versao, sync_seq, protocolo) | Troca o registro local pelo devolvido; marca `sincronizado` |
| `duplicado` | Já tinha sido aplicado antes (reenvio por falha de rede) | Trata como `aplicado` (vem o registro atual) |
| `conflito` | `versao_base` ≠ versão atual do servidor | Guarda `registro_servidor`; marca `conflito`; segue §5 |
| `rejeitado` | Regra do banco/permissão recusou (reaproveita `erros.py`: 403, 404, 422, 400) | **Não adianta reenviar igual.** Mostra `detail`; marca `erro`; usuário corrige ou descarta |

Mapeamento dos erros por operação = o mesmo da API atual [API]: `sem_permissao` (403), `nao_encontrado` (404), `regra_de_negocio` / `referencia_invalida` / `campo_obrigatorio` (422), `valor_invalido` (400). A mensagem do gatilho vai em `detail`.

---

## 5. Como o servidor identifica e trata conflitos

**Detecção (regra única):** para `atualizar`, o servidor compara `versao_base` (a que o app viu) com `versao` atual no banco.

- iguais → aplica, o banco incrementa `versao` → `aplicado`;
- diferentes → **não aplica** → `conflito`.

Isso é o que o `PATCH` atual já faz com `409 conflito_de_versao` [API]; o sync só devolve a mesma ideia **por operação**.

**Quando acontece conflito**
1. O servidor grava uma linha em `sync_conflito` [BANCO]: `entidade_tipo`, `entidade_uuid`, `versao_servidor`, `versao_cliente` (= `versao_base`), `payload_cliente` (o que o app queria), `id_dispositivo` (da sessão), `status = aberto`.
2. Responde `conflito` com o `registro_servidor` e o `id_conflito`.

**Política proposta para a v1 — "o servidor vence, a intenção do app é preservada":**
- O app **descarta** a versão local conflitante e passa a mostrar a do servidor, mas **mantém** o que o usuário tinha digitado (`payload_cliente` no banco e uma cópia local) para ele refazer a alteração se ainda fizer sentido.
- Por que não mesclar campo a campo: o banco **não guarda o histórico de campo** (só `historico_atendimento` para eventos de status/destino/técnico), então não dá para saber, com segurança, quais campos mudaram desde `versao_base`.
- Status é **sempre** do servidor (máquina de estados no gatilho): uma mudança de status que chegar com `versao_base` velha vira conflito, e uma que viole a máquina de estados vira `rejeitado`.
- Resolução manual por Netafim Admin (`resolvido_servidor` | `resolvido_cliente` | `descartado`) fica **fora da v1**; as linhas ficam em `aberto` até haver tela/rota (§9, ponto E).

**Caso particular: criar com UUID que já existe**
- mesmo conteúdo → `duplicado` (sucesso idempotente);
- conteúdo diferente → `conflito`.
Isso **resolve a dúvida do 409 da DT-017** para o sync: o app não precisa interpretar 409; lê o `resultado`.

---

## 6. PULL — o que o Android recebe para atualizar o local

`GET /api/v1/sync/pull?desde=<sync_seq>&limite=200`

```json
{
  "itens": [
    { "entidade": "atendimento", "removido": false, "registro": { "id_atendimento": "7c1e...", "versao": 8, "sync_seq": 90431, "...": "..." } },
    { "entidade": "evidencia",   "removido": true,  "registro": { "id": "aa01...", "sync_seq": 90433 } }
  ],
  "cursor": 90433,
  "tem_mais": false
}
```

- Primeira sincronização: `desde=0` (ou omitido) → baixa tudo que o usuário **pode ver** (o RLS filtra; o servidor não faz filtro próprio).
- O app guarda `cursor` e usa no próximo pull (em Room: `syncSeqServidor`).
- `removido: true` quando `deletado_em` não é nulo (tombstone).
- Ordem: crescente por `sync_seq`. Paginação: repita enquanto `tem_mais`.
- Aplicação local **idempotente**: só substitui se `registro.versao`/`sync_seq` forem maiores que os locais. Registro com `statusLocal = pendente_envio` **não é sobrescrito** pelo pull (a operação pendente passa pelo conflito do push).

> ⚠️ **Risco conhecido do cursor:** `sync_seq` é tirado no momento da escrita, não do commit. Duas transações concorrentes podem commitar fora de ordem e o app pularia um registro. Mitigação proposta: o app pede com **sobreposição** (`desde = cursor - 1000`) e a aplicação idempotente descarta o repetido. Alternativa mais robusta (marca-d'água no servidor) precisa de decisão — §9, ponto C.

---

## 7. Arquivos (evidências)

1. Metadados sobem pelo **push** como `entidade = "evidencia"`, `status_upload = pendente` (campos de [BANCO]: `etapa`, `tipo_arquivo`, `mime_type`, `tamanho_bytes`, `hash_arquivo` SHA-256, `data_captura`, `origem_captura`, lat/long).
2. O binário sobe depois por `PUT /api/v1/evidencias/{id}/arquivo` **[NOVO]** (corpo = bytes; o servidor confere tamanho e SHA-256 contra o metadado, grava no Object Storage, marca `enviado`, `varredura_status = pendente`).
3. Falha → `tentativas_upload + 1`; reenvio é seguro (mesmo hash = idempotente).
4. Foto/vídeo só da câmera do app; documento só PDF como anexo extra (CHECKs do banco impõem; o app deve validar antes).
O detalhe do storage (URL pré-assinada ou proxy) depende do Gabriel/Douglas ou de quem fechar a infra — ponto F.

---

## 8. O que muda para a Nicole (`AtendimentoEntity`, Room)

Respostas às perguntas que ela já tinha deixado:

| Campo | Proposta |
|---|---|
| `idAtendimento` (String) | UUID v4 gerado no app = `id_atendimento` do banco. Já está certo |
| `numeroProtocolo` (nullable) | Certo: `null` até o servidor devolver (`aplicado`/pull). Na tela, mostrar "Aguardando sincronização" |
| `versaoBase` | **Última `versao` recebida do servidor.** Vai no push como `versao_base`. Em registro novo local = `0`/null |
| `syncSeqServidor` | `sync_seq` do registro recebido (informativo). O **cursor do pull** é um valor único do app (guardar à parte, ex. DataStore), não por linha |
| `statusLocal` | `sincronizado` · `pendente_envio` · `conflito` · `erro` (**não confundir** com o `status` do atendimento, que é do servidor: aberto, destinado…) |
| `origemRegistro` | `local` (criado no aparelho) · `servidor` (veio do pull) |
| `dadosJson` | O mesmo JSON do `registro` da API (formato `AtendimentoDetalhe` [API]) — assim o pull grava direto e o push monta `campos` dele |

Faltam **duas tabelas locais**: (a) fila de operações (`id_operacao`, entidade, id, tipo, `versao_base`, `campos`, tentativas, estado) e (b) cópia do `registro_servidor` do conflito para a tela de resolução.

Fila no app: reenviar com **o mesmo `id_operacao`** em caso de falha de rede (é isso que dá `duplicado` em vez de dupla aplicação). Backoff exponencial; não reenviar `rejeitado` sem o usuário editar.

---

## 9. Decisões e pendências (o que **não** está fechado)

| # | Ponto | Dono | Observação |
|---|---|---|---|
| A | **Tabela de operações processadas** (`sync_operacao`: `id_operacao` PK, `id_dispositivo`, `resultado`, `resposta` jsonb, `criado_em`) | [GRUPO] + banco | **Não existe no banco.** Sem ela, um reenvio de *atualização* cuja resposta se perdeu volta como `conflito` falso (a `versao` já subiu). Para *criar*, o UUID já protege. Proposta: migração **009** (a 008 é a regra do Rafael) |
| B | Política de conflito v1 = "servidor vence + preserva payload" | [GRUPO] | Alternativa: mesclar por campo (exige log de campo, que o banco não tem) |
| C | Cursor: sobreposição no cliente × marca-d'água no servidor | Nicole (Pessoa 4) | Sobreposição é simples e suficiente para o volume esperado |
| D | Quais campos do atendimento o app pode **atualizar** pelo sync | Rafael | **Informado pelo Rafael em 15/11/2026 (não conferido linha a linha pelo autor do contrato):** o PATCH aceita hoje `versao` mais `status`, `destinado_a`, `id_distribuidor`, `id_tecnico_responsavel` (aceita nulo para desvincular) e `situacao_projeto` (aceita nulo); mais nada. Quem pode o quê e as transições são regras do gatilho do banco, então o sync devolve a mesma recusa por operação (422 `regra_de_negocio` ou 403). Sugestão para a v1: manter só esses campos. |
| E | Resolução de conflitos pelo Admin (rota + tela) | [GRUPO] | Fora da v1 |
| F | Upload de arquivo (proxy × URL pré-assinada, antivírus, limites) | infra | Depende do Object Storage |
| G | Escopo da v1: `atendimento` + `evidencia`; **relatório** e `agendamento` na v2 | [GRUPO] | Relatório tem `versao` própria e mais regras |
| H | Limite de lote (50) e de página (200/500) | [GRUPO] | Ajustável |
| I | Quem implementa o backend do sync | Nicole (Pessoa 4: backend e Android da sincronização) | Rodrigo (Pessoa 1) fecha o contrato com ela e aplica os scripts de banco (a migração 009 da tabela de operações entra aqui) |

---

## 10. Para destravar a Nicole **agora**

Ela pode começar sem esperar o backend, usando só o que está decidido:
1. UUID local e protocolo nulo; `statusLocal` e `origemRegistro` com os valores da §8.
2. Fila de operações no Room e a lógica `aplicado / duplicado / conflito / rejeitado` contra uma **API falsa** (os JSONs das §4 e §6 servem de fixture).
3. Aplicação idempotente do pull (só substitui se `versao`/`sync_seq` maiores; nunca sobrescreve `pendente_envio`).

O que muda entre a v0.1 e a final (itens A–I) mexe em **campos opcionais** do corpo, não na forma geral. Mudar o formato dos JSONs desta proposta exige aviso aqui antes.

---

## Correções de 15/11/2026 (informadas pelo Rafael)

- A função `pull_atendimentos` ainda não existe; é a que o Rafael se comprometeu a fazer.
- O detalhe do atendimento (`GET` e a resposta do `PATCH`) **ainda não traz `sync_seq`**; só o `POST` e a lista trazem. O Rafael vai acrescentar no detalhe, porque a resposta `aplicado` (seção 4) precisa dele.

## Atualização de 15/11/2026, noite (Rafael, branch `rafael-atendimentos-models`, commit `ed1ff7d`)

Conferido por mim no código dessa branch (não rodei os testes dele):

- O detalhe do atendimento (`GET` e a resposta do `PATCH`) **agora traz `sync_seq`**. A correção da seção anterior fica resolvida na branch dele, ainda não mesclada.
- `pull_atendimentos(desde=0, limite=200)`, em `apps/atendimentos/sincronizacao.py`: devolve `{itens, cursor, tem_mais}` por `sync_seq` crescente, no máximo **500** por chamada (padrão 200, o que bate com o ponto H). **Não recebe `usuario`** (diferente do rascunho): o RLS decide o que cada perfil recebe, pela conexão da API. Excluído desce como tombstone `{id_atendimento, versao, sync_seq}`. Com página vazia, o cursor devolvido é o próprio `desde`. Só cobre a entidade `atendimento`; a rota `GET /sync/pull`, o push, as outras entidades e a tabela de operações **não existem**.
- **Chave do id (decisão a):** o rascunho usava `id`; a API real usa `id_atendimento`. Os registros de atendimento acima (resposta do push, conflito e pull) passaram a usar `id_atendimento`, para o registro ser o mesmo JSON do detalhe. A operação do push (`id` na seção 3) e a evidência (`aa01...`) **não foram alteradas**: o nome da chave da evidência ainda não está definido. A Nicole precisa ler `id_atendimento` no Room.
- **Histórico offline (decisão b), proposta do Rafael:** a linha do tempo do detalhe precisa do histórico sem internet, e a tabela de histórico não tem `sync_seq` próprio. Ele propõe incluir a chave `historico` dentro do registro do atendimento. **Implementada em 16/11/2026 (ver a atualização abaixo); a decisão do grupo continua sem resposta.**
- **Quem implementa a rota `GET /sync/pull` e o push (decisão c):** continua em aberto (ponto I da seção 9).

## Atualização de 17/11/2026 (Rafael, branch `rafael-atendimentos-models`, commit `060b4b0`, de 16/11 às 10:46)

Conferido por mim lendo o código e a mensagem do commit (`sincronizacao.py`, `serializers.py`, `docs/api/atendimentos-openapi.yaml`). **Não rodei os testes.** A branch continua sem mesclar na `main` (PR #3).

- **O histórico agora desce no pull, dentro do registro do atendimento**, na chave `historico`. Ele tem o mesmo formato e a mesma ordem do `GET /atendimentos/{id}`: do mais antigo para o mais novo, por `data_evento` e, em empate, `id_historico`. Cada item tem `id_historico`, `tipo_evento`, `origem_evento`, `valor_anterior`, `valor_novo` e `data_evento`. A observação e o responsável **não** vão (a observação é texto livre e pode ter dado pessoal). O `id_historico` é estável, e o app pode usá-lo para não duplicar o evento.
- **Cada atendimento que desce leva a linha do tempo inteira**, não só os eventos novos. O tombstone (`removido: true`) **não** leva histórico. Atendimento sem eventos devolve `historico: []`.
- **Custo:** no máximo três consultas ao banco por página (atendimentos, culturas e histórico), qualquer que seja o tamanho da página. O histórico é lido pela mesma permissão do banco: quem não enxerga o atendimento não recebe o histórico dele.
- **Limitação conhecida, documentada pelo próprio Rafael:** três eventos (`reagendado`, `reclassificado` e `editado_pos_finalizacao`) são gravados pelo banco **sem nenhum UPDATE no atendimento**. O `sync_seq` do atendimento não sobe, então esses eventos só chegam no pull quando o atendimento mudar por outro motivo. O `GET /atendimentos/{id}` (online) sempre lê o histórico direto da tabela e traz tudo. A Nicole precisa saber disso ao montar a linha do tempo offline.
- **Decisão em aberto (b):** a Nicole e o grupo ainda não responderam se o histórico dentro do registro é o formato definitivo. Duas saídas foram levantadas para a limitação acima, e **nenhuma está decidida nem implementada:** (1) uma migração que suba só o `sync_seq` do atendimento quando um desses três eventos for gravado, sem somar na `versao`; (2) dar `sync_seq` próprio ao `historico_atendimento`, o que mudaria o formato acima. Quem tem que escolher é o grupo, com a Nicole.
- **O que continua igual:** a rota `GET /sync/pull`, o push, as outras entidades (evidência, agendamento, relatório) e a tabela de operações **não existem**. O ponto I (quem implementa) segue em aberto.
