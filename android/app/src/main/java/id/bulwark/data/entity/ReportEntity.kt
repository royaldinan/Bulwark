package id.bulwark.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A report and its real transaction hash (PRD table `report`). The Lapor screen reads this to show
 * the Polygonscan proof.
 */
@Entity(tableName = "report")
data class ReportEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "kind")
    val kind: String,
    @ColumnInfo(name = "hash")
    val hash: String,
    @ColumnInfo(name = "tx_hash")
    val txHash: String?,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
