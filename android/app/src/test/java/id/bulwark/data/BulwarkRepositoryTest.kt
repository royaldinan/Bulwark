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
import id.bulwark.data.entity.AppStateEntity
import id.bulwark.data.entity.DomainCacheEntity
import id.bulwark.data.model.MediationGateway
import id.bulwark.data.model.RemoteVerdict
import id.bulwark.data.model.ReportKind
import id.bulwark.data.model.Verdict
import id.bulwark.data.model.VerdictSource
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BulwarkRepositoryTest {

    @Test
    fun transportFailureDoesNotBecomeUnknownOrCacheEntry() = runTest {
        val cache = FakeDomainCacheDao()
        val gateway = FakeGateway(remoteVerdict = null)
        val repository = BulwarkRepository(
            database = FakeAppDatabase(cache),
            gateway = gateway,
            clock = { 1_000L },
        )

        val decision = repository.checkDomain("Example.com")

        assertNull("transport failure has no verdict", decision)
        assertEquals(1, gateway.domainChecks)
        assertTrue("transport failure must not populate the domain cache", cache.upserts.isEmpty())
    }

    @Test
    fun backendUnknownIsReturnedAndCachedAsAnOfficialVerdict() = runTest {
        val cache = FakeDomainCacheDao()
        val repository = BulwarkRepository(
            database = FakeAppDatabase(cache),
            gateway = FakeGateway(RemoteVerdict(Verdict.UNKNOWN, VerdictSource.NONE)),
            clock = { 1_000L },
        )

        val decision = repository.checkDomain("example.com")

        assertNotNull(decision)
        assertEquals(Verdict.UNKNOWN, decision?.verdict)
        assertEquals(VerdictSource.NONE, decision?.source)
        assertEquals(1, cache.upserts.size)
        assertEquals(Verdict.UNKNOWN.wire, cache.upserts.single().verdict)
        assertEquals(VerdictSource.NONE.wire, cache.upserts.single().source)
    }

    private class FakeDomainCacheDao : DomainCacheDao {
        val upserts = mutableListOf<DomainCacheEntity>()

        override suspend fun find(domainHash: String): DomainCacheEntity? =
            upserts.lastOrNull { it.domainHash == domainHash }

        override suspend fun upsert(entity: DomainCacheEntity) {
            upserts.removeAll { it.domainHash == entity.domainHash }
            upserts += entity
        }

        override suspend fun purgeExpired(now: Long) {
            upserts.removeAll { it.expiresAt < now }
        }
    }

    private class FakeAppStateDao : AppStateDao {
        private val values = mutableMapOf<String, String>()

        override suspend fun upsert(state: AppStateEntity) {
            values[state.key] = state.value
        }

        override suspend fun get(key: String): String? = values[key]

        override fun observeValue(key: String): Flow<String?> = flowOf(values[key])
    }

    private class FakeGateway(
        private val remoteVerdict: RemoteVerdict?,
    ) : MediationGateway {
        var domainChecks = 0

        override suspend fun checkDomain(
            domain: String,
            domainHash: String,
            prefix: String,
        ): RemoteVerdict? {
            domainChecks++
            return remoteVerdict
        }

        override suspend fun checkApk(sha256: String): RemoteVerdict? =
            throw UnsupportedOperationException("not used")

        override suspend fun report(kind: ReportKind, hash: String): String? =
            throw UnsupportedOperationException("not used")
    }

    private class FakeAppDatabase(
        private val domains: DomainCacheDao,
        private val appState: AppStateDao = FakeAppStateDao(),
    ) : AppDatabase() {
        override fun domainCacheDao(): DomainCacheDao = domains

        override fun linkEventDao(): LinkEventDao = unusedDao()

        override fun reportDao(): ReportDao = unusedDao()

        override fun apkCacheDao(): ApkCacheDao = unusedDao()

        override fun apkEventDao(): ApkEventDao = unusedDao()

        override fun appStateDao(): AppStateDao = appState

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
