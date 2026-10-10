plugins {
    alias(libs.plugins.legalease.android.feature)
}

android {
    namespace = "com.hcdc.legalease.feature.history"
}

dependencies {
    implementation(libs.androidx.paging.compose)
    testImplementation(libs.androidx.paging.testing)
}
