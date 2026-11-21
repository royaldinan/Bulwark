package id.bulwark.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Every recorded link decision (PRD table `link_event`). This is the source of the "link diblokir"
 * counter on Beranda.
 */
@Entity(tableName = "link_event")
data class LinkEventEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "domain_hash")
    val domainHash: String,
    @ColumnInfo(name = "domain")
    val domain: String,
    @ColumnInfo(name = "action")
    val action: String,
    @ColumnInfo(name = "source")
    val source: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
