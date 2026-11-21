package id.bulwark.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import id.bulwark.data.dao.DomainCacheDao
import id.bulwark.data.dao.LinkEventDao
import id.bulwark.data.dao.ReportDao
import id.bulwark.data.entity.DomainCacheEntity
import id.bulwark.data.entity.LinkEventEntity
import id.bulwark.data.entity.ReportEntity

@Database(
    entities = [DomainCacheEntity::class, LinkEventEntity::class, ReportEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun domainCacheDao(): DomainCacheDao
    abstract fun linkEventDao(): LinkEventDao
    abstract fun reportDao(): ReportDao

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
