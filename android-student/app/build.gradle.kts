plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.smartattendance.student"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.smartattendance.student"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
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
            // Allow Chrome DevTools remote debugging of the WebView in debug builds only.
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
}

dependencies {
    // No AndroidX / third-party dependencies: the shell uses only the Android
    // framework (WebView, BluetoothLeScanner, NotificationManager, MediaStore).
    // This keeps the APK small and the build reproducible.
}
