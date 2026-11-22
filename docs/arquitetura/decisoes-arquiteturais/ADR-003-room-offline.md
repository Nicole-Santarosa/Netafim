# ADR-003 — Room + SQLite para persistência offline no Android

- **Status:** Aceito no Documento de Requisitos.
- **Referência temporal:** Documento de Requisitos (setembro/2026).

## Contexto
Técnicos trabalham em campo e podem ficar sem conexão. O preenchimento do atendimento e o registro de evidências não podem depender de conectividade contínua.

## Decisão
Utilizar Room sobre SQLite no aplicativo Android para persistência local e operação offline.

O MER/DER descreve nove tabelas locais. Registros criados no aparelho utilizam UUID gerado localmente. Dados e evidências permanecem no dispositivo até a sincronização.

## Consequências
- O aplicativo mantém localmente os dados necessários ao atendimento em execução.
- O protocolo pode permanecer nulo até ser atribuído pelo servidor.
- A fila de operações deve preservar operações pendentes e dependências.
- Sincronização precisa tratar versões e conflitos.
- Evidências binárias ficam no armazenamento privado do aplicativo; Room guarda caminhos e metadados.
- Migrações Room devem preservar dados existentes.

## Padrões técnicos documentados
- `statusLocal`: `sincronizado`, `pendente_envio`, `conflito`, `erro`.
- `origemRegistro`: `local` ou `servidor`.
- `dadosJson`: JSON da API em `snake_case`, conforme confirmação técnica.
- `versaoBase`: versão que o aplicativo conhecia ao editar.
- Cursor global de pull armazenado separadamente do `sync_seq` individual.

O Contrato v0.1 propõe PUSH antes de PULL, com resultado individual por operação. A política de conflitos e decisões A–I ainda exigem confirmação.

## Cuidados
- Tratar duplicidade, reenvio, conflito e falhas de rede.
- Evitar sobrescrever alterações locais pendentes.
- Implementar migrations e testes de atualização.
- Proteger dados e credenciais no dispositivo.

## Pendências
Confirmar política de conflito, estratégia de cursor, campos atualizáveis, escopo da v1 e proteção dos dados locais. Implementar e testar endpoints de sincronização.

## Referências
Documento de Requisitos (5.3); MER e DER (páginas 3 e 45–47); Documentação Técnica (36–42 e 46–48); Contrato de Sincronização v0.1 (2–9).
