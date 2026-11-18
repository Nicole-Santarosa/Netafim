# Módulo de acesso no aplicativo Android

> **Responsável:** Rodrigo (Pessoa 1). **Código:** `android/feature/acesso` (biblioteca) e a ligação em `android/app` (`NavegacaoRaiz.kt`).
> **API que o módulo usa:** [`docs/api/acesso-openapi.yaml`](../api/acesso-openapi.yaml) e `docs/modulos/acesso.md`, que vivem na linha do back-end (`pr/01` a `pr/07`, `feat/exportacao-atendimentos`); esta linha do Android é independente dela.
> **Estado:** implementado e testado, em branches empilhadas ainda **não mescladas** na `main` (ver "Branches e ordem de merge").

---

## 1. O que o módulo faz

| Requisito | O que o aplicativo entrega |
|---|---|
| RF01 Realizar login | Login com e-mail corporativo e senha; segundo fator (código do aplicativo autenticador) quando a conta tem MFA; "Esqueci minha senha"; entrada pelo link do e-mail (convite e redefinição) |
| RF02 Controlar níveis de acesso | A sessão guarda o perfil e as permissões; as telas só mostram o que a pessoa pode fazer; a API continua sendo quem decide |
| RF16 Gerenciar usuários | Lista, detalhe, novo usuário (convite), editar nome e telefone, ativar, desativar, definir perfil, reenviar convite e excluir; aprovação de cadastros pendentes (Netafim Admin) |
| RF20 Gerenciar permissões | Permissões por perfil: lista dos perfis e edição das permissões de cada um (só Netafim Admin) |
| RNF17 a RNF23 e LGPD | Cadastro com aviso de privacidade e aceite; trocar senha; MFA; Privacidade e meus dados (pedidos do titular e cópia dos próprios dados) |

---

## 2. Telas

Telas com quadro no Figma (arquivo "Netafim", seção Acesso): Login (quadro "HD 01"), Recuperar senha, Cadastro, Aguardando validação, Aprovação de cadastro e Gestão de usuários. **As demais não têm quadro** e usam a mesma base visual: barra azul de 64 dp com o título, campos e botões do Cadastro (`PaginaComCabecalho`, `CampoDoPrototipo`, `BotaoCheio`, `BotaoContorno`).

| Tela | Rota | Quem acessa | Como chegar | API |
|---|---|---|---|---|
| Login e segundo fator | `acesso/login` | Todos | Abertura do app sem sessão | `POST auth/login`, `POST auth/mfa/verificar` |
| Cadastro | `acesso/cadastro` | Cliente final | Login, "Criar conta" | `GET auth/termo`, `POST auth/cadastro` |
| Recuperar senha | `acesso/esqueci-senha` | Todos | Login, "Esqueci minha senha" | `POST auth/senha/esqueci` |
| Convite e redefinição | `acesso/link` (deep link `netafim://convite?acao=&token=`) | Todos | Link do e-mail | `POST auth/convite/aceitar`, `POST auth/senha/redefinir` |
| Meu perfil | `acesso/perfil` | Todos | Início, foto de perfil | `GET me`, `GET/POST me/mfa/*` |
| Trocar senha | `acesso/perfil/senha` | Todos | Meu perfil | `POST me/senha` |
| **Privacidade** | `acesso/privacidade` | Todos | Meu perfil, "Privacidade e meus dados" | `GET me/solicitacoes-lgpd`, `GET me/dados` |
| **Fazer um pedido** | `acesso/privacidade/novo` | Todos | Privacidade, "Fazer um pedido" | `POST me/solicitacoes-lgpd` |
| **Pedidos de privacidade (fila)** | `acesso/privacidade/fila` | Netafim Admin | Início, "Pedidos de privacidade" | `GET solicitacoes-lgpd` |
| **Pedido de privacidade (detalhe)** | `acesso/privacidade/fila/{id}` | Netafim Admin | Fila, cartão do pedido | `POST solicitacoes-lgpd/{id}/analisar`, `/atender`, `/negar`, `GET solicitacoes-lgpd/{id}/dados` |
| Aprovação de cadastro | `acesso/aprovacao` | Permissão `aprovar_cadastro` | Início | `GET usuarios`, `POST usuarios/{id}/aprovar` e `recusar`, `GET distribuidores`, `GET clientes`, `GET perfis` |
| Gestão de usuários | `acesso/usuarios` | Admins | Início | `GET usuarios` |
| Novo usuário | `acesso/usuarios/novo` | Admin de distribuidor e de cliente | Gestão, "Adicionar usuário" | `POST usuarios` |
| Detalhe do usuário | `acesso/usuarios/{id}` | Admins | Gestão, cartão do usuário | `GET usuarios/{id}`, `POST .../situacao`, `POST .../perfil`, `POST .../reenviar-convite`, `DELETE usuarios/{id}`, `POST usuarios/{id}/mfa/resetar`, `GET usuarios/{id}/dispositivos`, `POST dispositivos/{id}/revogar` |
| Editar usuário | `acesso/usuarios/{id}/editar` | Admins | Detalhe, "Editar dados" | `PATCH usuarios/{id}` |
| Permissões por perfil | `acesso/permissoes` | Netafim Admin | Início | `GET perfis`, `GET permissoes` |
| Domínios de e-mail | `acesso/dominios` | Netafim Admin | Início | `GET/POST dominios`, `PATCH/DELETE dominios/{id}` |
| Editar permissões de um perfil | `acesso/permissoes/{id}` | Netafim Admin | Lista de perfis | `PUT perfis/{id}/permissoes` |

