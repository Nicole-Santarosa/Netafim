# Erros HTTP: padrão de respostas e tratamento

> **Para quem é:** quem consome a API (Android e outros módulos) e quem cria endpoints novos.
> **Regra de ouro:** o app decide o que fazer pelo **`codigo`** (estável), nunca pelo texto de `detail`.
> **Fontes:** `backend/apps/contas/excecoes.py`, `backend/apps/core/erros.py` (Rafael) e os contratos OpenAPI.

---

## 1. Os três formatos de resposta de erro

| # | Quando | Formato |
|---|---|---|
| **A** | Regra de negócio, permissão ou erro vindo do banco | `{ "detail": "mensagem em português", "codigo": "snake_case" }` |
| **B** | Validação de campo (400) | `{ "campo": ["mensagem"], "outro_campo": ["mensagem"] }` |
| **C** | Padrão do DRF/JWT (veja abaixo) | `{ "detail": "mensagem" }` (**sem** `codigo`) |

O app deve tratar os três. **Quando não houver `codigo`, use o status HTTP.**

**Respostas do formato C que existem de verdade** (observadas na API):

| Situação | Status | Corpo |
|---|---|---|
| Chamada sem token | 401 | `{"detail": "As credenciais de autenticação não foram fornecidas."}` |
| Access inválido ou vencido | 401 | `{"detail": "Given token not valid for any token type", "code": "token_not_valid", "messages": [...]}` (atenção: a chave é `code`, em inglês, e não `codigo`) |
| Sem permissão numa rota inteira (ex.: perfil Usuário em `GET usuarios`, não-Netafim-Admin em `dominios`) | 403 | `{"detail": "Você não tem permissão para esta ação."}` ou `{"detail": "Ação restrita ao Administrador Netafim."}`, **sem** `codigo` |
| Registro inexistente ou invisível | 404 | `{"detail": "No Usuario matches the given query."}` (texto em inglês, sem `codigo`; **não exiba ao usuário**) |
| Limite de tentativas | 429 | `{"detail": "Pedido foi suprimido. Espera-se que esteja diponível em N segundos."}` |

Já o `403 sem_permissao` **com** `codigo` (formato A) aparece quando a regra é aplicada dentro de uma ação, por exemplo `Somente o Administrador Netafim bloqueia ou desbloqueia contas.`

---

## 2. Status HTTP: o que significa e o que o app faz

| Status | Significado | O que o app faz |
|---|---|---|
| **200 / 201 / 202** | Sucesso (202: aceito, ex.: cadastro, esqueci a senha) | Seguir o fluxo |
| **204** | Sucesso sem corpo (logout, trocar senha, excluir) | Não tentar ler JSON |
| **400** | Campo ausente, formato inválido ou regra simples (ex.: `senha_atual_incorreta`) | Mostrar no campo (formato B) ou a mensagem (formato A) |
| **401** | Sem token, token vencido, credenciais inválidas, código MFA errado | Se for `token_invalido`/sem token: **renovar uma vez** com refresh; se falhar, voltar ao login. Se for `credenciais_invalidas`/`codigo_mfa_invalido`: mostrar a mensagem |
| **403** | Sem permissão, ou conta/aparelho sem acesso liberado | Mostrar a mensagem do `codigo`. **Não** repetir a chamada |
| **404** | Não existe **ou a pessoa não pode ver** (a API não revela qual) | Tratar como "não encontrado" |
| **409** | Conflito: duplicado, versão velha, estado incompatível | Veja a seção 4 |
| **422** | O banco recusou por regra de negócio (gatilho, CHECK, referência) | Mostrar o `detail` (já é legível) |
| **429** | Muitas tentativas por IP | Esperar e tentar depois |
| **503** | Recurso indisponível (ex.: `mfa_indisponivel`) | Avisar e tentar mais tarde |
| **5xx** | Falha do servidor | Mensagem genérica, tentar de novo |

---

## 3. Códigos de erro (`codigo`)

### 3.1 Autenticação e sessão

