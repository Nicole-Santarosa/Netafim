# Autenticação: login, JWT e autorização

> **Para quem é:** quem constrói telas e chamadas da API (Android e outros módulos).
> **Fonte:** código do módulo de acesso (`pr/05-mfa`) e `docs/api/acesso-openapi.yaml`. O contrato completo e atualizado é sempre o OpenAPI.
> **Base da API:** `/api/v1/` · **Formato:** JSON · **Erros:** veja [`erros-http.md`](erros-http.md)

---

## 1. Em 30 segundos

1. O app faz **login** com e-mail, senha e o **UUID do aparelho** e recebe um **access** (15 min) e um **refresh** (90 dias).
2. Todas as outras chamadas levam `Authorization: Bearer <access>`.
3. Quando o access vence (resposta **401**), o app troca o **refresh** por um par novo em `auth/refresh`.
4. Contas com **verificação em duas etapas (MFA)** recebem, no login, um **desafio** em vez dos tokens e concluem em `auth/mfa/verificar`.
5. O servidor **não decide permissões sozinho**: as regras de quem pode o quê moram no banco (perfil, permissões e RLS). O app só usa a lista `permissoes` para mostrar ou esconder botões.

---

## 2. Tokens

| Token | Duração | Onde usar | Observações |
|---|---|---|---|
| **access** (JWT) | 15 minutos | Cabeçalho `Authorization: Bearer ...` | A cada requisição a API confere no banco se a conta segue ativa e se o aparelho não foi revogado |
| **refresh** (opaco) | 90 dias | Só em `auth/refresh` e `auth/logout` | Guardado no servidor só como hash. **Rotação:** cada uso devolve um refresh novo e invalida o anterior |

Regras que o app precisa respeitar:

- **Guarde os tokens em armazenamento seguro** (no app: `ArmazenamentoSessaoSeguro`). Nunca em log.
- **Reuso de refresh já trocado = suspeita de roubo**: o servidor encerra as sessões daquele aparelho.
- **Não há tolerância**: se a resposta da rotação se perder na rede, o refresh antigo já não vale e o usuário precisa entrar de novo.
- Duas requisições simultâneas **não** devem renovar ao mesmo tempo. Serialize a renovação (o app usa um `Mutex`) e, se outra já renovou, reaproveite o access novo.

---

## 3. Fluxos

### 3.1 Login

`POST /api/v1/auth/login` (público)

```json
{
  "email": "ana@agroirriga.com.br",
  "senha": "********",
  "dispositivo": {
    "id_dispositivo": "7c1e0a52-3f1d-4c8e-9b0a-1d2f3a4b5c6d",
    "modelo": "Samsung SM-S938B",
    "versao_app": "1.0.0"
  }
}
```

- `dispositivo.id_dispositivo` (UUID gerado e guardado no app) é **obrigatório**. `modelo` (até 80 caracteres) e `versao_app` (até 20) são opcionais.
- O e-mail não diferencia maiúsculas de minúsculas.

**Resposta 200, conta sem MFA** (formato `Tokens`):

```json
{
  "access": "eyJ...",
  "refresh": "x9f...",
  "access_expira_em": "2026-11-02T14:15:00Z",
  "refresh_expira_em": "2027-01-31T14:00:00Z",
  "usuario": { "id_usuario": 7, "nome": "Ana Souza", "perfil": { "nome_perfil": "Distribuidor Admin" }, "permissoes": ["abrir_atendimento", "vincular_tecnico"], "mfa_ativo": false }
}
```

**Resposta 200, conta com MFA** (formato `LoginMfa`, **sem sessão**):

```json
{ "mfa_necessario": true, "desafio": "eyJ...", "desafio_expira_em": "2026-11-02T14:05:00Z" }
```

> **Atenção:** a resposta de login tem **dois formatos**. Teste `mfa_necessario` antes de ler `access`.

**Se o identificador do aparelho já pertence a outro usuário** (`409 dispositivo_de_outro_usuario`): gere um novo UUID, guarde-o e repita o login. O app já faz isso sozinho.

> **Correção recente:** antes, quando quem entrava era um **Netafim Admin**, a API respondia `403 dispositivo_revogado` em vez do 409 (o banco deixa o admin enxergar aparelhos alheios). Já está corrigido na branch `pr/05-mfa` (commit ainda não mesclado): agora todo perfil recebe `409 dispositivo_de_outro_usuario`. Enquanto o merge não acontece, um Netafim Admin que receba `dispositivo_revogado` num aparelho nunca revogado deve gerar um novo UUID.

### 3.2 Segundo fator (MFA)

`POST /api/v1/auth/mfa/verificar` (público)

```json
{ "desafio": "eyJ...", "codigo": "123456" }
```

- `codigo`: 6 dígitos do aplicativo autenticador **ou** um código de recuperação (`XXXXX-XXXXX`).
- O desafio vale **5 minutos**. Vencido ou adulterado → `401 desafio_mfa_invalido`: volte para a tela de senha.
- Código errado → `401 codigo_mfa_invalido`. **Cada erro conta como falha de login** (5 seguidas bloqueiam a conta por 15 minutos).
- Resposta 200: o mesmo formato `Tokens` do login.

