plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
    kotlin("plugin.serialization") version "1.9.23"
}

android {
    namespace = "com.hcdc.legalease"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hcdc.legalease"
        minSdk = 31
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // inside android { defaultConfig { ... } }
        val geminiKey = project.findProperty("GEMINI_API_KEY") as String? ?: ""
        // Keep empty when missing; never ship real keys.
        resValue("string", "gemini_api_key", geminiKey.ifBlank { "" })
        // BuildConfig → com.hcdc.legalease.BuildConfig.GEMINI_API_KEY
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiKey\"")

        // Resource fallback → R.string.gemini_api_key
        // If you don't want a real key baked into the APK, keep "YOUR_KEY" when blank.
        resValue("string", "gemini_api_key", geminiKey.ifBlank { "AIzaSyCWu_IS86YZDChpK3m9iHDYFDIe9RzFIAo" })
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug { }
    }

    // Use Java 17 for AGP 8+/SDK 35
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        mlModelBinding = true
        buildConfig = true
    }

    androidResources {
        noCompress += "tflite"
    }

    // (Optional) if you need to exclude LiteRT pulled transitively
    // packaging { resources { excludes += "META-INF/**" } } // example; keep if you hit conflicts
}

// Optional: block LiteRT if pulled transitively
configurations.all {
    exclude(group = "com.google.ai.edge.litert")
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:33.2.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-ml-modeldownloader")

    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")

    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.3")

    implementation("com.google.mlkit:text-recognition:16.0.1")

    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.navigation:navigation-compose:2.9.1")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.12")
    implementation("com.squareup.okhttp3:logging-interceptor:5.0.0-alpha.12")
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("io.coil-kt:coil-gif:2.5.0")
    implementation("io.github.afreakyelf:Pdf-Viewer:2.3.7")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
