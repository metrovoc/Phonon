import org.gradle.language.jvm.tasks.ProcessResources
import java.io.File

/**
 * Data and asset files whose format changed between game versions. `src/main/resources` holds the newest format;
 * `overlays/<version>/` holds the files as they were before that version. A node uses, for each file, the overlay
 * with the lowest version above its own, so the closest older format wins.
 *
 * [isBefore] says whether this node's game version is older than an overlay's version.
 */
fun ProcessResources.applyOverlays(overlays: File, isBefore: (String) -> Boolean) {
    val applicable = overlays.listFiles { file -> file.isDirectory && isBefore(file.name) }.orEmpty()
        .sortedWith(compareByDescending<File> { it.name.split('.').map(String::toInt).let { v -> v + List(4 - v.size) { 0 } }.joinToString(".") { "%05d".format(it.toInt()) } })
    val chosen = linkedMapOf<String, File>()
    for (dir in applicable) {
        dir.walk().filter { it.isFile && it.name != ".DS_Store" }.forEach { chosen[it.relativeTo(dir).invariantSeparatorsPath] = it }
    }
    inputs.files(chosen.values).withPropertyName("overlays")
    eachFile {
        if (relativePath.pathString in chosen && file.toPath().normalize().startsWith(overlays.toPath().normalize()).not()) {
            exclude()
        }
    }
    for ((path, file) in chosen) {
        from(file) { into(path.substringBeforeLast('/', "")) }
    }
}
