pluginManagement {
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "Jordan"

include(
    "jordan-core",
    "jordan-platform",
    "jordan-render",
    "jordan-physics",
    "jordan-audio",
    "jordan-assets",
    "jordan-scripting",
    "jordan-serialization",
    "jordan-ui",
    "jordan-studio",
    "jordan-player",
)
