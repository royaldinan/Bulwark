package id.bulwark.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import id.bulwark.data.entity.ApkEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ApkEventDao {

    @Insert
    suspend fun insert(event: ApkEventEntity): Long

    @Query("SELECT COUNT(*) FROM apk_event WHERE action = :action")
    suspend fun countByAction(action: String): Int

    @Query("SELECT COUNT(*) FROM apk_event WHERE action = :action")
    fun observeCountByAction(action: String): Flow<Int>

    @Query("SELECT * FROM apk_event ORDER BY created_at DESC, id DESC LIMIT 5")
    fun observeRecent(): Flow<List<ApkEventEntity>>
}
