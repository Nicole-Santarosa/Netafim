# Sincronização Android e API: rotas de pull e de push

Status: as duas rotas estão implementadas e testadas contra o PostgreSQL local. `GET /sync/pull` é uma view fina sobre `pull_atendimentos(desde, limite)` (`backend/apps/atendimentos/sincronizacao.py`, view em `views_sync.py`). `POST /sync/push` está em `push.py` (lógica) e `views_sync.py` (view) e reaproveita `abrir_atendimento` e `atualizar_atendimento` (`servicos.py`), então a máquina de estados e as permissões continuam no gatilho do banco.

Responsável pelas rotas: Pessoa 2 (Rafael), conforme a divisão confirmada em 19/11/2026. A Pessoa 4 (Nicole) faz só a camada local do Android e consome estes resultados. Os nomes de tipo Kotlin não fazem parte deste contrato, são acordados à parte.

Estado em 20/11/2026: as duas rotas estão na branch `rafael-atendimentos-models`, sem merge na main, com 316 testes da suíte versionada passando. Não há teste com JWT real, porque o módulo de acesso do Rodrigo ainda não entrou; os testes usam `force_authenticate`.

A seção "Pontos em aberto" separa o que foi decidido (19/11/2026) do que segue pendente. O restante descreve o que o código faz. Onde o texto diz "proposta", é algo pendente que não foi implementado.

O mesmo conteúdo está no OpenAPI (`atendimentos-openapi.yaml`, tag `sync`). Em caso de divergência entre os dois, corrija os dois.

## 0. Fontes consultadas e o que não foi encontrado

- `docs/api/atendimentos-openapi.yaml`, `apps/atendimentos` (views, serializers, serviços, exceções, `pull_atendimentos`) e `apps/core/erros.py`: lidos nesta branch.
- Rascunho do Contrato de Sincronização (Rodrigo, v0.1 de 02/11/2026, com atualizações de 15 a 17/11): não está nesta branch. Está só em `origin/docs/contrato-sincronizacao-proposta`, arquivo `docs/api/contrato-sincronizacao-proposta.md`. Foi lido por `git show`, sem trocar de branch. Este documento parte dele e diverge em alguns nomes (seção 6, decisões).
- Seções 40 e 42 da documentação técnica (revisão de 19/11/2026): o texto não está em arquivo do repositório. Foi fornecido pelo Rafael e conferido contra este contrato, sem contradição de regra. As três divergências encontradas (GET do duplicado só no POST isolado, 400 sem `codigo` e 422 com `codigo`, rejeitado 409 `duplicado` sem registro) foram incorporadas à documentação técnica em 20/11/2026.
- Classe `ErroNegocio`: não existe nesta branch. Ela vive em `apps/contas/excecoes.py` nas branches do Rodrigo (ainda não mesclado) e produz o corpo `{"detail": ..., "codigo": ...}` mais campos extras. Aqui o equivalente são `ConflitoDeVersao` e `SemPermissaoParaAlterar` (`apps/atendimentos/excecoes.py`) e o tradutor `apps/core/erros.py`. O corpo de erro por operação deste contrato segue o mesmo formato `{codigo, detail}`.

## 1. Convenções comuns

- Base: `/api/v1`, rotas sem barra final, só JSON, `Authorization: Bearer <access>`.
- O usuário que o banco enxerga é o da sessão. O RLS e os gatilhos decidem permissão e máquina de estados, como no `POST` e no `PATCH` de hoje. A sincronização não reimplementa regra.
- Erros de lote (a requisição inteira falhou):
  - 401 com `codigo: token_invalido` (módulo de acesso) ou 401 padrão do DRF, que não traz `codigo`. O Android trata `codigo` como opcional.
  - Nesta branch, sem o módulo de acesso, a rota fechada responde 403 no formato padrão do DRF (já descrito em `NaoAutenticado` no OpenAPI). O 401 só aparece depois do merge do Rodrigo.
  - 400 de validação do formato do corpo, no formato padrão do DRF (`{"campo": ["mensagem"]}`), sem `codigo`.
