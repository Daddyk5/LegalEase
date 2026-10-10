plugins {
    alias(libs.plugins.legalease.android.library)
    alias(libs.plugins.legalease.android.compose)
}

android {
    namespace = "com.hcdc.legalease.core.designsystem"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    api(libs.androidx.material.icons.extended)
}
