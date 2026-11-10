import net.fabricmc.loom.api.LoomGradleExtensionAPI

// Minecraft is obfuscated before 26.1, so those nodes need the remapping Loom and Mojang's names. The plain Loom from
// 26.1 on has no `mod*` configurations; here they become the ordinary ones, so one build script serves every node.
val unobfuscated = project.name.substringBefore('.').toInt() >= 26
apply(plugin = if (unobfuscated) "net.fabricmc.fabric-loom" else "net.fabricmc.fabric-loom-remap")
if (unobfuscated) {
    for ((alias, target) in mapOf("modImplementation" to "implementation", "modCompileOnly" to "compileOnly")) {
        val aliased = configurations.create(alias)
        configurations.named(target) { extendsFrom(aliased) }
    }
} else {
    dependencies.add("mappings", the<LoomGradleExtensionAPI>().officialMojangMappings())
}
