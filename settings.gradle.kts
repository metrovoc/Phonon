pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "FabricMC" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.10"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// Override version from VERSION env var (strips 'v' prefix from git tags like v0.1.0)
System.getenv("VERSION")?.let { env -> gradle.beforeProject { version = env.removePrefix("v") } }

stonecutter {
    create(rootProject) {
        // One node per range of game releases that share an API; stonecutter.properties.toml lists the releases.
        fun match(project: String, vararg loaders: String) {
            for (loader in loaders) version("$project-$loader", project).buildscript("build.$loader.gradle.kts")
        }
        match("1.21.1", "neoforge", "fabric")
        match("1.21.3", "neoforge", "fabric")
        match("1.21.4", "neoforge", "fabric")
        match("1.21.5", "neoforge", "fabric")
        match("1.21.6", "neoforge", "fabric")
        match("1.21.8", "neoforge", "fabric")
        match("1.21.9", "neoforge")
        match("1.21.10", "neoforge", "fabric")
        match("1.21.11", "neoforge", "fabric")
        match("26.1.2", "neoforge", "fabric")
        match("26.2", "neoforge", "fabric")
        match("26.3", "neoforge", "fabric")
        vcsVersion = "26.3-neoforge"
    }
}

rootProject.name = "Phonon"
