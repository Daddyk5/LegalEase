package com.hcdc.legalease.buildlogic

import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Applies detekt with the shared config and a per-module baseline for pre-existing findings. */
internal fun Project.configureDetekt() {
    pluginManager.apply("io.gitlab.arturbosch.detekt")
    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        baseline = file("detekt-baseline.xml")
    }
    // detekt 1.23.x embeds the Kotlin 2.0 compiler; keep its classpath off the project's Kotlin version.
    val detektKotlin = libs.findVersion("detektKotlin").get().requiredVersion
    configurations.matching { it.name == "detekt" }.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") useVersion(detektKotlin)
        }
    }
}
