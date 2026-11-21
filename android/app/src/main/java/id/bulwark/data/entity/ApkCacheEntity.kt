package id.bulwark.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Cache of APK verdicts (PRD table `apk_cache`). */
@Entity(tableName = "apk_cache")
data class ApkCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "sha256")
    val sha256: String,
    @ColumnInfo(name = "verdict")
    val verdict: String,
    @ColumnInfo(name = "source")
    val source: String,
    @ColumnInfo(name = "checked_at")
    val checkedAt: Long,
    @ColumnInfo(name = "expires_at")
    val expiresAt: Long,
)
