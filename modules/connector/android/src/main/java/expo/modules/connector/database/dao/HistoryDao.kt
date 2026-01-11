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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: HistoryEntity): Long

    @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY timestamp DESC LIMIT :limit)")
    suspend fun trim(limit: Int): Int

    @Query("DELETE FROM history")
    suspend fun deleteAll(): Int
}