# API Netafim (Django REST Framework)

## Pré-requisitos

- Python 3.12 e [uv](https://docs.astral.sh/uv/)
- PostgreSQL 16, local ou via `docker compose`

## Primeira execução

```bash
cp ../.env.example ../.env          # preencha DJANGO_SECRET_KEY e POSTGRES_PASSWORD
docker compose up -d db             # na raiz do repositório, ou use um PostgreSQL próprio

cd backend
uv sync
export $(grep -v '^#' ../.env | xargs)                      # Linux/macOS
export DATABASE_URL=postgres://netafim:SUA_SENHA@localhost:5432/netafim
uv run python manage.py migrate
uv run python manage.py createsuperuser                     # cria o primeiro Netafim Admin
uv run python manage.py runserver
```

No Windows (PowerShell), defina as variáveis com `$env:DJANGO_DEBUG="true"` e assim por diante.

O `createsuperuser` cria um **Netafim Admin** ativo e registra o domínio do e-mail dele como domínio corporativo da Netafim. A partir daí, os demais domínios, organizações e usuários são cadastrados pela API.

- Documentação navegável: <http://localhost:8000/api/docs/>
- Painel Django (apoio técnico): <http://localhost:8000/admin/>

## Testes e qualidade

```bash
uv run pytest                 # usa o PostgreSQL de DATABASE_URL (padrão: netafim/netafim@localhost)
uv run pytest --cov           # com cobertura
uv run ruff check . && uv run ruff format --check .
```

Os testes precisam de PostgreSQL de verdade: as regras do banco (CHECK, gatilhos, índices parciais) fazem parte do que está sendo testado.

## Contrato da API

Depois de mudar endpoints ou serializers, regenere o contrato. O CI falha se ele estiver desatualizado:

```bash
uv run python manage.py spectacular --file ../docs/api/acesso-openapi.yaml
```

## Estrutura

```
config/                 configuração do projeto (PROVISÓRIO, alinhar com o grupo)
apps/contas/            módulo de acesso (usuários e permissões)
  models.py             tabelas do MER/DER (Anexo A.1)
  migrations/           0001 tabelas · 0002 regras do banco · 0003 perfis e matriz
  servicos/             regras de negócio (login, tokens, cadastro, auditoria)
  escopo.py             quem vê/gerencia quem + permissões do DRF (para os outros módulos)
  api/                  serializers, views e rotas
  tests/                banco, autenticação, cadastro e restrições de acesso
```

Detalhes do módulo: [`docs/modulos/acesso.md`](../docs/modulos/acesso.md).
