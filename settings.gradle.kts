pluginManagement {
    repositories {
        google()
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

rootProject.name = "rincewinder"

// :core      — čistý Kotlin/JVM: model stavů souboru, fronta, klient Agenta (testy bez Androidu)
// :testing   — falešný Agent v JVM pro testy fronty a výpadků (M1)
// :app       — Pixel appka (Compose), přibude v M4
// :dadb-fork — ADB klient s patchem pro Android 4.1, přibude v M3
include(":core")
include(":testing")
include(":dadb-fork")
include(":app")
