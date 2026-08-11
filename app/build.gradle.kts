plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.hearthd.android.portal"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.hearthd.android.portal"
        // Portal (2nd gen) ships on Android 9 (API 28).
        minSdk = 28
        targetSdk = 35
        // Stamped by CI from the flake's git revCount (see flake.nix). Local or
        // dirty builds fall back to these dev placeholders.
        versionCode = (project.findProperty("portal.versionCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("portal.versionName") as String?) ?: "0.1.0"
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // Exposes VERSION_NAME / VERSION_CODE to the app (shown on screen).
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.01.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
