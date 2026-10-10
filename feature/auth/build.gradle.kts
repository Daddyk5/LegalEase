plugins {
    alias(libs.plugins.legalease.android.feature)
}

android {
    namespace = "com.hcdc.legalease.feature.auth"
}

dependencies {
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
}
