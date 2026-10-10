plugins {
    alias(libs.plugins.legalease.android.library)
    alias(libs.plugins.legalease.hilt)
}

android {
    namespace = "com.hcdc.legalease.core.ml"
}

dependencies {
    implementation(project(":core:domain"))

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.ml.modeldownloader)
    implementation(libs.litert)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)
}
