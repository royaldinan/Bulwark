package id.bulwark.data

import androidx.room.DatabaseConfiguration
import androidx.room.InvalidationTracker
import androidx.sqlite.db.SupportSQLiteOpenHelper
import id.bulwark.data.dao.ApkCacheDao
import id.bulwark.data.dao.ApkEventDao
import id.bulwark.data.dao.AppStateDao
import id.bulwark.data.dao.DomainCacheDao
import id.bulwark.data.dao.LinkEventDao
import id.bulwark.data.dao.ReportDao
import id.bulwark.data.entity.ReportEntity
import id.bulwark.data.model.MediationGateway
import id.bulwark.data.model.RemoteVerdict
import id.bulwark.data.model.ReportAlreadyListedException
import id.bulwark.data.model.ReportKind
import id.bulwark.data.model.ReportStatus
import id.bulwark.ui.ReportOutcome
import java.lang.reflect.Proxy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import id.bulwark.ui.ReportKind as UiReportKind

class ReportDuplicateTest {

    @Test
    fun duplicateDomainIsStoredAndMappedAsAlreadyListed() = runTest {
        val database = FakeAppDatabase()
        val repository = RoomBulwarkRepository(
            database = database,
            core = BulwarkRepository(database, FakeGateway { throw ReportAlreadyListedException() }),
        )

        val outcome = repository.report(UiReportKind.DOMAIN, "Example.com")

        assertEquals(ReportOutcome.AlreadyListed, outcome)
        val row = database.reports.rows.single()
        assertEquals(ReportKind.DOMAIN.wire, row.kind)
        assertEquals(ReportStatus.ALREADY_LISTED.wire, row.status)
        assertNull(row.txHash)
    }

    @Test
    fun duplicateApkIsStoredAndMappedAsAlreadyListed() = runTest {
        val database = FakeAppDatabase()
        val repository = RoomBulwarkRepository(
            database = database,
            core = BulwarkRepository(database, FakeGateway { throw ReportAlreadyListedException() }),
        )

        val outcome = repository.report(UiReportKind.APK, "ab".repeat(32))

        assertEquals(ReportOutcome.AlreadyListed, outcome)
        val row = database.reports.rows.single()
        assertEquals(ReportKind.APK.wire, row.kind)
        assertEquals(ReportStatus.ALREADY_LISTED.wire, row.status)
        assertNull(row.txHash)
    }

    @Test
    fun unavailableGatewayKeepsTheExistingFailureOutcome() = runTest {
        val database = FakeAppDatabase()
        val repository = RoomBulwarkRepository(
            database = database,
            core = BulwarkRepository(database, FakeGateway { null }),
        )

        val outcome = repository.report(UiReportKind.APK, "ab".repeat(32))

        assertTrue(outcome is ReportOutcome.Failed)
        assertEquals(
            "Laporan gagal dikirim. Periksa koneksi lalu coba lagi.",
            (outcome as ReportOutcome.Failed).message,
        )
        assertEquals(ReportStatus.FAILED.wire, database.reports.rows.single().status)
    }

    private class FakeGateway(
        private val result: () -> String?,
    ) : MediationGateway {
        override suspend fun checkDomain(
            domain: String,
            domainHash: String,
            prefix: String,
        ): RemoteVerdict? = throw UnsupportedOperationException("not used")

        override suspend fun checkApk(sha256: String): RemoteVerdict? =
            throw UnsupportedOperationException("not used")

        override suspend fun report(kind: ReportKind, hash: String): String? = result()
    }

    private class FakeReportDao : ReportDao {
        val rows = mutableListOf<ReportEntity>()

        override suspend fun insert(report: ReportEntity): Long {
            val id = (rows.maxOfOrNull { it.id } ?: 0L) + 1L
            rows += report.copy(id = id)
            return id
        }

        override suspend fun updateResult(id: Long, txHash: String?, status: String) {
            val index = rows.indexOfFirst { it.id == id }
            rows[index] = rows[index].copy(txHash = txHash, status = status)
        }

        override suspend fun findById(id: Long): ReportEntity? = rows.singleOrNull { it.id == id }

        override suspend fun all(): List<ReportEntity> = rows.toList()
    }

    private class FakeAppDatabase : AppDatabase() {
        val reports = FakeReportDao()

        override fun domainCacheDao(): DomainCacheDao = unusedDao()

        override fun linkEventDao(): LinkEventDao = unusedDao()

        override fun reportDao(): ReportDao = reports

        override fun apkCacheDao(): ApkCacheDao = unusedDao()

        override fun apkEventDao(): ApkEventDao = unusedDao()

        override fun appStateDao(): AppStateDao = unusedDao()

        override fun clearAllTables() = Unit

        override fun createInvalidationTracker(): InvalidationTracker = InvalidationTracker(this)

        override fun createOpenHelper(configuration: DatabaseConfiguration): SupportSQLiteOpenHelper =
            throw UnsupportedOperationException("not used")

        @Suppress("UNCHECKED_CAST")
        private inline fun <reified T> unusedDao(): T =
            Proxy.newProxyInstance(
                T::class.java.classLoader,
                arrayOf(T::class.java),
            ) { _, _, _ -> throw UnsupportedOperationException("not used") } as T
    }
}
