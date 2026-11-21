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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.bulwark.ui.BulwarkRepository
import id.bulwark.ui.HomeStats
import id.bulwark.ui.theme.BulwarkInfoCard
import id.bulwark.ui.theme.BulwarkPrimaryButton
import id.bulwark.ui.theme.BulwarkStatTile
import id.bulwark.ui.theme.BulwarkStatusHero
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
    HomeScreen(
        stats = stats,
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
            Spacer(Modifier.height(16.dp))
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

private fun heroSubtitle(stats: HomeStats?): String = when {
    stats == null -> "Memuat status perlindungan..."
    stats.protectionActive -> "Bulwark menjaga sendiri, tanpa perlu kamu curigai."
    else -> "Selesaikan izin agar perlindungan bisa menyala."
}

private fun formatCount(count: Int?): String = count?.toString() ?: "…"
