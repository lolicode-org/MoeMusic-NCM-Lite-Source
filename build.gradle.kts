import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.4.10"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.10"
    id("com.gradleup.shadow") version "9.6.1"
    id("idea")
}

version = providers.gradleProperty("version").get()
group = providers.gradleProperty("maven_group").get()

repositories {
    mavenLocal()
    mavenCentral()
    maven {
        name = "Lolicode Releases"
        url = uri("https://maven.lolicode.org/releases")
        content {
            includeGroupByRegex("org\\.lolicode.*")
        }
    }
    maven {
        name = "Lolicode Snapshots"
        url = uri("https://maven.lolicode.org/snapshots")
        content {
            includeGroupByRegex("org\\.lolicode.*")
        }
    }
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/lolicode-org/MoeMusic")
        credentials {
            username = providers.gradleProperty("gpr.user")
                .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                .orElse("")
                .get()
            password = providers.gradleProperty("gpr.key")
                .orElse(providers.environmentVariable("GITHUB_PACKAGES_TOKEN"))
                .orElse(providers.environmentVariable("PACKAGES_READ_TOKEN"))
                .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                .orElse("")
                .get()
        }
        content {
            includeGroupByRegex("org\\.lolicode.*")
        }
    }
    maven {
        name = "GitHubPackages NCM API"
        url = uri("https://maven.pkg.github.com/lolicode-org/NCM-API-Lite-Kt")
        credentials {
            username = providers.gradleProperty("gpr.user")
                .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                .orElse("")
                .get()
            password = providers.gradleProperty("gpr.key")
                .orElse(providers.environmentVariable("GITHUB_PACKAGES_TOKEN"))
                .orElse(providers.environmentVariable("PACKAGES_READ_TOKEN"))
                .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                .orElse("")
                .get()
        }
    }
}

dependencies {
    // MoeMusic API transitively provides the guaranteed runtime baseline:
    // Kotlin stdlib, kotlinx-coroutines, kotlinx-serialization (core + json), and SLF4J API.
    compileOnly("org.lolicode.moemusic:api:${providers.gradleProperty("plugin_api_version").get()}")

    // Netease Cloud Music API wrapper
    implementation("org.lolicode:neteasemusicapilitekt:${providers.gradleProperty("ncm_api_version").get()}")

    testImplementation(kotlin("test"))
    testImplementation("org.lolicode.moemusic:api:${providers.gradleProperty("plugin_api_version").get()}")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        apiVersion = KotlinVersion.KOTLIN_2_2
        languageVersion = KotlinVersion.KOTLIN_2_2
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
}

idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}

tasks.jar {
    inputs.property("projectName", project.name)

    from("LICENSE") {
        rename { "${it}_${project.name}" }
    }
}

tasks.shadowJar {
    archiveClassifier.set("full")

    dependencies {
        include(dependency("org.lolicode:neteasemusicapilitekt:${providers.gradleProperty("ncm_api_version").get()}"))
    }
}
