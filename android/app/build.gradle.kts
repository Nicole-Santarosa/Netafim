plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// URL base da API definida nas propriedades locais do Gradle.
val urlApi = providers.gradleProperty("netafim.apiBaseUrl").get()

ksp {
    arg("room.schemaLocation", "$projectDir/schemas") // room.schemaLocation informa ao Room onde salvar os schemas. $projectDir/schemas define a pasta schemas dentro do módulo app.
}

// Configurações do Android
android {
    namespace = "com.example.netafim"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.netafim"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Disponibiliza a URL da API para o AcessoGrafo.
        buildConfigField("String", "URL_API", "\"$urlApi\"")
    }

    // Configurações de build
    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    // Configurações de compatibilidade com Java
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // Configurações de compatibilidade com Kotlin
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences) // Dependência para persistir o cursor global de sincronização PULL
    implementation(project(":feature:acesso")) // Módulo de acesso: autenticação, cadastro, sessão e perfil.
    implementation(libs.room.ktx) // Dependência para utilizar withTransaction (permite executar várias operações DAO dentro de uma única transação).
}
