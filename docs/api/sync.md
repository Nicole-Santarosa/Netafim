# Sincronização Android e API: rotas de pull e de push

Status: as duas rotas estão implementadas e testadas contra o PostgreSQL local. `GET /sync/pull` é uma view fina sobre `pull_atendimentos(desde, limite)` (`backend/apps/atendimentos/sincronizacao.py`, view em `views_sync.py`). `POST /sync/push` está em `push.py` (lógica) e `views_sync.py` (view) e reaproveita `abrir_atendimento` e `atualizar_atendimento` (`servicos.py`), então a máquina de estados e as permissões continuam no gatilho do banco.

Responsável pelas rotas: Pessoa 2 (Rafael), conforme a divisão confirmada em 19/11/2026. A Pessoa 4 (Nicole) faz só a camada local do Android e consome estes resultados. Os nomes de tipo Kotlin não fazem parte deste contrato, são acordados à parte.

Revisão de 21/11/2026: datas com fuso (seção 1), duplicado sem registro tratado como erro (seções 3.4 e 4), nomes de campo separados de valores de `resultado` (seção 3.4) e propostas do grupo registradas na seção 6. Nada disso muda o código.

Estado em 24/11/2026 (commit 61b87d9): as duas rotas estão na branch `rafael-atendimentos-models`, sem merge na main, com 355 testes da suíte versionada passando. Não há teste com JWT real, porque o módulo de acesso do Rodrigo ainda não entrou; os testes usam `force_authenticate`.

A seção "Pontos em aberto" separa o que foi decidido (19/11/2026) do que segue pendente. O restante descreve o que o código faz hoje. Os estados usados no texto são: decidido ou aprovado (combinado, sem código, salvo quando o texto disser implementado), implementado (está no código, com o commit), validado (conferido em teste ou banco, com onde e quando) e pendente (o que ainda falta escrever, revisar, aplicar ou implementar). "Depende de validação" marca o que aguarda a análise do responsável pelo banco. Onde o texto diz "proposta", é algo que ainda não foi decidido nem implementado. O `pull_atendimentos` atual olha só o `sync_seq` do atendimento.

O mesmo conteúdo está no OpenAPI (`atendimentos-openapi.yaml`, tag `sync`). Em caso de divergência entre os dois, corrija os dois.

## 0. Fontes consultadas e o que não foi encontrado

- `docs/api/atendimentos-openapi.yaml`, `apps/atendimentos` (views, serializers, serviços, exceções, `pull_atendimentos`) e `apps/core/erros.py`: lidos nesta branch.
- Rascunho do Contrato de Sincronização (Rodrigo, v0.1 de 02/11/2026, com atualizações de 15 a 17/11): não está nesta branch. Está só em `origin/docs/contrato-sincronizacao-proposta`, arquivo `docs/api/contrato-sincronizacao-proposta.md`. Foi lido por `git show`, sem trocar de branch. Este documento parte dele e diverge em alguns nomes (seção 6, decisões).
- Seções 40 e 42 da documentação técnica (revisão de 19/11/2026): o texto não está em arquivo do repositório. Foi fornecido pelo Rafael e conferido contra este contrato, sem contradição de regra. As três divergências encontradas (GET do duplicado só no POST isolado, 400 sem `codigo` e 422 com `codigo`, rejeitado 409 `duplicado` sem registro) foram incorporadas à documentação técnica em 20/11/2026.
- Classe `ErroNegocio`: não existe nesta branch. Ela vive em `apps/contas/excecoes.py` nas branches do Rodrigo (ainda não mesclado) e produz o corpo `{"detail": ..., "codigo": ...}` mais campos extras. Aqui o equivalente são `ConflitoDeVersao` e `SemPermissaoParaAlterar` (`apps/atendimentos/excecoes.py`) e o tradutor `apps/core/erros.py`. O corpo de erro por operação deste contrato segue o mesmo formato `{codigo, detail}`.

## 1. Convenções comuns

- Base: `/api/v1`, rotas sem barra final, só JSON, `Authorization: Bearer <access>`.
- O usuário que o banco enxerga é o da sessão. O RLS e os gatilhos decidem permissão e máquina de estados, como no `POST` e no `PATCH` de hoje. A sincronização não reimplementa regra.
- Datas e fuso: o servidor usa `America/Sao_Paulo` com fuso ligado, e o DRF devolve datas ISO 8601 com o deslocamento do servidor, por exemplo `2026-11-02T11:03:11-03:00`, e não com `Z`. Confirmado em teste do DRF com essa configuração. O Android deve ler a data com o deslocamento (por exemplo `OffsetDateTime.parse`) e comparar por instante. Na entrada (`data_abertura`), o servidor aceita `Z` ou qualquer deslocamento.
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
            "data_evento": "2026-11-02T11:03:11-03:00"
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
   - O cursor deve ser lido como uma posição de sincronização com sobreposição e recuperação por resincronização completa, e não como uma garantia absoluta de entrega.
   - Motivo: o `sync_seq` é tirado por `nextval` na escrita e não no commit. Uma transação que confirma depois de a sua posição já ter ficado fora da janela de 1.000 não é recuperada pela sobreposição. Só a resincronização completa recupera esse registro (seção 4, subseção "Resincronização completa").
   - A janela de sobreposição de 1.000 conta posições globais da `seq_sync`, uma sequência compartilhada por 22 tabelas, e não atendimentos. A migração 019 (`sync_seq` no histórico) também consome números dela, então a janela cobre ainda menos atendimentos do que o número sugere.
   - Melhoria futura, Depende de validação do Rodrigo: trava consultiva (advisory lock em `fn_sync_meta`) e pull com lock exclusivo curto lendo o `last_value` da sequência, para o cursor nunca passar de uma escrita ainda não confirmada. Impacto a avaliar: `fn_sync_meta`, concorrência e bloqueio de escrita. Não implementado; o `pull_atendimentos` atual continua olhando só o `sync_seq` do atendimento.
