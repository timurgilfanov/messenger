package timur.gilfanov.messenger.data.source.local.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.data.source.local.database.entity.SettingEntity

@Dao
interface SettingsDao {

    @Query("SELECT * FROM settings WHERE userId = :userId")
    fun observeAllByUser(userId: String): Flow<List<SettingEntity>>

    @Query("SELECT * FROM settings WHERE userId = :userId AND key = :key")
    suspend fun get(userId: String, key: String): SettingEntity?

    @Query("SELECT * FROM settings WHERE userId = :userId")
    suspend fun getAll(userId: String): List<SettingEntity>

    @Query("SELECT * FROM settings WHERE localVersion > syncedVersion")
    suspend fun getUnsynced(): List<SettingEntity>

    @Upsert
    suspend fun upsert(setting: SettingEntity)

    @Upsert
    suspend fun upsert(settings: List<SettingEntity>)
}
