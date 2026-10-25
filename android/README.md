# App Android Netafim (Kotlin + Jetpack Compose)

## Módulos

| Módulo | Conteúdo | Responsável |
|---|---|---|
| `:app` | Application, MainActivity, tema e navegação raiz (**provisório**) | a alinhar |
| `:feature:acesso` | login, solicitação de cadastro, esqueci a senha, perfil e sessão | Rodrigo |

Cada integrante cria o próprio módulo em `feature/` e o registra no `settings.gradle.kts`, assim ninguém precisa editar o código do outro.

## Rodando

1. Suba a API (veja `backend/README.md`).
2. Abra a pasta `android/` no Android Studio e rode o app `app` no emulador.

O emulador acessa a API do computador em `http://10.0.2.2:8000/api/v1/` (padrão em `gradle.properties`). Para outro endereço:

```bash
./gradlew assembleDebug -Pnetafim.apiBaseUrl=https://SEU_SERVIDOR/api/v1/
```

HTTP sem TLS só é permitido no build de debug e para `10.0.2.2`/`localhost` (`src/debug/res/xml/network_security_config.xml`).

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
