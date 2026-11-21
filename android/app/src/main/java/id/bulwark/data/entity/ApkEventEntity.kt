package id.bulwark.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Every APK decision (PRD table `apk_event`). Source of the "APK dikarantina" counter. */
@Entity(tableName = "apk_event")
data class ApkEventEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "sha256")
    val sha256: String,
    @ColumnInfo(name = "file_name")
    val fileName: String,
    @ColumnInfo(name = "original_path")
    val originalPath: String,
    @ColumnInfo(name = "quarantine_path")
    val quarantinePath: String?,
    @ColumnInfo(name = "action")
    val action: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