O Início (`InicioTela`) pertence ao módulo `app`; ele mostra os atalhos conforme a permissão da pessoa.

### 2.1 Privacidade e meus dados (LGPD)

- **Baixar meus dados:** busca `GET me/dados`, formata o JSON e o entrega pela **folha de compartilhar do sistema** (a pessoa escolhe onde guardar ou enviar). O app **não grava** a cópia no aparelho: ela fica só na memória até a entrega. A cópia traz conta, consentimento, aparelhos e pedidos; **não inclui** relatórios nem evidências dos atendimentos (não deve ser apresentada como "todos os seus dados"). Se o servidor tiver o módulo de atendimentos, a chave opcional `atendimentos` também vem no JSON.
- **Meus pedidos:** lista do mais novo ao mais antigo, com situação (Recebido, Em análise, Atendido, Negado), data do pedido, prazo (ou data de conclusão) e o **motivo**, quando negado. A lista atualiza sozinha ao voltar da tela de novo pedido.
- **Fazer um pedido:** oito tipos em linguagem simples (confirmar tratamento, receber cópia, corrigir, anonimizar, excluir, levar para outro serviço, saber com quem é compartilhado, retirar consentimento) e uma observação opcional (até 1000 caracteres, enviada sem espaços nas pontas e omitida se vazia).
- **Anonimizar e excluir** pedem uma **confirmação reforçada**: se o pedido for atendido, a conta é anonimizada, a pessoa perde o acesso e não há como desfazer; os registros legais dos atendimentos continuam guardados. Só depois de confirmar o pedido segue para a API.
- Erros tratados: `409 solicitacao_em_aberto` (já existe um pedido igual em andamento, aparece no campo do tipo), `503 lgpd_indisponivel` (servidor sem a migração 010), campos inválidos e falta de rede.
- Quem **atende** os pedidos é o Netafim Admin, pela fila da seção 2.2.

### 2.2 Fila de pedidos de privacidade (Netafim Admin)

