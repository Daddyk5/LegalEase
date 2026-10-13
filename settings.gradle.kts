pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "LegalEase"

include(":app")
include(":core:designsystem")
include(":core:domain")
include(":core:data")
include(":core:ml")
include(":core:security")
include(":feature:auth")
include(":feature:scan")
include(":feature:result")
include(":feature:history")
include(":feature:generator")
include(":feature:settings")
include(":llm_pack")
include(":baselineprofile")
