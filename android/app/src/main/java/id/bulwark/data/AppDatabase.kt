package id.bulwark.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import id.bulwark.data.dao.ApkCacheDao
import id.bulwark.data.dao.ApkEventDao
import id.bulwark.data.dao.AppStateDao
import id.bulwark.data.dao.DomainCacheDao
import id.bulwark.data.dao.LinkEventDao
import id.bulwark.data.dao.ReportDao
import id.bulwark.data.entity.ApkCacheEntity
import id.bulwark.data.entity.ApkEventEntity
import id.bulwark.data.entity.AppStateEntity
import id.bulwark.data.entity.DomainCacheEntity
import id.bulwark.data.entity.LinkEventEntity
import id.bulwark.data.entity.ReportEntity

@Database(
    entities = [
        DomainCacheEntity::class,
        LinkEventEntity::class,
        ReportEntity::class,
        ApkCacheEntity::class,
        ApkEventEntity::class,
        AppStateEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun domainCacheDao(): DomainCacheDao
    abstract fun linkEventDao(): LinkEventDao
    abstract fun reportDao(): ReportDao
    abstract fun apkCacheDao(): ApkCacheDao
    abstract fun apkEventDao(): ApkEventDao
    abstract fun appStateDao(): AppStateDao

    companion object {
        private const val NAME = "bulwark.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    NAME,
                )
                    // Dev-only: no shipped schema yet, so a version bump rebuilds instead of crashing.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
