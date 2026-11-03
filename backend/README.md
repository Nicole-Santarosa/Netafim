# API Netafim (Django REST Framework)

A API usa o **banco do grupo** (PostgreSQL no Supabase) como fonte da verdade: tabelas, gatilhos, funções e RLS pertencem ao banco. O Django só mapeia as tabelas (`managed = False`) e **nunca roda `migrate` nesse banco**.

## Pré-requisitos

- Python 3.12 e [uv](https://docs.astral.sh/uv/)
- PostgreSQL 16 ou superior (para testes e desenvolvimento local)

## Configuração

```bash
cp ../.env.example ../.env          # preencha DJANGO_SECRET_KEY
cd backend
uv sync
```

### Conexão com o banco

A API conecta como **`app_django`** (sem `BYPASSRLS`), nunca como dono do esquema nem como `postgres`. O `config/settings.py` aceita **dois modos**; configure só um:

| Modo | Variáveis | Observações |
|---|---|---|
| **`DB_*`** (convenção do grupo) | `DB_HOST` (obrigatória), `DB_PASSWORD` (obrigatória), `DB_PORT` (padrão `5432`), `DB_NAME` (padrão `postgres`), `DB_USER` (padrão `app_django`), `DB_SSLMODE` | **Tem prioridade:** vale sempre que `DB_HOST` estiver preenchido. Evita o problema de caractere especial na senha. `DB_SSLMODE` é `prefer` quando o host é `localhost`, `127.0.0.1` ou `db`, e `require` em qualquer outro; use `disable` num banco local sem SSL. |
| **`DATABASE_URL`** | `postgres://app_django:SENHA@HOST:5432/postgres?sslmode=require` | Senhas com caracteres especiais precisam ir codificadas na URL (`@` → `%40`, `#` → `%23`, `/` → `%2F`). Sem `DB_HOST` nem `DATABASE_URL`, o padrão é `postgres://app_django:app_django@localhost:5432/netafim`, só para uso local. |

`DATABASE_CONN_MAX_AGE` (padrão `60`, em segundos) vale nos dois modos.

No **Supabase** use o *Session pooler* (IPv4, porta 5432) em *Connect*; o usuário vira `app_django.<ref-do-projeto>`.

As variáveis **`POSTGRES_*`** do `.env.example` **não são lidas pela API**: servem só ao `docker-compose.yml`, para criar o banco do contêiner `db`. Atenção: nesse arquivo a API recebe `DATABASE_URL` com o usuário `POSTGRES_USER`, que é o superusuário do contêiner e não o `app_django`, então o RLS não é exercido. O contêiner nasce vazio (o Django nunca cria as tabelas); para usá-lo, carregue [`db/snapshot_banco_atual.sql`](db/snapshot_banco_atual.sql), que cria o `app_django`, e aponte a API para ele pelos modos acima. O `docker-compose.yml` é provisório e não foi testado nesta revisão.

> **Não rode `migrate` contra o banco do grupo.** O esquema é mantido nos scripts SQL do banco.

## Rodando a API

```bash
uv run python manage.py runserver
```

- Swagger (documentação navegável e teste): <http://localhost:8000/api/docs/>
- Esquema OpenAPI (para importar no Insomnia): <http://localhost:8000/api/schema/>

### Primeiro administrador

A API não cria o primeiro Netafim Admin (o RLS exige um administrador como ator). Gere o hash da senha e aplique no SQL Editor do banco:

```bash
uv run python manage.py gerar_hash_senha
```

```sql
update usuario set password = '<hash>' where lower(email) = lower('<e-mail do admin>');
```

## Testes e qualidade

```bash
uv run pytest                 # precisa de um PostgreSQL com superusuário (DATABASE_URL)
uv run pytest --cov
uv run ruff check . && uv run ruff format --check .
```

Os testes rodam **como a API roda no ar**: conectados como `app_django`, com RLS, gatilhos e funções reais. O `DATABASE_URL` dos testes aponta para um PostgreSQL com **superusuário**, só para criar e derrubar os bancos de teste. Cada teste ganha um banco novo, copiado de um modelo criado a partir de [`infra/db/base/001_schema_inicial.sql`](../infra/db/base/001_schema_inicial.sql) mais as migrações de [`infra/db/migracoes/`](../infra/db/migracoes/), na ordem. O banco de teste é o mesmo que será aplicado.

## Contrato da API

Depois de mudar endpoints ou serializers, regenere o contrato. O CI falha se ele estiver desatualizado:

```bash
uv run python manage.py spectacular --file ../docs/api/acesso-openapi.yaml
```

## Estrutura

```
config/                 configuração do projeto (PROVISÓRIO, alinhar com o grupo)
apps/contas/            módulo de acesso (usuários e permissões)
  models.py             mapeamento das tabelas do banco (managed = False)
  contexto.py           ator da transação (set_config) para o RLS do banco
  servicos/             regras de negócio (login, tokens, cadastro, tradução de erros do banco)
  escopo.py             quem vê/gerencia quem + permissões do DRF (para os outros módulos)
  api/                  serializers, views e rotas
  tests/                banco (contrato), autenticação, cadastro e restrições de acesso
db/
  testes/                    dados_teste.sql (usuários de teste, só para banco local)
```

Os scripts de banco (base e migrações, **não aplicadas** no banco compartilhado) ficam em [`../infra/db/`](../infra/db/), fora do `backend/`; veja as regras de numeração em [`infra/db/README.md`](../infra/db/README.md).

Detalhes do módulo: [`docs/modulos/acesso.md`](../docs/modulos/acesso.md).
