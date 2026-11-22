# Relatórios: endpoints

> **Estado: NÃO IMPLEMENTADO.** Não existe, em nenhuma branch, API de relatórios do atendimento. Este arquivo **não inventa endpoints**: ele registra o que o banco já oferece, o que falta decidir e uma proposta de caminho, para quem for construir o módulo.
> **Fontes:** esquema do banco (`infra/db/base/001_schema_inicial.sql`) e padrões já adotados em [`atendimentos.md`](atendimentos.md), [`autenticacao.md`](autenticacao.md) e [`erros-http.md`](erros-http.md).

---

## 1. O que o banco já tem (conferido)

| Tabela | Para que serve |
|---|---|
| `relatorio_atendimento` | Relatório do atendimento (tem `versao`, `status`, `deletado_em`, `sync_seq`) |
| `relatorio_versao` | Versões do relatório |
| `relatorio_pdf` | PDF gerado |
| `relatorio_especialista` | Especialistas vinculados |
| `motivo_campo_relatorio` | Campos do relatório por motivo do atendimento |
| `pergunta_formulario`, `resposta_pergunta_formulario`, `resposta_opcao` | Formulário de perguntas e respostas |
| `avaliacao_formulario` | Avaliação |
| `assinatura` | Assinatura |
| `evidencia` | Fotos, vídeos e PDFs anexos (UUID gerado no aparelho, upload separado) |

Regras já impostas pelo banco, que a API deve **respeitar, não duplicar**:

- Para o atendimento ir de `em_atendimento` a `resolvido`, existe um relatório com `status = 'finalizado'`.
- Para `encerrado`, a pesquisa de satisfação precisa estar respondida.
- Exclusão sempre lógica (`deletado_em`); `versao` e `sync_seq` por gatilho.
- Evidências: foto e vídeo só da câmera do app; documento só PDF como anexo extra.

---

## 2. O que falta definir (decisões do grupo)

| # | Pergunta | Quem decide |
|---|---|---|
| 1 | Quem é o dono do módulo de relatórios (backend)? | Grupo |
| 2 | O relatório é criado offline no app e sincronizado? (provável, pelo `sync_seq` e UUID) | Nicole/Rafael |
| 3 | Fluxo de status do relatório (rascunho → finalizado) e quem finaliza | Grupo |
| 4 | Geração do PDF: no servidor ou no app? Onde armazenar? | Infra |
| 5 | Upload de evidências (proxy × URL pré-assinada, antivírus, limites) | Infra |
| 6 | Quem pode ver/editar relatório (RLS e permissões, inclusive `editar_atendimento_finalizado`) | Grupo |

---

## 3. Proposta de rotas (rascunho, **não é contrato**)

Seguindo o mesmo padrão dos atendimentos (UUID do aparelho, `versao` para concorrência, erros no formato padrão):

| Método e rota | Função |
|---|---|
| `GET /api/v1/atendimentos/{id}/relatorio` | Relatório atual do atendimento |
| `PUT /api/v1/atendimentos/{id}/relatorio` | Cria ou atualiza (envia `versao`); idempotente pelo UUID |
| `POST /api/v1/atendimentos/{id}/relatorio/finalizar` | Marca como `finalizado` (libera `resolvido`) |
| `GET /api/v1/atendimentos/{id}/relatorio/pdf` | PDF final |
| `PUT /api/v1/evidencias/{id}/arquivo` | Envio do binário (metadados sobem antes) |

Todos exigem `Authorization: Bearer`, seguem [`erros-http.md`](erros-http.md) (404 para o que a pessoa não vê, 409 `conflito_de_versao`, 422 para regra do banco) e entram no contrato de sincronização quando este for aprovado.

---

## 4. Como avançar (passo a passo sugerido)

1. Grupo responde as perguntas 1, 3 e 6 da seção 2.
2. Quem for o dono escreve o contrato OpenAPI do relatório **antes** do código (como o Rafael fez para atendimentos).
3. Validar o contrato com a Nicole (Android) e este arquivo é substituído pelo resumo real.
4. Testes no mesmo padrão: banco PostgreSQL real, casos de permissão por perfil, isolamento entre organizações e conflito de versão.

---

## 5. Enquanto isso

Quem constrói o app pode modelar o relatório localmente (Room) usando as colunas do banco e a proposta de sincronização, sem esperar o backend. Se o contrato mudar, o aviso deve vir por aqui.
