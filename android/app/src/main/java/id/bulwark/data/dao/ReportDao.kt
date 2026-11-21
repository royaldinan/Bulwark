package id.bulwark.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import id.bulwark.data.entity.ReportEntity

@Dao
interface ReportDao {

    @Insert
    suspend fun insert(report: ReportEntity): Long

    @Query("UPDATE report SET tx_hash = :txHash, status = :status WHERE id = :id")
    suspend fun updateResult(id: Long, txHash: String?, status: String)

    @Query("SELECT * FROM report WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): ReportEntity?

    @Query("SELECT * FROM report ORDER BY created_at DESC")
    suspend fun all(): List<ReportEntity>
}
