// Explicit imports: in a Gradle Kotlin script `java` already resolves to the Java plugin
// extension, so `java.util.Properties` / `java.io.File` would not compile.
import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// API base URL (public, ships in the APK — never a secret here).
// Override with: ./gradlew assembleDebug -PAPI_BASE_URL=https://yourdomain.com
// or the API_BASE_URL environment variable (used by CI and scripts/build-apk.sh).
val defaultApiBaseUrl = "https://attendance-system-33pl.onrender.com"
val apiBaseUrl: String = (
    (findProperty("API_BASE_URL") as String?)
        ?: System.getenv("API_BASE_URL")
        ?: defaultApiBaseUrl
    ).trim().trimEnd('/')

// ---- Release signing ---------------------------------------------------------
// Signing material is optional while the release build is being tested: without it the
// release variant falls back to the debug key, so `assembleRelease` still produces an
// installable APK. Supply KEYSTORE_FILE / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD
// (environment variables for CI, or android/local.properties for local builds - both
// git-ignored) to produce a properly signed release. A debug-signed APK must not be
// distributed.
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

fun signingValue(name: String): String? =
    (System.getenv(name) ?: localProperties.getProperty(name))?.trim()?.takeIf { it.isNotEmpty() }

val keystoreFile = signingValue("KEYSTORE_FILE")
val keystorePassword = signingValue("KEYSTORE_PASSWORD")
val keystoreAlias = signingValue("KEY_ALIAS")
val keystoreKeyPassword = signingValue("KEY_PASSWORD") ?: keystorePassword
// Relative paths in KEYSTORE_FILE are resolved against android/ (the Gradle root project).
val keystoreAbsolutePath = keystoreFile?.let { rootProject.file(it).absolutePath }
val hasReleaseKeystore = keystoreAbsolutePath != null && keystorePassword != null && keystoreAlias != null

// Version can be raised from the command line: -PversionCode=2 -PversionName=1.1.0
val appVersionCode = (findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
val appVersionName = (findProperty("versionName") as String?) ?: "1.0.0"

android {
    namespace = "com.smartattendance.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.smartattendance.app"
        minSdk = 26
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName

        // Single source of truth for the backend host in the native app.
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = File(keystoreAbsolutePath!!)
                storePassword = keystorePassword
                keyAlias = keystoreAlias
                keyPassword = keystoreKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Real keystore when configured, otherwise the debug key so a test release APK
            // can be installed. Replace before publishing anything to end users.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            if (!hasReleaseKeystore) {
                logger.lifecycle(
                    "Release build: no key-stores configured - signing with the DEBUG key " +
                        "(test installs only). Set KEYSTORE_FILE / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD for a distributable APK."
                )
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
