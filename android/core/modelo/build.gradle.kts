import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Modelos e regras puras do atendimento. É Kotlin/JVM puro (sem plugin Android) para poder ser
// usado pelo :feature:atendimento e pelo módulo de dados sem puxar nada do Android.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Java 17 no código Java e no Kotlin, igual aos demais módulos.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Testes unitários (rodam na JVM).
    testImplementation(libs.junit)
}
