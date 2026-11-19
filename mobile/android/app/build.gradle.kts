plugins {
    id("com.android.application")
    id("kotlin-android")
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.smartiq.app"
    compileSdk = 34

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    defaultConfig {
        applicationId = "com.smartiq.app"
        // Bluetooth LE scanning and the foreground service both need API 23+.
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            // No keystore is configured yet: this produces an installable test
            // build signed with the debug key, never a store-ready artifact.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

flutter {
    // The Flutter project root (mobile/), one level above android/.
    source = "../.."
}
