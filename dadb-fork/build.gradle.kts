plugins {
    kotlin("jvm")
}

// Fork dadb 2.0.0 (Apache 2.0) upravený pro adbd z Androidu 4.1, viz NOTICE-RINCEWINDER.md.
// Čisté JVM: používá se v :app (Android) i v testech na PC.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api("com.squareup.okio:okio:2.10.0")
    testImplementation(kotlin("test"))
}

// Ruční čtecí kontrola proti skutečnému tělu: .\gradlew.bat :dadb-fork:liveBody "-Phost=192.168.1.105"
// (v PowerShellu uvozovky nutné, jinak se argument rozpadne na tečkách)
tasks.register<JavaExec>("liveBody") {
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dadb.LiveBody")
    args(providers.gradleProperty("host").orElse("192.168.1.105").get())
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
    }
}