Ativar, desativar e redefinir o MFA estão na seção 6.

### 3.3 Renovar a sessão

`POST /api/v1/auth/refresh` (público)

```json
{ "refresh": "x9f..." }
```

Resposta 200: `Tokens` (par novo, **substitua os dois**). Resposta 401: o refresh não vale mais, encerre a sessão local e volte ao login.

### 3.4 Sair

`POST /api/v1/auth/logout` (autenticado) com `{ "refresh": "..." }` → **204**. Revoga o refresh. Sem internet, o app limpa a sessão local e o refresh expira sozinho.

### 3.5 Senha

| Ação | Rota | Corpo | Resposta |
|---|---|---|---|
| Esqueci a senha | `POST auth/senha/esqueci` | `{ "email" }` | **202** sempre (não revela se o e-mail existe) |
| Redefinir pelo link do e-mail | `POST auth/senha/redefinir` | `{ "token", "nova_senha" }` | 200 |
| Aceitar convite | `POST auth/convite/aceitar` | `{ "token", "senha", "aceite_termo": true, "versao_termo" }` | 200 |
| Trocar a senha logado | `POST me/senha` | `{ "senha_atual", "nova_senha" }` | **204** |

Regras de senha: mínimo de **8 caracteres**, não pode ser só números, não pode ser uma senha comum e não pode repetir a atual. Senha rejeitada volta no formato de campo, por exemplo `{"nova_senha": ["Esta senha é muito curta. Ela precisa conter pelo menos 8 caracteres."]}`, com **uma ou mais mensagens**. Os erros `senha_atual_incorreta` e `senha_repetida` vêm no formato `{detail, codigo}` com status 400.

**Cadastro com e-mail já existente** responde **202** com a mesma mensagem de um cadastro novo, de propósito (a API não revela quais e-mails existem).

**Links de e-mail:** `netafim://convite?acao=<convite|redefinir-senha>&token=<token>`. O token é de uso único. Link usado, vencido ou inventado responde **`401 token_invalido`** ("Link inválido ou expirado. Peça um novo.").

**Convite não libera o acesso por si só:** quem aceita o convite define a senha, mas se a conta ainda está `pendente` o login continua dando `403 conta_pendente` até o Netafim Admin aprovar. Contas criadas por um Netafim Admin já nascem `ativo` e entram logo depois de definir a senha.

`auth/logout` **exige** o access token (sem ele: 401), além do refresh no corpo.

### 3.6 Cadastro com aviso de privacidade (LGPD)

1. `GET auth/termo` → `{ "versao_vigente": "1.0" }` (público).
2. `POST auth/cadastro` com `nome`, `email`, `senha`, `aceite_termo: true`, `versao_termo` (a vigente) e, opcionalmente, `telefone`. Resposta **202**.
3. A conta nasce **`pendente`**. Só um Netafim Admin aprova (veja [`usuarios.md`](usuarios.md)).
4. Versão do termo desatualizada → `400 termo_desatualizado`. A resposta já traz o campo extra `versao_vigente`: use-o e peça novo aceite.

---

## 4. Estados da conta no login

Quando a senha está certa mas a conta não pode entrar, a resposta é **403** com um destes códigos:

| `codigo` | Situação | Mensagem sugerida ao usuário |
|---|---|---|
| `conta_pendente` | Aguardando aprovação | "Seu cadastro ainda está em análise pela Netafim." |
| `conta_recusada` | Cadastro recusado | "Sua solicitação de acesso não foi aprovada." |
| `conta_inativa` | Desativada | "Sua conta está inativa. Procure o administrador." |
| `conta_bloqueada` | Bloqueada | "Sua conta está bloqueada. Procure a Netafim." |
| `dispositivo_revogado` | Aparelho revogado | "Este aparelho teve o acesso revogado." |

E-mail ou senha errados, ou conta temporariamente bloqueada por tentativas, respondem **401 `credenciais_invalidas`** com a **mesma mensagem**, de propósito: a API não revela se o e-mail existe.

---

## 5. Autorização: quem pode o quê

A autorização tem **três camadas**, todas do lado do servidor:

1. **Perfil e permissões** (tabelas `perfil`, `permissao`, `perfil_permissao` do banco). Seis perfis:

| Perfil | Organização | Nível |
|---|---|---|
| Netafim Admin | Netafim | admin |
| Netafim Usuário | Netafim | usuario |
| Distribuidor Admin | Distribuidor | admin |
| Distribuidor Usuário | Distribuidor | usuario |
| Cliente Admin | Cliente | admin |
| Cliente Usuário | Cliente | usuario |

2. **Escopo da organização:** cada pessoa só enxerga e altera o que é da própria organização (e o Netafim Admin vê tudo). Isso é imposto pelo banco (RLS), não só pela API.
3. **Regras de negócio do banco** (gatilhos), por exemplo a máquina de estados do atendimento.

