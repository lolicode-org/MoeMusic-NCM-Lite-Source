pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "moemusic-ncmlite-source"

val apiBuildDir = file("../NCM-API-Lite-Kt")
if (apiBuildDir.resolve("settings.gradle.kts").isFile) {
    includeBuild(apiBuildDir) {
        dependencySubstitution {
            substitute(module("org.lolicode:neteasemusicapilitekt"))
                .using(project(":"))
        }
    }
}
