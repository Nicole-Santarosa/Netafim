// PROVISÓRIO: estrutura base criada junto com o módulo de acesso. Cada integrante
// adiciona o próprio módulo de funcionalidade aqui (ex.: ":feature:atendimento").
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "NetafimPosVenda"

include(":app")
include(":feature:acesso")