5. Paginação: repetir a chamada com `desde` igual ao `cursor` devolvido enquanto `tem_mais` for true.
6. Custo: no máximo 3 consultas por página (atendimentos, culturas, histórico), qualquer que seja o tamanho da página.

Limitação conhecida, válida hoje: três eventos de histórico (`reagendado`, `reclassificado` e `editado_pos_finalizacao`) são gravados pelo banco sem nenhum UPDATE no atendimento. O `sync_seq` do atendimento não sobe, então esses eventos só chegam no pull quando o atendimento mudar por outro motivo. O `GET /atendimentos/{id}` (online) lê o histórico direto da tabela e sempre traz tudo. A correção decidida é a coluna `sync_seq` em `historico_atendimento` (seção 6), ainda não implementada. Estado: Decidido com a Pessoa 4 (22/11/2026), não implementado, depende de migração do Rodrigo.

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
| id_operacao | sim | UUID gerado no app, único por operação. Na versão atual do contrato ele só serve para correlacionar a resposta (o servidor o devolve como veio). Reenviar uma operação da fila sempre com o mesmo `id_operacao`: a fila do app nunca gera um novo ao reenviar. |
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
- `id_distribuidor_origem` é opcional para Netafim e cliente; obrigatório e igual ao distribuidor do cadastro para o perfil Distribuidor (Admin ou Usuário). Para Netafim e cliente, o cliente deve omitir a chave quando não houver valor, e o servidor aceita omitida ou nula. Para o perfil Distribuidor, campo omitido, nulo ou de outro distribuidor é rejeitado com 400 no campo `id_distribuidor_origem` e a mensagem "Informe o distribuidor do seu cadastro.", no push (`rejeitado`, só daquela operação) e no `POST /atendimentos`;
- `id_cliente` para o perfil Distribuidor (Admin ou Usuário): quando preenchido, em qualquer origem, precisa ser um cliente da carteira do distribuidor do usuário. Fora da carteira ou inexistente é rejeitado com 400 no campo `id_cliente` e a mensagem "Informe um cliente da carteira do seu distribuidor.", no push (`rejeitado`, só daquela operação) e no `POST /atendimentos`. Nulo ou omitido continua aceito (detalhe na seção 6, bloco de 24/11/2026);
- `nome_produtor` e `nome_fazenda_informado` vazios ou só com espaços contam como ausentes. O cliente deve omitir a chave quando o texto estiver vazio;
- chaves desconhecidas e as que o servidor decide (`status`, `numero_protocolo`, `versao`, `sync_seq`, `prioridade`, destinação, técnico, snapshots, `id_solicitante`) são ignoradas. O solicitante é sempre o usuário da sessão;
- `id_atendimento` dentro do `payload` precisa ser igual ao `id_atendimento` da operação. Se diferir, a operação é rejeitada com 400 no campo `id_atendimento`.

Nota sobre `id_distribuidor_origem`: quando o usuário é distribuidor, o app deve enviar o campo com o valor vindo da sessão autenticada dele, e não de um campo de formulário. O servidor confere o valor contra o distribuidor do cadastro do usuário autenticado (`app_id_distribuidor()`), só quando o usuário é do perfil Distribuidor (`app_tipo_org() = 'distribuidor'`). Usuários Netafim e cliente não são afetados. O servidor não preenche o campo por conta própria, para não mudar a comparação do duplicado (seção 3.5). Para o Distribuidor Admin a obrigatoriedade também evita um 403 enganoso: a policy de SELECT só libera a linha quando o distribuidor de origem, o distribuidor destinado ou a carteira de clientes são dele, então sem o campo ele não consegue ler de volta o atendimento que abriu. O banco continua recusando id inexistente (422 `referencia_invalida`). Estado: Decidido e implementado em 23/11/2026.

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

Atenção a um ponto que gerou confusão: `criado`, `atualizado`, `duplicado`, `conflito_de_versao` e `rejeitado` são valores do campo `resultado`, não campos do JSON. `conflito_de_versao` também aparece como `codigo` dentro de `erro`. Os campos da operação enviada são `id_operacao`, `entidade`, `tipo`, `id_atendimento`, `versao_esperada` (só em atualizar) e `payload`. Nos resultados, o campo extra `comparacao` (`igual` ou `diferente`) existe só no `duplicado`.

