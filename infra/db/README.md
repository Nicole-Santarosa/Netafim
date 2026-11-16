# Scripts de banco (PostgreSQL / Supabase)

Estrutura combinada pelo grupo (DT-011). O banco compartilhado é mantido por **scripts SQL**; ninguém
roda `migrate` do Django nele.

```
infra/db/
  base/                      retrato inicial do banco do grupo (já aplicado no Supabase)
    001_schema_inicial.sql
  migracoes/                 mudanças depois da base, numeradas, UMA sequência para todos
    001_..., 002_...
  verificar_migracoes.py     confere a numeração e calcula checksums
backend/db/testes/
  dados_teste.sql            usuários de teste (senha conhecida): só para banco local
```

## Regras

1. **Nome:** `NNN_descricao_em_minusculas.sql`. O número é único em cada pasta e compartilhado por todos
   os integrantes. A CI falha se dois arquivos da mesma pasta tiverem o mesmo número.
2. **Reservar o número antes de escrever:** anote no registro de decisões (ou avise o grupo) qual número
   vai usar. A CI só enxerga uma branch por vez: duas pessoas que pegam o mesmo número em branches
   diferentes só são pegas quando a segunda atualiza com a `main`, por isso reserve antes.
3. **Não editar uma migração depois de aplicada:** crie outra com o próximo número. O checksum registrado
   detecta alteração.
4. **Idempotência:** prefira scripts que repetem sem erro (`IF NOT EXISTS`, `CREATE OR REPLACE`).
5. **Quem aplica:** somente a Pessoa 1 (responsável pelo banco central), depois do "de acordo" do grupo.
   Quem escreve a migração entrega o arquivo.
6. **Os números não precisam ser contínuos:** PRs empilhados e paralelos entram na `main` em ordem
   diferente da numeração.

## Registro das migrações aplicadas

A migração `006_registro_de_migracoes.sql` (em PR próprio, junto das propostas 002 e 003) cria a tabela `migracao_aplicada` e a função
`fn_registrar_migracao`. Depois de aplicar qualquer script no Supabase, registre-o:

```bash
python infra/db/verificar_migracoes.py --checksums     # arquivo  sha256
```

```sql
select fn_registrar_migracao('migracoes/004_login_sem_leitura_de_hash.sql', '<sha256>');
```

A função recusa o mesmo arquivo duas vezes (execução duplicada) e recusa o mesmo arquivo com checksum
diferente (arquivo alterado depois de aplicado). Dados registrados: arquivo, checksum, data e quem aplicou.
Os scripts que **já estão** aplicados no Supabase (a base) precisam ser registrados à mão uma vez, por quem
tem acesso ao banco e sabe o estado real.

## Banco de teste

Os testes de integração do backend montam o banco a partir de `base/001_schema_inicial.sql` e das
migrações, na ordem. Não existe mais um retrato separado em `backend/db/testes/`: o que os testes usam é o
mesmo que será aplicado.
