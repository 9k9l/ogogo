plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose)
}

android {
    namespace = "com.looker.droidify"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.looker.droidify"
        minSdk = 23
        versionName = "0.7.8"
        versionCode = 780
    }

    androidResources.generateLocaleConfig = true

    buildTypes {
        release {
            // Left off on purpose: the Google Play library uses protobuf + reflection,
            // which needs custom keep rules before R8 can be switched on safely.
            isMinifyEnabled = false
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = ".d"
        }
    }

    packaging {
        resources {
            excludes += listOf(
                "/DebugProbesKt.bin",
                "/META-INF/{AL2.0,LGPL2.1,LICENSE*}",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

kotlin {
    compilerOptions {
        optIn.add("kotlin.RequiresOptIn")
    }
}

dependencies {
    // Google Play client (anonymous login, search, download links)
    implementation(libs.gplayapi)

    // Kept because the existing res/ folder styles use Material / AppCompat themes
    implementation(libs.material)
    implementation(libs.appcompat)
    implementation(libs.core.ktx)
    implementation(libs.activity)

    implementation(libs.kotlin.stdlib)
    implementation(libs.bundles.coroutines)
    implementation(libs.okhttp)
    implementation(libs.lifecycle.viewModel)

    // Images
    implementation(libs.bundles.coil)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.activity)
    implementation(libs.compose.lifecycle.viewmodel)
}
