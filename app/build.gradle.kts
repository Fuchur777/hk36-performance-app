import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    // Needed for the user-data export/import DTOs in data/export/. The JSON library itself
    // already comes in transitively via :core's api(...) dependency, but the compiler plugin
    // that generates serializers only applies to the module it's declared in.
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

/**
 * Release signing is opt-in. `keystore.properties` and the .jks it points at are deliberately
 * untracked (see .gitignore): the signing key must never live in the repository, and losing it
 * means never being able to update an app already published under it.
 *
 * When the file is absent — a fresh clone, another machine, CI — the build still configures and
 * every debug/test task works exactly as before; only `assembleRelease` degrades, producing an
 * *unsigned* APK. That is deliberate: a missing key should be visible in the output filename
 * rather than failing every unrelated Gradle invocation.
 */
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use { load(it) }
}
val hasReleaseSigning = keystoreProperties.getProperty("storeFile") != null

android {
    namespace = "nl.schellenberg.hk36ttc"
    compileSdk = 37

    defaultConfig {
        applicationId = "nl.schellenberg.hk36ttc"
        minSdk = 26
        targetSdk = 37
        versionCode = 23
        versionName = "0.9.5"

        // Generated fresh at build configuration time - shown alongside versionName/versionCode
        // in the About screen so a specific build can still be pinned down within a day even
        // though versionCode/versionName are now bumped by hand on every commit (see
        // docs/00-plan.md).
        buildConfigField(
            "String",
            "BUILD_DATE",
            "\"${LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)}\""
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Only when the key is actually present; otherwise this stays unsigned and the
            // output is named app-release-unsigned.apk, which is hard to mistake for shippable.
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // No native code of our own, but a dependency ships a small .so — this packages its
            // debug symbols into the bundle so Play Console can symbolicate any native crash,
            // regardless of which dependency the library came from.
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets {
        // Lets MigrationTest (androidTest) load the tracked schema exports to build historic
        // versions of the database via MigrationTestHelper.
        getByName("androidTest") {
            // `directories` rather than the deprecated `srcDirs(...)` (AGP 9).
            assets.directories.add("$projectDir/schemas")
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))

    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")

    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.room:room-testing:2.8.4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
