plugins {
    alias(libs.plugins.legalease.android.feature)
}

android {
    namespace = "com.hcdc.legalease.feature.scan"
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.coil.compose)
    implementation(libs.pdf.viewer)
}
