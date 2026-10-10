plugins {
    alias(libs.plugins.legalease.android.feature)
}

android {
    namespace = "com.hcdc.legalease.feature.generator"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
}