Cada resultado tem `id_operacao`, `id_atendimento`, `resultado` e `status` (o código HTTP equivalente que a mesma operação teria no endpoint isolado). Os demais campos dependem do resultado:

| resultado | status | Quando | Corpo além dos campos fixos |
|---|---|---|---|
| criado | 201 | Atendimento novo gravado | registro: detalhe completo, com historico, versao, sync_seq e numero_protocolo |
| atualizado | 200 | Atualização aplicada | registro: detalhe novo, com a versao e o sync_seq novos |
| duplicado, comparacao igual | 200 | O UUID já existe, é visível ao usuário e o conteúdo é igual ao enviado | comparacao: "igual"; registro: o registro atual do servidor. É sucesso idempotente |
| duplicado, comparacao diferente | 409 | O UUID já existe, é visível ao usuário e o conteúdo é diferente | comparacao: "diferente"; erro com codigo "duplicado"; registro_servidor: o registro atual. É conflito |
| conflito_de_versao | 409 | `versao_esperada` diferente da versão atual do servidor | erro com codigo "conflito_de_versao", detail e versao_atual; registro_servidor (só no push, o PATCH isolado devolve só a versao_atual) |
| rejeitado | 400, 403, 404, 409 ou 422 | Validação, permissão, regra do banco ou atendimento invisível, ou criação com id_atendimento que já existe mas o usuário não enxerga (409 duplicado, sem registro) | erro com detail e, quando houver, codigo, campos e versao_atual |

Detalhe de cada rejeição (`rejeitado`):

| status | Causa | erro |
|---|---|---|
| 400 | Validação por campo (`tipo_venda` ausente, obrigatório da origem, lista fora dos valores, entidade ou tipo inválido, `id_atendimento` do payload diferente do da operação, `versao_esperada` ausente em atualizar, nenhuma mudança informada) | detail genérico e `campos`: `{"tipo_venda": ["Informe o tipo de venda (projeto ou venda_regular)."]}`. Sem codigo, como no DRF |
| 400 | `id_distribuidor_origem` ausente, nulo ou de outro distribuidor para usuário do perfil Distribuidor | detail genérico e `campos`: `{"id_distribuidor_origem": ["Informe o distribuidor do seu cadastro."]}`. Rejeita só aquela operação, sem codigo |
| 400 | `id_cliente` preenchido fora da carteira ou inexistente para usuário do perfil Distribuidor | detail genérico e `campos`: `{"id_cliente": ["Informe um cliente da carteira do seu distribuidor."]}`. Rejeita só aquela operação, sem codigo |
| 400 | Valor que o banco recusa por formato ou tamanho (22xxx) | codigo `valor_invalido` |
| 403 | RLS ou permissão: `sem_permissao` | codigo `sem_permissao` |
| 404 | Atualizar um atendimento inexistente, excluído ou invisível ao usuário | codigo `nao_encontrado` e detail "Não encontrado." O código existe só no push; o PATCH isolado segue sem codigo |
| 409 | Criar com um id_atendimento que já existe mas que o usuário não enxerga | codigo `duplicado` e detail "Já existe um registro com esses dados.", sem registro e sem registro_servidor, para não revelar que o atendimento existe |
| 422 | Gatilho ou CHECK do banco: `regra_de_negocio`, `referencia_invalida`, `campo_obrigatorio` | codigo e detail; no gatilho, a própria mensagem dele; no CHECK de tabela, mensagem fixa |

A tradução do erro do PostgreSQL é a mesma de `apps/core/erros.py` (a função `_traduzir` é reaproveitada). Erro de banco que a API não sabe traduzir não vira resultado: propaga como 500 e a transação da requisição é desfeita (o reenvio é seguro, porque criar é idempotente). O 409 `duplicado` de uma criação cujo id existe mas o usuário não enxerga também é um `rejeitado` (status 409, codigo `duplicado`, sem registro, para não revelar que o atendimento existe). Nesse caso, no push, o app marca a operação como erro local, sem GET e sem reenvio automático (a rejeição local sem GET está alinhada com a Pessoa 4; o não reenvio automático foi proposto a ela em 21/11/2026 e aguarda confirmação). Um UUID gerado no aparelho que colide com o de outra pessoa é praticamente impossível, então o caso indica defeito, e trocar a chave primária local e a fila automaticamente seria mais arriscado do que mostrar o erro. No `POST /atendimentos` isolado, a orientação do OpenAPI (gerar novo id e reenviar) continua valendo.

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
- A regra do `id_distribuidor_origem` do perfil Distribuidor (seção 3.2) não altera a comparação do duplicado, porque o servidor não preenche o campo e o valor gravado é o do payload. Caso de borda: se o Netafim Admin trocar o distribuidor do usuário entre a criação e o reenvio, o reenvio volta como `rejeitado` 400 (o payload já não confere com o cadastro) e não como `duplicado` igual. Sessão desatualizada: se a troca acontecer com a sessão do usuário aberta, o app mantém o id antigo do distribuidor até renovar a sessão, e o backend responde 400 no campo `id_distribuidor_origem` às criações novas até lá.

Regra combinada: conteúdo igual é sucesso, diferente é conflito. Quem confirma e entrega o resultado é a Pessoa 2, no servidor.

