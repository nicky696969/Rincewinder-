plugins {
    kotlin("jvm") version "2.2.20" apply false
    // AGP 9 má vestavěný Kotlin (plugin kotlin-android se neaplikuje); Compose compiler = verze Kotlinu.
    id("com.android.application") version "9.0.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
}
