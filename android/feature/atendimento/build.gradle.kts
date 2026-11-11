// Módulo de atendimento: abertura, listagem, detalhe e acompanhamento dos atendimentos de pós-venda.
// Responsável: Rafael. Por enquanto só a camada de interface; a de dados entra depois.
plugins {
    // Biblioteca Android (o :app é quem gera o APK) e Kotlin.
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    // Compilador do Compose, necessário para as funções @Composable.
    alias(libs.plugins.kotlin.compose)
}

android {
    // Pacote base do módulo; o R (recursos) deste módulo nasce dele.
    namespace = "com.netafim.posvenda.atendimento"
    compileSdk = 35

    defaultConfig {
        // Mesmo minSdk do :app e do :feature:acesso.
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Java 17 no código Java e no Kotlin, igual aos demais módulos.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    // Liga o Compose neste módulo.
    buildFeatures {
        compose = true
    }
    // Nos testes unitários, chamadas ao framework Android devolvem valor padrão em vez de lançar erro.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // Compose: o BOM fixa as versões de todas as bibliotecas Compose, por isso as demais não têm versão.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    // Anotação @Preview e utilitários de pré-visualização.
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    // Ferramentas de inspeção e preview, só nas builds de debug.
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Ciclo de vida: collectAsStateWithLifecycle e viewModel() dentro de composables.
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // Navegação entre as telas do módulo.
    implementation(libs.androidx.navigation.compose)
    // Coroutines para as chamadas assíncronas dos ViewModels.
    implementation(libs.kotlinx.coroutines.android)

    // Testes unitários (rodam na JVM, sem aparelho).
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