### 3.6 Idempotência

- Criar: a chave primária é o UUID gerado no app. Reenviar a mesma criação nunca duplica o atendimento. O reenvio volta como `duplicado` com `comparacao: igual`.
- Atualizar: o `UPDATE` só acontece com `versao = versao_esperada`, então reenviar não soma a `versao` duas vezes. Limitação: se a resposta de uma atualização se perdeu na rede, o reenvio volta como `conflito_de_versao` falso (a versão do servidor já subiu). O app consegue perceber olhando o `registro_servidor`, mas o servidor não distingue sem guardar as operações já processadas (ponto em aberto 4).
- Lote inteiro reenviado: cada operação é avaliada de novo pelas regras acima. O resultado é o mesmo de reenviar uma a uma, sem duplicar atendimento e sem somar versão duas vezes.

### 3.7 Notificações

A chamada das funções de notificação (migração 015) não foi implementada. Em `push.py` há um ponto de extensão comentado e marcado, logo depois do savepoint da criação. Quando a 015 existir, a chamada entra ali, com falha só em log, sem derrubar a operação nem mudar o resultado devolvido. Não faz parte do contrato HTTP.

Migrações vizinhas, do Rodrigo e ainda não aplicadas: a 017 é o gatilho de aprovação de cadastro, com a chamada dentro de `BEGIN ... EXCEPTION WHEN OTHERS` e `RAISE WARNING` só com o SQLSTATE; a 018 é a revogação de INSERT do `app_django` em `notificacao`. Ordem de reversão: 018, 017, 015.

Decidido, não implementado: a chamada das funções da migração 015 será feita na mesma requisição da mudança, porque os eventos `avaliacao_pendente` e `agendado` exigem que o ator seja o mesmo da transição. Regras: só quando o resultado for `criado` ou `atualizado`, nunca em `duplicado`; dentro de savepoint; sem notificar quando o ator é o próprio destinatário; uma nova tentativa em caso de erro 40001.

## 4. Como o Android consome

A tabela traz o mínimo que a Pessoa 4 precisa tratar. Os nomes de tipo Kotlin são acordados à parte.

O app deve separar três níveis de resultado e não misturá-los:

- Resultado por operação (campo `resultado` do push): `criado`, `atualizado`, `duplicado` com `comparacao: igual` (sucesso idempotente), `duplicado` com `comparacao: diferente` (duplicidade: mesmo id com dados diferentes), `conflito_de_versao` e `rejeitado`. Cada um tem tratamento próprio, na tabela abaixo.
- Falha do lote inteiro: a resposta não traz `resultados` (sem resposta, timeout, 5xx, 401 ou 400 de formato). Nenhuma operação do lote vale como concluída e a fila fica como está.
- Falha de persistência local (Room): o erro acontece ao gravar no aparelho um resultado do push ou uma página do pull que o servidor já entregou. Não é resposta da API. A operação não é marcada como concluída e o cursor do PULL não avança. No ciclo seguinte, uma criação reenviada volta como `duplicado` igual; numa atualização vale a limitação da seção 3.6.

PUSH (um resultado por operação):

| Situação para o app | Origem no contrato | O que a camada local faz |
|---|---|---|
| Sucesso | criado, atualizado, ou duplicado com comparacao igual | Troca o registro local pelo `registro` devolvido; guarda `versao`, `sync_seq` e `numero_protocolo`; marca `sincronizado` |
| Duplicidade | duplicado com comparacao diferente (409), ou rejeitado 409 duplicado sem registro | Diferente: tratar como conflito e guardar `registro_servidor`. Sem registro: marcar `erro` com a mensagem, sem GET e sem reenvio automático |
| Conflito de versão | conflito_de_versao (409, `versao_atual`) | Guardar `registro_servidor`; marcar `conflito`; manter o que o usuário digitou para refazer |
| Validação | rejeitado 400 com `campos`, ou 422 | Não reenviar igual. Mostrar `detail` e `campos`; marcar `erro`; o usuário corrige ou descarta |
| Autorização | rejeitado 403 ou 404 | Não reenviar. Marcar `erro` com a mensagem |
| Comunicação | sem resposta, timeout, 5xx do lote | Manter a fila; reenviar com os mesmos `id_operacao`, com espera crescente |
| Sessão | 401 do lote (`token_invalido` ou 401 sem codigo) | O interceptor renova o token e reenvia o lote. Só se a renovação falhar, interromper o ciclo, manter fila e cursor e pedir login. 403 por operação é `rejeitado` e não derruba a sessão |

PULL:

| Situação para o app | Origem no contrato | O que a camada local faz |
|---|---|---|
| Página recebida | itens com removido false | Aplicar de forma idempotente: só substituir se `versao` ou `sync_seq` forem maiores; nunca sobrescrever `pendente_envio`; guardar o `historico` do registro |
| Cursor e paginação | cursor e tem_mais | Guardar o cursor só depois de aplicar a página inteira; repetir com `desde` igual ao cursor enquanto `tem_mais`; pedir com sobreposição de 1.000, limitada a zero |
| Remoções | itens com removido true | Se a cópia local estiver em `sincronizado`, apagar pelo `id_atendimento` (o tombstone só tem id, versão e sync_seq). Decidido com a Pessoa 4 (22/11/2026), não implementado: se estiver em `pendente_envio` ou `conflito`, o tombstone não apaga o registro; se o PUSH seguinte devolver 404 ou 403, o registro vai para `erro`, mas os dados do usuário não são apagados (seção 4.1, passo 7) |
| Falha | 400, 401, erro de rede | Manter o cursor anterior e tentar de novo depois; 401 renova token |

