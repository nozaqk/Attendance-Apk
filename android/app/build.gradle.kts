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

android {
    namespace = "com.smartattendance.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.smartattendance.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        // Single source of truth for the backend host in the native app.
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
