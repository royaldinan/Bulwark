package id.bulwark.ui

import kotlinx.coroutines.flow.Flow

enum class ReportKind { DOMAIN, APK }

data class HomeStats(
    val protectionActive: Boolean,
    val blockedLinkCount: Int,
    val quarantinedApkCount: Int,
    val lastSyncAt: Long?,
)

sealed interface ReportOutcome {
    data class Sent(val txHash: String) : ReportOutcome
    object AlreadyListed : ReportOutcome
    data class Failed(val message: String) : ReportOutcome
}

interface BulwarkRepository {
    fun observeHomeStats(): Flow<HomeStats>
    suspend fun report(kind: ReportKind, value: String): ReportOutcome
}
