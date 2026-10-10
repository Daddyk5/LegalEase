plugins {
    alias(libs.plugins.legalease.android.application)
    alias(libs.plugins.legalease.android.compose)
    alias(libs.plugins.legalease.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "com.hcdc.legalease"

    defaultConfig {
        applicationId = "com.hcdc.legalease"
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    // The on-device language model ships outside the base APK.
    assetPacks += ":llm_pack"

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:ml"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:scan"))
    implementation(project(":feature:result"))
    implementation(project(":feature:history"))
    implementation(project(":feature:generator"))

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
}
