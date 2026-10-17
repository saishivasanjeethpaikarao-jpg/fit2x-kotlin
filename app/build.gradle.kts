plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Google Services (Firebase) is only applied when google-services.json exists.
// CI builds without it get a graceful fallback: AuthRepository shows
// "Google sign-in isn't configured yet" instead of crashing the build.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.fit2x.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fit2x.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "1.0.5-diagnostic"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
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
    // AndroidX core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose (versions via BOM)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.material.icons.extended)

    // Navigation + Lifecycle
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // Room (compiler via KSP)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Firebase (versions via BOM)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    // Health Connect — native step sync
    implementation(libs.health.connect.client)

    // Background reminders + local settings
    implementation(libs.workmanager)
    implementation(libs.datastore.preferences)

    // Image loading
    implementation(libs.coil.compose)

    // Barcode decoding (diet tracker)
    implementation(libs.zxing.core)
    // Barcode camera scanner (diet tracker)
    implementation(libs.zxing.android.embedded)

    // Native Google Sign-In via Credential Manager
    implementation(libs.credentials)
    // Classic Google Sign-In (stable API)
    implementation("com.google.android.gms:play-services-auth:21.0.0")
}
