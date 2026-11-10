plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven("https://maven.fabricmc.net/") { name = "FabricMC" }
}

dependencies {
    // Both Loom variants: net.fabricmc.fabric-loom-remap and net.fabricmc.fabric-loom
    implementation("net.fabricmc:fabric-loom:1.19.4")
}
