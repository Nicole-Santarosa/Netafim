# Netafim Pós-venda

Aplicativo Android offline-first de pós-venda para a Netafim, com API Django REST Framework e banco PostgreSQL no Supabase. Projeto do Centro Universitário Barão de Mauá.

## Back-end

API Django REST Framework que conversa com o PostgreSQL do Supabase. Fica em `backend/`.

### Requisitos

- Python 3.12 ou mais novo
- [uv](https://docs.astral.sh/uv/)

### Como rodar

Dentro da pasta `backend/`:

1. Instalar as dependências:

   ```
   uv sync
   ```

2. Criar o arquivo `backend/.env` com as variáveis da seção abaixo. Esse arquivo é ignorado pelo Git e não deve ser enviado ao repositório.

3. Conferir a configuração:

   ```
   uv run python manage.py check
   ```

### Variáveis do .env

O arquivo é lido em `backend/config/settings.py`. Variáveis já definidas no ambiente têm prioridade sobre o arquivo.

Obrigatórias (sem elas o projeto não sobe):

| Variável | Para que serve |
|---|---|
| `SECRET_KEY` | Chave secreta do Django. Use um valor longo e aleatório, diferente em cada ambiente. |
| `DB_HOST` | Host do Session pooler do Supabase. Exemplo: `<host-do-pooler>`. |
| `DB_PASSWORD` | Senha da role `app_django`. Exemplo: `<senha-do-app_django>`. |

Opcionais:

| Variável | Padrão | Para que serve |
|---|---|---|
| `DB_USER` | `app_django` | Usuário do banco. No pooler tem o formato `app_django.<ref-do-projeto>`. |
| `DB_NAME` | `postgres` | Nome do banco. |
| `DB_PORT` | `5432` | Porta do Session pooler. |
| `DEBUG` | `false` | `true` liga o modo de depuração. Nunca ligar fora do desenvolvimento. |
| `ALLOWED_HOSTS` | vazio | Hosts aceitos pela API, separados por vírgula. |

Exemplo:

```
SECRET_KEY=<chave-secreta-longa-e-aleatoria>
DEBUG=true
ALLOWED_HOSTS=localhost,127.0.0.1
DB_HOST=<host-do-pooler>
DB_PORT=5432
DB_NAME=postgres
DB_USER=app_django.<ref-do-projeto>
DB_PASSWORD=<senha-do-app_django>
```

### Conexão com o banco

- A conexão usa o Session pooler do Supabase.
- O usuário tem o formato `app_django.<ref-do-projeto>`.
- A API conecta sempre como `app_django`, uma role que não ignora o RLS. Nunca como `postgres`.
- O IP de quem estiver testando precisa estar liberado em Network Restrictions no Supabase.
- O pooler não aceita cursor no servidor nem prepared statements, por isso o `settings.py` desliga os dois (`DISABLE_SERVER_SIDE_CURSORS` e `prepare_threshold = None`).

### Regras do projeto

- O Django nunca cria tabelas. Os models usam `managed = False`, não existe pasta `migrations` e o `migrate` não é usado.
- As tabelas, funções, gatilhos e políticas de RLS vêm dos scripts SQL do banco. O SQL é a fonte da verdade. Os scripts ainda não estão versionados neste repositório; onde ficam será combinado com o grupo.
- Cada requisição roda dentro de uma transação (`ATOMIC_REQUESTS = True`).

### Estrutura de pastas do back-end

```
backend/
├── config/
│   ├── __init__.py
│   ├── settings.py   configurações lidas das variáveis de ambiente
│   ├── urls.py       rotas (ainda vazio)
│   └── wsgi.py
├── manage.py
├── pyproject.toml    dependências e versão do Python
└── uv.lock
```

### Estado atual do back-end

Pronto:
- Base do projeto Django (config, manage.py, dependências com uv).
- Conexão com o banco no Supabase testada.
- DRF configurado para aceitar e devolver só JSON.

Ainda não existe:
- Apps do Django.
- Models.
- Endpoints.
- Autenticação.
