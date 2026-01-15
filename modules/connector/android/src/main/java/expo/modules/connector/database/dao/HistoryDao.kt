package expo.modules.connector.database.dao

import androidx.room.*
import expo.modules.connector.database.entities.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    suspend fun getAll(): List<HistoryEntity>

    /**
     * Using REPLACE as an upsert strategy. While message IDs are guaranteed to be unique,
     * this serves as a safety measure to prevent application crashes from potential 
     * SQLiteConstraintExceptions if a duplicate ID ever occurs. In such cases, 
     * the existing entry is simply updated.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: HistoryEntity): Long

    @Query("DELETE FROM history WHERE message_id NOT IN (SELECT message_id FROM history ORDER BY timestamp DESC LIMIT :limit)")
    suspend fun trim(limit: Int): Int

    @Query("DELETE FROM history")
    suspend fun deleteAll(): Int
}