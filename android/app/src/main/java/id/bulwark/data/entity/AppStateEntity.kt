package id.bulwark.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Small technical key/value store (PRD table `app_state`), e.g. contract address, last sync time. */
@Entity(tableName = "app_state")
data class AppStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "key")
    val key: String,
    @ColumnInfo(name = "value")
    val value: String,
)
