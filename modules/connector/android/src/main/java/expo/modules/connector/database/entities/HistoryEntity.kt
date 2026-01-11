package expo.modules.connector.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "message_id")
    val messageId: String,

    @ColumnInfo(name = "type")
    val type: Int,

    @ColumnInfo(name = "encrypted_payload")
    val encryptedPayload: String,

    @ColumnInfo(name = "address")
    val address: String?,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long
)