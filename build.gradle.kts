import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.3.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.21"
    id("com.gradleup.shadow") version "9.4.1"
    id("idea")
}

version = providers.gradleProperty("version").get()
group = providers.gradleProperty("maven_group").get()

repositories {
    mavenLocal()
    mavenCentral()
    maven {
        name = "Lolicode on Codeberg"
        url = uri("https://codeberg.org/api/packages/lolicode/maven")
        content {
            includeGroupByRegex("org\\.lolicode.*")
        }
    }
    maven {
        name = "GitHubPackages NCM API"
        url = uri("https://maven.pkg.github.com/lolicode-org/NCM-API-Lite-Kt")
        credentials {
            username = System.getenv("GITHUB_ACTOR").orEmpty()
            password = System.getenv("GITHUB_TOKEN").orEmpty()
        }
    }
    maven {
        name = "GitHubPackages MoeMusic"
        url = uri("https://maven.pkg.github.com/lolicode-org/MoeMusic")
        credentials {
            username = System.getenv("GITHUB_ACTOR").orEmpty()
            password = System.getenv("GITHUB_TOKEN").orEmpty()
        }
    }
}

dependencies {
    // MoeMusic API (resolved via composite build from parent :api project)
    compileOnly("org.lolicode.moemusic:api:${providers.gradleProperty("plugin_api_version").get()}")

    // kotlinx-serialization — runtime provided transitively by neteasemusicapilitekt; needed at compile time for @Serializable
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-core:${providers.gradleProperty("kotlinx_serialization_version").get()}")

    // Netease Cloud Music API wrapper
    implementation("org.lolicode:neteasemusicapilitekt:${providers.gradleProperty("ncm_api_version").get()}")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${providers.gradleProperty("coroutines_version").get()}")

    // SLF4J — runtime provided by Minecraft/the loader on platform modules
    compileOnly("org.slf4j:slf4j-api:${providers.gradleProperty("slf4j_version").get()}")

    testImplementation(kotlin("test"))
    testImplementation("org.lolicode.moemusic:api:${providers.gradleProperty("plugin_api_version").get()}")
    testImplementation("org.slf4j:slf4j-api:${providers.gradleProperty("slf4j_version").get()}")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
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
