# Módulo de acesso: usuários e permissões

**Responsável:** Rodrigo Aparecido de Almeida
**Entrega:** módulo de acesso funcionando (back-end, Android, banco e testes)
**Base:** Documento de Requisitos, Regras de Negócio e MER/DER (Anexos A.1, A.5 e B)

## O que este módulo faz

| Requisito | Onde está |
|---|---|
| RF01 / RNF17 – login | `POST /api/v1/auth/login`, tela de login |
| RF02 / RNF20 – níveis de acesso | 6 perfis e a matriz do Anexo B, que **moram no banco** (tabelas `perfil`, `permissao`, `perfil_permissao`) |
| RNF18 – e-mail corporativo | `dominio_email_corporativo`, conferido pela API e por gatilho do banco |
| RNF19 – validação do cliente final | autocadastro nasce `pendente`; só o Netafim Admin aprova |
| RF16 – gerenciar usuários | `/api/v1/usuarios` (criar, editar, ativar/desativar, excluir) |
| RF20 – gerenciar permissões | `PUT /api/v1/perfis/{id}/permissoes` |
| LGPD – consentimento | aceite do aviso com versão e data no cadastro e no convite |

## O banco é a fonte da verdade

O esquema (tabelas, constraints, gatilhos, funções e RLS) é do **banco do grupo** (Supabase). O Django **não cria nem altera** nada nele: os modelos são `managed = False` e nunca se roda `migrate` contra esse banco. Quando uma regra de segurança vive no banco (RLS, gatilho, função), a API a respeita e não a duplica às cegas.

### Contrato com o banco

- A API conecta como **`app_django`** (sem `BYPASSRLS`), nunca como dono do esquema nem como `postgres`. No Supabase, use o **Session pooler** (IPv4, porta 5432), com o usuário `app_django.<ref-do-projeto>` e `sslmode=require`.
- Cada requisição roda numa **transação** (`ATOMIC_REQUESTS`) e informa o ator ao banco: `set_config('app.current_user_id', '<id>', true)`. Sem ator o RLS nega tudo (falha fechada). No login e no cadastro, antes de existir ator, informa-se `app.login_email`.
- **Tokens:** `app_django` não tem privilégio em `token_acesso`. Emitir, rotacionar e revogar passam só por `fn_emitir_token`, `fn_rotacionar_token` e `fn_revogar_token`.
- **Falhas de login e bloqueio** (5 erros, 15 minutos) são contadas pelo banco em `fn_registrar_login`; a API não consegue alterar esses campos.
- **Auditoria:** o banco grava a trilha (`log_auditoria`, com cadeia de hash) por gatilhos e funções. O sinal `evento_auditoria` da API fica só como log complementar.
- Os testes usam um retrato do banco em [`backend/db/snapshot_banco_atual.sql`](../../backend/db/snapshot_banco_atual.sql). Se o banco mudar, regere o retrato e rode a suíte.

### Aviso para quem usa o banco compartilhado

Não rode `migrate` contra o Supabase. O `Dockerfile` provisório ainda executa `migrate` na subida e deve deixar de fazê-lo.

## Autenticação

- **Access token:** JWT de 15 minutos no cabeçalho `Authorization: Bearer ...`. A cada requisição a API confere no banco se a conta continua ativa e se o aparelho não foi revogado.
- **Refresh token:** opaco, **90 dias** (regra do banco), guardado só como hash. Cada uso gera um novo (rotação). Reusar um refresh já trocado encerra as sessões daquele aparelho, porque indica roubo. **Não há janela de tolerância:** se a resposta da rotação se perder na rede, o app precisa entrar de novo.
- **Aparelho obrigatório:** o login exige o UUID do aparelho (o banco só emite refresh para um aparelho do usuário). O Netafim Admin pode revogar um aparelho perdido; isso encerra os tokens dele e invalida o access na hora.
- **Bloqueio:** 5 senhas erradas seguidas bloqueiam o login por 15 minutos, contadas pelo banco. Só o Netafim Admin desbloqueia antes.
- **Sem enumeração:** login, cadastro e "esqueci a senha" respondem igual exista ou não o e-mail.
- **Logout:** revoga o refresh informado.

## Fluxo de cadastro (Regras de Negócio)

1. A pessoa solicita acesso pelo app → usuário `pendente`, sem perfil nem organização.
2. O Netafim Admin lista os pendentes (`GET /usuarios?situacao=pendente`).
3. Ele aprova definindo organização e perfil (`POST /usuarios/{id}/aprovar`) ou recusa.
4. A pessoa recebe e-mail e passa a entrar no app.