- **Lista:** todos os pedidos, na ordem de prazo da API, com filtros **Abertos** (recebido e em análise, o padrão), **Atendidos**, **Negados** e **Todos** (o filtro é feito no aparelho). Cada cartão mostra o titular (ou "Titular anonimizado"), o tipo, a situação e o prazo (ou a data de conclusão); pedido fora do prazo (`atrasada`, calculado pela API) fica com borda e texto em vermelho e há um aviso com a contagem no alto da lista.
- **Detalhe:** titular, e-mail de contato, tipo, situação, datas, observação do titular e motivo da negativa. A API não tem rota de um pedido só: o pedido vem da lista e, depois de cada ação, da resposta da ação.
- **Ações** (`acoesDoPedido`, a API continua decidindo): **Marcar em análise** (só pedido recebido); **Atender** (pedido aberto, com confirmação); **Negar** (pedido aberto, com o **motivo obrigatório**, de até 2000 caracteres, que é o que o titular lê); **Baixar dados do titular** (só `acesso` e `portabilidade`, de pedido não negado e de titular que ainda tem conta). Pedido concluído não muda.
- **Atender anonimização ou eliminação** anonimiza a conta do titular de verdade (nome, e-mail e telefone descaracterizados, conta desativada, aparelhos e sessões encerrados, sem volta; o histórico legal dos atendimentos fica). O diálogo avisa isso. **Separação de funções:** o administrador não vê o botão Atender no próprio pedido desses tipos (a API também recusa com `403 proprio_pedido`); outro administrador precisa atendê-lo, e o dono do pedido ainda pode negar.
- **Cópia dos dados do titular:** mesma entrega da seção 2.1 (folha de compartilhar, sem gravar no aparelho). Exige a rota `GET solicitacoes-lgpd/{id}/dados`, que está na branch `feat/exportacao-atendimentos` do back-end: **uma API sem ela responde 404 e o app mostra a mensagem genérica de erro**.

### 2.3 Regras de gestão de usuários (`acoesPermitidas`)

A tela de detalhe só mostra os botões que a API aceitaria (a API continua decidindo; qualquer recusa vira mensagem):

- ninguém gerencia a si mesmo pela lista;
- o **Netafim Admin** gerencia todos; só ele desbloqueia contas e define o perfil (permissão `definir_perfil`);
- o **admin de distribuidor ou de cliente** gerencia só usuários de nível Usuário da própria organização;
- **reenviar convite** só para pendente; **ativar** só para inativo (ou bloqueado, para o Netafim Admin); **desativar** só para ativo; desativar e excluir pedem confirmação.

### 2.3.1 MFA e aparelhos no detalhe do usuário

- **Redefinir verificação em duas etapas** (só Netafim Admin): `POST usuarios/{id}/mfa/resetar`, com confirmação. Desativa o MFA da pessoa e encerra as sessões dela, para quem perdeu o aparelho e os códigos de recuperação. Sem MFA ativo a API responde 409 `mfa_inativo`.
- **Ver aparelhos**: quem gerencia a pessoa vê a lista (modelo, versão do app, último acesso, se foi revogado). **Revogar o acesso** de um aparelho é só do Netafim Admin (`POST dispositivos/{id}/revogar`), com confirmação.
- As ações seguem o servidor: as rotas ficam em `UsuarioViewSet`, que exige uma das permissões de gestão (editar, criar ou aprovar cadastro); revogar aparelho e redefinir MFA são só do Netafim Admin.

### 2.3.2 Domínios de e-mail (Netafim Admin)

Lista, cadastro, ativar e desativar, e excluir (com confirmação) dos domínios aceitos para a Netafim e para cada distribuidor (RNF18). O domínio é validado no aparelho (formato, sem `@`, em minúsculas) e de novo no servidor; domínio de distribuidor exige escolher o distribuidor. Atalho na tela inicial, só para o Netafim Admin.

### 2.4 Permissões por perfil (RF20)

`aprovar_cadastro` e `definir_perfil` ficam **travadas** no Netafim Admin (a API também recusa com `permissao_essencial`). A mudança vale já na próxima requisição de cada pessoa com o perfil; o app atualiza o perfil de quem está logado.

---

## 3. Arquitetura

```
android/feature/acesso/src/main/java/com/example/netafim/acesso/
├── AcessoGrafo.kt          ponto de entrada (injeção manual); inicializar() no Application
├── dominio/                modelos, regras puras e interfaces (Repositorio*): sem Android
├── dados/                  Retrofit (AcessoApi), DTOs, Repositorio*Remoto, armazenamento seguro da sessão
└── ui/                     telas Compose + ViewModels, por assunto (login, cadastro, perfil, usuarios,
                            permissoes, privacidade, admin, comum) e AcessoNavegacao.kt
```

