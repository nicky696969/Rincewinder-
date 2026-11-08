plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Pixel appka (docs/ZADANI_F1.md §5). minSdk 34 = Pixel 9 Pro; targetSdk 37 kvůli ACCESS_LOCAL_NETWORK.
android {
    namespace = "com.nicky.rincewinder"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.nicky.rincewinder"
        minSdk = 34
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0-m4"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// APK Agenta se přibaluje z dist/ (zamčeno Nickym 31. 10.: „Nainstalovat Agent“ z Pixelu bez PC).
// Kopíruje se do statické složky assets (soubor je v .gitignore); AGP 9 nedovolí Provider v sourceSets.
val copyAgentApk by tasks.registering(Copy::class) {
    val apk = rootProject.file("dist/RincewinderAgent_v0.2.0.apk")
    onlyIf { apk.isFile }
    from(apk)
    rename { "agent.apk" }
    into(file("src/main/assets"))
}
tasks.named("preBuild") { dependsOn(copyAgentApk) }

dependencies {
    implementation(project(":core"))
    implementation(project(":dadb-fork"))

    val composeBom = platform("androidx.compose:compose-bom:2025.09.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.3")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    // QR z displeje Agenta: skener z Play services (bez CameraX a bez oprávnění ke kameře v appce)
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
}
