package id.bulwark.files

import android.os.Environment
import android.os.FileObserver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ApkWatcherTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun defaultDirectoriesKeepMissingWhatsAppCandidates() {
        val root = folder.newFolder("storage")
        val downloads = File(root, Environment.DIRECTORY_DOWNLOADS)
        Environment.setTestDirectories(root, downloads)

        try {
            assertEquals(
                listOf(
                    downloads,
                    File(root, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents"),
                    File(root, "WhatsApp/Media/WhatsApp Documents"),
                ),
                ApkWatcher.defaultDirectories(),
            )
        } finally {
            Environment.clearTestDirectories()
        }
    }

    @Test
    fun startCreatesMissingDirectoryAndWatchesCloseAndMoveEvents() {
        val directory = File(folder.root, "WhatsApp Documents")
        FileObserver.clearTestWatchers()

        try {
            ApkWatcher(listOf(directory)) {}.start()

            assertTrue(directory.isDirectory)
            val observer = FileObserver.getStartedTestWatchers().single()
            assertEquals(directory.absolutePath, observer.testPath)
            assertEquals(FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO, observer.testMask)
        } finally {
            FileObserver.clearTestWatchers()
        }
    }

    @Test
    fun startReportsApksAlreadyInDirectory() {
        val directory = folder.newFolder("existing")
        val apk = File(directory, "before-start.apk").apply { writeText("apk") }
        File(directory, "ignored.txt").writeText("text")
        val finished = mutableListOf<File>()
        FileObserver.clearTestWatchers()

        try {
            ApkWatcher(listOf(directory), finished::add).start()

            assertEquals(listOf(apk), finished)
        } finally {
            FileObserver.clearTestWatchers()
        }
    }
}
