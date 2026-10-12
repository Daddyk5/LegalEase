plugins {
    alias(libs.plugins.legalease.jvm.library)
    `java-test-fixtures`
    alias(libs.plugins.kover)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.javax.inject)
    api(libs.androidx.paging.common)

    // Fakes and test helpers shared with feature modules: testImplementation(testFixtures(project(":core:domain")))
    testFixturesImplementation(libs.kotlinx.coroutines.test)
    testFixturesImplementation(libs.junit)
}

// Coverage gate (Phase 7): `./gradlew :core:domain:koverVerify`.
kover {
    reports {
        verify {
            rule { minBound(70) }
        }
    }
}