- Erros por operação (dentro do push) seguem o formato do tradutor de erros: `{codigo, detail}`, mais `campos` quando for validação por campo e `versao_atual` no conflito de versão.

## 2. GET /api/v1/sync/pull

Base: `pull_atendimentos(desde, limite)`. A view é fina: lê os parâmetros, converte para inteiro, valida a faixa, chama a função e devolve o dicionário como está.

Parâmetros de consulta:

| Parâmetro | Tipo | Padrão | Faixa | Observação |
|---|---|---|---|---|
| desde | inteiro | 0 | maior ou igual a 0 | Cursor: devolve só registros com `sync_seq` maior que este valor (exclusivo). |
| limite | inteiro | 200 | 1 a 500 | Tamanho máximo da página. |

Valor fora da faixa ou que não seja inteiro (inclusive vazio, decimal e acima do maior `bigint`): 400 no formato padrão, apontando o campo (`{"desde": ["Informe um número inteiro."]}`). A validação é da view, antes de chamar a função. O cliente deve limitar `desde` a zero ao aplicar a sobreposição (ver regras).

Resposta 200:

```json
{
  "itens": [
    {
      "entidade": "atendimento",
      "removido": false,
      "registro": {
        "id_atendimento": "7c1e0000-0000-4000-8000-000000000001",
        "numero_protocolo": "OS-0042",
        "versao": 8,
        "sync_seq": 90431,
        "culturas": [{"id_cultura": 3, "nome": "Cafe"}],
        "historico": [
          {
            "id_historico": 501,
            "tipo_evento": "criado",
            "origem_evento": "usuario",
            "valor_anterior": null,
            "valor_novo": "aberto",
            "data_evento": "2026-11-02T14:03:11Z"
          }
        ]
      }
    },
    {
      "entidade": "atendimento",
      "removido": true,
      "registro": {"id_atendimento": "55d20000-0000-4000-8000-000000000002", "versao": 3, "sync_seq": 90433}
    }
  ],
  "cursor": 90433,
  "tem_mais": false
}
```

O exemplo abrevia o registro. O registro completo tem exatamente as chaves do detalhe (`AtendimentoDetalhe`, `GET /atendimentos/{id}`), mais `historico`.

Campos:

- itens: lista em ordem crescente de `sync_seq`, no máximo `limite`.
- entidade: sempre `atendimento` hoje. Reservado para outras entidades (evidência, agendamento) em versões futuras.
- removido: true quando o atendimento tem `deletado_em` preenchido.
- registro (removido false): o mesmo JSON do detalhe, que é o que o app guarda em `dadosJson`. Traz `culturas` (lista de `{id_cultura, nome}` em ordem de nome, sem vínculo nem cultura excluídos) e `historico`.
- historico: eventos do atendimento inteiro (não só os novos), do mais antigo para o mais novo, ordenados por `data_evento` e, em empate, por `id_historico`. Cada evento tem `id_historico`, `tipo_evento`, `origem_evento`, `valor_anterior`, `valor_novo` e `data_evento`. A observação e o responsável do evento não vão, porque a observação é texto livre e pode ter dado pessoal. O `id_historico` é estável: o app o usa para não duplicar o evento. Atendimento sem eventos devolve lista vazia.
- registro (removido true), o tombstone: só `id_atendimento`, `versao` e `sync_seq`. Sem `historico`, sem culturas, sem mais nenhum campo.
- cursor: o maior `sync_seq` da página. Se a página vier vazia, é o próprio `desde` recebido, para o app não andar para trás.
- tem_mais: true quando existe outra página. A função pede um registro além do limite para saber isso, sem `count()` à parte. Com a página terminando exatamente no último registro, vem false.

Regras:

