package id.bulwark.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import id.bulwark.data.entity.ApkCacheEntity

@Dao
interface ApkCacheDao {

    @Query("SELECT * FROM apk_cache WHERE sha256 = :sha256 LIMIT 1")
    suspend fun find(sha256: String): ApkCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ApkCacheEntity)

    @Query("DELETE FROM apk_cache WHERE expires_at < :now")
    suspend fun purgeExpired(now: Long)
}
