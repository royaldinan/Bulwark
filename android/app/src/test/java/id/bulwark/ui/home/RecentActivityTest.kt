package id.bulwark.ui.home

import id.bulwark.data.entity.ApkEventEntity
import id.bulwark.data.entity.LinkEventEntity
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentActivityTest {

    @Test
    fun keepsOnlyFiveNewestEventsAcrossLinksAndApks() {
        val links = listOf(
            linkEvent(id = 1, createdAt = 1_000),
            linkEvent(id = 2, createdAt = 3_000),
            linkEvent(id = 3, createdAt = 5_000),
            linkEvent(id = 4, createdAt = 7_000),
        )
        val apks = listOf(
            apkEvent(id = 1, createdAt = 2_000),
            apkEvent(id = 2, createdAt = 4_000),
            apkEvent(id = 3, createdAt = 6_000),
            apkEvent(id = 4, createdAt = 8_000),
        )

        val result = buildRecentActivities(links, apks)

        assertEquals(5, result.size)
        assertEquals(
            listOf("file-4.apk", "link-4.example", "file-3.apk", "link-3.example", "file-2.apk"),
            result.map { it.name },
        )
    }

    @Test
    fun mapsRecordedActionsAndSourcesToIndonesianLabels() {
        val result = buildRecentActivities(
            links = listOf(
                linkEvent(id = 1, createdAt = 4_000, action = "blocked", source = "registry"),
                linkEvent(id = 2, createdAt = 3_000, action = "allowed", source = "safebrowsing"),
                linkEvent(id = 3, createdAt = 2_000, action = "blocked", source = "cache"),
            ),
            apks = listOf(
                apkEvent(id = 1, createdAt = 5_000, action = "quarantined"),
                apkEvent(id = 2, createdAt = 1_000, action = "ignored"),
            ),
        )

        assertEquals(
            listOf("Dikarantina", "Diblokir", "Diteruskan", "Diblokir", "Diabaikan"),
            result.map { it.actionLabel },
        )
        assertEquals(
            listOf("Pemantau file", "Daftar publik", "Safe Browsing", "Penyimpanan lokal", "Pemantau file"),
            result.map { it.sourceLabel },
        )
    }

    @Test
    fun returnsEmptyListWhenRoomHasNoEvents() {
        assertTrue(buildRecentActivities(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun formatsTimestampInTheProvidedLocalZone() {
        val timestamp = Instant.parse("2024-01-01T00:00:00Z").toEpochMilli()

        assertEquals(
            "01/01/2024, 07:00",
            formatActivityTime(timestamp, ZoneId.of("Asia/Jakarta")),
        )
    }

    private fun linkEvent(
        id: Long,
        createdAt: Long,
        action: String = "blocked",
        source: String = "registry",
    ) = LinkEventEntity(
        id = id,
        domainHash = "hash-$id",
        domain = "link-$id.example",
        action = action,
        source = source,
        createdAt = createdAt,
    )

    private fun apkEvent(
        id: Long,
        createdAt: Long,
        action: String = "quarantined",
    ) = ApkEventEntity(
        id = id,
        sha256 = "hash-$id",
        fileName = "file-$id.apk",
        originalPath = "/Download/file-$id.apk",
        quarantinePath = null,
        action = action,
        createdAt = createdAt,
    )
}