1. Ordem crescente de `sync_seq`. Um atendimento alterado de novo muda de posição (vai para o fim), porque o `sync_seq` é novo.
2. Quem decide o que cada perfil recebe é o RLS, pela conexão da API com o usuário da sessão. A rota não filtra por usuário. Um atendimento que o perfil não enxerga não aparece, nem o histórico dele.
3. O RLS não esconde excluídos logicamente. Por isso o excluído que o perfil enxerga desce como tombstone.
4. O app pode receber o mesmo registro mais de uma vez. O cursor é tirado na escrita e não no commit: duas transações concorrentes podem confirmar fora de ordem, e um registro pode ficar para trás do cursor. A mitigação combinada é do cliente: pedir com sobreposição (`desde = max(0, cursor - 1000)`) e aplicar de forma idempotente (só substituir quando `versao` ou `sync_seq` forem maiores que os locais; nunca sobrescrever registro com `statusLocal = pendente_envio`).
5. Paginação: repetir a chamada com `desde` igual ao `cursor` devolvido enquanto `tem_mais` for true.
6. Custo: no máximo 3 consultas por página (atendimentos, culturas, histórico), qualquer que seja o tamanho da página.

Limitação conhecida, sem correção: três eventos de histórico (`reagendado`, `reclassificado` e `editado_pos_finalizacao`) são gravados pelo banco sem nenhum UPDATE no atendimento. O `sync_seq` do atendimento não sobe, então esses eventos só chegam no pull quando o atendimento mudar por outro motivo. O `GET /atendimentos/{id}` (online) lê o histórico direto da tabela e sempre traz tudo. Proposta pendente, não implementada: subir só o `sync_seq` do atendimento quando um dos três eventos for gravado, sem somar na `versao` (seção 6, ponto em aberto 2). Isso exige migração do Rodrigo.

## 3. POST /api/v1/sync/push

Implementado em `push.py` e `views_sync.py`.

### 3.1 Requisição

```json
{
  "operacoes": [
    {
      "id_operacao": "0b6f0000-0000-4000-8000-000000000010",
      "entidade": "atendimento",
      "tipo": "criar",
      "id_atendimento": "7c1e0000-0000-4000-8000-000000000001",
      "payload": {
        "id_atendimento": "7c1e0000-0000-4000-8000-000000000001",
        "origem": "cliente_final",
        "motivo": "duvidas",
        "id_cliente": 12,
        "cidade": "Jau",
        "nome_fazenda_informado": "Fazenda Esperanca",
        "contato_solicitante": "14999990000",
        "tipo_venda": "projeto",
        "culturas": [3]
      }
    },
    {
      "id_operacao": "9aa10000-0000-4000-8000-000000000011",
      "entidade": "atendimento",
      "tipo": "atualizar",
      "id_atendimento": "55d20000-0000-4000-8000-000000000002",
      "versao_esperada": 7,
      "payload": {"status": "cancelado"}
    }
  ]
}
```

Campos da operação:

| Campo | Obrigatório | Descrição |
|---|---|---|
| id_operacao | sim | UUID gerado no app, único por operação. Na versão atual do contrato ele só serve para correlacionar a resposta (o servidor o devolve como veio). Reenviar uma operação na fila com o mesmo `id_operacao`. |
| entidade | sim | Só `atendimento` na v1. Outro valor vira operação rejeitada (400). |
| tipo | sim | `criar` ou `atualizar`. |
| id_atendimento | sim | UUID do atendimento, gerado no app. |
| versao_esperada | só em atualizar | Última `versao` que o app recebeu do servidor para este atendimento. Inteiro maior ou igual a 1. Em criar, é ignorado. |
| payload | sim | Objeto com os campos. Veja 3.2 e 3.3. |

Lote: de 1 a 50 operações, processadas na ordem do array. Cada operação roda numa transação própria (savepoint), então uma recusa não derruba as outras e não aborta a transação da requisição. Invalidam o lote inteiro (400 no formato padrão, nenhuma operação executada): corpo fora do formato, lista vazia, mais de 50 operações (mensagem `O lote aceita no máximo 50 operações.`) e `id_operacao` ausente, que não seja UUID ou repetido dentro do lote. Qualquer outro problema de uma operação (entidade, tipo, `id_atendimento`, `versao_esperada`, payload) vira resultado `rejeitado` só daquela operação.

