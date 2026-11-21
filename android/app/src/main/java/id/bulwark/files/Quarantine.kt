package id.bulwark.files

import android.content.Context
import java.io.File

class Quarantine(private val directory: File) {

    constructor(context: Context) : this(File(context.filesDir, DIRECTORY_NAME))

    fun directory(): File = directory

    fun move(source: File): File? {
        if (!source.isFile) return null
        val target = uniqueTarget(source.name)
        val moved = when {
            source.renameTo(target) -> true
            copy(source, target) -> source.delete()
            else -> false
        }
        return if (moved) target else null
    }

    fun contains(file: File): Boolean = runCatching {
        file.isFile && file.canonicalFile.parentFile == directory.canonicalFile
    }.getOrDefault(false)

    fun list(): List<File> =
        directory.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()

    private fun uniqueTarget(name: String): File {
        ensureDirectory()
        val direct = File(directory, name)
        if (!direct.exists()) return direct

        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val extension = if (dot > 0) name.substring(dot) else ""
        var index = 1
        while (true) {
            val candidate = File(directory, "$stem-$index$extension")
            if (!candidate.exists()) return candidate
            index++
        }
    }

    private fun ensureDirectory() {
        if (!directory.isDirectory) directory.mkdirs()
    }

    private fun copy(source: File, target: File): Boolean = runCatching {
        source.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        true
    }.getOrDefault(false)

    companion object {
        const val DIRECTORY_NAME: String = "quarantine"
    }
}