| `codigo` | Status | Quando acontece |
|---|---|---|
| `credenciais_invalidas` | 401 | E-mail/senha errados **ou** conta temporariamente bloqueada (mesma mensagem, de propósito) |
| `token_invalido` | 401 | **Refresh** inválido, revogado ou já trocado (em `auth/refresh`, `auth/logout`). Reuso de um refresh já trocado traz "Sessão encerrada por segurança" e derruba também o refresh novo daquele aparelho. Para o **access**, veja o formato C acima (`code: token_not_valid`) |
| `conta_pendente` | 403 | Cadastro aguardando aprovação |
| `conta_recusada` | 403 | Cadastro recusado |
| `conta_inativa` | 403 | Conta desativada |
| `conta_bloqueada` | 403 | Conta bloqueada |
| `dispositivo_revogado` | 403 | Aparelho revogado pelo Netafim Admin |
| `dispositivo_de_outro_usuario` | 409 | O UUID do aparelho já é de outra pessoa: **gerar novo UUID e repetir**. Valia só para perfis comuns: para o Netafim Admin a API respondia `403 dispositivo_revogado`. Corrigido na `pr/05-mfa` (ainda não mesclada); veja `autenticacao.md` |
| `dispositivo_obrigatorio` | 400 | Existe no código, mas na prática o login sem aparelho volta no **formato B**: `{"dispositivo": ["Este campo é obrigatório."]}` |
| `termo_desatualizado` | 400 | Aviso de privacidade em versão antiga; a resposta traz `versao_vigente` |

### 3.2 MFA

| `codigo` | Status | Quando acontece |
|---|---|---|
| `codigo_mfa_invalido` | 401 no login · 400 ao ativar/desativar | Código errado, vencido ou já usado |
| `desafio_mfa_invalido` | 401 | Desafio adulterado ou com mais de 5 minutos: voltar à senha |
| `mfa_ja_ativo` | 409 | Tentou iniciar/confirmar com MFA já ativo |
| `mfa_nao_iniciado` | 409 | Confirmou sem iniciar a configuração |
| `mfa_inativo` | 409 | Tentou desativar/redefinir sem MFA ativo |
| `mfa_indisponivel` | 503 | Servidor sem conexão `auth` ou banco sem a migração `007` |
| `senha_incorreta` | 400 | Senha errada ao desativar o MFA |

### 3.3 Usuários e organizações

| `codigo` | Status | Quando acontece |
|---|---|---|
| `sem_permissao` | 403 | O perfil não pode fazer esta ação |
| `email_em_uso` | 400 | Já existe usuário com este e-mail |
| `dominio_nao_autorizado` | 400 | Domínio do e-mail não autorizado para a organização |
| `senha_atual_incorreta` | 400 | Troca de senha com a atual errada |
| `senha_repetida` | 400 | Nova senha igual à atual |
| `nao_pendente` | 400 | Aprovar/recusar quem não está pendente |
| `organizacao_obrigatoria` · `organizacao_invalida` · `organizacao_incoerente` | 400 | Organização ausente, inexistente ou incompatível com o perfil |
| `perfil_invalido` | 400 | Perfil não encontrado |
| `perfil_incompativel` | 400 | O perfil é de outro tipo de organização que o do usuário |
| `transicao_invalida` | 400 | Mudança de situação não permitida (só: ativo↔inativo, ativo↔bloqueado) |
| `ultimo_admin` | 400 | Não se pode desativar, excluir ou rebaixar o último Netafim Admin ativo. É uma rede de segurança: como ninguém gerencia a si mesmo, na prática só aparece em disputa entre duas requisições. **Não foi observado nos testes pela API** |
| `convite_ja_aceito` | 400 | Reenviar convite para quem já definiu a senha (use "esqueci a senha") |
| `convite_indisponivel` | 400 | Convite não cabe para este usuário (situação que não permite) |
| `reset_indisponivel` | 400 | Redefinição de senha indisponível para a conta |
| `permissao_essencial` | 400 | Tentou remover uma permissão que o sistema exige |
| `organizacao_com_usuarios` · `distribuidor_com_clientes` | 409 | Exclusão bloqueada por vínculos |
| `regra_do_banco` | 400 | Regra do banco sem tradução específica |

### 3.4 Atendimentos (API do Rafael)

