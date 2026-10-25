# Módulo de acesso: usuários e permissões

**Responsável:** Rodrigo Aparecido de Almeida
**Entrega:** módulo de acesso funcionando (back-end, Android, banco e testes)
**Base:** Documento de Requisitos, Regras de Negócio e MER/DER (Anexos A.1, A.5 e B)

## O que este módulo faz

| Requisito | Onde está |
|---|---|
| RF01 / RNF17 – login | `POST /api/v1/auth/login`, tela de login |
| RF02 / RNF20 – níveis de acesso | 6 perfis + matriz do Anexo B carregados na migration `0003` |
| RNF18 – e-mail corporativo | `DOMINIO_EMAIL_CORPORATIVO`, conferido na API e por gatilho no banco |
| RNF19 – validação do cliente final | autocadastro nasce `pendente`; só o Netafim Admin aprova |
| RF16 – gerenciar usuários | `/api/v1/usuarios` (criar, editar, ativar/desativar, excluir) |
| RF20 – gerenciar permissões | `PUT /api/v1/perfis/{id}/permissoes` |
| LGPD – consentimento | aceite do aviso com versão e data no cadastro e no convite |

## Banco de dados

Tabelas do MER/DER implementadas com os mesmos nomes de tabela e coluna:
`usuario`, `perfil`, `permissao`, `perfil_permissao`, `distribuidor`, `cliente`,
`dominio_email_corporativo`, `token_acesso`, `dispositivo`.

Regras garantidas pelo PostgreSQL (seção 5.1), cobertas por testes em `test_banco.py`:

- uma organização por usuário, com `tipo_organizacao` coerente com a chave preenchida;
- usuário `ativo` exige perfil e organização;
- o perfil do usuário é sempre do mesmo tipo de organização (chave estrangeira composta), o que impede escalada de privilégio mesmo fora da API;
- Netafim e distribuidor só ficam ativos com domínio corporativo ativo (gatilho);
- e-mail único sem diferenciar maiúsculas, só entre usuários não excluídos;
- senha só como hash; token só como hash SHA-256;
- inativar, bloquear, recusar ou excluir a conta revoga os tokens; revogar o aparelho revoga os tokens dele;
- campos de segurança do token não podem ser alterados depois de gravados;
- `sync_seq` global atribuído pelo banco em `distribuidor` e `cliente`.

## Autenticação

- **Access token:** JWT de 15 minutos no cabeçalho `Authorization: Bearer ...`.
- **Refresh token:** opaco, 30 dias, guardado só como hash em `token_acesso`. Cada uso gera um novo (rotação). Reusar um refresh já trocado encerra a sessão daquele aparelho, porque indica roubo.
- **Bloqueio:** 5 senhas erradas seguidas bloqueiam o login por 15 minutos.
- **Sem enumeração:** login, cadastro e "esqueci a senha" respondem igual exista ou não o e-mail.
- **Aparelho:** o app envia um UUID próprio no login; o Netafim Admin pode revogar um aparelho perdido.

## Fluxo de cadastro (Regras de Negócio)

1. A pessoa solicita acesso pelo app → usuário `pendente`, sem perfil nem organização.
2. O Netafim Admin lista os pendentes (`GET /usuarios?situacao=pendente`).
3. Ele aprova definindo organização e perfil (`POST /usuarios/{id}/aprovar`) ou recusa.
4. A pessoa recebe e-mail e passa a entrar no app.

Admins também podem cadastrar pessoas por **convite** (`POST /usuarios`). O convidado recebe um link para definir a senha e aceitar o aviso de privacidade. Quando o convite é feito por admin de distribuidor ou de cliente, o usuário entra como nível Usuário da mesma organização e continua pendente até o Netafim Admin validar.

## Quem gerencia quem

| Ator | Vê | Gerencia |
|---|---|---|
| Netafim Admin | todos | todos; único que aprova, define perfil e bloqueia |
| Distribuidor Admin | usuários do próprio distribuidor | usuários de nível Usuário do próprio distribuidor |
| Cliente Admin | usuários da própria empresa | usuários de nível Usuário da própria empresa |
| Perfis Usuário | só a si mesmo (`/me`) | ninguém |

Proteções extras: ninguém gerencia a si mesmo pela lista, e o último Netafim Admin ativo não pode ser desativado, excluído ou rebaixado.

## Endpoints

O contrato completo, gerado pelo código, está em [`docs/api/acesso-openapi.yaml`](../api/acesso-openapi.yaml). Com a API rodando, a documentação navegável fica em `/api/docs/`.

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

`request.user` traz `tipo_organizacao`, `distribuidor_id`, `cliente_id` e `perfil`, que é o necessário para a regra de visibilidade dos atendimentos (seção 5.3), responsabilidade do módulo de atendimento.

**Android:**

```kotlin
// Retrofit já com token e renovação automática:
val api = AcessoGrafo.retrofitAutenticado.create(AtendimentoApi::class.java)

// Usuário logado e permissões (funciona offline):
val usuario = AcessoGrafo.repositorio.sessao.value?.usuario
if (usuario?.temPermissao(Permissoes.ABRIR_ATENDIMENTO) == true) { ... }
```

**Auditoria:** os eventos de segurança (login, falha, bloqueio, aprovação, mudança de perfil...) são publicados no sinal `apps.contas.servicos.auditoria.evento_auditoria`. Quem implementar `LOG_AUDITORIA` só precisa conectar um receptor; este módulo não grava nessa tabela.

## Pontos para alinhar com o grupo antes do merge

Arquivos **provisórios**, criados só para o módulo rodar e ser testado:

- `backend/config/` (settings, urls), `backend/Dockerfile`, `docker-compose.yml` e `.env.example`;
- `android/` raiz (settings, versões em `gradle/libs.versions.toml`), `android/app/` (Application, MainActivity, tema com cores aproximadas e tela inicial provisória);
- `.github/workflows/`.

Decisões que afetam outros módulos:

1. **Formato de erro** `{"detail", "codigo"}` nos endpoints do acesso. Vale adotar o mesmo padrão nos demais?
2. **`fn_atribuir_sync_seq` e a sequência `sync_seq_global`** foram criadas aqui. Quem cuida da sincronização deve reutilizá-las, não recriá-las.
3. **`LOG_AUDITORIA`**: definir quem implementa; o ponto de integração já existe (sinal acima).
4. **Visibilidade com Row Level Security (MER/DER, seção 5.3):** fica com o módulo de atendimento; este módulo fornece perfil, organização e permissões.
5. **Organização informada no autocadastro:** o MER/DER não tem coluna para a pessoa dizer de qual empresa é. Hoje o admin deduz pelo e-mail ou pelo contato. Sugestão: uma coluna opcional `organizacao_informada`, se o grupo concordar.
6. **Identidade visual:** o tema Android usa cores aproximadas até termos o manual da Netafim.

## Rodando localmente

Veja [`backend/README.md`](../../backend/README.md) e [`android/README.md`](../../android/README.md).
