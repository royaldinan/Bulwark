package id.bulwark.files

import android.os.Environment
import android.os.FileObserver
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

class ApkWatcher(
    private val directories: List<File>,
    private val onApkFinished: (File) -> Unit,
) {

    private val observers = CopyOnWriteArrayList<FileObserver>()

    @Volatile
    private var running = false

    fun start() {
        if (running) return
        running = true
        directories.filter { it.isDirectory }.forEach { directory ->
            val observer = object : FileObserver(directory.absolutePath, MASK) {
                override fun onEvent(event: Int, path: String?) {
                    val name = path ?: return
                    if (!name.endsWith(APK_EXTENSION, ignoreCase = true)) return
                    val file = File(directory, name)
                    if (file.isFile) onApkFinished(file)
                }
            }
            observer.startWatching()
            observers += observer
        }
    }

    fun stop() {
        running = false
        observers.forEach { it.stopWatching() }
        observers.clear()
    }

    companion object {

        const val APK_EXTENSION: String = ".apk"

        private val MASK: Int = FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO

        // VERIFY: folder WhatsApp di Android 11+ hanya bisa dibaca dengan izin All files access
        // (V2 di PRD) atau lewat SAF. Path di bawah adalah kandidat, bukan jaminan bisa diakses.
        fun defaultDirectories(): List<File> {
            val root = Environment.getExternalStorageDirectory()
            val candidates = listOf(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                File(root, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents"),
                File(root, "WhatsApp/Media/WhatsApp Documents"),
            )
            return candidates.filter { it.isDirectory }
        }
    }
}
