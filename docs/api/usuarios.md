# Usuários: endpoints, perfis e permissões

> **Para quem é:** quem constrói telas de gestão de usuários e quem precisa saber "quem pode o quê".
> **Fonte:** `docs/api/acesso-openapi.yaml`, `docs/modulos/acesso.md` e a matriz do banco (Anexo B). O contrato completo é o OpenAPI.
> **Base:** `/api/v1/` · **Autenticação:** `Authorization: Bearer <access>` (veja [`autenticacao.md`](autenticacao.md)) · **Erros:** [`erros-http.md`](erros-http.md)

---

## 1. Em 30 segundos

- Usuários nascem de duas formas: **autocadastro** (fica `pendente` até o Netafim Admin aprovar) ou **convite** feito por um administrador.
- Cada administrador vê e gerencia **só a própria organização**. O Netafim Admin vê todos.
- A situação da conta é uma de: `pendente`, `ativo`, `recusado`, `inativo`, `bloqueado`.
- Regras de segurança importantes moram no **banco** (RLS e gatilhos). A API devolve mensagens claras, mas quem garante é o banco.

---

## 2. Endpoints

### 2.1 O próprio usuário

| Método e rota | O que faz | Resposta |
|---|---|---|
| `GET me` | Dados, perfil, organização e `permissoes` | 200 |
| `PATCH me` | Atualiza nome e telefone | 200 |
| `POST me/senha` | Troca a senha (`senha_atual`, `nova_senha`) | 204 |
| `GET me/mfa` · `POST me/mfa/iniciar` · `confirmar` · `desativar` | Verificação em duas etapas | veja [`autenticacao.md`](autenticacao.md#6-verificação-em-duas-etapas-mfatotp) |

### 2.2 Gestão de usuários

| Método e rota | Quem usa | O que faz |
|---|---|---|
| `GET usuarios` | admins, no próprio escopo | Lista paginada (filtros na seção 3) |
| `POST usuarios` | admins | Cria por **convite** |
| `GET usuarios/{id}` | admins, no escopo | Detalhe |
| `PATCH usuarios/{id}` | admins, no escopo | Edita dados |
| `DELETE usuarios/{id}` | admins, no escopo | Exclusão **lógica**; resposta 204 |
| `POST usuarios/{id}/aprovar` | **Netafim Admin** | Aprova cadastro pendente. Na prática exige `tipo_organizacao`, o id da organização (`id_distribuidor` ou `id_cliente`) **e** `id_perfil`; sem eles responde 400 (`organizacao_obrigatoria`, `perfil_invalido`). Um admin de distribuidor recebe **404**, porque não enxerga cadastros pendentes |
| `POST usuarios/{id}/recusar` | **Netafim Admin** | Recusa cadastro pendente |
| `POST usuarios/{id}/situacao` | conforme a permissão (bloquear: Netafim Admin) | Muda para `ativo`, `inativo` ou `bloqueado` |
| `POST usuarios/{id}/perfil` | **Netafim Admin** | Define o perfil (`{ "id_perfil" }`) |
| `POST usuarios/{id}/reenviar-convite` | admins | **Só enquanto a pessoa ainda não definiu a senha.** Pendente: novo convite (14 dias). Ativo sem senha definida: link de redefinição (2 horas). Resposta 204. Quem já definiu a senha recebe `400 convite_ja_aceito` (nesse caso use "esqueci a senha") |
| `POST usuarios/{id}/mfa/resetar` | **Netafim Admin** | Remove o MFA de quem perdeu o aparelho. Resposta 204; 409 `mfa_inativo` se a pessoa não tem MFA; um admin de distribuidor recebe 404 |
| `GET usuarios/{id}/dispositivos` | Netafim Admin ou quem gerencia o usuário | Aparelhos da pessoa. **A resposta é uma lista simples** (`[ {...}, ... ]`), sem paginação. O OpenAPI antigo a descrevia como paginada; isso foi corrigido na `pr/05-mfa` (ainda não mesclada) |
| `POST dispositivos/{id_dispositivo}/revogar` | **Netafim Admin** | Revoga o aparelho e encerra os tokens dele |

### 2.3 Apoio

| Método e rota | Quem usa | O que faz |
|---|---|---|
| `GET perfis` · `GET perfis/{id}` | todos | Perfis e suas permissões |
| `PUT perfis/{id}/permissoes` | **Netafim Admin** | Troca as permissões de um perfil (`{ "permissoes": ["codigo", ...] }`) |
| `GET permissoes` · `GET permissoes/{codigo}` | todos | Catálogo de permissões |
| `GET/POST/PATCH/DELETE distribuidores`, `clientes` | leitura no escopo; escrita só **Netafim Admin** | Organizações |
| `GET/POST/PATCH/DELETE dominios` | **Netafim Admin** | Domínios de e-mail corporativo autorizados |

---

## 3. Listar usuários

`GET /api/v1/usuarios?situacao=pendente&busca=ana&page=1`

| Parâmetro | Valores | Observação |
|---|---|---|
| `situacao` | `pendente`, `ativo`, `recusado`, `inativo`, `bloqueado` | |
| `tipo_organizacao` | `netafim`, `distribuidor`, `cliente` | |
| `id_distribuidor` / `id_cliente` | inteiro | |
| `busca` | texto | Procura em nome e e-mail |
| `page` | a partir de 1 | Resposta paginada: `count`, `next`, `previous`, `results` |

Ordem: nome, depois id.

---

## 4. Permissões por perfil

Códigos exatamente como estão na tabela `permissao` do banco. **O banco é a fonte da verdade:** se mudar lá, mude aqui e em `apps/contas/constantes.py`.

| Permissão | Netafim Admin | Distrib. Admin | Cliente Admin | Netafim Usuário | Distrib. Usuário | Cliente Usuário |
|---|:-:|:-:|:-:|:-:|:-:|:-:|
| `aprovar_cadastro` | ✔ | | | | | |
| `criar_usuarios` | ✔ | ✔ | ✔ | | | |
| `editar_usuarios` | ✔ | ✔ | ✔ | | | |
| `excluir_usuarios` | ✔ | ✔ | ✔ | | | |
| `definir_perfil` | ✔ | | | | | |
| `abrir_atendimento` | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| `responder_atendimento` | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| `consultar_proprios` | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| `consultar_empresa` | ✔ | ✔ | ✔ | | | |
| `consultar_clientes_vinculados` | ✔ | ✔ | | | | |
| `consultar_todos` | ✔ | | | | | |
| `vincular_tecnico` | ✔ | ✔ | | | | |
| `destinar_atendimento` | ✔ | | | | | |
| `reclassificar_atendimento` | ✔ | | | | | |
| `encerrar_atendimento` | ✔ | | | | | |
| `editar_atendimento_finalizado` | ✔ | | | | | |
| `dashboard_gerencial` | ✔ | ✔ | ✔ | | | |

---

## 5. Quem gerencia quem

| Ator | Vê | Gerencia |
|---|---|---|
| Netafim Admin | todos | todos; **único** que aprova, define perfil e bloqueia |
| Distribuidor Admin | usuários do próprio distribuidor | usuários de nível Usuário do próprio distribuidor |
| Cliente Admin | usuários da própria empresa | usuários de nível Usuário da própria empresa |
| Perfis Usuário | só a si mesmo (`GET me`) | ninguém |

**Proteções automáticas:**

- Ninguém gerencia a si mesmo pela lista.
- O **último Netafim Admin ativo** não pode ser desativado, excluído nem rebaixado.
- O domínio do e-mail precisa estar autorizado para a organização (`dominio_nao_autorizado`).

---

## 6. Fluxos

### 6.1 Autocadastro e aprovação

```
App: POST auth/cadastro           -> 202, conta "pendente"
Admin Netafim: GET usuarios?situacao=pendente
Admin Netafim: POST usuarios/{id}/aprovar
     { "tipo_organizacao": "distribuidor", "id_distribuidor": 1, "id_perfil": 3 }
Pessoa recebe e-mail e passa a entrar
   (ou: POST usuarios/{id}/recusar -> "recusado")
```

`aprovar` aceita `tipo_organizacao`, `id_distribuidor`, `id_cliente` e `id_perfil`. A combinação precisa fazer sentido (`organizacao_incoerente`, `organizacao_obrigatoria`, `perfil_invalido`).

### 6.2 Convite por administrador

`POST usuarios`:

```json
{ "nome": "Carlos Lima", "email": "carlos@agroirriga.com.br", "telefone": "16999998888",
  "tipo_organizacao": "distribuidor", "id_distribuidor": 1, "id_perfil": 4 }
```

- Só `nome` e `email` são obrigatórios no contrato, mas na prática o Netafim Admin precisa informar também `tipo_organizacao` (sem ele: `400 organizacao_obrigatoria`).
- **Netafim Admin** cria qualquer usuário, que já nasce **`ativo`** com `convite_pendente: true` (a pessoa ainda precisa definir a senha pelo link).
- **Admin de distribuidor ou de cliente**: a API **ignora em silêncio** o perfil e a organização pedidos e cria sempre um usuário de nível **Usuário da própria organização**, em situação **`pendente`** até o Netafim Admin validar. Observado nos testes: um Distribuidor Admin que pediu perfil de admin e outro distribuidor recebeu `201` com o perfil Distribuidor Usuário, no próprio distribuidor. O app não deve oferecer essas escolhas a esses perfis, porque a resposta de sucesso não avisa que foram trocadas.
- O convidado recebe um e-mail com link para definir a senha e aceitar o aviso de privacidade (veja [`autenticacao.md`](autenticacao.md#35-senha)).

### 6.3 Desativar, bloquear e excluir

- `situacao: "inativo"` suspende o acesso sem apagar. `"bloqueado"` (e o desbloqueio) é do Netafim Admin.
- Transições permitidas: `ativo → inativo`, `ativo → bloqueado`, `inativo → ativo`, `bloqueado → ativo`. Qualquer outra responde `transicao_invalida`. Quem está `pendente` ou `recusado` não passa por aqui (use aprovar/recusar).
- O último Netafim Admin ativo é protegido (`ultimo_admin`). Como ninguém gerencia a si mesmo (`403 sem_permissao`), na prática isso só protege contra duas requisições simultâneas; **não consegui provocar esse erro pela API**.
- `DELETE` é **exclusão lógica** (`deletado_em`): a pessoa some das listas e o aplicativo recebe o registro como removido.
- Organização com usuários ativos não pode ser excluída (`409 organizacao_com_usuarios`); distribuidor com clientes, `409 distribuidor_com_clientes`.

### 6.4 Aparelho perdido

`POST dispositivos/{id}/revogar` (Netafim Admin). Encerra os tokens do aparelho e invalida o access na hora. Se a pessoa também perdeu o MFA: `POST usuarios/{id}/mfa/resetar`.

---

## 7. Formato do usuário (resumo)

```json
{
  "id_usuario": 7, "nome": "Ana Souza", "email": "ana@agroirriga.com.br", "telefone": "16999998888",
  "situacao": "ativo", "tipo_organizacao": "distribuidor",
  "organizacao": { "tipo": "distribuidor", "id": 1, "nome": "Agro Irriga Ltda" },
  "perfil": { "id_perfil": 3, "nome_perfil": "Distribuidor Admin", "tipo_organizacao": "distribuidor", "nivel": "admin" },
  "convite_pendente": false, "data_aprovacao": "...", "ultimo_acesso": "...", "data_criacao": "..."
}
```

`GET me` acrescenta `permissoes`, `exige_troca_senha`, `mfa_ativo` e `versao_termo_consentimento`.

---

## 8. Boas práticas para quem integra

- **Escopo:** nunca filtre por perfil no app para "proteger" dados; o banco já filtra. Use o resultado como veio.
- **404 em vez de 403** para o que a pessoa não enxerga: a API não revela que o registro existe (ex.: admin de outro distribuidor, usuário excluído). Já um perfil **Usuário** que chama `GET usuarios` ou `GET usuarios/{id}` recebe **403**, porque a rota inteira exige permissão de gestão.
- **404 e alguns 403 vêm sem `codigo`** e o texto do 404 é "No Usuario matches the given query." (inglês). Decida pelo status e não mostre esse texto (veja [`erros-http.md`](erros-http.md)).
- **Senha nunca trafega de volta.** O campo é só de escrita; o banco e a API não devolvem hash.
- **Outros módulos** que precisem do usuário (por exemplo `atendimento.solicitante`) devem usar o modelo `Usuario` do módulo de acesso. Nunca chame `usuario.check_password()`; use `credenciais.conferir()`.

---

## 9. O que ainda não existe

- Tela de administração de usuários no app (a API está pronta; falta a interface).
- Botão de "redefinir MFA" na futura tela de gestão.
- Aplicação das migrações `004`, `005` e `007` no banco do grupo (Supabase) depende de quem tem acesso ao banco.
