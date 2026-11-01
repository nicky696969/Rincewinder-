plugins {
    kotlin("jvm")
}

// Falešný Agent (jádro skutečného Agenta z agent/core, čistá Java SDK 16) pro testy :core bez těla.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

sourceSets {
    main {
        java.srcDir("../agent/core")
    }
}
