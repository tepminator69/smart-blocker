plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.smartblocker.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.smartblocker.app"
        minSdk = 26          // Android 8.0 and newer
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    // One fixed key, so a new build installs over the old one without uninstalling.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/smartblocker-debug.jks")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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

// No extra libraries: only Android itself and Kotlin.