### 4.1 Resincronização completa

Estado: Decidido com a Pessoa 4 (22/11/2026), não implementado. Não há rota nem parâmetro novo: é o `GET /sync/pull` de hoje usado com `desde = 0` e repetido até `tem_mais` ser false.

Para que serve: o cursor não é garantia absoluta (seção 2, regra 4). A resincronização completa é o caminho de recuperação de um registro que ficou para trás da janela de sobreposição de 1.000.

Gatilhos:

- ação manual do usuário;
- automática, após 7 dias sem sincronizar, quando houver conexão;
- após mudança de versão do contrato.

Procedimento:

1. Executar o PUSH primeiro.
2. Voltar o cursor a 0.
3. Executar o PULL de todas as páginas.
4. Nunca sobrescrever registro com `statusLocal` igual a `pendente_envio` ou `conflito`.
5. Aplicar de forma idempotente.
6. Persistir o cursor só depois de cada página inteira ter sido aplicada com sucesso no Room.
7. Os tombstones (`removido: true`) apagam a cópia local só quando ela está em `sincronizado`. Regra do tombstone, Decidido com a Pessoa 4 (22/11/2026), não implementado: se a cópia local estiver com `statusLocal` igual a `pendente_envio` ou `conflito`, o tombstone não apaga o registro. O app mantém a cópia e o que o usuário digitou. Se o PUSH seguinte devolver 404 ou 403 por operação (`rejeitado`), o registro vai para `erro` com a mensagem, mas os dados do usuário não são apagados. Só registros em `sincronizado` são apagados pelo tombstone.
8. Reconciliação. Regra aprovada em 24/11/2026, implementação pendente na camada local. Condições (na camada local, os estados são `SINCRONIZADO`, `PENDENTE_DE_ENVIO`, `CONFLITO` e `ERRO`):
   - roda só depois de todas as páginas do `desde = 0` aplicadas com sucesso e com `tem_mais` (`temMais` na camada local) igual a false na última página;
   - guarda os ids recebidos durante todo o PULL completo e compara com os atendimentos locais do usuário da sessão (`usuarioDonoId`);
   - remove somente as cópias em `sincronizado` cujo id não veio no PULL completo;
   - preserva as cópias em `pendente_envio`, `conflito` e `erro`;
   - qualquer falha de página, de comunicação ou de persistência cancela a reconciliação inteira;
   - nunca roda no PULL incremental.

   Motivo: o RLS de `atendimento` depende de `id_tecnico_responsavel` e `id_distribuidor`, que o PATCH pode alterar. Um atendimento reatribuído some do pull do técnico antigo sem tombstone, porque para ele o registro deixou de ser visível, e a cópia local dele só sai por este passo.

   Limitação: os tombstones nunca são apagados no servidor, então o `desde = 0` devolve todos eles e a lista só cresce (seção 5, limitação 6).

### 4.2 Ordem do ciclo

Ordem de sincronização: o rascunho do Rodrigo recomenda push antes de pull. A orquestração do ciclo completo é do app (um componente coordenador chama push e depois pull; ver seção 6, ponto em aberto 1). Alinhado com a Pessoa 4 em 22/11/2026, pendente de consolidação por escrito do grupo; implementação não iniciada. As duas rotas são independentes.

## 5. Limitações conhecidas

1. Eventos de histórico `reagendado`, `reclassificado` e `editado_pos_finalizacao` não avançam o `sync_seq` do atendimento (seção 2). Válida hoje. A correção decidida é a coluna `sync_seq` em `historico_atendimento` (seção 6), ainda não implementada.
2. O cursor é tirado na escrita e não no commit (seção 2, regra 4). Ele é uma posição de sincronização com sobreposição e recuperação por resincronização completa, e não uma garantia absoluta: uma transação que confirma depois de a sua posição sair da janela de 1.000 só é recuperada pela resincronização completa (seção 4, subseção "Resincronização completa"). A janela de 1.000 conta posições globais da `seq_sync`, compartilhada por 22 tabelas, e não atendimentos, e a migração 019 também consome números dela. A trava consultiva como melhoria futura Depende de validação do Rodrigo (seção 2, regra 4).
3. Reenvio de atualização cuja resposta se perdeu volta como conflito de versão (seção 3.6). Não há tabela de operações processadas (migração 020, em escrita, sem validação; seção 6). O app percebe olhando o `registro_servidor`. Ressalva da Pessoa 4, vale mesmo depois da 020: a retenção de 90 dias, igual à do refresh token, não é necessariamente o tempo máximo que uma operação fica pendente. Depois de expirada a retenção, o reenvio pode voltar como `conflito_de_versao` falso, sem duplicar a atualização.
4. O pull só olha o `sync_seq` do atendimento. Os vínculos de cultura têm `sync_seq` próprio, mas nenhuma rota da API altera cultura depois da abertura, e na abertura o vínculo é gravado na mesma transação do atendimento. Se isso mudar, o pull deixa de cobrir (seção 6, ponto em aberto 3).
5. Atendimento reatribuído sai do pull do técnico antigo sem tombstone. O RLS de `atendimento` depende de `id_tecnico_responsavel` e `id_distribuidor`, que o PATCH pode alterar, e para o técnico antigo o registro deixa de ser visível, em vez de aparecer como excluído. No pull incremental a cópia antiga continua no aparelho e só sai na próxima resincronização completa (seção 4.1, passo 8, regra aprovada em 24/11/2026, implementação pendente na camada local).
6. Os tombstones nunca são apagados no servidor: não há DELETE em `atendimento` e o expurgo de manutenção não os toca. Por isso o `desde = 0` sempre devolve todos e a lista só cresce.

