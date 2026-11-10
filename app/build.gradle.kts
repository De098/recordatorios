plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.de098.recordatorios"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.de098.recordatorios"
        minSdk = 29
        targetSdk = 36
        versionCode = 6
        versionName = "0.3.3"
    }

    // F-Droid firma con su propia clave: aquí no hay firma ni nada personal
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    // sin bloques de metadatos de dependencias en el APK (compilación reproducible)
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