### 3.2 payload de criar

O mesmo corpo do `POST /atendimentos` (schema `AberturaAtendimento`), validado pelo mesmo serializer e gravado por `abrir_atendimento`. Valem todas as regras de hoje:

- obrigatórios sempre: `id_atendimento`, `origem`, `motivo`, `contato_solicitante`, `tipo_venda` (`projeto` ou `venda_regular`, sem valor padrão) e `culturas` com pelo menos um id;
- obrigatórios por origem: `cliente_final` exige `id_cliente`, `cidade` e `nome_fazenda_informado`; `distribuidor` exige `nome_produtor` e `cidade`; `netafim` exige `area_setor`, `nome_produtor` e `cidade`;
- `programacao_feiras` não é motivo de abertura (400 no campo `motivo`);
- `id_distribuidor_origem` é opcional em todas as origens. O cliente deve omitir a chave quando não houver valor. O servidor aceita omitida ou nula;
- `nome_produtor` e `nome_fazenda_informado` vazios ou só com espaços contam como ausentes. O cliente deve omitir a chave quando o texto estiver vazio;
- chaves desconhecidas e as que o servidor decide (`status`, `numero_protocolo`, `versao`, `sync_seq`, `prioridade`, destinação, técnico, snapshots, `id_solicitante`) são ignoradas. O solicitante é sempre o usuário da sessão;
- `id_atendimento` dentro do `payload` precisa ser igual ao `id_atendimento` da operação. Se diferir, a operação é rejeitada com 400 no campo `id_atendimento`.

### 3.3 payload de atualizar

Só os campos que o `PATCH /atendimentos/{id}` aceita hoje: `status`, `destinado_a`, `id_distribuidor`, `id_tecnico_responsavel` (aceita nulo para desvincular) e `situacao_projeto` (aceita nulo). A versão não vai no `payload`, vai em `versao_esperada` (uma `versao` dentro do `payload` é ignorada). Exige ao menos um desses campos. Outras chaves são ignoradas, inclusive `tipo_venda`, que o `PATCH` ignora de propósito. Aplicado por `atualizar_atendimento`: `UPDATE ... WHERE versao = versao_esperada`. A máquina de estados e quem pode cada mudança são do gatilho do banco.

### 3.4 Resposta

Lote lido: `200 OK`, sempre, mesmo com falhas dentro. Um resultado por operação, na mesma ordem e com o mesmo tamanho da lista enviada.

```json
{
  "resultados": [
    {"id_operacao": "0b6f...", "id_atendimento": "7c1e...", "resultado": "criado", "status": 201, "registro": {"...": "..."}},
    {"id_operacao": "9aa1...", "id_atendimento": "55d2...", "resultado": "conflito_de_versao", "status": 409,
     "erro": {"codigo": "conflito_de_versao", "detail": "O atendimento foi alterado por outra pessoa. Atualize e tente de novo.", "versao_atual": 8},
     "registro_servidor": {"...": "..."}}
  ]
}
```

Cada resultado tem `id_operacao`, `id_atendimento`, `resultado` e `status` (o código HTTP equivalente que a mesma operação teria no endpoint isolado). Os demais campos dependem do resultado:

| resultado | status | Quando | Corpo além dos campos fixos |
|---|---|---|---|
| criado | 201 | Atendimento novo gravado | registro: detalhe completo, com historico, versao, sync_seq e numero_protocolo |
| atualizado | 200 | Atualização aplicada | registro: detalhe novo, com a versao e o sync_seq novos |
| duplicado, comparacao igual | 200 | O UUID já existe, é visível ao usuário e o conteúdo é igual ao enviado | comparacao: "igual"; registro: o registro atual do servidor. É sucesso idempotente |
| duplicado, comparacao diferente | 409 | O UUID já existe, é visível ao usuário e o conteúdo é diferente | comparacao: "diferente"; erro com codigo "duplicado"; registro_servidor: o registro atual. É conflito |
| conflito_de_versao | 409 | `versao_esperada` diferente da versão atual do servidor | erro com codigo "conflito_de_versao", detail e versao_atual; registro_servidor (só no push, o PATCH isolado devolve só a versao_atual) |
| rejeitado | 400, 403, 404 ou 422 | Validação, permissão, regra do banco ou atendimento invisível | erro com detail e, quando houver, codigo, campos e versao_atual |

