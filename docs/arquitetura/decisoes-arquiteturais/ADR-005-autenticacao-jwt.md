# ADR-005 — Autenticação por tokens Bearer (JWT a confirmar)

- **Status:** Parcialmente definido; formato JWT não confirmado nos documentos consultados.
- **Data deste registro:** 2026-11-08.

## Contexto
O Android precisa autenticar requisições à API. A Documentação Técnica determina o cabeçalho `Authorization: Bearer <access>`. O MER/DER prevê tokens com validade, rotação e revogação e uma tabela `TOKEN_ACESSO` que armazena hashes SHA-256 de refresh, convite e redefinição de senha.

## O que está documentado
- Requisições autenticadas usam `Authorization: Bearer <access>`.
- Tokens possuem validade e podem ser revogados.
- O modelo prevê refresh, convite e redefinição de senha.
- Tokens são armazenados como hash, não em texto claro.
- Funções do serviço de login emitem, rotacionam e revogam tokens.
- A API verifica se o dispositivo associado à sessão não foi revogado.

## O que não está confirmado
Os documentos não declaram explicitamente que o access token seja JWT. Também não definem algoritmo de assinatura, claims, issuer, audience, tempo de vida do access token, biblioteca JWT ou política completa de rotação e reutilização.

Portanto, o nome deste arquivo preserva o exemplo de ADR solicitado, mas JWT continua sendo uma escolha a aprovar, não uma tecnologia confirmada.

## Decisão registrada
Manter autenticação por token Bearer e o ciclo de vida de tokens descrito no MER/DER. A adoção de JWT como formato específico do access token depende de aprovação técnica explícita.

## Consequências
- Android centraliza o cabeçalho Bearer no interceptor HTTP.
- API valida token, validade, usuário e estado do dispositivo.
- Refresh tokens são armazenados no servidor somente como hash.
- Rotação e revogação ocorrem pelas funções do serviço de login.
- Formato concreto do access token precisa ser documentado.

## Alternativas a avaliar
1. JWT assinado como access token, com refresh token opaco e rotacionável.
2. Tokens opacos para access e refresh, com validação centralizada no servidor.

Os documentos não registram comparação nem escolha entre essas alternativas.

## Pendências para aprovação
- [ ] Confirmar se access token será JWT ou opaco.
- [ ] Definir algoritmo e parâmetros, se JWT.
- [ ] Definir claims e validações.
- [ ] Definir expiração.
- [ ] Definir armazenamento seguro no Android.
- [ ] Definir rotação, revogação e resposta a reutilização.
- [ ] Validar revogação por bloqueio/inativação e por dispositivo.
- [ ] Atualizar status para `Aceito` após decisão formal.

## Referências
MER e DER (TOKEN_ACESSO e seção 6); Documentação Técnica (38); Contrato de Sincronização v0.1 (seção 2).
