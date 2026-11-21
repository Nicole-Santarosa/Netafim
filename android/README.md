# App Android Netafim (Kotlin + Jetpack Compose)

## Módulos

| Módulo | Conteúdo | Responsável |
|---|---|---|
| `:app` | Application, MainActivity, tema e navegação raiz (**provisório**) | a alinhar |
| `:feature:acesso` | login, solicitação de cadastro, esqueci a senha, perfil, sessão e links de convite e redefinição de senha | Rodrigo |

Cada integrante cria o próprio módulo em `feature/` e o registra no `settings.gradle.kts`, assim ninguém precisa editar o código do outro.

## Rodando

1. Suba a API (veja `backend/README.md`).
2. Abra a pasta `android/` no Android Studio e rode o app `app` no emulador.

O emulador acessa a API do computador em `http://10.0.2.2:8000/api/v1/` (padrão em `gradle.properties`). Para outro endereço:

```bash
./gradlew assembleDebug -Pnetafim.apiBaseUrl=https://SEU_SERVIDOR/api/v1/
```

HTTP sem TLS só é permitido no build de debug e para `10.0.2.2`/`localhost` (`src/debug/res/xml/network_security_config.xml`).

### Celular físico (USB)

O celular não enxerga o `10.0.2.2`. Com a depuração USB autorizada, encaminhe a porta da API pelo cabo e gere o app apontando para `localhost`:

```bash
adb reverse tcp:8000 tcp:8000
./gradlew installDebug -Pnetafim.apiBaseUrl=http://localhost:8000/api/v1/
```

### Links dos e-mails (convite e redefinição de senha)

Abrem o app numa tela do módulo de acesso. Para testar sem e-mail:

```bash
adb shell am start -a android.intent.action.VIEW -c android.intent.category.BROWSABLE   -d 'netafim://convite?acao=redefinir-senha&token=<token do e-mail>'
```

O esquema `netafim://` só existe no **debug**: qualquer app instalado pode registrá-lo e receber o token do link. No **release** o link é um App Link `https://<domínio>/convite?acao=...&token=...`, verificado pelo sistema.

Para o release (e para o App Link funcionar em produção) são necessários, **fora do app**:

1. Um domínio HTTPS (ex.: `app.seu-dominio.com.br`) publicando `https://<domínio>/.well-known/assetlinks.json` com o `package_name` `com.example.netafim` e o SHA-256 do certificado de assinatura do release.
2. No servidor da API, a variável `URL_APP_CONVITE=https://<domínio>/convite` (o padrão do back-end é `netafim://convite`, só para desenvolvimento).
3. Gerar o app com o domínio, e a API em HTTPS:

```bash
./gradlew assembleRelease -Pnetafim.apiBaseUrl=https://SEU_SERVIDOR/api/v1/ -Pnetafim.linkHost=app.seu-dominio.com.br
```

O `assembleRelease` **falha** se a API não for `https://` ou se `netafim.linkHost` estiver vazio (tarefa `verificarConfiguracaoRelease`), para não gerar um APK que aponta para o emulador ou cujos links dos e-mails não abrem.

> No Windows, se o caminho do projeto tiver acento (como `Repositório`), o Gradle recusa a compilação. Use `-Pandroid.overridePathCheck=true` ou mova o projeto para uma pasta sem acento.

## Testes

```bash
./gradlew testDebugUnitTest      # validações, ViewModels e repositório (MockWebServer)
./gradlew lintDebug
```

## Sessão e segurança

- Tokens guardados em DataStore, cifrados com AES-256-GCM por uma chave do Android Keystore.
- Access token renovado automaticamente ao receber 401 (rotação do refresh, com uma única renovação para requisições simultâneas).
- A sessão é restaurada ao abrir o app **sem precisar de internet**; o perfil mostra os últimos dados salvos quando estiver offline.
- Backup na nuvem desativado (`allowBackup=false` e `data_extraction_rules.xml`).

## Usando o acesso em outro módulo

```kotlin
val api = AcessoGrafo.retrofitAutenticado.create(MinhaApi::class.java)
val usuario = AcessoGrafo.repositorio.sessao.value?.usuario
usuario?.temPermissao(Permissoes.ABRIR_ATENDIMENTO)
```

Detalhes: [`docs/modulos/acesso.md`](../docs/modulos/acesso.md).