Detalhe de cada rejeição (`rejeitado`):

| status | Causa | erro |
|---|---|---|
| 400 | Validação por campo (`tipo_venda` ausente, obrigatório da origem, lista fora dos valores, entidade ou tipo inválido, `id_atendimento` do payload diferente do da operação, `versao_esperada` ausente em atualizar, nenhuma mudança informada) | detail genérico e `campos`: `{"tipo_venda": ["Informe o tipo de venda (projeto ou venda_regular)."]}`. Sem codigo, como no DRF |
| 400 | Valor que o banco recusa por formato ou tamanho (22xxx) | codigo `valor_invalido` |
| 403 | RLS ou permissão: `sem_permissao` | codigo `sem_permissao` |
| 404 | Atualizar um atendimento inexistente, excluído ou invisível ao usuário | codigo `nao_encontrado` e detail "Não encontrado." O código existe só no push; o PATCH isolado segue sem codigo |
| 422 | Gatilho ou CHECK do banco: `regra_de_negocio`, `referencia_invalida`, `campo_obrigatorio` | codigo e detail; no gatilho, a própria mensagem dele; no CHECK de tabela, mensagem fixa |

A tradução do erro do PostgreSQL é a mesma de `apps/core/erros.py` (a função `_traduzir` é reaproveitada). Erro de banco que a API não sabe traduzir não vira resultado: propaga como 500 e a transação da requisição é desfeita (o reenvio é seguro, porque criar é idempotente). O 409 `duplicado` de uma criação cujo id existe mas o usuário não enxerga também é um `rejeitado` (status 409, codigo `duplicado`, sem registro, para não revelar que o atendimento existe). Nesse caso o app gera um novo `id_atendimento` e reenvia o mesmo conteúdo como operação nova (mesma orientação do contrato de `POST /atendimentos`).

### 3.5 Como o servidor decide o duplicado

1. O payload é validado primeiro. Inválido vira `rejeitado` 400, sem tocar no banco.
2. O servidor tenta criar. Se o banco responder 23505 (UUID já existe), relê o atendimento pelo `id_atendimento`.
3. Se a leitura não achar nada (o RLS esconde), o resultado é `rejeitado` 409 `duplicado`, sem registro.
4. Se achar, compara o conteúdo. Todos iguais: `duplicado` com `comparacao: igual` (sucesso). Algum diferente: `duplicado` com `comparacao: diferente` (conflito).

Regra de comparação (decidida em 19/11/2026):

- Só entram os campos que o serializer de criação aceita (lista fechada). O que o servidor gera fica de fora: `numero_protocolo`, `versao`, `sync_seq`, `historico`, datas geradas e o solicitante.
- Chave ausente no payload conta como nulo ou vazio gravado: é igual se o gravado for nulo ou texto vazio, e diferente se o gravado tiver valor. Nulo e texto vazio enviados também contam igual ao nulo gravado.
- Culturas são comparadas como conjunto de ids, sem ordem.
- `data_abertura` é opcional no serializer (`required: False`, porque o banco usa `now()` por padrão). Só entra na comparação quando o cliente a enviou, e aí vale o mesmo instante em UTC, mesmo escrito em outro fuso. Se não foi enviada, o valor gravado é do servidor e fica de fora.
- Atendimento excluído logicamente conta como não visível (cai no passo 3).

Regra combinada: conteúdo igual é sucesso, diferente é conflito. Quem confirma e entrega o resultado é a Pessoa 2, no servidor.

### 3.6 Idempotência

