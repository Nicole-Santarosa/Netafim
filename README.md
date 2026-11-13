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
│   │   ├── excecoes.py      conflito de versão e falta de permissão na alteração
│   │   ├── exportacao.py    exportar_atendimentos, a parte dos atendimentos na exportação do titular
│   │   ├── models.py        models do atendimento (espelho das tabelas, sem migrations)
│   │   ├── serializers.py   entrada da abertura e da alteração, saída da lista, do detalhe e do histórico
│   │   ├── servicos.py      abrir_atendimento e atualizar_atendimento
│   │   ├── urls.py          rotas de /api/v1/atendimentos
│   │   ├── views.py         listagem, abertura, detalhe, alteração e catálogo de culturas
│   │   └── tests/
│   │       ├── __init__.py
│   │       ├── test_abertura_model.py
│   │       ├── test_exportacao.py
│   │       ├── test_historico_origem.py
│   │       ├── test_integridade_propriedade.py
│   │       ├── test_rls_perfis.py
│   │       ├── test_serializer_abertura.py
│   │       ├── test_servico_abertura.py
│   │       ├── test_view_abertura.py
│   │       ├── test_view_alteracao.py
│   │       ├── test_view_alteracao_situacao.py
│   │       ├── test_view_culturas.py
│   │       ├── test_view_detalhe.py
│   │       └── test_view_listagem.py
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
│   ├── urls.py            rotas (api/v1/ inclui as do atendimento)
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

O que cada arquivo de teste cobre (121 testes no total, conferidos com `uv run pytest --collect-only -q`):

- `tests/test_conexao.py` (1): a conexão de teste é local e não ignora o RLS.
- `tests/test_trava_seguranca.py` (1): o `settings_teste.py` recusa host que não seja local.
- `apps/core/tests/test_erros.py` (8): o tradutor de erros do banco (23514, 23505, 23503, 23502, 42501 e 22xxx) e dois erros reais do PostgreSQL.
- `apps/atendimentos/tests/test_abertura_model.py` (2): a abertura pelo model preenche protocolo, status, prioridade, versão, sync_seq e histórico; `save()` e `delete()` do histórico são recusados.
- `apps/atendimentos/tests/test_servico_abertura.py` (6): `abrir_atendimento`, incluindo a falha num vínculo de cultura, que desfaz o atendimento.
- `apps/atendimentos/tests/test_serializer_abertura.py` (13): validação do corpo da abertura.
- `apps/atendimentos/tests/test_view_abertura.py` (11): `POST /api/v1/atendimentos`, incluindo a lista `culturas` (gravada e visível no detalhe; id repetido, inexistente ou excluído responde 400 no campo; abertura sem culturas igual a antes).
- `apps/atendimentos/tests/test_view_listagem.py` (11): `GET /api/v1/atendimentos`, com ordem, filtros por status e por origem (sozinhos e juntos), paginação e isolamento por empresa.
- `apps/atendimentos/tests/test_view_detalhe.py` (10): `GET /api/v1/atendimentos/<uuid>`, incluindo as culturas (lista vazia, ordem por nome, isolamento entre atendimentos e o mesmo campo na resposta do `PATCH`).
- `apps/atendimentos/tests/test_view_alteracao.py` (9): `PATCH /api/v1/atendimentos/<uuid>`, com controle de versão e permissões.
- `apps/atendimentos/tests/test_view_alteracao_situacao.py` (8): `PATCH` com `situacao_projeto` (Netafim Admin altera e limpa com nulo, valor fora da lista dá 400, versão velha dá 409, `PATCH` sem o campo não mexe nele).
- `apps/atendimentos/tests/test_view_culturas.py` (3): `GET /api/v1/culturas`, com as culturas dos dados iniciais em ordem de nome, sem a cultura excluída e fechado para quem não está autenticado. A cultura excluída vem da fixture `cultura_excluida` do `conftest.py`, que a apaga no fim do teste.
- `apps/atendimentos/tests/test_integridade_propriedade.py` (4): propriedade do mesmo cliente abre; propriedade de outro cliente é recusada pela FK composta (422); o CHECK `ck_atend_propriedade_cliente` é conferido por INSERT direto pela conexão de administrador; e o Cliente Usuário que envia `id_propriedade` sem `id_cliente` recebe 403 do RLS, que recusa antes de o CHECK ser avaliado. As propriedades são criadas por SQL, pela conexão de administrador, porque ainda não há model.
- `apps/atendimentos/tests/test_historico_origem.py` (5): o CHECK de origem do histórico (usuário exige responsável, sistema não pode ter responsável) e o evento "criado" da abertura pela API, que sai como usuário.
- `apps/atendimentos/tests/test_exportacao.py` (10): `exportar_atendimentos` com titular sem atendimento, titular que abriu (com o que ele digitou e os eventos em que é responsável), técnico responsável (sem o que o cliente digitou), atendimento de outra pessoa fora, Netafim Admin exportando outro usuário (só o do titular, mesmo enxergando os dois), objeto ou id do usuário, saída serializável em JSON, ordem estável, duas consultas qualquer que seja o volume e atendimento excluído logicamente (entra com `excluido_em`).
- `apps/atendimentos/tests/test_rls_perfis.py` (19): o RLS do atendimento nos seis perfis, com 8 usuários (um de cada perfil, mais o Distribuidor Admin e o Cliente Usuário de uma segunda organização) e 8 atendimentos montados pela conexão de administrador. Confere, por perfil, o que ele enxerga e o que consegue alterar (UPDATE com rowcount); o caso do Cliente Admin que vê o atendimento de outro usuário da empresa mas não o altera; o isolamento entre distribuidores e entre empresas.