## 6. Decisões e pontos em aberto

Decidido em 19/11/2026 e já refletido no código e neste contrato:

- Comparação do duplicado no servidor, com a regra da seção 3.5.
- Sem tabela de operações no PostgreSQL (migração 020, em escrita, sem validação): o push funciona sem ela. Criar é idempotente pelo UUID. Atualizar tem a limitação da seção 3.6.
- Nomes deste contrato: `id_atendimento`, `versao_esperada`, `payload` e os resultados `criado`, `atualizado`, `duplicado`, `conflito_de_versao`, `rejeitado`. Eles divergem do rascunho v0.1 do Rodrigo (`id`, `versao_base`, `campos`, `aplicado`, `conflito`).
- `id_atendimento` do payload diferente do da operação, e entidade desconhecida: 400 no campo, só daquela operação.
- Conflito não é gravado em `sync_conflito` na v1. A tabela existe no banco, mas não é usada: não há tela nem rota de resolução, e o `registro_servidor` já volta na resposta do push. Fica para outra fase.
- `registro_servidor` incluído no `conflito_de_versao` do push. O PATCH isolado não mudou.
- Lote acima de 50 operações: 400 no formato padrão, com a mensagem do limite (não 413).
- `desde` e `limite` inválidos no pull: 400 apontando o campo, validado pela view.
- 404 por operação do push traz `codigo: nao_encontrado`. O PATCH isolado não mudou.
- Notificações (migração 015): não implementadas, só o ponto de extensão comentado depois do savepoint da criação.
- Evidências e outras entidades fora do push: `entidade` obrigatória e só `atendimento`.
- Criação de id que existe mas o usuário não enxerga: rejeitado 409 `duplicado` sem registro. O `payload` de criar é o corpo inteiro do `POST /atendimentos`.

Decidido e implementado em 23/11/2026:

- `id_distribuidor_origem` obrigatório e igual ao distribuidor do cadastro para o usuário do perfil Distribuidor (Admin ou Usuário); 400 no campo `id_distribuidor_origem` quando ausente, nulo ou de outro distribuidor, no `POST /atendimentos` e no push (`rejeitado` só daquela operação, com `campos`). Opcional para Netafim e cliente, e o servidor não preenche o campo (seção 3.2). Não altera a comparação do duplicado (seção 3.5).

Decidido e implementado em 24/11/2026 (commit 61b87d9):

- `id_cliente` para o perfil Distribuidor (Admin ou Usuário): quando preenchido, precisa ser um cliente da carteira do distribuidor do usuário, conferido por `app_cliente_visivel(id_cliente)` na mesma consulta que lê o ator (`app_tipo_org()` e `app_id_distribuidor()`). Fora da carteira responde 400 no campo `id_cliente`, com a mensagem "Informe um cliente da carteira do seu distribuidor.". Nulo ou omitido continua aceito. Vale para qualquer origem, inclusive `cliente_final`. Cliente inexistente passa de 422 (`referencia_invalida`, chave estrangeira) para 400 nesse perfil. Cliente excluído logicamente ou inativo não é filtrado, igual ao banco. Vale no `POST /atendimentos` e no push, em que só a operação errada vira `rejeitado`, com status 400 e `campos`. Usuários Netafim e cliente não são afetados. O servidor não preenche nem altera o campo, então a comparação do duplicado (seção 3.5) não muda. Validado na suíte versionada (355 testes, 19 novos em `test_distribuidor_origem.py`) contra o PostgreSQL local em Docker, em 24/11/2026. No banco, a policy de INSERT ainda não amarra essa regra (proposta da migração 021, abaixo).

Decidido com a Pessoa 4 (22/11/2026), não implementado:

- Coordenador do ciclo, um componente no app que chama push e depois pull, com cada rota independente (seção 4.2 e ponto em aberto 1): Pessoa 2 implementa, só com interfaces, sem Retrofit. Ordem PUSH antes de PULL. Push com falha de rede não dispara pull; 401 interrompe; rejeição individual não impede o pull.
- `sync_seq` do histórico. Decidido com a Pessoa 4 (22/11/2026), não implementado, depende de migração do Rodrigo. Coluna `sync_seq` em `historico_atendimento`, usando a mesma `seq_sync` do atendimento, com um único cursor global. Estrutura confirmada pela Pessoa 4. Migração 019, número confirmado pelo Rodrigo. Estado: Decidido, não implementado. A migração faz `ADD COLUMN sync_seq bigint NOT NULL DEFAULT 0` (só metadado), cria um gatilho `BEFORE INSERT` próprio, com função `SECURITY DEFINER` e `search_path` fixo, que usa `nextval('seq_sync')` (só o `postgres` tem USAGE em `seq_sync`; a `fn_sync_meta` não serve, porque grava `data_atualizacao`, que o histórico não tem) e cria um índice comum em `sync_seq`, não único, porque os eventos antigos ficam com 0. Sem problema de volume: as tabelas estão vazias no banco real, que é PostgreSQL 17. A coluna nova não mexe em `fn_atendimento_antes`. Quem escreve o SQL é o responsável pela camada de pesquisa e notificações; o responsável pelo banco revisa e aplica. Estado da migração 019 em 26/11/2026: no bloco "Migrações 019 e 020", abaixo.
- Formato futuro do pull, com a coluna acima. Decidido, não implementado (o pull atual segue a seção 2):
  - um item por atendimento, com o histórico completo; o `limite` passa a contar atendimentos;
  - o `sync_seq` efetivo do atendimento na ordenação é o maior entre o dele e o dos seus eventos;
  - o `cursor` da página é o maior `sync_seq` efetivo da página, e o registro mantém o próprio `sync_seq`;
  - eventos antigos ficam com `sync_seq = 0` (o histórico é imutável, sem UPDATE) e só chegam ao aparelho pela resincronização completa (seção 4, subseção "Resincronização completa"), não pelo pull incremental;
  - regra do app depois da mudança: aplicar os eventos novos mesmo quando a `versao` do atendimento não mudou, deduplicando por `id_historico`. Hoje continua valendo a regra da seção 4 (só substituir se `versao` ou `sync_seq` forem maiores).
- Resincronização completa: gatilhos e procedimento na seção 4, subseção "Resincronização completa", com a regra do tombstone no passo 7. O passo 8 (reconciliação) é regra aprovada em 24/11/2026, com implementação pendente na camada local.
- `sync_operacao` na v1, versão mínima, só para atualizar. Decidido com a camada local (22/11/2026), não implementado. Chave: `id_operacao`, estável em todo reenvio. Na v1 só o resultado `atualizado` é guardado. Mesmo `id_operacao`, mesmo usuário e mesmo hash devolve o resultado original sem reaplicar. Migração 020 (estado no bloco "Migrações 019 e 020", abaixo). Especificação da 020:
  - colunas: `id_operacao` uuid (chave primária), `id_usuario`, `id_dispositivo`, `id_atendimento`, `hash_conteudo` (SHA-256 em hexadecimal, 64 caracteres), `resultado` com `CHECK (resultado IN ('atualizado'))` na v1, `resposta` jsonb e `criado_em`;
  - índice em `criado_em`;
  - RLS por `id_usuario = app_usuario_id()`: cada usuário só enxerga as próprias operações;
  - o `app_django` recebe só SELECT e INSERT;
  - o hash é determinístico: JSON canônico com chaves ordenadas e SHA-256 de `tipo`, `id_atendimento`, `versao_esperada` e `payload`;
  - mesmo `id_operacao` com hash diferente, ou de outro usuário, devolve 409 com `codigo: operacao_reutilizada`, sem revelar o resultado guardado. No contexto do sync, só o código é definido aqui; ele entra na tabela de erros do PR #31, não no PR #3;
  - retenção de 90 dias, igual à do refresh token. O pg_cron não faz parte da 020: a limpeza será uma migration separada, posterior à 020, com data a definir pelo responsável pelo banco;
  - concorrência: desenho acordado em 24/11/2026, com a confirmação final do responsável pelo banco ainda pendente, não implementado. Tudo dentro do savepoint da operação, na mesma transação da requisição:
    1. `pg_advisory_xact_lock(hashtextextended('sync_operacao:' || id_operacao::text, 0))`;
    2. SELECT da `sync_operacao` pelo `id_operacao`, sob RLS (só enxerga as operações do próprio usuário);
    3. se achar com o mesmo hash, devolve a `resposta` armazenada, sem reaplicar; se achar com hash diferente, devolve 409 `operacao_reutilizada`;
    4. se não achar, faz o PATCH (`atualizar_atendimento`) e grava o resultado com INSERT;
    5. erro 23505 nesse INSERT (o `id_operacao` é de outro usuário, invisível pela RLS no SELECT) desfaz o savepoint e devolve `operacao_reutilizada`.

    O desenho depende do nível de isolamento READ COMMITTED, para que o SELECT feito depois do lock enxergue a linha que outra transação confirmou enquanto esperava. É o padrão hoje: o Django roda com `ATOMIC_REQUESTS = True` e sem `isolation_level` definido nas opções do banco, então vale o padrão do PostgreSQL. Se o nível mudar, o desenho precisa ser revisto;
  - limitação conhecida, ressalva da camada local: o prazo do refresh token não é necessariamente o tempo máximo de uma operação pendente. Depois de expirada a retenção, o reenvio pode voltar como `conflito_de_versao` falso, sem duplicar a atualização, porque a `versao_esperada` já impede.