- Criar: a chave primária é o UUID gerado no app. Reenviar a mesma criação nunca duplica o atendimento. O reenvio volta como `duplicado` com `comparacao: igual`.
- Atualizar: o `UPDATE` só acontece com `versao = versao_esperada`, então reenviar não soma a `versao` duas vezes. Limitação: se a resposta de uma atualização se perdeu na rede, o reenvio volta como `conflito_de_versao` falso (a versão do servidor já subiu). O app consegue perceber olhando o `registro_servidor`, mas o servidor não distingue sem guardar as operações já processadas (ponto em aberto 4).
- Lote inteiro reenviado: cada operação é avaliada de novo pelas regras acima. O resultado é o mesmo de reenviar uma a uma, sem duplicar atendimento e sem somar versão duas vezes.

### 3.7 Notificações

A chamada das funções de notificação (migração 015) não foi implementada. Em `push.py` há um ponto de extensão comentado e marcado, logo depois do savepoint da criação. Quando a 015 existir, a chamada entra ali, com falha só em log, sem derrubar a operação nem mudar o resultado devolvido. Não faz parte do contrato HTTP.

## 4. Como o Android consome

A tabela traz o mínimo que a Pessoa 4 precisa tratar. Os nomes de tipo Kotlin são acordados à parte.

PUSH (um resultado por operação):

| Situação para o app | Origem no contrato | O que a camada local faz |
|---|---|---|
| Sucesso | criado, atualizado, ou duplicado com comparacao igual | Troca o registro local pelo `registro` devolvido; guarda `versao`, `sync_seq` e `numero_protocolo`; marca `sincronizado` |
| Duplicidade | duplicado com comparacao diferente (409), ou rejeitado 409 duplicado sem registro | Diferente: tratar como conflito e guardar `registro_servidor`. Sem registro: gerar novo `id_atendimento` e reenviar como operação nova |
| Conflito de versão | conflito_de_versao (409, `versao_atual`) | Guardar `registro_servidor`; marcar `conflito`; manter o que o usuário digitou para refazer |
| Validação | rejeitado 400 com `campos`, ou 422 | Não reenviar igual. Mostrar `detail` e `campos`; marcar `erro`; o usuário corrige ou descarta |
| Autorização | rejeitado 403 ou 404 | Não reenviar. Marcar `erro` com a mensagem |
| Comunicação | sem resposta, timeout, 5xx do lote | Manter a fila; reenviar com os mesmos `id_operacao`, com espera crescente |
| Sessão | 401 do lote (`token_invalido` ou 401 sem codigo) | Renovar o token e reenviar o lote; sem renovação, pedir login |

PULL:

| Situação para o app | Origem no contrato | O que a camada local faz |
|---|---|---|
| Página recebida | itens com removido false | Aplicar de forma idempotente: só substituir se `versao` ou `sync_seq` forem maiores; nunca sobrescrever `pendente_envio`; guardar o `historico` do registro |
| Cursor e paginação | cursor e tem_mais | Guardar o cursor só depois de aplicar a página inteira; repetir com `desde` igual ao cursor enquanto `tem_mais`; pedir com sobreposição de 1.000, limitada a zero |
| Remoções | itens com removido true | Apagar a cópia local pelo `id_atendimento` (o tombstone só tem id, versão e sync_seq) |
| Falha | 400, 401, erro de rede | Manter o cursor anterior e tentar de novo depois; 401 renova token |

Ordem de sincronização: o rascunho do Rodrigo recomenda push antes de pull. A orquestração do ciclo completo é do app (um componente coordenador chama push e depois pull), decisão ainda pendente do grupo (seção 6, ponto em aberto 1). As duas rotas são independentes.

## 5. Limitações conhecidas

