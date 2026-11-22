# ADR-002 — Django REST Framework como backend e API REST

- **Status:** Aceito no Documento de Requisitos.
- **Referência temporal:** Documento de Requisitos (setembro/2026).

## Contexto
O Android precisa comunicar-se com serviços centrais para autenticação, autorização, regras de negócio, persistência, relatórios e sincronização. O sistema possui perfis e escopos distintos.

## Decisão
Utilizar Python com Django REST Framework no backend e na API REST.

A autenticação e as regras de negócio ficam na API. O MER/DER prevê RLS no PostgreSQL como camada adicional de controle de visibilidade.

## Responsabilidades
- Expor endpoints REST ao aplicativo.
- Autenticar usuários e validar sessões.
- Aplicar permissões e regras de negócio.
- Consultar e persistir dados no PostgreSQL.
- Orquestrar operações com Object Storage.
- Implementar fluxos de atendimento e relatórios.
- Implementar endpoints de sincronização após aprovação do contrato.

## Consequências
- Regras de negócio não devem ficar exclusivamente no Android.
- API e banco devem aplicar controles de autorização compatíveis.
- Contratos de endpoint, serialização, erros e versionamento precisam ser documentados.
- O preenchimento local deve continuar possível sem conectividade.

## Alternativas consideradas
Os documentos consultados não registram comparação formal entre frameworks backend. Não atribuir avaliação comparativa não documentada.

## Pendências
- Manter contrato OpenAPI.
- Definir campos atualizáveis pela sincronização.
- Implementar/validar endpoints de sync, marcados como novos no contrato v0.1.
- Validar autenticação, autorização, RLS e erros.

## Referências
Documento de Requisitos (5.1 e 5.2); MER e DER (página 3); Documentação Técnica (38, 42, 43, 46–48); Contrato de Sincronização v0.1.
