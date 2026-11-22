# Documentação — Projeto Netafim

Este diretório reúne a documentação técnica e de apoio do **Aplicativo de Pós-venda Netafim**. O objetivo é centralizar as informações do projeto e facilitar a consulta por desenvolvedores, integrantes da equipe e responsáveis pela manutenção do sistema.

## 1. Sobre o projeto

O projeto consiste em um aplicativo de pós-venda da Netafim, com suporte a operações offline e sincronização posterior com o servidor. A solução documentada contempla:

- aplicativo Android, com persistência local utilizando Room/SQLite;
- API e regras de negócio implementadas com Python e Django REST Framework;
- banco de dados central PostgreSQL;
- armazenamento externo de arquivos de evidência, como fotos e vídeos;
- comunicação entre aplicativo e servidor por meio de API;
- mecanismos de autenticação, autorização e sincronização de dados.

A documentação deve ser consultada em conjunto com o Documento de Requisitos, as Regras de Negócio, o MER/DER, a Documentação Técnica e o Contrato de Sincronização do projeto.

## 2. Organização dos documentos

```text
docs/
├── README.md
├── arquitetura/
│   ├── visao-geral-arquitetura.md
│   ├── infraestrutura.md
│   ├── seguranca-e-permissoes.md
│   ├── versionamento-banco.md
│   └── decisoes-arquiteturais/
│       ├── README.md
│       ├── ADR-001-postgresql.md
│       ├── ADR-002-django-rest-framework.md
│       ├── ADR-003-room-offline.md
│       ├── ADR-004-object-storage.md
│       └── ADR-005-autenticacao-jwt.md
├── banco-de-dados/
│   ├── dicionario-de-dados.md
│   ├── relacionamentos.md
│   ├── modelo-banco-local-room.md
│   └── politica-integridade-e-acesso.md
├── sincronizacao/
│   ├── fluxo-push-pull.md
│   ├── tratamento-conflitos.md
│   ├── upload-evidencias.md
│   ├── estrategia-idempotencia.md
│   └── estados-sincronizacao.md
└── design/
    ├── wireframe.md
    └── prototipo.md
```

## 3. Índice de documentação

### 3.1 Arquitetura

Documenta a estrutura geral da solução, a infraestrutura prevista, os mecanismos de segurança e as decisões arquiteturais.

| Documento | Conteúdo |
|---|---|
| [Visão geral da arquitetura](arquitetura/visao-geral-arquitetura.md) | Componentes da solução e comunicação entre aplicativo, API, banco de dados e armazenamento de arquivos. |
| [Infraestrutura](arquitetura/infraestrutura.md) | Organização dos serviços e infraestrutura prevista para execução do sistema. |
| [Segurança e permissões](arquitetura/seguranca-e-permissoes.md) | Autenticação, autorização, perfis de acesso e proteção dos dados. |
| [Versionamento do banco](arquitetura/versionamento-banco.md) | Organização e aplicação das alterações no banco de dados. |
| [Decisões arquiteturais (ADRs)](arquitetura/decisoes-arquiteturais/README.md) | Índice das decisões arquiteturais registradas. |

**ADRs:**

- [ADR-001 — PostgreSQL](arquitetura/decisoes-arquiteturais/ADR-001-postgresql.md)
- [ADR-002 — Django REST Framework](arquitetura/decisoes-arquiteturais/ADR-002-django-rest-framework.md)
- [ADR-003 — Room e operação offline](arquitetura/decisoes-arquiteturais/ADR-003-room-offline.md)
- [ADR-004 — Object Storage](arquitetura/decisoes-arquiteturais/ADR-004-object-storage.md)
- [ADR-005 — Autenticação JWT](arquitetura/decisoes-arquiteturais/ADR-005-autenticacao-jwt.md)

### 3.2 Banco de dados

Descreve a estrutura dos dados, os relacionamentos, o banco local do aplicativo e as políticas de integridade e acesso.

| Documento | Conteúdo |
|---|---|
| [Dicionário de dados](banco-de-dados/dicionario-de-dados.md) | Entidades, atributos, tipos e descrições dos campos. |
| [Relacionamentos](banco-de-dados/relacionamentos.md) | Relacionamentos entre entidades e respectivas chaves. |
| [Modelo do banco local Room](banco-de-dados/modelo-banco-local-room.md) | Estrutura de persistência local utilizada pelo aplicativo Android. |
| [Política de integridade e acesso](banco-de-dados/politica-integridade-e-acesso.md) | Restrições, integridade referencial e regras de acesso aos dados. |

