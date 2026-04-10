plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.geminieraser.app"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.geminieraser.app"
        minSdk = 24
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
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)


    // Coil for premium image loading in Compose
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Coroutines for off-thread processing
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // Foundation pager for onboarding swipe screens
    implementation("androidx.compose.foundation:foundation:1.7.8")

    
    // OkHttp for interacting with Python backend
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // MediaPipe Interactive Segmenter — free, on-device AI segmentation (no API key needed)
    // implementation("com.google.mediapipe:tasks-vision:0.10.14") (Removed because FastSAM in the cloud replaces it entirely, fixing 16KB alignment)

    // Google Mobile Ads
    implementation("com.google.android.gms:play-services-ads:23.0.0")

    // Google Play Billing
    implementation("com.android.billingclient:billing-ktx:6.2.0")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}