| `codigo` | Status | Quando acontece |
|---|---|---|
| `duplicado` | 409 | `id_atendimento` já existe (POST) |
| `conflito_de_versao` | 409 | `versao` enviada não é a atual (PATCH); a resposta traz `versao_atual` |
| `regra_de_negocio` | 422 | Gatilho ou CHECK do banco recusou; `detail` traz a mensagem do gatilho |
| `referencia_invalida` | 422 | Referência que não existe (cliente, propriedade, distribuidor, técnico) |
| `campo_obrigatorio` | 422 | Campo obrigatório ficou sem valor |
| `valor_invalido` | 400 | Valor com formato ou tamanho inválido |
| `sem_permissao` | 403 | O banco recusou por permissão |

> **Tradução automática de erros do banco** (módulo core): `23505` → 409 `duplicado` · `23503` → 422 `referencia_invalida` · `23514` → 422 `regra_de_negocio` (mensagem do gatilho segue como está; violação de CHECK usa mensagem fixa) · `23502` → 422 `campo_obrigatorio` · `42501` → 403 `sem_permissao` · classe `22` → 400 `valor_invalido`.

---

## 4. O 409 tem mais de um significado

Nunca trate "409" sozinho. Leia o `codigo`:

| `codigo` | O que significa | O que fazer |
|---|---|---|
| `duplicado` (POST) | Já existe um registro com este id | **Só** trate como sucesso depois de um `GET` do mesmo id devolver 200. Se o GET der 404, o id pertence a algo que a pessoa não vê: gere novo UUID e reenvie o mesmo conteúdo |
| `conflito_de_versao` (PATCH) | Outra pessoa alterou antes | Recarregar (`GET`), mostrar o estado novo e só então tentar de novo com a versão atual |
| `dispositivo_de_outro_usuario` | UUID do aparelho já em uso | Gerar novo UUID e repetir o login |
| `mfa_*` | Estado do MFA incompatível | Atualizar a tela com `GET me/mfa` |
| `organizacao_com_usuarios` / `distribuidor_com_clientes` | Exclusão bloqueada | Orientar a mover ou excluir os vínculos |

---

## 5. Como mostrar o erro ao usuário

1. Procure o `codigo` na tabela de mensagens do app (`MensagensErro`). Se existir, use a mensagem do app.
2. Se não existir, use o `detail` (já vem em português).
3. Se não houver nenhum dos dois, mostre: "Não foi possível concluir agora. Tente novamente em instantes."
4. **Erro de campo (formato B):** mostre sob o campo certo.
5. **Sem internet:** mensagem própria do app ("Sem conexão com a internet..."), nunca um erro técnico.
6. Nunca exiba nomes de tabela, coluna ou trecho de SQL. A API já não os envia.

---

## 6. Para quem cria endpoints novos

- Levante `ErroNegocio(mensagem, codigo, status_code=...)` (módulo de acesso) para devolver o formato A com `codigo` estável.
- Escolha o **status certo**: validação 400, permissão 403, inexistente/invisível 404, conflito 409, regra do banco 422.
- Use `codigo` em `snake_case`, em português, sem acento, e **não renomeie depois de publicado** (o app depende dele).
- **Não** devolva texto do banco ao cliente. Traduza (o módulo core já faz isso).
- Documente cada status novo no OpenAPI e **neste arquivo**.
- Em transações, lembre que o erro do banco aborta a transação da requisição; deixe o tratador global fazer o rollback.

---

## 7. Pendências

- Falta consolidar o tratador de erros: hoje há dois (módulo de acesso e módulo core do Rafael) com formatos compatíveis, mas código separado. Proposta: unificar no merge.
- As mensagens de 404 e de alguns 403 vêm do DRF, sem `codigo` e (no 404) em inglês. Proposta: um tratador único que as traduza e acrescente `codigo` (`nao_encontrado`, `sem_permissao`).
- **Verificado contra a API real** (PostgreSQL 16 de teste): os códigos e status de login, estados da conta, refresh, logout, senha, cadastro, termo, usuários (aprovar, recusar, situação, convite, exclusão), perfis, organizações, MFA e o 429. **Não observados por mim pela API** (existem no código, mas não os provoquei): `ultimo_admin`, `organizacao_incoerente`, `organizacao_invalida`, `perfil_incompativel`, `convite_indisponivel`, `reset_indisponivel`, e os erros de atendimento (cobertos pelos testes do Rafael, que eu rodei, mas não pela API ao vivo).
