plugins {
    id("net.neoforged.moddev") version "2.0.148"
    id("neoforge-mutex")
}

val mc = sc.current.version
val modVersion = (project.version.takeIf { it != Project.DEFAULT_VERSION } ?: sc.properties["mod.version"]).toString()
version = modVersion
group = sc.properties["mod.group"]
base.archivesName = "phonon-neoforge-$mc"

val requiredJava = if (sc.current.parsed >= "26.1") JavaVersion.VERSION_25 else JavaVersion.VERSION_21

repositories {
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
        filter { includeGroup("maven.modrinth") }
    }
}

// The driver: a development-only source set that plays scripts in the client. Never packaged.
//     ./gradlew :26.2-neoforge:runDriver -Pscript=../../driver/smoke.txt [-Pmonitor="Display name"]
// Screenshots and clips land in versions/<node>/run/driver/screenshots, the log in run/driver/logs/latest.log.
val driver: SourceSet = sourceSets.create("driver")

// Both loaders share src/; each build leaves out the other's package.
sourceSets.main { java.exclude("com/metrovoc/phonon/fabric/**") }
driver.java.exclude("driver/FabricHook.java")
configurations[driver.implementationConfigurationName].extendsFrom(configurations.implementation.get())
configurations[driver.runtimeOnlyConfigurationName].extendsFrom(configurations.runtimeOnly.get())

dependencies {
    // Both loaders bundle Fabric Mixin and MixinExtras, so shared code can use them.
    compileOnly("net.fabricmc:sponge-mixin:0.17.3+mixin.0.8.7")
    compileOnly(annotationProcessor("io.github.llamalad7:mixinextras-common:0.5.3")!!)
    "driverImplementation"(sourceSets.main.get().output)

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.google.code.gson:gson:2.11.0")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.16")
}

repositories {
    exclusiveContent {
        forRepository { maven("https://maven.fabricmc.net") { name = "Fabric" } }
        filter { includeGroupAndSubgroups("net.fabricmc") }
    }
}

val phononRunDir = providers.gradleProperty("phononRunDir").orElse("run")

neoForge {
    version = sc.properties["deps.neoforge"]
    addModdingDependenciesTo(driver)

    mods {
        register("phonon") {
            sourceSet(sourceSets.main.get())
            // The driver's classes join the mod, so its event subscriber registers as part of it.
            sourceSet(driver)
        }
    }

    runs {
        configureEach {
            gameDirectory = file(phononRunDir.get())
        }
        register("client") { client() }
        register("server") { server() }
        register("driver") {
            client()
            sourceSet = driver
            gameDirectory = file(providers.gradleProperty("gdir").orElse("run/driver").get())
            systemProperty("driver.script", providers.gradleProperty("script").map { file(it).absolutePath }.orElse("").get())
            systemProperty("driver.monitor", providers.gradleProperty("monitor").orElse("").get())
            programArguments.addAll("--username", providers.gradleProperty("uname").orElse("Dev").get(), "--width", "1600", "--height", "1000")
        }
    }
}

java {
    withSourcesJar()
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava
    toolchain.languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
}

tasks {
    test { useJUnitPlatform() }

    processResources {
        applyOverlays(rootProject.file("overlays")) { sc.current.parsed < it }
        val props = mapOf(
            "version" to modVersion,
            "mod_id" to sc.properties["mod.id"],
            "mod_name" to sc.properties["mod.name"],
            "mod_author" to sc.properties["mod.author"],
            "license" to sc.properties["mod.license"],
            "description" to sc.properties["mod.description"],
            "minecraft_version_range" to sc.properties["mod.mc_compat"],
            "neoforge_min" to (sc.properties.getOrNull("deps.neoforge_min") ?: sc.properties["deps.neoforge"]),
            "neoforge_loader_version_range" to "[1,)",
            "credits" to "",
        )
        inputs.properties(props)
        filesMatching("META-INF/neoforge.mods.toml") { expand(props) }
        exclude("fabric.mod.json", "phonon.fabric.mixins.json")
    }

    named("processDriverResources", ProcessResources::class) { exclude("fabric.mod.json") }

    named("createMinecraftArtifacts") { dependsOn("stonecutterGenerate") }

    withType<Jar> {
        from(rootProject.file("LICENSE")) { rename { "${it}_Phonon" } }
        manifest.attributes(
            "Specification-Title" to "Phonon",
            "Specification-Vendor" to "metrovoc",
            "Specification-Version" to modVersion,
            "Implementation-Title" to project.name,
            "Implementation-Version" to modVersion,
            "Implementation-Vendor" to "metrovoc",
            "Built-On-Minecraft" to mc,
        )
    }
}
