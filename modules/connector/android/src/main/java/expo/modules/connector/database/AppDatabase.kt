package expo.modules.connector.database

import androidx.room.Database
import androidx.room.RoomDatabase
import expo.modules.connector.database.dao.HistoryDao
import expo.modules.connector.database.entities.HistoryEntity

@Database(entities = [HistoryEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}
