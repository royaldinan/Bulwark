package id.bulwark.ui.home

import id.bulwark.data.entity.ApkEventEntity
import id.bulwark.data.entity.LinkEventEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MAX_RECENT_ACTIVITIES = 5

data class RecentActivity(
    val name: String,
    val actionLabel: String,
    val sourceLabel: String,
    val createdAt: Long,
)

internal fun buildRecentActivities(
    links: List<LinkEventEntity>,
    apks: List<ApkEventEntity>,
): List<RecentActivity> {
    val linkActivities = links.map { event ->
        RecentActivity(
            name = event.domain,
            actionLabel = actionLabel(event.action),
            sourceLabel = sourceLabel(event.source),
            createdAt = event.createdAt,
        )
    }
    val apkActivities = apks.map { event ->
        RecentActivity(
            name = event.fileName,
            actionLabel = actionLabel(event.action),
            sourceLabel = "Pemantau file",
            createdAt = event.createdAt,
        )
    }
    return (linkActivities + apkActivities)
        .sortedByDescending { it.createdAt }
        .take(MAX_RECENT_ACTIVITIES)
}

internal fun formatActivityTime(
    timestamp: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String = activityTimeFormatter.format(Instant.ofEpochMilli(timestamp).atZone(zoneId))

private fun actionLabel(action: String): String = when (action.lowercase(Locale.ROOT)) {
    "blocked" -> "Diblokir"
    "allowed", "forwarded" -> "Diteruskan"
    "quarantined" -> "Dikarantina"
    "ignored" -> "Diabaikan"
    else -> "Tidak diketahui"
}

private fun sourceLabel(source: String): String = when (source.lowercase(Locale.ROOT)) {
    "registry" -> "Daftar publik"
    "safebrowsing" -> "Safe Browsing"
    "cache" -> "Penyimpanan lokal"
    "none" -> "Tidak diketahui"
    else -> source
}

private val activityTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern(
    "dd/MM/yyyy, HH:mm",
    Locale.forLanguageTag("id-ID"),
)
