plugins {
    // Plugin do aplicativo Android.
    alias(libs.plugins.android.application) apply false

    // Plugin para módulos Android do tipo biblioteca.
    alias(libs.plugins.android.library) apply false

    // Plugin Kotlin para projetos Android.
    alias(libs.plugins.kotlin.android) apply false

    // Plugin utilizado pelo Jetpack Compose.
    alias(libs.plugins.kotlin.compose) apply false

    // Plugin para serialização Kotlin.
    alias(libs.plugins.kotlin.serialization) apply false

    // Plugin KSP, necessário para gerar o código do Room.
    alias(libs.plugins.ksp) apply false
}
