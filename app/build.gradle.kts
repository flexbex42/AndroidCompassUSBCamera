plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.camera6"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.camera6"
        minSdk = 27
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // AUSBC native AAR (keep whichever you use)
    // implementation(files("libs/libausbc-debug.aar"))
    implementation(files("libs/libausbc-release.aar"))

    // Core AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)

    // Layouts
    implementation(libs.androidx.constraintlayout)

    // Material Components (from version catalog)
    implementation(libs.material)                    // com.google.android.material:material

    // Compose / UI libs (you have Compose in the project; keep as needed)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.graphics) // duplicated intentionally removed? (kept for clarity)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.material3)

    // Navigation
    implementation(libs.androidx.navigation.runtime.ktx)
    implementation(libs.androidx.navigation.compose)

    // Material3 Android (alias from your toml)
    implementation(libs.androidx.material3.android)

    // Logging
    implementation(libs.timber)

    implementation(libs.materialDialogsCore)
    //implementation(libs.materialDialogsList)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}