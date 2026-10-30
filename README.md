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
├── apps/
│   ├── __init__.py
│   ├── atendimentos/
│   │   ├── __init__.py
│   │   ├── apps.py
│   │   ├── models.py   models do atendimento (espelho das tabelas, sem migrations)
│   │   └── tests/
│   │       ├── __init__.py
│   │       └── test_abertura_model.py
│   └── core/
│       ├── __init__.py
│       ├── erros.py    traduz erros do banco em respostas HTTP (EXCEPTION_HANDLER do DRF)
│       └── tests/
│           ├── __init__.py
│           └── test_erros.py
├── config/
│   ├── __init__.py
│   ├── settings.py        configurações lidas das variáveis de ambiente
│   ├── settings_teste.py  configurações dos testes (banco local, com trava de segurança)
│   ├── urls.py            rotas (ainda vazio)
│   └── wsgi.py
├── tests/
│   ├── test_conexao.py
│   └── test_trava_seguranca.py
├── conftest.py            ajuste do pytest para usar o banco já montado
├── manage.py
├── pyproject.toml         dependências e versão do Python
└── uv.lock
```

### Testes

Os testes rodam sempre em um PostgreSQL local em Docker, nunca no Supabase. O SQLite não serve, porque não executa gatilhos nem RLS.

Os scripts SQL ainda não estão versionados no repositório. A pasta `infra/db` (scripts 01 a 04) é passada pelo responsável pelo banco.

1. Subir o PostgreSQL local:

   ```
   docker run -d --name netafim-pg-teste \
     -e POSTGRES_PASSWORD=senha_local_teste \
     -p 127.0.0.1:5544:5432 postgres:16
   ```

2. Criar o banco e carregar os scripts em ordem (na raiz do repositório). O script 03 cria a role `app_django`; a senha dela é definida no último comando:

   ```
   docker exec netafim-pg-teste createdb -U postgres netafim_teste
   for f in 01_schema 02_funcoes_gatilhos 03_seguranca_rls 04_dados_iniciais; do
     docker exec -i netafim-pg-teste psql -U postgres -d netafim_teste -v ON_ERROR_STOP=1 < infra/db/$f.sql || break
   done
   docker exec netafim-pg-teste psql -U postgres -d netafim_teste \
     -c "alter role app_django with password 'senha_local_teste'"
   ```

3. Rodar os testes, dentro de `backend/`:

   ```
   uv run pytest
   ```

O `config/settings_teste.py` aponta para esse banco por padrão. Para usar outro banco local, defina `TESTE_DB_HOST`, `TESTE_DB_PORT`, `TESTE_DB_NAME`, `TESTE_DB_USER`, `TESTE_DB_PASSWORD` e, se quiser, `TESTE_SECRET_KEY`. Os valores desse arquivo têm prioridade sobre o `backend/.env`, então o banco do Supabase nunca é usado nos testes.

Há uma trava de segurança: se o host do banco não for `127.0.0.1` nem `localhost`, o `settings_teste.py` levanta `ImproperlyConfigured` e os testes nem começam. O `test_trava_seguranca.py` confere essa trava.

### Estado atual do back-end

Pronto:
- Base do projeto Django (config, manage.py, dependências com uv).
- Conexão com o banco no Supabase testada.
- DRF configurado para aceitar e devolver só JSON.
- App atendimentos com os models do atendimento (espelho das tabelas, sem migrations).
- Base de testes (pytest contra o PostgreSQL local em Docker, com trava contra banco remoto).
- Tradutor de erros do banco para a API (`apps/core/erros.py`): erro de regra de negócio, duplicado, referência inválida, campo obrigatório, sem permissão e valor inválido viram respostas HTTP com `codigo` e `detail`. Erro de CHECK de tabela não mostra a mensagem do banco; erro de gatilho mostra a própria mensagem.
- Serviço de abertura de atendimento (`apps/atendimentos/servicos.py`): `abrir_atendimento(id_solicitante, dados)` aceita só as colunas que o solicitante pode informar (o próprio solicitante entra como argumento, não nos dados), grava com `Atendimento.objects.create` e relê a linha para trazer protocolo, versão, sync_seq e status gerados pelo banco. Erros do banco passam direto para o tradutor. Reenvio do mesmo id ainda não é tratado (o banco recusa o duplicado).
- Serializer de entrada da abertura (`apps/atendimentos/serializers.py`): `AberturaAtendimentoSerializer` valida os campos que o aplicativo pode enviar (obrigatórios: `id_atendimento`, `origem`, `motivo` e `contato_solicitante`), as listas fixas pelos `TextChoices` do model e as regras entre campos (`cliente_final` exige `id_cliente`, `netafim` exige `area_setor`, `qtd_participantes_prevista` maior que zero). Os snapshots e `id_distribuidor_origem` ficam de fora e o `validated_data` pode ir direto para `abrir_atendimento`. O banco continua sendo a fonte da verdade. Só entrada.
- View de abertura (`apps/atendimentos/views.py`): `POST /api/v1/atendimentos` (`AberturaAtendimentoView`) valida com `AberturaAtendimentoSerializer`, chama `abrir_atendimento(request.user.pk, validated_data)` e responde 201 com `AtendimentoCriadoSerializer` (`id_atendimento`, `numero_protocolo`, `status`, `prioridade`, `versao`, `sync_seq`, `data_abertura`). Exige usuário autenticado; erros do banco passam pelo tradutor. Testada com `force_authenticate`; a autenticação real (JWT) é de outro módulo e ainda não está nesta branch. Ainda não preenche `id_distribuidor_origem` nem os snapshots.

Ainda não existe:
- Demais apps do Django.
- Demais models.
- Demais endpoints (listagem, detalhe e mudança de status).
- Autenticação.
