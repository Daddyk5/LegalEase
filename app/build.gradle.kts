plugins {
    alias(libs.plugins.legalease.android.application)
    alias(libs.plugins.legalease.android.compose)
    alias(libs.plugins.legalease.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.play.publisher)
}

android {
    namespace = "com.hcdc.legalease"

    defaultConfig {
        applicationId = "com.hcdc.legalease"
        // CI passes the build number; local builds stay at 1.
        versionCode = providers.environmentVariable("VERSION_CODE").orNull?.toInt() ?: 1
        versionName = providers.environmentVariable("VERSION_NAME").orNull ?: "1.0"
    }

    // Release signing comes only from environment variables (GitHub Secrets in CI), never from git.
    val releaseKeystore = providers.environmentVariable("LEGALEASE_KEYSTORE_PATH").orNull
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("LEGALEASE_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("LEGALEASE_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("LEGALEASE_KEY_PASSWORD").get()
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    // The on-device language model ships outside the base APK.
    assetPacks += ":llm_pack"

    buildTypes {
        release {
            // Unsigned when the variables are absent, so local release builds still work.
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

// Gradle Play Publisher: uploads the release bundle to the internal testing track. Used only by the
// release workflow, and only when a Play service-account key is provided (SETUP.md §10d).
val playCredentials = providers.environmentVariable("PLAY_SERVICE_ACCOUNT_JSON_PATH").orNull
play {
    enabled.set(playCredentials != null)
    playCredentials?.let { serviceAccountCredentials.set(file(it)) }
    track.set("internal")
    defaultToAppBundles.set(true)
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
    implementation(project(":feature:settings"))
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)
    debugImplementation(libs.leakcanary)

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
    androidTestImplementation(testFixtures(project(":core:domain")))
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
}