- **Estado:** cada tela tem um `ViewModel` com um `StateFlow` de estado e a `Tela` composable recebe só estado e funções (por isso tem `@Preview` e é fácil de testar). A `Rota` liga os dois.
- **Resultado:** os repositórios devolvem `Resultado<T>` (`Sucesso` ou `Falha(ErroAcesso)`); nenhuma exceção chega à tela. `ErrosHttp` traduz HTTP e rede; `MensagensErro` traz o texto por código da API.
- **Rede:** `Rede` monta o OkHttp/Retrofit com `kotlinx.serialization` (`ignoreUnknownKeys`, `explicitNulls = false`); o cliente autenticado renova o token sozinho. Outros módulos usam `AcessoGrafo.clienteAutenticado`/`retrofitAutenticado` e `AcessoGrafo.repositorio.sessao.value?.usuario` (usuário logado e permissões).
- **Sessão:** tokens e usuário em armazenamento seguro; sem internet, o app mostra os dados salvos.
- **Repositórios no grafo:** `repositorio` (sessão, login, MFA, senha), `repositorioCadastros`, `repositorioUsuarios`, `repositorioPerfis`, `repositorioPrivacidade`.

### Pacote e `applicationId`

O Android usa **`com.example.netafim`** (`namespace` e `applicationId`), por decisão do grupo de 15/11/2026, para ficar igual à base de persistência (Room) e dos demais módulos. Antes dessa data o módulo de acesso usava `com.netafim.posvenda`. O renome foi feito num único PR no topo da cadeia, sem reescrever os anteriores. Os subpacotes são `com.example.netafim` (app) e `com.example.netafim.acesso` (módulo de acesso). Como o `applicationId` mudou, o aparelho trata o app como um app novo (a sessão e as fotos do app com o id antigo ficam nele).

### Como adicionar uma tela nova ao módulo

1. Modelos e regras em `dominio/`; método novo na interface do repositório.
2. Rota no `AcessoApi`, DTO em `Dtos.kt` e implementação em `dados/Repositorio*Remoto.kt`.
3. `ViewModel` + `Tela` + `Rota` em `ui/<assunto>/`; textos em `res/values/strings.xml` (sem texto fixo na tela).
4. Rota em `RotasAcesso` (e em `exigeSessao`, se exigir login), `composable` em `AcessoNavegacao.kt` e, se for tela nova do grafo, a chamada em `app/.../NavegacaoRaiz.kt`.
5. Testes: ViewModel com repositório falso e o repositório remoto com `MockWebServer`.

---

## 4. Como executar e testar

Com o JDK 21 em `JAVA_HOME`, dentro de `android/`:

```bash
./gradlew :feature:acesso:testDebugUnitTest :app:assembleDebug \
  -Pnetafim.apiBaseUrl=http://localhost:8010/api/v1/ -Pandroid.overridePathCheck=true --offline
```

(`10.0.2.2:8000` é o padrão do projeto e só serve para emulador.) Para ver no aparelho por USB: subir a API do back-end (`manage.py runserver 0.0.0.0:8010`), `adb reverse tcp:8010 tcp:8010` e instalar `app/build/outputs/apk/debug/app-debug.apk`. **Cuidado:** a API local, quando aponta para o Supabase, usa o banco real; contas de teste `e2e.*` devem ser apagadas ao fim (SQL de limpeza fora do repositório).

**Testes unitários do módulo: 223, sem falhas** (JUnit, `kotlinx-coroutines-test`, `MockWebServer`). Cobrem ViewModels (login, cadastro, MFA, trocar senha, aprovação, usuários, detalhe, edição, permissões, privacidade, fila de pedidos), regras de domínio (`acoesPermitidas`, `acoesDoPedido`, permissões essenciais, tipos de pedido) e o contrato com a API (rotas, métodos, corpos, erros).

### Verificação no aparelho

