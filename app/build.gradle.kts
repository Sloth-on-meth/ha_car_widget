plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.sloth.carwidget"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sloth.carwidget"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "1.9"
    }

    signingConfigs {
        // Fixed key committed to the repo so every CI build can install over the previous one.
        create("shared") {
            storeFile = rootProject.file("keystore/widget.keystore")
            storePassword = "android"
            keyAlias = "widget"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
