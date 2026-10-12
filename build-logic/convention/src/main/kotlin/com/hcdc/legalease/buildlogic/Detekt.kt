package com.hcdc.legalease.buildlogic

import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Applies detekt (with its ktlint formatting rules), the shared config, and a per-module baseline. */
internal fun Project.configureDetekt() {
    pluginManager.apply("io.gitlab.arturbosch.detekt")
    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        baseline = file("detekt-baseline.xml")
    }
    dependencies { add("detektPlugins", libs.findLibrary("detekt-formatting").get()) }
    // detekt 1.23.x embeds the Kotlin 2.0 compiler; keep its classpath off the project's Kotlin version.
    val detektKotlin = libs.findVersion("detektKotlin").get().requiredVersion
    configurations.matching { it.name == "detekt" }.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") useVersion(detektKotlin)
        }
    }
}
