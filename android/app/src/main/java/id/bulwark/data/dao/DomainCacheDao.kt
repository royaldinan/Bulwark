package id.bulwark.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import id.bulwark.data.entity.DomainCacheEntity

@Dao
interface DomainCacheDao {

    @Query("SELECT * FROM domain_cache WHERE domain_hash = :domainHash LIMIT 1")
    suspend fun find(domainHash: String): DomainCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DomainCacheEntity)

    @Query("DELETE FROM domain_cache WHERE expires_at < :now")
    suspend fun purgeExpired(now: Long)
}