- `completarTipoDeVenda`, implementação da Pessoa 4, tela da Pessoa 2: `suspend fun completarTipoDeVenda(id: String, tipoVenda: TipoVenda): ResultadoDaAcao`. Atualiza o atendimento local e reativa a operação que já está na fila, sem criar duplicata. O sucesso só é retornado depois da confirmação da transação no Room.

Migrações 019 e 020, estado em 26/11/2026 (nenhuma aplicada no banco real):

- 019, coluna `sync_seq` em `historico_atendimento`, com a mesma sequência `seq_sync`: escrita pelo responsável pela camada de pesquisa e notificações. Validada localmente em PostgreSQL 16 em 26/11/2026, sobre um baseline mínimo: aplicação da migration, teste, rollback e INSERT como `app_django`. Pendentes: revisão e aplicação no banco real pelo responsável pelo banco. O pull ainda não usa a coluna (seção 2).
- 020, tabela `sync_operacao`: em escrita, sem validação. Especificação no item `sync_operacao`, acima. Não inclui pg_cron.
- Limpeza da retenção de 90 dias da `sync_operacao`: migration separada, posterior à 020, com data a definir pelo responsável pelo banco. Sem arquivo.

Depende de validação do Rodrigo:

- Observação, comportamento atual do banco: o Distribuidor Admin que abre sem `id_distribuidor_origem` e sem cliente recebe 403, porque a policy de SELECT não inclui `id_solicitante = usuário` para esse perfil e o `RETURNING` do INSERT não consegue ler a linha. A API já evita o caso ao exigir o campo (seção 3.2).
- Avaliação do watermark do cursor: trava consultiva em `fn_sync_meta` e pull com lock exclusivo curto lendo o `last_value` (seção 2, regra 4). Impacto em `fn_sync_meta`, concorrência e bloqueio de escrita.

Proposta, aguardando confirmação:

- Migração 021, `WITH CHECK` na policy de INSERT de `atendimento`. Proposta, aguardando o responsável pelo banco; não decidida e não implementada. Para o tipo distribuidor, exigiria `id_distribuidor_origem = app_id_distribuidor()` e (`id_cliente` nulo ou `app_cliente_visivel(id_cliente)`), para fechar por qualquer caminho o vazamento de visibilidade entre distribuidores. Hoje essas regras valem só na API (seção 3.2 e blocos de 23 e 24/11/2026 acima): a policy de INSERT ainda não amarra o perfil Distribuidor, só confere o `id_cliente` do usuário de cliente.
- Integração (enviada ao grupo em 21/11/2026): Pessoa 2 integra HTTP, coordenador e Room e escreve os testes integrados; Pessoa 4 testa a persistência Room; Pessoa 1 valida em aparelho contra o Supabase real depois do merge do acesso.

Em aberto:

1. Quem orquestra o ciclo completo (ordem de push e pull). Alinhado com a Pessoa 4 em 22/11/2026, pendente de consolidação por escrito do grupo; implementação não iniciada. A orquestração é do app (componente coordenador que chama push e depois pull) e cada rota fica independente. Fica registrado aqui até o grupo consolidar por escrito.
2. `sync_seq` dos eventos de histórico (`reagendado`, `reclassificado`, `editado_pos_finalizacao`). Decidido com a Pessoa 4 (22/11/2026), não implementado, depende de migração do Rodrigo. A decisão é a coluna `sync_seq` em `historico_atendimento`, com a mesma `seq_sync` e um único cursor global; o detalhe, o formato futuro do pull e a regra do app estão no bloco "Decidido com a Pessoa 4" acima. Até a migração entrar, vale a limitação da seção 2.
3. Vínculos de cultura fora do `sync_seq` do atendimento. Conferido em leitura do SQL (`infra/db/02_funcoes_gatilhos.sql`): o único gatilho de `atendimento_cultura` é `trg_sync` (`fn_sync_meta`), que só preenche o `data_atualizacao` e o `sync_seq` da própria linha de vínculo. Nada toca o atendimento. Hoje não há rota que mude cultura depois da abertura, e na abertura o vínculo nasce na mesma transação do atendimento, então não há perda. Se uma rota assim for criada, uma mudança de cultura não sobe o `sync_seq` do atendimento e não chegaria no pull. Nesse caso a mudança precisa subir o `sync_seq` do atendimento (uma regra própria, a decidir: subir o `sync_seq` do atendimento sem somar na `versao`).
4. Tabela `sync_operacao`, migração 020 (em escrita, sem validação). A versão mínima, só para atualizar, está decidida com a camada local (22/11/2026), não implementada. O desenho de concorrência foi acordado em 24/11/2026, com a confirmação final do responsável pelo banco ainda pendente, não implementado. A especificação e o desenho estão no item `sync_operacao` acima; a limpeza dos 90 dias fica para uma migration separada e posterior. Fica descrita como saída para o conflito falso do reenvio de atualização. Quando existir, o push devolve a `resposta` guardada quando a operação repetir.