Admins também cadastram pessoas por **convite** (`POST /usuarios`). O convidado recebe um link para definir a senha e aceitar o aviso de privacidade.

- **Netafim Admin** cria qualquer usuário, que já nasce aprovado. Como o banco só emite convite para usuário *pendente*, a API cria pendente, emite o convite e aprova em seguida.
- **Admin de distribuidor ou de cliente** cria usuários de nível Usuário da própria organização, que ficam pendentes até o Netafim Admin validar.
- **Reenviar convite:** pendente recebe novo convite (14 dias); quem já foi aprovado recebe o link de redefinição (2 horas).

### Links do e-mail (convite e redefinição de senha)

Os e-mails levam um link `netafim://convite?acao=<convite|redefinir-senha>&token=<token>`. O app o abre sozinho, numa tela do módulo de acesso (**Definir senha de acesso** ou **Redefinir senha**), onde a pessoa cria a senha (no convite, também aceita o aviso de privacidade) e segue para o login.

- O link é registrado por um `activity-alias` no manifesto do **próprio módulo** (`feature/acesso`), apontando para a `MainActivity`, e o Navigation Compose o leva até a tela. O `:app` não precisa conhecer o link.
- O token é de uso único, só é validado pela API e é descartado do estado do app assim que usado. Link inválido ou expirado mostra uma mensagem e leva ao login.
- Testado num celular real (Samsung SM-S938B): redefinição de senha, convite com aceite do aviso, e login com a senha criada.

## Quem gerencia quem

| Ator | Vê | Gerencia |
|---|---|---|
| Netafim Admin | todos | todos; único que aprova, define perfil e bloqueia |
| Distribuidor Admin | usuários do próprio distribuidor | usuários de nível Usuário do próprio distribuidor |
| Cliente Admin | usuários da própria empresa | usuários de nível Usuário da própria empresa |
| Perfis Usuário | só a si mesmo (`/me`) | ninguém |

Proteções extras: ninguém gerencia a si mesmo pela lista, e o último Netafim Admin ativo não pode ser desativado, excluído ou rebaixado. Quem garante isso, em duas camadas, é a API (mensagem clara) e o banco (RLS e gatilhos).

## Endpoints

O contrato completo, gerado pelo código, está em [`docs/api/acesso-openapi.yaml`](../api/acesso-openapi.yaml). Com a API rodando, a documentação navegável fica em `/api/docs/` (Swagger).

| Método e rota | Quem usa |
|---|---|
| `POST auth/login` · `auth/refresh` · `auth/logout` | todos |
| `POST auth/cadastro` · `GET auth/termo` | público (app) |
| `POST auth/convite/aceitar` · `auth/senha/esqueci` · `auth/senha/redefinir` | público (link do e-mail) |
| `GET/PATCH me` · `POST me/senha` | usuário logado |
| `GET/POST usuarios` · `GET/PATCH/DELETE usuarios/{id}` | admins, no escopo |
| `POST usuarios/{id}/aprovar` · `recusar` · `situacao` · `perfil` · `reenviar-convite` | admins, conforme a permissão |
| `GET usuarios/{id}/dispositivos` · `POST dispositivos/{id}/revogar` | Netafim Admin |
| `distribuidores` · `clientes` | leitura no escopo; escrita só Netafim Admin |
| `GET perfis` · `GET permissoes` · `PUT perfis/{id}/permissoes` | leitura para todos; edição só Netafim Admin |
| `dominios` | Netafim Admin |

Erros de regra de negócio seguem o formato do DRF com um código estável:
`{"detail": "mensagem em português", "codigo": "conta_pendente"}`.

## Como testar com Swagger ou Insomnia

1. Suba a API (veja [`backend/README.md`](../../backend/README.md)).
2. **Swagger:** abra `http://localhost:8000/api/docs/`. Faça `POST /auth/login` (informe `dispositivo.id_dispositivo` com um UUID qualquer), copie o `access` e clique em **Authorize** (esquema `jwtAuth`).
3. **Insomnia:** *Import → From URL* `http://localhost:8000/api/schema/` (ou o arquivo `docs/api/acesso-openapi.yaml`). Crie uma variável de ambiente `token` e use `Bearer {{ token }}`.
4. **Primeiro administrador:** a API não cria o primeiro Netafim Admin (o RLS exige um administrador como ator). Rode `uv run python manage.py gerar_hash_senha` e aplique o hash no SQL Editor: `update usuario set password = '<hash>' where lower(email) = lower('<e-mail do admin>');`.

