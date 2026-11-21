package id.bulwark.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class QuarantineTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun sourceFile(name: String, content: String = "danger"): File =
        File(folder.root, name).apply { writeText(content) }

    @Test
    fun moveRelocatesFileIntoQuarantineDirectory() {
        val directory = folder.newFolder("quarantine")
        val quarantine = Quarantine(directory)
        val source = sourceFile("evil.apk")

        val stored = quarantine.move(source)

        assertNotNull(stored)
        assertFalse(source.exists())
        assertTrue(stored!!.isFile)
        assertEquals(directory.canonicalFile, stored.parentFile!!.canonicalFile)
        assertEquals("evil.apk", stored.name)
        assertEquals("danger", stored.readText())
    }

    @Test
    fun moveReturnsNullWhenSourceMissing() {
        val quarantine = Quarantine(folder.newFolder("quarantine"))
        val missing = File(folder.root, "absent.apk")
        assertNull(quarantine.move(missing))
    }

    @Test
    fun moveKeepsBothFilesWhenNamesCollide() {
        val directory = folder.newFolder("quarantine")
        val quarantine = Quarantine(directory)

        val first = quarantine.move(sourceFile("dup.apk", "one"))
        val second = quarantine.move(sourceFile("dup.apk", "two"))

        assertNotNull(first)
        assertNotNull(second)
        assertNotEquals(first!!.name, second!!.name)
        assertEquals(2, quarantine.list().size)
    }

    @Test
    fun containsRecognisesStoredFile() {
        val directory = folder.newFolder("quarantine")
        val quarantine = Quarantine(directory)
        val stored = quarantine.move(sourceFile("a.apk"))!!

        assertTrue(quarantine.contains(stored))
        assertFalse(quarantine.contains(File(folder.root, "other.apk")))
    }

    @Test
    fun listIsEmptyBeforeAnyMove() {
        val quarantine = Quarantine(folder.newFolder("quarantine"))
        assertTrue(quarantine.list().isEmpty())
    }
}