### 3.3 Sincronização

Descreve a comunicação entre o aplicativo offline e o servidor, incluindo envio e recebimento de dados, conflitos, evidências, idempotência e estados locais.

| Documento | Conteúdo |
|---|---|
| [Fluxo PUSH/PULL](sincronizacao/fluxo-push-pull.md) | Fluxo proposto para envio de operações ao servidor e recebimento de atualizações. |
| [Tratamento de conflitos](sincronizacao/tratamento-conflitos.md) | Detecção e tratamento de alterações concorrentes. |
| [Upload de evidências](sincronizacao/upload-evidencias.md) | Envio e armazenamento dos arquivos associados aos registros. |
| [Estratégia de idempotência](sincronizacao/estrategia-idempotencia.md) | Prevenção de duplicidades em reenvios de operações e arquivos. |
| [Estados de sincronização](sincronizacao/estados-sincronizacao.md) | Estados locais que indicam a situação de sincronização dos registros. |

### 3.4 Design e experiência do usuário

Reúne os materiais visuais utilizados para orientar a interface e a navegação do aplicativo.

| Documento | Conteúdo |
|---|---|
| [Wireframe](design/wireframe.md) | Referência ao wireframe desenvolvido no Figma. |
| [Protótipo](design/prototipo.md) | Referência ao protótipo navegável desenvolvido no Figma. |

## 4. Ordem sugerida de leitura

Para compreender o projeto de forma progressiva, recomenda-se a seguinte ordem:

1. **Visão geral da arquitetura:** compreender os componentes e suas responsabilidades.
2. **Banco de dados:** conhecer as entidades, os relacionamentos e as regras de integridade.
3. **Segurança e permissões:** entender autenticação, autorização e restrições de acesso.
4. **Sincronização:** estudar o fluxo offline, o PUSH/PULL, os conflitos e o envio de evidências.
5. **ADRs e infraestrutura:** consultar as justificativas das decisões e a organização dos serviços.
6. **Wireframe e protótipo:** visualizar a estrutura das telas e a experiência prevista para o usuário.

## 5. Situação e interpretação dos documentos

Nem todos os itens documentados representam funcionalidades já implementadas. É importante observar o status indicado em cada arquivo.

Em especial:

- Os documentos de sincronização organizam a proposta v0.1 do Contrato de Sincronização. Os endpoints e mecanismos descritos como propostos não devem ser considerados implementados sem verificação no código.
- As decisões arquiteturais registradas em ADRs devem ser consideradas conforme o status indicado em cada ADR. Uma decisão pendente precisa de aprovação antes de ser tratada como definitiva.
- A documentação de infraestrutura deve ser lida considerando as definições vigentes do projeto. A hospedagem da API, do PostgreSQL e do Object Storage deve seguir a configuração efetivamente aprovada e implantada.
- A decisão sobre JWT deve ser confirmada: o uso de token Bearer e o ciclo de vida do refresh token não comprovam, por si só, que o formato JWT foi definitivamente adotado.
- A estratégia de versionamento do banco central e as migrações do Room são mecanismos distintos e devem ser controlados separadamente.

Quando houver divergência entre um documento de proposta e a implementação atual, a equipe deve registrar a decisão e atualizar a documentação correspondente.

## 6. Manutenção da documentação

A documentação deve acompanhar a evolução do sistema.

Sempre que houver alteração relevante:

1. identificar os documentos afetados;
2. atualizar a descrição para refletir o comportamento aprovado e implementado;
3. registrar decisões arquiteturais relevantes em um ADR;
4. atualizar diagramas e referências quando necessário;
5. revisar os links deste índice caso arquivos ou diretórios sejam renomeados.

Recomenda-se que as alterações na documentação sejam versionadas no mesmo repositório do projeto e revisadas por meio de pull requests.

---

**Projeto:** Aplicativo de Pós-venda Netafim
**Diretório:** `docs/`
**Formato:** Markdown (`.md`)
