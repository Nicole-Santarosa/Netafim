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

A API conecta como **`app_django`** (sem `BYPASSRLS`), nunca como dono do esquema nem como `postgres`:

```
DATABASE_URL=postgres://app_django:SENHA@HOST:5432/postgres?sslmode=require
```

No **Supabase** use o *Session pooler* (IPv4, porta 5432) em *Connect*; o usuário vira `app_django.<ref-do-projeto>`. Senhas com caracteres especiais precisam ir codificadas na URL (`@` → `%40`, `#` → `%23`, `/` → `%2F`).

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

Os testes rodam **como a API roda no ar**: conectados como `app_django`, com RLS, gatilhos e funções reais. O `DATABASE_URL` dos testes aponta para um PostgreSQL com **superusuário**, só para criar e derrubar os bancos de teste. Cada teste ganha um banco novo, copiado de um modelo criado a partir de [`db/snapshot_banco_atual.sql`](db/snapshot_banco_atual.sql), um retrato do banco do grupo. Se o banco mudar, regere o retrato e rode a suíte.

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
  snapshot_banco_atual.sql   retrato do banco, usado só nos testes
  propostas/                 mudanças sugeridas ao banco (NÃO aplicadas)
```

Detalhes do módulo: [`docs/modulos/acesso.md`](../docs/modulos/acesso.md).
