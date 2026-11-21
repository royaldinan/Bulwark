package id.bulwark.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import id.bulwark.data.entity.LinkEventEntity

@Dao
interface LinkEventDao {

    @Insert
    suspend fun insert(event: LinkEventEntity): Long

    @Query("SELECT COUNT(*) FROM link_event WHERE action = :action")
    suspend fun countByAction(action: String): Int
}