5. **Teste de fumaça contra o banco de verdade** (só leitura, não cria nada): com a API ligada ao Supabase, rode `API_URL=... SMOKE_EMAIL=... SMOKE_SENHA=... uv run python scripts/smoke_api.py`. Confere login, `/me`, listas, renovação de sessão e logout.

## Como os outros módulos usam o acesso

**Back-end:**

```python
from apps.contas.constantes import Permissao
from apps.contas.escopo import RequerPermissao

class AtendimentoViewSet(...):
    permission_classes = [RequerPermissao.de(Permissao.ABRIR_ATENDIMENTO)]

# Em regras de negócio:
if request.user.tem_permissao(Permissao.VINCULAR_TECNICO): ...
```

`request.user` traz `tipo_organizacao`, `distribuidor_id`, `cliente_id` e `perfil`. Como o ator já foi informado ao banco na autenticação, **as consultas dos outros módulos na mesma requisição já passam pelo RLS** (as políticas de visibilidade do atendimento são do banco).

Os códigos de `Permissao` são os mesmos da tabela `permissao`. Há um teste que acusa se código e banco se separarem.

**Android:**

```kotlin
// Retrofit já com token e renovação automática:
val api = AcessoGrafo.retrofitAutenticado.create(AtendimentoApi::class.java)

// Usuário logado e permissões (funciona offline):
val usuario = AcessoGrafo.repositorio.sessao.value?.usuario
if (usuario?.temPermissao(Permissoes.ABRIR_ATENDIMENTO) == true) { ... }
```

## Pontos para alinhar com o grupo

Arquivos **provisórios** criados só para o módulo rodar e ser testado: `backend/config/` (settings, urls), `backend/Dockerfile`, `docker-compose.yml`, `.env.example`, `android/` raiz e `android/app/`, `.github/workflows/`. A branch do Rafael também mexe em `config/settings.py`, `pyproject.toml` e `uv.lock`, então haverá conflito de merge a resolver.

### Sobre o banco (itens que dependem de quem mantém o SQL)

1. **Encerrar sessões na troca de senha.** O banco não tem como revogar todos os tokens de um usuário. Há uma proposta em [`backend/db/propostas/001_fn_revogar_tokens_usuario.sql`](../../backend/db/propostas/001_fn_revogar_tokens_usuario.sql). Enquanto não for aplicada, a API troca a senha, registra um aviso no log e as sessões antigas valem até expirar.
2. **A API lê o hash da senha e a trilha de auditoria.** `app_django` tem `SELECT` na tabela `usuario` inteira e em `log_auditoria`. Sugestão: privilégio por coluna (sem `password`) e sem leitura da auditoria.
3. **Gatilho `fn_usuario_protege` não protege senha, e-mail nem consentimento de terceiros.** Um admin de empresa com `editar_usuarios` poderia alterá-los direto no banco (a API não oferece isso).
4. **Política `usuario_insert_admin`** deixa um admin de empresa inserir usuário já ativo e com perfil admin. A API restringe (pendente, nível Usuário), mas o banco não.
5. **Funções com `search_path` mutável** (10, apontadas pelo Supabase) e **sem timeouts** por papel.
6. **`fn_rotacionar_token`:** sem tolerância para resposta perdida em rede móvel, não devolve o tipo do token consumido e `fn_emitir_token` não devolve a validade.
7. **Último Netafim Admin:** a proteção está só na API; sob concorrência dois admins poderiam se inativar ao mesmo tempo.
8. **Configuração `app.acao_sistema`:** quem executa SQL com a credencial da API consegue ligá-la com `set_config`, e ela desliga as proteções do gatilho `fn_usuario_protege`, assim como o ator (`app.current_user_id`) é informado pela própria conexão. O código da API nunca a usa; a mitigação é a credencial só ter o necessário e consultas sempre parametrizadas.

### Decisões que afetam outros módulos

1. **Formato de erro** `{"detail", "codigo"}` nos endpoints do acesso. Vale adotar o mesmo padrão nos demais?
2. **Códigos de permissão:** a fonte é a tabela `permissao`. Quem usar o código por texto solto precisa conferir os nomes (por exemplo, `criar_usuarios`, no plural).
3. **Organização informada no autocadastro:** o MER/DER não tem coluna para a pessoa dizer de qual empresa é. Hoje o admin deduz pelo e-mail ou pelo contato.
4. **Identidade visual:** o tema Android usa cores aproximadas até termos o manual da Netafim.

## Rodando localmente

Veja [`backend/README.md`](../../backend/README.md) e [`android/README.md`](../../android/README.md).
