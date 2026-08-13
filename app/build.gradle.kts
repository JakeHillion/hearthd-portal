import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.hearthd.android.portal"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.hearthd.android.portal"
        // Portal (2nd gen) ships on Android 9 (API 28).
        minSdk = 28
        targetSdk = 35
        // Stamped by CI from the flake's git revCount (see flake.nix). Local or
        // dirty builds fall back to these dev placeholders.
        versionCode = (project.findProperty("portal.versionCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("portal.versionName") as String?) ?: "0.1.0"

        // ONNX Runtime ships prebuilt native libraries for every ABI. The
        // Portal is arm64, so keep only that one and spare the APK the rest.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    // The wake-word .onnx models are read straight from assets by ONNX Runtime;
    // leaving them uncompressed keeps that a plain mmap-friendly read.
    androidResources {
        noCompress += "onnx"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // Exposes VERSION_NAME / VERSION_CODE to the app (shown on screen).
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    // On-device wake-word inference. The AAR bundles the native runtime, so no
    // NDK is needed at build time (see flake.nix).
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.29.0")

    // WebSocket + HTTP client for the Home Assistant voice pipeline (Alpha).
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
