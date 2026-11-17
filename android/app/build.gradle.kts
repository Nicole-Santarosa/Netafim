// PROVISÓRIO: casca do aplicativo (Application, Activity, tema e navegação raiz),
// criada para o módulo de acesso rodar. Alinhar com o grupo antes do merge.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val urlApi = providers.gradleProperty("netafim.apiBaseUrl").get()

// Domínio do App Link dos e-mails de convite e de redefinição de senha (ex.: app.netafim.com.br).
// Em branco no debug; o release exige um valor (ver verificarConfiguracaoRelease).
val linkHost = providers.gradleProperty("netafim.linkHost").orElse("").get().trim()

android {
    namespace = "com.example.netafim"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.netafim"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "URL_API", "\"$urlApi\"")
        buildConfigField("String", "LINK_HOST", "\"$linkHost\"")
        // Sem domínio, o filtro https do manifesto aponta para um host reservado que não casa com nada.
        manifestPlaceholders["linkHost"] = linkHost.ifEmpty { "link.invalid" }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// O release não pode sair com a URL de desenvolvimento (HTTP, emulador) nem sem o domínio dos links:
// o app só fala HTTPS em produção e, sem o App Link verificado, os links dos e-mails não abrem o app.
val verificarConfiguracaoRelease = tasks.register("verificarConfiguracaoRelease") {
    val url = urlApi
    val host = linkHost
    doLast {
        check(url.startsWith("https://")) {
            "Build release recusado: netafim.apiBaseUrl precisa começar com https:// (recebido: $url). " +
                "Ex.: ./gradlew assembleRelease -Pnetafim.apiBaseUrl=https://SEU_SERVIDOR/api/v1/ " +
                "-Pnetafim.linkHost=app.seu-dominio.com.br"
        }
        check(host.isNotEmpty()) {
            "Build release recusado: defina netafim.linkHost (domínio do App Link, ex.: app.seu-dominio.com.br). " +
                "Sem ele os links de convite e de redefinição de senha não abrem o app."
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(verificarConfiguracaoRelease) }

dependencies {
    implementation(project(":feature:acesso"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
