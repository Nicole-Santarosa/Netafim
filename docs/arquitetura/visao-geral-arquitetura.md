# Visão geral da arquitetura — App Netafim

> Consolidação do Documento de Requisitos, MER/DER, Documentação Técnica e Contrato de Sincronização v0.1. Este documento distingue decisões registradas de propostas e pendências.

## 1. Objetivo

O aplicativo digitaliza a abertura, execução e acompanhamento de atendimentos de pós-venda. Técnicos devem registrar atendimentos em campo, inclusive sem conexão, mantendo dados e evidências localmente até a sincronização.

A primeira versão prioriza Android.

## 2. Visão lógica

```text
┌──────────────────────────────────────┐
│ Aplicativo Android                   │
│ Interface + Room/SQLite              │
│ Operação offline + fila de operações│
└──────────────────┬───────────────────┘
                   │ API REST / Bearer
                   ▼
┌──────────────────────────────────────┐
│ Backend Python / Django REST Framework│
│ Autenticação, autorização, regras,    │
│ relatórios e sincronização            │
└──────────────┬───────────────┬────────┘
               │               │
               ▼               ▼
┌──────────────────────┐  ┌────────────────────┐
│ PostgreSQL gerenciado│  │ Object Storage     │
│ Supabase             │  │ privado e separado │
│ dados, RLS, triggers │  │ fotos, vídeos, PDF │
└──────────────────────┘  └────────────────────┘
```

Diagrama lógico. A localização física definitiva da API e do Object Storage depende da implantação.

## 3. Componentes

| Componente | Tecnologia | Responsabilidade |
|---|---|---|
| Aplicativo | Android | Interface e operação em campo |
| Persistência móvel | Room + SQLite | Dados locais/offline |
| Backend/API | Python + Django REST Framework | API REST, autenticação, autorização e regras |
| Banco central | PostgreSQL gerenciado no Supabase | Dados estruturados, integridade e RLS |
| Arquivos | Object Storage privado | Fotos, vídeos, assinaturas e PDFs |
| Execução | Docker | Serviços em containers |
| Hospedagem inicial | VPS | Hospedagem inicial definida no Documento de Requisitos |

**Atenção:** o Documento de Requisitos define VPS como hospedagem inicial. O MER/DER posterior indica PostgreSQL no Supabase como serviço gerenciado e informa que a hospedagem da API e do armazenamento será definida na implantação. Não presumir que o PostgreSQL ficará dentro da VPS.

## 4. Fluxos

### Online
1. Usuário autentica-se no aplicativo.
2. Android envia requisição à API.
3. API valida autenticação, autorização e regras.
4. Backend consulta/altera PostgreSQL e, quando necessário, Object Storage.
5. API devolve a resposta.

### Offline
1. Técnico inicia/preenche atendimento sem internet.
2. Android grava dados no Room/SQLite.
3. Evidências ficam no armazenamento privado do aplicativo; Room guarda caminhos e metadados.
4. Operações aguardam conectividade.
5. Ao reconectar, aplicativo envia operações e sincroniza alterações do servidor.

O foco offline da primeira versão é o atendimento em execução; histórico completo offline não é requisito no Documento de Requisitos.

### Sincronização
O Contrato v0.1 propõe PUSH antes de PULL, resultado individual por operação, atualização local idempotente e upload separado dos binários. Os endpoints de sincronização são indicados como novos/a implementar e o contrato contém decisões A–I pendentes.

## 5. Persistência e arquivos

- PostgreSQL é o banco central; MER/DER registra 41 tabelas.
- Room/SQLite possui nove tabelas locais.
- Registros criados no aparelho usam UUID.
- O servidor atribui o número de protocolo.
- O modelo central utiliza `versao`, `sync_seq` e `deletado_em` para concorrência e sincronização.
- Fotos, vídeos, assinaturas e PDFs ficam em Object Storage privado. PostgreSQL guarda `storage_key`, hash e metadados, não URL pública.

## 6. Segurança

- Autenticação e regras de negócio ficam na API.
- RLS no PostgreSQL complementa a autorização da API.
- Android envia `Authorization: Bearer <access>`.
- MER/DER prevê tokens armazenados como hash, com validade, rotação e revogação.
- Arquivos são privados e acessados sob demanda.
- Auditoria prevê cadeia de hashes e âncora externa periódica.

Algoritmos, parâmetros criptográficos, TLS, gestão de segredos e outros controles precisam ser definidos e validados na implementação.

## 7. Fora do escopo inicial

- Expansão para iOS ou outras plataformas.
- Integrações com Salesforce e SAP.
- Evolução dos dashboards, indicadores e automações.

## 8. Decisões e pendências

| Tema | Situação |
|---|---|
| Android | Definido para v1 |
| Room + SQLite | Definido |
| Django REST Framework | Definido |
| PostgreSQL | Definido; MER/DER especifica Supabase gerenciado |
| Object Storage | Definido como separado e privado |
| Docker | Definido |
| VPS | Definida como hospedagem inicial |
| Localização da API e Object Storage | A definir na implantação |
| JWT como formato do access token | Não confirmado |
| Contrato de sincronização | Proposta v0.1, decisões A–I pendentes |

## 9. Documentos relacionados

- `infraestrutura.md`
- `seguranca-e-permissoes.md`
- `versionamento-banco.md`
- `decisoes-arquiteturais/`
- `../banco-de-dados/dicionario-de-dados.md`
- `../banco-de-dados/relacionamentos.md`
- `../banco-de-dados/modelo-banco-local-room.md`
- `../banco-de-dados/politica-integridade-e-acesso.md`

## 10. Fontes

Documento de Requisitos (seções 5, 8, 9 e apêndices); MER e DER (seções 5 e 6 e Anexo A); Documentação Técnica (seções 36–48); Contrato de Sincronização v0.1 (02/11/2026).
