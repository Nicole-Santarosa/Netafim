# Estratégia de idempotência

> **Status:** proposta v0.1; depende da aprovação e implementação dos mecanismos descritos.

## 1. Objetivo

Garantir que uma mesma operação enviada mais de uma vez — por exemplo, após perda de conexão ou de resposta — não produza duplicidade nem aplique alterações indevidas.

## 2. Identificadores

O contrato prevê dois identificadores UUID com finalidades diferentes:

- **UUID do registro (`id`)**: identifica a entidade no banco e pode ser gerado no aparelho para registros criados offline.
- **UUID da operação (`id_operacao`)**: identifica uma tentativa lógica de alteração enviada à API. Deve ser único e permanecer igual em todos os reenvios daquela mesma operação.

Não gerar um novo `id_operacao` ao repetir uma requisição cuja resposta se perdeu. O uso do mesmo identificador permite ao servidor reconhecer o reenvio.

## 3. Resultados esperados no PUSH

| Situação | Resultado proposto |
|---|---|
| Operação ainda não processada e válida | `aplicado` |
| Operação já processada anteriormente | `duplicado`, acompanhado do registro atual |
| Atualização com versão-base desatualizada | `conflito` |
| Operação recusada por regra, permissão ou validação | `rejeitado` |

O app deve tratar `duplicado` como sucesso equivalente a `aplicado`, atualizando sua cópia local com o registro retornado.

## 4. Persistência de operações processadas

O contrato identifica uma lacuna no banco: ainda não existe uma tabela para registrar operações já processadas. A proposta é criar `sync_operacao`, com os campos:

- `id_operacao` (chave primária);
- `id_dispositivo`;
- `resultado`;
- `resposta` (`jsonb`);
- `criado_em`.

Essa tabela permitiria recuperar a resposta de uma operação previamente aplicada, em vez de tentar executá-la novamente.

**Atenção:** a tabela é uma proposta pendente de aprovação. O documento de origem sugere uma migração futura, mas o número da migração não deve ser considerado reservado ou confirmado sem conferência do histórico real do banco e aprovação do responsável.

## 5. Criação com UUID já existente

O contrato define o tratamento especial:

- se o UUID já existe e o conteúdo é igual, retornar `duplicado`;
- se o UUID já existe e o conteúdo é diferente, retornar `conflito`.

A comparação de conteúdo precisa ser definida de forma consistente no backend, considerando a representação canônica dos campos relevantes. O contrato não detalha algoritmo de canonicalização; portanto, isso permanece uma decisão de implementação.

## 6. Atualizações e perda de resposta

O contrato destaca um caso importante: sem persistência de `id_operacao`, se uma atualização for aplicada e a resposta se perder, o reenvio pode encontrar uma versão já incrementada e ser interpretado como conflito falso.

A tabela de operações processadas deve permitir devolver o resultado original associado à mesma operação, sem reaplicar a alteração.

## 7. Upload de evidências

Para o binário, o contrato propõe reenvio seguro quando o mesmo hash é apresentado. O servidor valida `tamanho_bytes` e `hash_arquivo` antes de armazenar o arquivo.

A implementação deve garantir que uma repetição do mesmo upload não crie cópias redundantes nem altere indevidamente o registro. A política para mesmo identificador com hash diferente precisa ser definida explicitamente antes da implementação.

## 8. Responsabilidades do aplicativo

- criar `id_operacao` uma única vez por operação lógica;
- persistir a operação na fila local antes do envio;
- reutilizar o mesmo `id_operacao` em falhas de rede;
- remover/encerrar a operação da fila somente após resultado definitivo;
- não reenviar automaticamente operações `rejeitado` sem correção;
- tratar `duplicado` como sucesso;
- manter operações em conflito separadas para revisão.

## 9. Pendências

- aprovar e criar a tabela `sync_operacao`;
- definir prazo de retenção dos registros de operações;
- definir comportamento para reutilização indevida de `id_operacao` com payload diferente;
- definir canonicalização/comparação de payloads;
- especificar idempotência do upload para hash divergente;
- criar testes de repetição após timeout, perda de resposta e reenvio concorrente.

## 10. Referência

Baseado no **Contrato de Sincronização — proposta v0.1, 02/11/2026**. Os mecanismos são propostas e não devem ser considerados implementados sem validação do código e do banco.
