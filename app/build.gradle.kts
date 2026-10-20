plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.callpilot"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.callpilot"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0-assistant-preview"
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
    // Local wake-word detection. The included model currently detects “Hey Jarvis”.
    implementation("com.github.msnilsen:openwakeword-android:0.1.0")
}
