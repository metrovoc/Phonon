plugins {
    // Applies the remapping Loom before Minecraft 26.1 and the plain one from 26.1 on.
    id("fabric-loom-select")
}

val mc = sc.current.version
val modVersion = (project.version.takeIf { it != Project.DEFAULT_VERSION } ?: sc.properties["mod.version"]).toString()
version = modVersion
group = sc.properties["mod.group"]
base.archivesName = "phonon-fabric-$mc"

val requiredJava = if (sc.current.parsed >= "26.1") JavaVersion.VERSION_25 else JavaVersion.VERSION_21

repositories {
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
        filter { includeGroup("maven.modrinth") }
    }
}

// The driver: a development-only mod that plays scripts in the client. Never packaged.
//     ./gradlew :26.2-fabric:runDriver -Pscript=../../driver/smoke.txt [-Pmonitor="Display name"]
val driver: SourceSet = sourceSets.create("driver") {
    compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
}

// Both loaders share src/; each build leaves out the other's package.
sourceSets.main { java.exclude("com/metrovoc/phonon/neoforge/**") }
driver.java.exclude("driver/NeoForgeHook.java")

dependencies {
    minecraft("com.mojang:minecraft:$mc")
    // `mod*` configurations work on every version; fabric-loom-select converts them from 26.1 on.
    modImplementation("net.fabricmc:fabric-loader:${sc.properties["deps.fabric_loader"]}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${sc.properties["deps.fabric_api"]}")
    modCompileOnly("maven.modrinth:modmenu:${sc.properties["deps.modmenu"]}")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.google.code.gson:gson:2.11.0")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.16")
}

val phononRunDir = providers.gradleProperty("phononRunDir").orElse("run")

loom {
    mixin.useLegacyMixinAp = false
    mods {
        register("driver") { sourceSet(driver) }
    }
    runs {
        named("client") { runDir(phononRunDir.get()) }
        named("server") { runDir(phononRunDir.get()) }
        register("driver") {
            client()
            source(driver)
            runDir(providers.gradleProperty("gdir").orElse("run/driver").get())
            property("driver.script", providers.gradleProperty("script").map { file(it).absolutePath }.getOrElse(""))
            property("driver.monitor", providers.gradleProperty("monitor").getOrElse(""))
            programArgs("--username", providers.gradleProperty("uname").getOrElse("Dev"), "--width", "1600", "--height", "1000")
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
            "minecraft_version" to sc.properties["mod.mc_compat"],
            "fabric_loader_version" to sc.properties["deps.fabric_loader"],
            "fabric_api_min" to (sc.properties.getOrNull("deps.fabric_api_min") ?: sc.properties["deps.fabric_api"]),
            "java_version" to requiredJava.majorVersion,
        )
        inputs.properties(props)
        filesMatching("fabric.mod.json") { expand(props) }
        exclude("META-INF/neoforge.mods.toml")
    }

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