| Tela | Aparelho | Resultado |
|---|---|---|
| Login, recuperar senha, cadastro, aguardando, aprovação, gestão de usuários, perfil, início | Samsung (celular) e Galaxy Tab S9 FE | Conferidas contra o Figma |
| Detalhe do usuário, editar usuário, permissões por perfil (lista e editor), trocar senha (validação e erro da API) | Samsung SM-S938B, 15/11/2026 | Conferidas |
| **Privacidade, fazer um pedido** | Galaxy Tab S9 FE (SM-X510), 15/11/2026, API local → Supabase | Conferidas: lista de pedidos, cópia dos dados pela folha de compartilhar, validação sem tipo, confirmação reforçada (cancelada), pedido comum criado e listado com prazo, recusa de pedido repetido |
| **Fila de pedidos e detalhe (Netafim Admin)** | Galaxy Tab S9 FE (SM-X510), 15/11/2026, API local → Supabase | Conferidas com um pedido de cada tipo criado pela conta de cliente: lista e filtros, marcar em análise, atender (com confirmação), cópia dos dados do titular, negar sem motivo (validação) e com motivo, diálogo de atender anonimização (cancelado), e o titular vendo "Atendido" e os motivos das negativas em Meus pedidos. **Não testado no aparelho:** atender de fato uma anonimização (irreversível; só testes automatizados) e o aviso de pedido fora do prazo |
| **Novo usuário (convite)** | Galaxy Tab S9 FE (SM-X510), 15/11/2026, API local → Supabase, conta Distribuidor Admin | Conferido: validação dos campos vazios ("Informe o nome", "Informe o e-mail"), convite enviado (`POST usuarios` 201) e o usuário aparece como Pendente na lista. **Observação:** quem convida como admin de distribuidor ou de cliente cria o usuário pendente e o domínio do e-mail só é conferido quando o Netafim Admin aprova (`dominio_nao_autorizado`), por isso um domínio fora do cadastro é aceito no convite |
| **Trocar senha com sucesso** | Galaxy Tab S9 FE, 15/11/2026, API local → Supabase, conta Distribuidor Admin de teste | Conferida: a troca leva ao login; a senha antiga passa a ser recusada ("E-mail ou senha incorretos"), a nova entra; a senha original foi restaurada pelo próprio app |
| **MFA (ativar, código no login, desativar)** | Galaxy Tab S9 FE, 15/11/2026, mesma conta | Conferido: ativação pela chave exibida (código TOTP calculado fora do app), 10 códigos de recuperação, login pedindo o segundo fator, código errado recusado (401) e código correto aceito (auditoria `login_sucesso` com `mfa: totp`), desativação com senha e código; a conta voltou a ficar sem MFA |
| **Salvar permissões de um perfil** | Galaxy Tab S9 FE, 15/11/2026, API local → Supabase, conta Netafim Admin de teste | Conferido com o perfil Cliente Usuário (3 permissões): o botão Salvar só habilita com mudança; marcar "Consultar os atendimentos da empresa" e salvar (`PUT perfis/6/permissoes` 200) levou a lista a "4 permissões" e a marcação persistiu ao reabrir; desmarcar e salvar de novo (200) restaurou as 3 originais e as contagens de todos os perfis (17, 3, 10, 3, 8, 3) |

---

## 5. Branches e ordem de merge

Empilhadas (cada uma depende da anterior): `pr/04` → `pr/06-android-mfa` → `feat/acesso-telas-v2` → `feat/acesso-layout-figma` → `feat/acesso-trocar-senha` → `feat/acesso-detalhe-usuario` → `feat/acesso-editar-usuario` → `feat/acesso-permissoes-perfil` → `feat/acesso-privacidade-titular` → `feat/acesso-fila-privacidade` → `refactor/pacote-com-example-netafim`. Textos prontos dos PRs: `01_Gestao_do_Projeto/Textos_dos_PRs.md`.

---

## 6. O que ainda não existe

- Fotos de perfil ficam só no aparelho (a API ainda não tem onde receber a foto).
- Aviso por e-mail ao titular quando o pedido é concluído: o código do **servidor** existe na branch `feat/acesso-aviso-titular-e-erros` (não mesclada); o envio de verdade depende do SMTP do servidor.
- Confirmação do prazo de 15 dias com o jurídico (o servidor usa 15 dias; o app só mostra o prazo que vem da API).
- Os campos "propriedade" e "distribuidor" do cadastro são descartados pela API; `GET distribuidores/opcoes` (branch `feat/distribuidores-opcoes` do back-end) lista os distribuidores ativos para o cliente logado, mas o app ainda não a usa.
