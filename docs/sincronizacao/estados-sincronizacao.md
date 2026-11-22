# Estados de sincronização

> **Status:** modelo proposto v0.1 para o aplicativo Android/Room.
> Os estados abaixo descrevem a situação de sincronização e não substituem os estados de negócio das entidades.

## 1. Objetivo

Padronizar como o aplicativo representa registros locais em relação ao servidor, evitando misturar o andamento da sincronização com o estado funcional do atendimento ou da evidência.

## 2. Separação entre estado de negócio e estado local

O estado de negócio do atendimento é controlado pelo servidor (por exemplo, `aberto`, `destinado` e demais estados definidos pela máquina de estados). Já `statusLocal` informa se os dados locais foram enviados, estão pendentes ou precisam de intervenção.

Esses conceitos devem permanecer em campos separados.

## 3. Estados locais propostos

O contrato apresenta quatro valores para `statusLocal`:

| Estado | Significado | Comportamento esperado |
|---|---|---|
| `sincronizado` | Registro local alinhado com uma versão recebida do servidor | Pode ser atualizado por novos dados válidos do PULL |
| `pendente_envio` | Existe criação/alteração local ainda não confirmada pelo servidor | Deve entrar na fila de PUSH; não pode ser sobrescrito pelo PULL |
| `conflito` | O servidor recusou a atualização por divergência de versão | Preservar payload local e versão do servidor separadamente |
| `erro` | A operação foi rejeitada ou falhou de forma que exige tratamento | Exibir motivo; corrigir ou descartar antes de novo envio |

O contrato não especifica uma taxonomia completa de erros transitórios versus permanentes. Essa distinção deverá ser definida na implementação, sem criar estados adicionais sem aprovação.

## 4. Origem do registro

O campo `origemRegistro` também é proposto com dois valores:

- `local`: registro criado no aparelho;
- `servidor`: registro recebido pelo PULL.

Um registro criado offline pode ter UUID local e `numeroProtocolo = null` até a confirmação do servidor. A interface pode informar “Aguardando sincronização”.

## 5. Campos relacionados

O modelo local descrito no contrato inclui:

| Campo | Uso |
|---|---|
| `idAtendimento` | UUID gerado no app e correspondente ao identificador do banco |
| `numeroProtocolo` | Nulo até o servidor atribuir o protocolo |
| `versaoBase` | Última versão do servidor conhecida pelo app; em novo registro local, 0/nulo conforme o modelo adotado |
| `syncSeqServidor` | `sync_seq` do registro individual recebido do servidor |
| `statusLocal` | Estado de sincronização local |
| `origemRegistro` | Origem local ou servidor |
| `dadosJson` | Representação do registro no formato da API |

O cursor global usado pelo PULL deve ser armazenado separadamente, por exemplo no DataStore. Ele não é o mesmo campo `syncSeqServidor`.

## 6. Transições operacionais

O contrato permite descrever o seguinte ciclo básico, sem estabelecer uma máquina de estados formal completa:

- **Criação/edição offline:** registrar a operação na fila e marcar `pendente_envio`.
- **PUSH com `aplicado` ou `duplicado`:** substituir os dados locais pelo registro retornado e marcar `sincronizado`.
- **PUSH com `conflito`:** guardar a versão do servidor e o payload pretendido; marcar `conflito`.
- **PUSH com `rejeitado`:** apresentar o erro e marcar `erro`; não repetir sem correção ou descarte.
- **PULL de registro remoto:** aplicar somente se a versão/sequência recebida for mais recente e não houver operação local pendente que deva ser preservada.
- **Exclusão lógica recebida:** aplicar o tombstone indicado por `removido = true`/`deletado_em`.

## 7. Fila local de operações

O contrato identifica a necessidade de uma tabela local de fila, contendo pelo menos:

- `id_operacao`;
- `entidade`;
- `id`;
- `tipo`;
- `versao_base`;
- `campos`;
- `tentativas`;
- `estado`.

A fila deve persistir as operações antes do envio e reutilizar o mesmo `id_operacao` em reenvios após falha de rede. O contrato recomenda backoff exponencial.

Também é necessária uma cópia local do `registro_servidor` para apoiar a apresentação e revisão de conflitos.

## 8. Cuidados na aplicação do PULL

- Aplicar dados recebidos de forma idempotente.
- Não substituir dados locais pendentes de envio.
- Usar versão/sequência para evitar regressão de dados.
- Processar tombstones para refletir exclusões lógicas.
- Atualizar o cursor global somente conforme o contrato de paginação que vier a ser aprovado.

A sobreposição de cursor de 1.000 posições aparece como mitigação proposta para risco de commits fora de ordem; não é garantia formal e depende da decisão pendente sobre cursor/marca-d'água no servidor.

## 9. Pendências

- aprovar os nomes e semântica dos estados;
- detalhar estados de falha transitória e permanente, se necessários;
- definir transições e recuperação de operações interrompidas;
- aprovar estrutura definitiva da fila local e armazenamento do payload de conflito;
- decidir a estratégia definitiva de cursor.

## 10. Referência

Baseado no **Contrato de Sincronização — proposta v0.1, 02/11/2026**. Este documento organiza a proposta e não representa uma máquina de estados já implementada.
