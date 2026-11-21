package id.bulwark.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Cache of domain verdicts so the tunnel does not call the API on every query (PRD table `domain_cache`). */
@Entity(tableName = "domain_cache")
data class DomainCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "domain_hash")
    val domainHash: String,
    @ColumnInfo(name = "domain")
    val domain: String,
    @ColumnInfo(name = "verdict")
    val verdict: String,
    @ColumnInfo(name = "source")
    val source: String,
    @ColumnInfo(name = "checked_at")
    val checkedAt: Long,
    @ColumnInfo(name = "expires_at")
    val expiresAt: Long,
)
