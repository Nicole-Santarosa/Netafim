# Versionamento do banco de dados — App Netafim

> Regra registrada na Documentação Técnica, seção 44: scripts em elaboração não recebem número. A numeração é atribuída no momento da aplicação pela Pessoa 1, responsável pelo banco central.

## 1. Objetivo

Manter histórico rastreável das alterações do PostgreSQL, evitar colisões de numeração quando integrantes trabalham em paralelo e identificar scripts revisados e efetivamente aplicados.

## 2. Organização sugerida

```text
infra/
└── db/
    ├── base/
    │   └── 001_schema_inicial.sql
    ├── migrations/
    │   ├── 002_...
    │   ├── 003_...
    │   └── ...
    └── scripts-pendentes/
        ├── adicionar_indice_atendimento.sql
        └── ajustar_regra_transicao.sql
```

A estrutura é sugestão; adaptar ao repositório real sem duplicar scripts.

## 3. Nomenclatura

### Scripts pendentes
Enquanto estiver em elaboração, revisão ou aguardando aplicação, usar nome descritivo **sem numeração**:

```text
adicionar_indice_atendimento.sql
ajustar_regra_transicao_status.sql
criar_tabela_sync_operacao.sql
```

### Scripts aplicados
Somente no momento da aplicação, a Pessoa 1 atribui o próximo número disponível:

```text
001_schema_inicial.sql
002_criar_tabela_exemplo.sql
003_adicionar_indice_exemplo.sql
```

Exemplos ilustrativos; não reservam números futuros.

## 4. Fluxo acordado

1. Responsável pelo módulo cria o script com nome descritivo e sem número.
2. Script é revisado antes da aplicação.
3. Pessoa 1 verifica o histórico e identifica o próximo número.
4. Pessoa 1 atribui a numeração sequencial.
5. Pessoa 1 aplica o script no banco central.
6. Script passa a integrar o histórico numerado.
7. Resultado é registrado e documentação relacionada é atualizada.

## 5. Requisitos sugeridos para scripts

Cada script deve ter objetivo claro, ser revisado, declarar dependências, considerar constraints/índices/triggers/funções/RLS, evitar alterações destrutivas sem plano de migração, preservar dados quando aplicável e descrever validação.

Cabeçalho sugerido:

```sql
-- Projeto: App Netafim
-- Objetivo: <descrever a alteração>
-- Responsável pelo módulo: <nome/pessoa>
-- Status: pendente de revisão | revisado | aplicado
-- Dependências: <scripts ou objetos necessários>
-- Plano de validação: <consultas/testes>
```

Essa convenção de cabeçalho é sugestão, não exigência literal da documentação.

## 6. Aplicação e rastreabilidade

A Pessoa 1 mantém a sequência e aplica as alterações no banco central. Não renomear script pendente com número antes da aplicação e não reutilizar números já atribuídos.

## 7. Relação com o schema e sincronização

A Documentação Técnica cita `infra/db/base/001_schema_inicial.sql` como snapshot de referência informado pelo autor do Contrato de Sincronização.

O Contrato v0.1 menciona proposta de migração `009` para `sync_operacao` e observa que `008` seria a regra do Rafael. Conferir o histórico real antes de aplicar ou reservar qualquer número.

**Não criar `009_...` por conta própria:** Pessoa 1 atribui o número somente no momento da aplicação.

## 8. Room / SQLite

Room possui versionamento próprio, separado dos scripts PostgreSQL. A Documentação Técnica registra versão 1 e proposta de migração para versão 2 ao adicionar estruturas de fila e conflitos. Toda mudança exige incrementar a versão, implementar migration preservando dados e testar a atualização.

## 9. Checklist

- [ ] Script pendente com nome descritivo sem número.
- [ ] Revisão concluída.
- [ ] Dependências e ordem verificadas.
- [ ] Constraints, índices, triggers, funções e RLS considerados.
- [ ] Plano de validação descrito.
- [ ] Pessoa 1 atribuiu número.
- [ ] Aplicação executada e resultado registrado.
- [ ] Documentação e testes atualizados.

## 10. Fontes

Documentação Técnica (seção 44); Contrato de Sincronização v0.1 (seção 9, ponto A); MER e DER.
