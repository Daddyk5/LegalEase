plugins {
    alias(libs.plugins.legalease.android.feature)
}

android {
    namespace = "com.hcdc.legalease.feature.result"
}

dependencies {
    implementation(libs.coil.compose)
}
