# ADR-001 — PostgreSQL como banco central

- **Status:** Aceito no Documento de Requisitos; Supabase gerenciado registrado no MER/DER.
- **Referência temporal:** Documento de Requisitos (setembro/2026) e MER/DER (29/09/2026).

## Contexto
O sistema precisa persistir dados estruturados de usuários, organizações, atendimentos, relatórios, assinaturas, pesquisas, auditoria e demais entidades. O modelo prevê relacionamentos, constraints, gatilhos, controle de versão e RLS.

A entrevista inicial ainda considerava Oracle e VPS como possibilidades. O Documento de Requisitos posterior definiu PostgreSQL; o MER/DER especificou PostgreSQL gerenciado no Supabase.

## Decisão
Utilizar PostgreSQL como banco relacional central e, conforme MER/DER, hospedá-lo no Supabase como PostgreSQL gerenciado.

Autenticação e regras de negócio permanecem na API Django REST Framework. O projeto não utiliza o serviço de autenticação do Supabase e nenhuma tabela depende dele.

## Consequências
- Suporte relacional, constraints, índices, gatilhos e transações.
- RLS como camada adicional de isolamento.
- Necessidade de configurar e testar policies.
- Necessidade de definir região, plano, backup, recuperação e monitoramento.

## Alternativas consideradas
- **Oracle:** mencionado na entrevista inicial, mas não adotado no Documento de Requisitos posterior.
- **PostgreSQL instalado na VPS:** não é a topologia indicada pelo MER/DER, que registra Supabase gerenciado.

## Pendências
- Confirmar região e plano Supabase.
- Definir backup e recuperação.
- Validar RLS e isolamento entre organizações.
- Executar testes de carga, desempenho e concorrência.

## Referências
Documento de Requisitos (5.2 e apêndice 10.2); MER e DER (página 3 e seção 5).