### Estado atual do back-end

Pronto:
- Base do projeto Django (config, manage.py, dependências com uv).
- Conexão com o banco no Supabase testada.
- DRF configurado para aceitar e devolver só JSON.
- App atendimentos com os models do atendimento (espelho das tabelas, sem migrations).
- Base de testes (pytest contra o PostgreSQL local em Docker, com trava contra banco remoto).
- Tradutor de erros do banco para a API (`apps/core/erros.py`): erro de regra de negócio, duplicado, referência inválida, campo obrigatório, sem permissão e valor inválido viram respostas HTTP com `codigo` e `detail`. Erro de CHECK de tabela não mostra a mensagem do banco; erro de gatilho mostra a própria mensagem.
- Serviço de abertura de atendimento (`apps/atendimentos/servicos.py`): `abrir_atendimento(id_solicitante, dados)` aceita só as colunas que o solicitante pode informar (o próprio solicitante entra como argumento, não nos dados), grava com `Atendimento.objects.create` e, na mesma transação, um `AtendimentoCultura` por id da chave opcional `culturas` (se um vínculo falhar, o atendimento também é desfeito), e relê a linha para trazer protocolo, versão, sync_seq e status gerados pelo banco. Erros do banco passam direto para o tradutor. Reenvio do mesmo id ainda não é tratado (o banco recusa o duplicado).
- Serializer de entrada da abertura (`apps/atendimentos/serializers.py`): `AberturaAtendimentoSerializer` valida os campos que o aplicativo pode enviar (obrigatórios: `id_atendimento`, `origem`, `motivo` e `contato_solicitante`), as listas fixas pelos `TextChoices` do model e as regras entre campos (`cliente_final` exige `id_cliente`, `netafim` exige `area_setor`, `qtd_participantes_prevista` maior que zero). Os snapshots ficam de fora (o servidor monta), `id_distribuidor_origem` é opcional (o banco barra id inexistente), `culturas` é uma lista opcional de ids que recusa repetição e cultura inexistente ou excluída (erro no campo `culturas`) e o `validated_data` pode ir direto para `abrir_atendimento`. O banco continua sendo a fonte da verdade. Só entrada.
- View de abertura (`apps/atendimentos/views.py`): `POST /api/v1/atendimentos` (`AtendimentosView`) valida com `AberturaAtendimentoSerializer`, chama `abrir_atendimento(request.user.pk, validated_data)` e responde 201 com `AtendimentoCriadoSerializer` (`id_atendimento`, `numero_protocolo`, `status`, `prioridade`, `versao`, `sync_seq`, `data_abertura`). Exige usuário autenticado; erros do banco passam pelo tradutor. Testada com `force_authenticate`; a autenticação real (JWT) é de outro módulo e ainda não está nesta branch. Ainda não preenche os snapshots.
- Listagem de atendimentos (`GET /api/v1/atendimentos`, mesma `AtendimentosView`): devolve `count`, `next`, `previous` e `results` (25 por página, do mais novo para o mais antigo por `data_abertura`), com `AtendimentoListaSerializer` (`id_atendimento`, `numero_protocolo`, `status`, `motivo`, `origem`, `nome_produtor`, `nome_fazenda_informado`, `cidade`, `data_abertura`, `data_atualizacao`, `versao`, `sync_seq`). Filtros opcionais `?status=` e `?origem=` (`netafim`, `distribuidor` ou `cliente_final`), com os valores do model (inválido devolve 400); juntos, valem como E. A view não filtra por usuário: o RLS do banco decide o que cada um vê. O nome do cliente na lista virá do snapshot, em outra peça.
- Detalhe do atendimento (`GET /api/v1/atendimentos/<uuid:id_atendimento>`, `DetalheAtendimentoView`): devolve os dados completos (`AtendimentoDetalheSerializer`, lista explícita de campos, sem `deletado_em`) mais a chave `historico` com a linha do tempo (`HistoricoAtendimentoSerializer`: `id_historico`, `tipo_evento`, `origem_evento`, `valor_anterior`, `valor_novo`, `data_evento`; sem `observacao`, que pode ter dado pessoal). Traz também a chave `culturas`, lista de `{id_cultura, nome}` em ordem de nome (vazia quando não há cultura), lida dos models `Cultura` e `AtendimentoCultura` em uma consulta só, sem vínculos nem culturas excluídos logicamente. Responde 404 para atendimento inexistente, excluído ou que o RLS esconde do usuário.
- Alteração do atendimento (`PATCH /api/v1/atendimentos/<uuid:id_atendimento>`, na mesma `DetalheAtendimentoView`): o corpo traz `versao` (a que o aplicativo conhece, obrigatória) e ao menos um entre `status`, `destinado_a`, `id_distribuidor`, `id_tecnico_responsavel` (aceita nulo para desvincular) e `situacao_projeto` (mesma lista do `POST`, aceita nulo; depois da abertura só o Netafim Admin altera, regra do gatilho do banco, os outros perfis recebem 422). `atualizar_atendimento` faz `UPDATE ... WHERE versao = esperada`; sem linha alterada, responde 404 (invisível, inexistente ou excluído), 409 `conflito_de_versao` com `versao_atual` (versão velha) ou 403 `sem_permissao` (RLS de UPDATE). A máquina de estados e quem pode cada mudança são do gatilho do banco (erro vira 422). Transições que dependem de agendamento, relatório ou pesquisa seguem 422 até os outros módulos existirem. Responde 200 com o mesmo formato do detalhe.
- Catálogo de culturas (`GET /api/v1/culturas`, `CulturasView`): lista simples, sem paginação, de `{id_cultura, nome}` (`CulturaSerializer`), só culturas sem `deletado_em`, em ordem de nome e depois de `id_cultura`. Exige usuário autenticado. É de onde o aplicativo tira os ids da lista `culturas` da abertura.
- Exportação dos atendimentos do titular (LGPD, acesso e portabilidade) em `apps/atendimentos/exportacao.py`: `exportar_atendimentos(usuario)` recebe o usuário ou o id inteiro e devolve um dicionário pronto para JSON com os atendimentos em que ele é solicitante, técnico responsável ou o último a alterar (em ordem de abertura e protocolo, excluídos logicamente incluídos com `excluido_em`) e os eventos do histórico em que ele é o responsável. O que foi digitado no pedido só vai para o solicitante; snapshots, ids de outras pessoas e `observacao` do histórico ficam fora. Filtra pelo titular na consulta (o RLS segue valendo para quem está na sessão), não grava auditoria e não tem rota: quem a chama é a exportação do módulo de contas.
- Contrato dos endpoints de atendimento (abertura, listagem, detalhe, alteração e catálogo de culturas) em `docs/api/atendimentos-openapi.yaml`.

Ainda não existe:
- Demais apps do Django.
- Demais models.
- Autenticação.
