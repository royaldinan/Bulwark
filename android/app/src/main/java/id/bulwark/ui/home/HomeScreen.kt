package id.bulwark.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.bulwark.data.AppDatabase
import id.bulwark.ui.BulwarkRepository
import id.bulwark.ui.HomeStats
import id.bulwark.ui.theme.BulwarkInfoCard
import id.bulwark.ui.theme.BulwarkNavy
import id.bulwark.ui.theme.BulwarkPrimaryButton
import id.bulwark.ui.theme.BulwarkStatTile
import id.bulwark.ui.theme.BulwarkStatusHero
import id.bulwark.ui.theme.BulwarkSurface
import id.bulwark.ui.theme.BulwarkTextSecondary
import id.bulwark.ui.theme.BulwarkTopBar
import id.bulwark.ui.theme.BulwarkWhite

@Composable
fun HomeRoute(
    repository: BulwarkRepository,
    onReport: () -> Unit,
    onOpenRegistry: () -> Unit,
    onManagePermissions: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
) {
    val stats by repository.observeHomeStats().collectAsState(initial = null)
    val context = LocalContext.current
    val database = remember(context) { AppDatabase.get(context) }
    val linkEvents by remember(database) { database.linkEventDao().observeRecent() }
        .collectAsState(initial = null)
    val apkEvents by remember(database) { database.apkEventDao().observeRecent() }
        .collectAsState(initial = null)
    val activities = remember(linkEvents, apkEvents) {
        val currentLinks = linkEvents
        val currentApks = apkEvents
        if (currentLinks == null || currentApks == null) {
            null
        } else {
            buildRecentActivities(currentLinks, currentApks)
        }
    }
    HomeScreen(
        stats = stats,
        activities = activities,
        onReport = onReport,
        onOpenRegistry = onOpenRegistry,
        onManagePermissions = onManagePermissions,
        modifier = modifier,
        bottomBar = bottomBar,
    )
}

@Composable
fun HomeScreen(
    stats: HomeStats?,
    activities: List<RecentActivity>?,
    onReport: () -> Unit,
    onOpenRegistry: () -> Unit,
    onManagePermissions: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BulwarkWhite),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            BulwarkTopBar(title = "Beranda")
            Spacer(Modifier.height(12.dp))
            BulwarkStatusHero(
                title = if (stats?.protectionActive == true) "Perlindungan aktif" else "Perlindungan belum aktif",
                subtitle = heroSubtitle(stats),
                actionLabel = "Atur izin",
                onAction = onManagePermissions,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BulwarkStatTile(
                    label = "Link diblokir",
                    value = formatCount(stats?.blockedLinkCount),
                    modifier = Modifier.weight(1f),
                )
                BulwarkStatTile(
                    label = "APK dikarantina",
                    value = formatCount(stats?.quarantinedApkCount),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Aktivitas terakhir",
                color = BulwarkNavy,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            when {
                activities == null -> Text(
                    text = "Memuat aktivitas...",
                    color = BulwarkTextSecondary,
                    fontSize = 13.sp,
                )
                activities.isEmpty() -> Text(
                    text = "Belum ada aktivitas.",
                    color = BulwarkTextSecondary,
                    fontSize = 13.sp,
                )
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    activities.forEach { activity -> RecentActivityRow(activity) }
                }
            }
            Spacer(Modifier.height(20.dp))
            BulwarkInfoCard(
                icon = Icons.Default.Info,
                title = "Daftar publik",
                subtitle = "Lihat entri di Polygonscan",
                actionLabel = "Buka",
                onAction = onOpenRegistry,
            )
            Spacer(Modifier.height(20.dp))
            BulwarkPrimaryButton(text = "Laporkan domain", onClick = onReport)
        }
        bottomBar()
    }
}

@Composable
private fun RecentActivityRow(activity: RecentActivity) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BulwarkSurface)
            .padding(14.dp),
    ) {
        Text(
            text = activity.name,
            color = BulwarkNavy,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = activity.actionLabel,
            color = BulwarkNavy,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "Sumber: ${activity.sourceLabel}",
            color = BulwarkTextSecondary,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = formatActivityTime(activity.createdAt),
            color = BulwarkTextSecondary,
            fontSize = 12.sp,
        )
    }
}

private fun heroSubtitle(stats: HomeStats?): String = when {
    stats == null -> "Memuat status perlindungan..."
    stats.protectionActive -> "Bulwark menjaga sendiri, tanpa perlu kamu curigai."
    else -> "Selesaikan izin agar perlindungan bisa menyala."
}

private fun formatCount(count: Int?): String = count?.toString() ?: "…"
