package dev.ame.polaris.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A tiny key/value store for app settings, kept in the same database as everything else
 * (so it works offline and can sync later). Values are plain text; structured values
 * like a saved place are stored as JSON.
 */
@Entity(tableName = "settings")
data class Setting(
    @PrimaryKey val key: String,
    val value: String,
)

@Dao
interface SettingDao {
    @Query("SELECT value FROM settings WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(setting: Setting)

    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun remove(key: String)
}

suspend fun SettingDao.put(key: String, value: String) = put(Setting(key, value))