**Como o app deve usar:** o `usuario.permissoes` devolvido no login e em `GET /me` serve para **mostrar ou esconder** funcionalidades. Mesmo que o app erre, o servidor recusa com **403**. Trate o 403 como situação normal, nunca como falha do app.

Matriz completa de permissões por perfil: [`usuarios.md`](usuarios.md#4-permissões-por-perfil).

### Como o servidor identifica quem está agindo

Cada requisição roda numa transação em que a API informa o usuário ao banco (`app.current_user_id`). Sem usuário identificado o banco nega tudo. Isso vale para qualquer módulo que acesse o banco pela conexão da API.

---

## 6. Verificação em duas etapas (MFA/TOTP)

Opcional por usuário. Compatível com Google Authenticator, Microsoft Authenticator, Authy e 1Password (6 dígitos, 30 s).

| Ação | Rota | Corpo | Resposta |
|---|---|---|---|
| Ver estado | `GET me/mfa` | — | `{ "ativo", "codigos_recuperacao_restantes" }` |
| Iniciar | `POST me/mfa/iniciar` | — | `{ "segredo", "otpauth_uri" }` |
| Confirmar e ligar | `POST me/mfa/confirmar` | `{ "codigo" }` | `{ "codigos_recuperacao": [10 códigos] }` |
| Desativar | `POST me/mfa/desativar` | `{ "senha", "codigo" }` | 204 |
| Redefinir (suporte) | `POST usuarios/{id}/mfa/resetar` | — | 204 (**só Netafim Admin**; encerra as sessões da pessoa) |

**Pontos de atenção:**

- Os **códigos de recuperação aparecem uma única vez**. O app deve obrigar a pessoa a confirmar que os guardou antes de fechar a janela.
- Um código TOTP e um código de recuperação **valem uma vez**.
- Sem a conexão `auth` configurada no servidor, ou sem a migração `007` aplicada no banco, as rotas respondem **503 `mfa_indisponivel`** e o login segue só com senha.
- O MFA protege o **login**. Um access já emitido segue válido até vencer (15 min).

Guia completo: [`MFA_Autenticacao_em_Dois_Fatores.txt`](MFA_Autenticacao_em_Dois_Fatores.txt).

---

## 7. Limites de tentativas

| O quê | Limite |
|---|---|
| Login, renovar sessão (`auth/refresh`), aceitar convite e redefinir senha | 10 por minuto por IP (`THROTTLE_LOGIN`) |
| Cadastro e "esqueci a senha" | 5 por hora por IP (`THROTTLE_CADASTRO`) |
| MFA (verificar, iniciar, confirmar, desativar) | 10 por minuto por IP (`THROTTLE_MFA`) |
| Senha errada ou código MFA errado | 5 seguidos bloqueiam a conta por 15 minutos (contado pelo banco) |

Excedeu o limite por IP → **429** com `{"detail": "Pedido foi suprimido. Espera-se que esteja diponível em N segundos."}` (sem `codigo`; o texto vem do DRF, com a grafia dele). Os dois grupos de limite são independentes: estourar o do cadastro não bloqueia o login. Só o Netafim Admin desbloqueia a conta antes dos 15 minutos.

---

## 8. Passo a passo para o Android

```
1. Abrir o app  -> ler a sessão guardada (sem internet)
2. Sem sessão   -> tela de login
3. POST auth/login
      |- mfa_necessario = true  -> tela de código -> POST auth/mfa/verificar
      |- tokens                 -> guardar access + refresh + usuário
4. Chamadas      -> Authorization: Bearer <access>
5. Recebeu 401 numa chamada autenticada (sem token ou access vencido/inválido)
                 -> POST auth/refresh (uma vez, com trava)
      |- 200 -> guardar o par novo e repetir a chamada original
      |- 401 -> limpar sessão e voltar ao login
6. Sair          -> POST auth/logout (se houver rede) e limpar a sessão local
```

**Em desenvolvimento:** o build de debug só permite HTTP para `localhost` e `10.0.2.2`. Num celular físico, use `adb reverse tcp:8000 tcp:8000` e gere o app com `-Pnetafim.apiBaseUrl=http://localhost:8000/api/v1/`.

---

## 9. Checklist de boas práticas

- [ ] Nunca registrar senha, token, segredo de MFA ou códigos de recuperação em log.
- [ ] Mostrar a mensagem do `codigo`, não o texto cru do servidor, quando houver tradução no app.
- [ ] Tratar 401 com renovação única e travada; 403 como "sem permissão".
- [ ] Gerar o UUID do aparelho uma vez e guardá-lo.
- [ ] Não depender de `permissoes` para segurança: é só conforto de interface.

---

## 10. O que ainda não existe

- Obrigar MFA para administradores (hoje é opcional; decisão do grupo).
- Leitor de QR code no app (hoje: chave digitada ou link `otpauth://`).
- E-mail de aviso ao ativar ou desativar o MFA (depende do SMTP).
