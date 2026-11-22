package id.bulwark.data

import id.bulwark.data.model.LinkAction
import id.bulwark.data.model.ReportStatus
import id.bulwark.ui.BulwarkRepository as UiBulwarkRepository
import id.bulwark.ui.HomeStats
import id.bulwark.ui.ReportKind as UiReportKind
import id.bulwark.ui.ReportOutcome
import id.bulwark.data.model.ApkAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Real implementation of the UI's [UiBulwarkRepository] contract, backed by Room (`data/`).
 *
 * Every number comes from the database:
 * - `blockedLinkCount`  = COUNT(*) link_event WHERE action='blocked'
 * - `quarantinedApkCount` = COUNT(*) apk_event WHERE action='quarantined'
 * - `lastSyncAt` = app_state['last_sync_at']
 * - `protectionActive` = live state published by [id.bulwark.vpn.BulwarkVpnService] via [ProtectionState]
 *
 * Reporting is delegated to the core repository (cache + MediationApi); a tx hash is returned only
 * when the API actually returned one.
 */
class RoomBulwarkRepository(
    database: AppDatabase,
    private val core: BulwarkRepository,
) : UiBulwarkRepository {

    private val linkEvents = database.linkEventDao()
    private val apkEvents = database.apkEventDao()
    private val appState = database.appStateDao()

    override fun observeHomeStats(): Flow<HomeStats> = combine(
        linkEvents.observeCountByAction(LinkAction.BLOCKED.wire),
        apkEvents.observeCountByAction(ApkAction.QUARANTINED.wire),
        appState.observeValue(BulwarkRepository.KEY_LAST_SYNC),
        ProtectionState.active,
    ) { blocked, quarantined, lastSync, protection ->
        HomeStats(
            protectionActive = protection,
            blockedLinkCount = blocked,
            quarantinedApkCount = quarantined,
            lastSyncAt = lastSync?.toLongOrNull(),
        )
    }

    override suspend fun report(kind: UiReportKind, value: String): ReportOutcome {
        val row = when (kind) {
            UiReportKind.DOMAIN -> {
                val domain = ReportValidator.validDomain(value)
                    ?: return ReportOutcome.Failed("Alamat domain tidak valid.")
                core.reportDomain(domain)
            }

            UiReportKind.APK -> {
                val sha256 = ReportValidator.validSha256(value)
                    ?: return ReportOutcome.Failed("Hash APK tidak valid.")
                core.reportApk(sha256)
            }
        }

        val txHash = row.txHash
        return when {
            row.status == ReportStatus.SENT.wire && txHash != null -> ReportOutcome.Sent(txHash)
            row.status == ReportStatus.ALREADY_LISTED.wire -> ReportOutcome.AlreadyListed
            else -> ReportOutcome.Failed("Laporan gagal dikirim. Periksa koneksi lalu coba lagi.")
        }
    }
}
