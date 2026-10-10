import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.hcdc.legalease.buildlogic.lib
import com.hcdc.legalease.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Enables Compose on an Android application or library module. Apply after the Android plugin. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        extensions.findByType(ApplicationExtension::class.java)?.buildFeatures?.compose = true
        extensions.findByType(LibraryExtension::class.java)?.buildFeatures?.compose = true
        dependencies {
            val bom = platform(libs.lib("androidx-compose-bom"))
            add("implementation", bom)
            add("androidTestImplementation", bom)
            add("implementation", libs.lib("androidx-ui"))
            add("implementation", libs.lib("androidx-ui-graphics"))
            add("implementation", libs.lib("androidx-ui-tooling-preview"))
            add("implementation", libs.lib("androidx-material3"))
            add("debugImplementation", libs.lib("androidx-ui-tooling"))
        }
    }
}