1. Eventos de histórico `reagendado`, `reclassificado` e `editado_pos_finalizacao` não avançam o `sync_seq` do atendimento (seção 2).
2. O cursor é tirado na escrita e não no commit (seção 2, regra 4).
3. Reenvio de atualização cuja resposta se perdeu volta como conflito de versão (seção 3.6). Não há tabela de operações processadas (migração 009 não criada). O app percebe olhando o `registro_servidor`.
4. O pull só olha o `sync_seq` do atendimento. Os vínculos de cultura têm `sync_seq` próprio, mas nenhuma rota da API altera cultura depois da abertura, e na abertura o vínculo é gravado na mesma transação do atendimento. Se isso mudar, o pull deixa de cobrir (seção 6, ponto em aberto 3).

## 6. Decisões e pontos em aberto

Decidido em 19/11/2026 e já refletido no código e neste contrato:

- Comparação do duplicado no servidor, com a regra da seção 3.5.
- Sem tabela de operações no PostgreSQL (migração 009 reservada, sem arquivo): o push funciona sem ela. Criar é idempotente pelo UUID. Atualizar tem a limitação da seção 3.6.
- Nomes deste contrato: `id_atendimento`, `versao_esperada`, `payload` e os resultados `criado`, `atualizado`, `duplicado`, `conflito_de_versao`, `rejeitado`. Eles divergem do rascunho v0.1 do Rodrigo (`id`, `versao_base`, `campos`, `aplicado`, `conflito`).
- `id_atendimento` do payload diferente do da operação, e entidade desconhecida: 400 no campo, só daquela operação.
- Conflito não é gravado em `sync_conflito` na v1: não há tela nem rota de resolução, e o `registro_servidor` já volta na resposta.
- `registro_servidor` incluído no `conflito_de_versao` do push. O PATCH isolado não mudou.
- Lote acima de 50 operações: 400 no formato padrão, com a mensagem do limite (não 413).
- `desde` e `limite` inválidos no pull: 400 apontando o campo, validado pela view.
- 404 por operação do push traz `codigo: nao_encontrado`. O PATCH isolado não mudou.
- Notificações (migração 015): não implementadas, só o ponto de extensão comentado depois do savepoint da criação.
- Evidências e outras entidades fora do push: `entidade` obrigatória e só `atendimento`.
- Criação de id que existe mas o usuário não enxerga: rejeitado 409 `duplicado` sem registro. O `payload` de criar é o corpo inteiro do `POST /atendimentos`.

Em aberto:

1. Quem orquestra o ciclo completo (ordem de push e pull). Pendente do grupo. A orquestração é do app (componente coordenador que chama push e depois pull) e cada rota fica independente. Fica registrado aqui até o grupo fechar.
2. `sync_seq` próprio para eventos de histórico. Pendente, não implementado. Recomendação: subir só o `sync_seq` do atendimento quando um dos três eventos (`reagendado`, `reclassificado`, `editado_pos_finalizacao`) for gravado, sem somar na `versao`, porque não muda o formato do pull. Depende de migração do Rodrigo. A seção 42 da documentação técnica lista a alternativa de `sync_seq` próprio no histórico, que muda o formato do pull e exigiria cursor separado.
3. Vínculos de cultura fora do `sync_seq` do atendimento. Conferido em leitura do SQL (`infra/db/02_funcoes_gatilhos.sql`): o único gatilho de `atendimento_cultura` é `trg_sync` (`fn_sync_meta`), que só preenche o `data_atualizacao` e o `sync_seq` da própria linha de vínculo. Nada toca o atendimento. Hoje não há rota que mude cultura depois da abertura, e na abertura o vínculo nasce na mesma transação do atendimento, então não há perda. Se uma rota assim for criada, uma mudança de cultura não sobe o `sync_seq` do atendimento e não chegaria no pull. Nesse caso a mudança precisa subir o `sync_seq` do atendimento (o mesmo tratamento do ponto 2, sem somar na `versao`).
4. Migração 009 e tabela `sync_operacao` (`id_operacao` como chave, `id_dispositivo`, `resultado`, `resposta` em jsonb, data). Fica descrita como saída para o conflito falso do reenvio de atualização, sem criar nada. Se for criada, o push devolve a `resposta` guardada quando a operação repetir.
