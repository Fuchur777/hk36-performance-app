plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

// Fase 4b golden-vector fixtures: real recorded flight logs, too large (500KB-3.5MB each) to
// inline as Kotlin string literals like the AFM-table fixtures. Points at the single tracked
// copy in docs/data/reallife-samples/ (see that directory's README.md) rather than duplicating
// the files into this module — same "point at the one real copy" precedent as app/build.gradle.kts's
// androidTest sourceSet pulling in app/schemas/. NOTE: this couples :core's test build to a path
// outside the module; renaming docs/data/reallife-samples/ breaks tests with a classpath-not-found,
// not a compile error.
sourceSets {
    test {
        resources {
            srcDir(rootProject.projectDir.resolve("docs/data/reallife-samples")).include("*.json")
        }
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = false
    }
}
