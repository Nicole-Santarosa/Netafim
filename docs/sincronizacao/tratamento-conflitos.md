# Tratamento de conflitos de sincronização

> **Status:** política proposta para v1, pendente de aprovação do grupo.
> A API de sincronização descrita é proposta e ainda não está implementada.

## 1. Objetivo

Definir como o sistema identifica alterações concorrentes entre o Android (Room) e o servidor, preservando a integridade dos dados e a intenção do usuário.

## 2. Detecção por controle otimista de versão

Toda atualização enviada pelo app informa `versao_base`, correspondente à última versão do registro que o aparelho recebeu do servidor.

O servidor compara esse valor com a `versao` atual no banco:

| Condição | Comportamento proposto |
|---|---|
| `versao_base` igual à versão atual | Aplicar a atualização; o banco incrementa `versao`; resultado `aplicado` |
| `versao_base` diferente da versão atual | Não aplicar a atualização; registrar conflito e retornar `conflito` |

Essa regra segue a lógica descrita para o PATCH atual, que já prevê conflito de versão (`409 conflito_de_versao`). No endpoint de sincronização, o conflito é representado como resultado individual da operação.

## 3. Registro do conflito

Quando houver conflito, o servidor deverá registrar uma ocorrência em `sync_conflito`, com os dados previstos no contrato:

- `entidade_tipo`;
- `entidade_uuid`;
- `versao_servidor`;
- `versao_cliente` (valor de `versao_base`);
- `payload_cliente` (alteração pretendida pelo app);
- `id_dispositivo` (derivado da sessão);
- `status = aberto`.

A resposta do PUSH inclui `registro_servidor` e `id_conflito`.

## 4. Política proposta para a v1: servidor vence, intenção é preservada

A política sugerida é **“o servidor vence, a intenção do app é preservada”**:

1. O app substitui a representação ativa do registro pela versão atual do servidor.
2. O conteúdo que o usuário tentou enviar é preservado em `payload_cliente` no servidor e em uma cópia local.
3. O usuário pode revisar a intenção e refazer a alteração, caso ela ainda faça sentido, agora partindo da versão atual.

A proposta não realiza mesclagem automática campo a campo. O contrato justifica essa escolha porque não há histórico completo de alterações por campo; o histórico existente cobre eventos específicos, como status, destino e técnico, e não permite reconstruir com segurança todas as mudanças desde a versão-base.

## 5. Regras especiais

### Status

O status deve permanecer sob autoridade do servidor e respeitar a máquina de estados implementada por gatilhos/regras de banco. Uma operação com versão-base antiga resulta em conflito. Uma operação que viole a máquina de estados, mesmo com versão atual, deve ser rejeitada.

### Criação com UUID já existente

- Mesmo UUID e mesmo conteúdo: retornar `duplicado`, como sucesso idempotente.
- Mesmo UUID e conteúdo diferente: retornar `conflito`.

## 6. Resolução administrativa

A resolução manual por Netafim Admin — com estados como `resolvido_servidor`, `resolvido_cliente` ou `descartado` — está fora do escopo proposto para a v1. As ocorrências permaneceriam abertas até existir uma tela e uma rota próprias.

## 7. Comportamento do aplicativo

O app deve distinguir o estado de sincronização do registro do estado de negócio do atendimento. Para conflito:

- manter a versão do servidor como representação atual;
- preservar separadamente o payload local conflitante;
- marcar o registro/operação como `conflito`;
- informar o usuário e permitir revisão posterior;
- não reenviar automaticamente a mesma alteração conflitante sem revisão.

## 8. Pendências

A política precisa ser aprovada pelo grupo. Também estão pendentes a definição da rota/tela administrativa e a confirmação dos campos que o aplicativo poderá atualizar pelo sync.

## 9. Referência

Baseado no **Contrato de Sincronização — proposta v0.1, 02/11/2026**. Esta documentação descreve comportamento proposto, não implementação auditada.
